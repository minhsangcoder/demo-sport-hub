package com.sporthub.booking.service;

import com.sporthub.booking.repository.BookingRepository;
import com.sporthub.booking.web.BookingDtos.Booking;
import com.sporthub.common.exception.*;
import com.sporthub.common.security.RemoteIdentity.Caller;
import com.sporthub.common.security.Signatures;
import jakarta.validation.constraints.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;

@Service @Transactional(readOnly=true)
public class GroupAttendanceService {
 public record Ticket(UUID groupId,UUID memberId,String token,String qrSvg,String qrPngDataUrl,Instant expiresAt){}
 public record Scan(@NotBlank @Size(max=1500) String token){}
 public record Manual(@NotNull UUID memberId,@NotBlank @Size(min=20,max=500) String reason){}
 public record Attendance(UUID groupId,UUID memberId,Instant checkedInAt,UUID checkedInBy,String method){}
 private final BookingRepository repo;private final BookingDependencies dependencies;private final BookingLifecycle lifecycle;private final Clock clock;private final CheckinQr qr;private final String secret;private final jakarta.validation.Validator validator;private final LocalAppliedCollections collections;
 public GroupAttendanceService(BookingRepository repo,BookingDependencies dependencies,BookingLifecycle lifecycle,Clock clock,CheckinQr qr,@Value("${JWT_SECRET}") String secret,jakarta.validation.Validator validator,LocalAppliedCollections collections){this.repo=repo;this.dependencies=dependencies;this.lifecycle=lifecycle;this.clock=clock;this.qr=qr;this.secret=secret;this.validator=validator;this.collections=collections;}
 public Ticket own(UUID group,Caller actor){var member=memberForUser(group,actor.id());return ticket(group,(UUID)member.get("id"));}
 public List<Ticket> tickets(UUID group,Caller actor){owner(group,actor);var b=eligible(group);return repo.jdbc().queryForList("SELECT id FROM group_members WHERE group_id=? AND active ORDER BY joined_at,id",UUID.class,group).stream().map(id->ticket(b,id)).toList();}
 public List<Map<String,Object>> status(UUID group,Caller actor,String token){
  var b=repo.find(group);if(!isOwner(group,actor))dependencies.facilityPermission(b.facilityId(),actor,token,"BOOKING_READ");
  return repo.jdbc().queryForList("SELECT m.id AS member_id,m.display_name,a.checked_in_at,a.checked_in_by,a.method FROM group_members m LEFT JOIN group_member_attendance a ON a.member_id=m.id WHERE m.group_id=? AND m.active ORDER BY m.joined_at,m.id",group);
 }
 @Transactional public Attendance scan(UUID group,Scan input,Caller actor,String auth){
  if(input==null||!validator.validate(input).isEmpty())throw new IllegalArgumentException("A member token is required");
  var b=locked(group,actor,auth);UUID memberId=verify(b,input.token());return checkin(b,memberId,actor,"MEMBER_QR",null);
 }
 @Transactional public Attendance manual(UUID group,Manual input,Caller actor,String auth){
  if(input==null||!validator.validate(input).isEmpty()||input.reason().trim().length()<20)throw new IllegalArgumentException("Member and manual presence reason are required");
  return checkin(locked(group,actor,auth),input.memberId(),actor,"MANUAL_MEMBER",input.reason().trim());
 }
 private Booking locked(UUID group,Caller actor,String auth){var b=repo.find(group);dependencies.facilityPermission(b.facilityId(),actor,auth,"BOOKING_CHECK_IN");repo.lock(b.courtId());return eligible(group);}
 private Attendance checkin(Booking b,UUID id,Caller actor,String method,String reason){
  active(b.id(),id);Instant now=clock.instant();if(now.isBefore(b.startsAt().minusSeconds(1800))||!now.isBefore(b.endsAt()))throw new ConflictException("Member check-in is permitted from 30 minutes before play until booking end");
  if(repo.jdbc().queryForObject("SELECT count(*) FROM group_member_attendance WHERE member_id=?",Integer.class,id)>0)throw new ConflictException("Member is already checked in");
  if(b.status().equals("CONFIRMED"))lifecycle.transition(b,"CHECKED_IN",actor.id(),"GROUP_FIRST_MEMBER_CHECKED_IN",Map.of("memberId",id,"checkinMethod",method),now,null);
  repo.jdbc().update("INSERT INTO group_member_attendance(member_id,group_id,checked_in_at,checked_in_by,method,reason) VALUES(?,?,?,?,?,?)",id,b.id(),Timestamp.from(now),actor.id(),method,reason);
  repo.history(b.id(),actor.id(),"GROUP_MEMBER_CHECKED_IN",Map.of("memberId",id,"method",method,"checkedInAt",now));return new Attendance(b.id(),id,now,actor.id(),method);
 }
 private Booking eligible(UUID group){
  var b=repo.find(group);if(!Set.of("CONFIRMED","CHECKED_IN").contains(b.status())||!clock.instant().isBefore(b.endsAt())||!Boolean.TRUE.equals(repo.jdbc().queryForObject("SELECT EXISTS(SELECT 1 FROM booking_groups WHERE id=? AND state='CONFIRMED')",Boolean.class,group)))throw new ConflictException("Confirmed group entitlement is required");
  if(repo.jdbc().queryForObject("SELECT coalesce(sum(amount_paid),0) FROM group_members WHERE group_id=? AND active",java.math.BigDecimal.class,group).compareTo(b.amount())!=0)throw new ConflictException("Verified group funding is incomplete");
  collections.requireFullyPaid(b);
  if(repo.jdbc().queryForObject("SELECT count(*) FROM transfer_leases WHERE booking_id=? AND state IN ('ACTIVE','LOCKED')",Integer.class,group)>0)throw new ConflictException("Unresolved transfer blocks group attendance");return b;
 }
 private Ticket ticket(UUID group,UUID id){return ticket(eligible(group),id);}
 private Ticket ticket(Booking b,UUID id){UUID group=b.id();var m=active(group,id);Instant expires=clock.instant().plusSeconds(120);if(expires.isAfter(b.endsAt()))expires=b.endsAt();String raw=group+"|"+id+"|"+m.get("user_id")+"|"+b.facilityId()+"|"+b.courtId()+"|"+b.startsAt()+"|"+b.endsAt()+"|"+b.checkinTokenVersion()+"|"+expires.getEpochSecond();String code=Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8))+"."+Signatures.hmac(secret,"GROUP_MEMBER_CHECKIN|"+raw);return new Ticket(group,id,code,qr.svg(code),qr.pngDataUrl(code),expires);}
 private UUID verify(Booking b,String code){try{String[] parts=code.split("\\.");if(parts.length!=2)throw new IllegalArgumentException();String raw=new String(Base64.getUrlDecoder().decode(parts[0]),StandardCharsets.UTF_8);if(!Signatures.matches(Signatures.hmac(secret,"GROUP_MEMBER_CHECKIN|"+raw),parts[1]))throw new IllegalArgumentException();String[] f=raw.split("\\|");if(f.length!=9||!f[0].equals(b.id().toString())||!f[3].equals(b.facilityId().toString())||!f[4].equals(b.courtId().toString())||!f[5].equals(b.startsAt().toString())||!f[6].equals(b.endsAt().toString())||Integer.parseInt(f[7])!=b.checkinTokenVersion()||!Instant.ofEpochSecond(Long.parseLong(f[8])).isAfter(clock.instant()))throw new IllegalArgumentException();UUID id=UUID.fromString(f[1]);if(!f[2].equals(active(b.id(),id).get("user_id").toString()))throw new IllegalArgumentException();return id;}catch(Exception invalid){throw new ForbiddenException("Member QR is invalid, expired or belongs to another entitlement");}}
 private Map<String,Object> active(UUID group,UUID id){var rows=repo.jdbc().queryForList("SELECT id,user_id FROM group_members WHERE id=? AND group_id=? AND active",id,group);if(rows.isEmpty())throw new ResourceNotFoundException("Active group member not found");return rows.getFirst();}
 private Map<String,Object> memberForUser(UUID group,UUID user){var rows=repo.jdbc().queryForList("SELECT id FROM group_members WHERE group_id=? AND user_id=? AND active",group,user);if(rows.isEmpty())throw new ForbiddenException("Active group membership is required");return rows.getFirst();}
 private boolean isOwner(UUID group,Caller actor){return Boolean.TRUE.equals(repo.jdbc().queryForObject("SELECT EXISTS(SELECT 1 FROM booking_groups WHERE id=? AND owner_id=?)",Boolean.class,group,actor.id()));}
 private void owner(UUID group,Caller actor){if(!isOwner(group,actor))throw new ForbiddenException("Only Booking Owner may read all member tickets");}
}
