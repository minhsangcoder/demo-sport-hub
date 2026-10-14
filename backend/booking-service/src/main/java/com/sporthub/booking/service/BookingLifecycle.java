package com.sporthub.booking.service;

import com.sporthub.booking.repository.BookingRepository;
import com.sporthub.booking.web.BookingDtos.Booking;
import com.sporthub.common.exception.ConflictException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.*;

/** All callers hold the court lock; status, reservation and history commit together. */
@Component
public class BookingLifecycle {
    private static final Map<String,Set<String>> NEXT=Map.of(
        "PENDING",Set.of("CONFIRMED","CANCELLED","EXPIRED"),
        "CONFIRMED",Set.of("CHECKED_IN","CANCELLED"),
        "CHECKED_IN",Set.of("COMPLETED"));
    private final BookingRepository repo;
    private final Clock clock;
    public BookingLifecycle(BookingRepository repo,Clock clock){this.repo=repo;this.clock=clock;}

    public boolean hasLiveHold(Booking booking,Instant now){
        return repo.jdbc().queryForObject("""
            SELECT count(*) FROM slot_reservations r JOIN bookings b ON b.reservation_id=r.id
            WHERE b.id=? AND r.state='HOLD' AND r.expires_at>? AND r.expires_at=b.hold_expires_at
              AND r.court_id=b.court_id AND r.facility_id=b.facility_id
              AND r.starts_at=b.starts_at AND r.ends_at=b.ends_at AND r.holder_id=b.current_holder_id
            """,Integer.class,booking.id(),Timestamp.from(now))==1;
    }

    @Transactional(propagation=Propagation.MANDATORY)
    public Booking transition(Booking booking,String next,UUID actor,String action,Object details,Instant at,UUID payment){
        if(!NEXT.getOrDefault(booking.status(),Set.of()).contains(next))
            throw new ConflictException("Booking transition is not permitted");
        Instant now=clock.instant();
        if(next.equals("CONFIRMED")){
            if(at==null||!at.isBefore(booking.holdExpiresAt())||!now.isBefore(booking.holdExpiresAt())||!hasLiveHold(booking,now))
                throw new ConflictException("Booking no longer has a live payment hold");
            int changed=repo.jdbc().update("""
                UPDATE slot_reservations SET state='BOOKED'
                WHERE id=(SELECT reservation_id FROM bookings WHERE id=?) AND state='HOLD' AND expires_at>?
                """,booking.id(),Timestamp.from(now));
            if(changed!=1)throw new ConflictException("Reservation changed before confirmation");
        }else if(next.equals("CANCELLED")||next.equals("EXPIRED")){
            if(next.equals("EXPIRED")&&now.isBefore(booking.holdExpiresAt()))throw new ConflictException("Payment hold has not expired");
            int changed=repo.jdbc().update("UPDATE slot_reservations SET state='RELEASED' WHERE id=(SELECT reservation_id FROM bookings WHERE id=?) AND state=?",
                booking.id(),booking.status().equals("PENDING")?"HOLD":"BOOKED");
            if(changed!=1)throw new ConflictException("Reservation is inconsistent with booking status");
        }
        String source=payment!=null||action.equals("GROUP_CONFIRMED")?"PAYMENT_EVENT":actor==null?"SYSTEM_EXPIRY":"BOOKING_API";
        repo.auditContext(actor,source);
        int changed=switch(next){
            case "CONFIRMED" -> repo.jdbc().update("UPDATE bookings SET status=?,payment_id=?,paid_at=?,checkin_token_version=checkin_token_version+1,version=version+1,updated_at=NOW() WHERE id=? AND status=? AND version=?",next,payment,Timestamp.from(at),booking.id(),booking.status(),booking.version());
            case "CHECKED_IN" -> repo.jdbc().update("UPDATE bookings SET status=?,checked_in_at=?,version=version+1,updated_at=NOW() WHERE id=? AND status=? AND version=?",next,Timestamp.from(at),booking.id(),booking.status(),booking.version());
            case "COMPLETED" -> repo.jdbc().update("UPDATE bookings SET status=?,completed_at=?,version=version+1,updated_at=NOW() WHERE id=? AND status=? AND version=?",next,Timestamp.from(at),booking.id(),booking.status(),booking.version());
            default -> repo.jdbc().update("UPDATE bookings SET status=?,checkin_token_version=checkin_token_version+1,version=version+1,updated_at=NOW() WHERE id=? AND status=? AND version=?",next,booking.id(),booking.status(),booking.version());
        };
        if(changed!=1)throw new ConflictException("Booking changed before transition");
        var value=repo.read(repo.write(details));var audit=value.isObject()?((com.fasterxml.jackson.databind.node.ObjectNode)value).deepCopy():new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode();
        audit.put("previousStatus",booking.status()).put("newStatus",next).put("previousVersion",booking.version()).put("newVersion",booking.version()+1).put("changedAt",now.toString()).put("effectiveAt",at==null?now.toString():at.toString()).put("changeSource",source);
        repo.history(booking.id(),actor,action,audit);
        return repo.find(booking.id());
    }
}
