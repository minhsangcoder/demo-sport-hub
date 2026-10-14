package com.sporthub.booking.service;
import com.sporthub.booking.repository.BookingRepository;
import com.sporthub.common.exception.*;
import com.sporthub.common.security.RemoteIdentity.Caller;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.sql.Timestamp;
import java.util.*;
/** Hooks run inside the existing booking-group transaction, after its court lock. */
@Component @Transactional(propagation=org.springframework.transaction.annotation.Propagation.MANDATORY)
public class PlayGroupMembership {
 private final BookingRepository repo;private final Clock clock;
 public PlayGroupMembership(BookingRepository repo,Clock clock){this.repo=repo;this.clock=clock;}
 public UUID root(UUID booking){var ids=repo.jdbc().queryForList("SELECT play_group_id FROM play_group_bookings WHERE booking_group_id=?",UUID.class,booking);if(ids.isEmpty())throw new ResourceNotFoundException("Play group not found");return ids.getFirst();}
 public void created(UUID booking,String name,Caller actor,UUID existing){
  UUID root=existing==null?booking:existing;
  if(existing==null)repo.jdbc().update("INSERT INTO play_groups(id,origin_group_id,owner_id,name) VALUES(?,?,?,?)",root,booking,actor.id(),name);
  else {var row=lock(root);if(!row.get("owner_id").equals(actor.id()))throw new ForbiddenException("Only Booking Owner may reuse the play group");if(!row.get("state").equals("KEPT"))throw new ConflictException("Only a kept group may be reused");}
  repo.jdbc().update("INSERT INTO play_group_bookings(booking_group_id,play_group_id) VALUES(?,?)",booking,root);
  join(root,actor);audit(root,actor.id(),existing==null?"PLAY_GROUP_CREATED":"PLAY_GROUP_BOOKING_LINKED",Map.of("bookingId",booking));
 }
 public void joined(UUID booking,Caller actor){var ids=repo.jdbc().queryForList("SELECT play_group_id FROM play_group_bookings WHERE booking_group_id=?",UUID.class,booking);if(!ids.isEmpty())join(ids.getFirst(),actor);}
 public void exited(UUID booking,UUID user,UUID actor){var ids=repo.jdbc().queryForList("SELECT play_group_id FROM play_group_bookings WHERE booking_group_id=?",UUID.class,booking);if(ids.isEmpty())return;var row=lock(ids.getFirst());if(row.get("state").equals("OPEN")&&row.get("origin_group_id").equals(booking)){repo.jdbc().update("UPDATE play_group_members SET active=false,left_at=? WHERE play_group_id=? AND user_id=? AND active",Timestamp.from(clock.instant()),ids.getFirst(),user);repo.jdbc().update("UPDATE play_groups SET version=version+1 WHERE id=?",ids.getFirst());audit(ids.getFirst(),actor,"PLAY_MEMBER_LEFT_BOOKING",Map.of("userId",user));}}
 private void join(UUID root,Caller actor){var group=lock(root);if(group.get("state").equals("DISSOLVED"))throw new ConflictException("Play group is dissolved; new membership is closed");boolean current=Boolean.TRUE.equals(repo.jdbc().queryForObject("SELECT EXISTS(SELECT 1 FROM play_group_members WHERE play_group_id=? AND user_id=? AND active)",Boolean.class,root,actor.id()));if(current)return;
  if(repo.jdbc().queryForObject("SELECT count(*) FROM play_group_members WHERE play_group_id=? AND active",Integer.class,root)>=50)throw new ConflictException("Play group has reached its member limit");
  repo.jdbc().update("INSERT INTO play_group_members(play_group_id,user_id,display_name,joined_at) VALUES(?,?,?,?) ON CONFLICT(play_group_id,user_id) DO UPDATE SET active=true,display_name=EXCLUDED.display_name,joined_at=EXCLUDED.joined_at,left_at=NULL",root,actor.id(),actor.fullName(),Timestamp.from(clock.instant()));repo.jdbc().update("UPDATE play_groups SET version=version+1 WHERE id=?",root);audit(root,actor.id(),"PLAY_MEMBER_JOINED",Map.of());
 }
 public Map<String,Object> lock(UUID root){var rows=repo.jdbc().queryForList("SELECT * FROM play_groups WHERE id=? FOR UPDATE",root);if(rows.isEmpty())throw new ResourceNotFoundException("Play group not found");return rows.getFirst();}
 public void audit(UUID root,UUID actor,String action,Object details){repo.jdbc().update("INSERT INTO play_group_audit(play_group_id,actor_id,action,details,occurred_at) VALUES(?,?,?,?::jsonb,?)",root,actor,action,repo.write(details),Timestamp.from(clock.instant()));}
}
