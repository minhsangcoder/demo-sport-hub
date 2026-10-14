package com.sporthub.booking.service;

import com.sporthub.booking.repository.BookingRepository;
import com.sporthub.common.exception.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.UUID;
import static com.sporthub.common.schedule.ConfigurationGuardProtocol.*;

@Service
public class ConfigurationGuardService {
    private final BookingRepository repo;
    private final Clock clock;
    public ConfigurationGuardService(BookingRepository repo,Clock clock) {this.repo=repo;this.clock=clock;}
    public void lockFacility(UUID facility) {
        repo.jdbc().queryForObject("SELECT pg_advisory_xact_lock(hashtextextended(?,0))",Object.class,"CONFIGURATION|"+facility);
    }
    /** Caller must acquire this before its FINAL quote and keep it until reservation commit. */
    public void lockReservationFacility(UUID facility) {
        repo.jdbc().queryForObject("SELECT pg_advisory_xact_lock_shared(hashtextextended(?,0))",Object.class,"CONFIGURATION|"+facility);
    }
    public void requireAccepting(UUID facility) {
        lockReservationFacility(facility);
        if(repo.jdbc().queryForObject("SELECT count(*) FROM configuration_change_guards WHERE facility_id=? AND state='PREPARED'",Integer.class,facility)>0)
            throw new ConflictException("Facility configuration is being updated. Retry after it has been resolved");
    }
    @Transactional public Prepared prepare(Command command) {
        validate(command);lockFacility(command.facilityId());
        var old=repo.jdbc().queryForList("SELECT * FROM configuration_change_guards WHERE operation_id=?",command.operationId());
        if(!old.isEmpty()) {
            binding(old.getFirst(),command);
            if(!old.getFirst().get("state").equals("PREPARED"))throw new ConflictException("Configuration operation was already released");
        } else {
            if(repo.jdbc().queryForObject("SELECT count(*) FROM configuration_change_guards WHERE facility_id=? AND state='PREPARED'",Integer.class,command.facilityId())>0)
                throw new ConflictException("Another configuration change is pending");
            repo.jdbc().update("INSERT INTO configuration_change_guards(operation_id,facility_id,source,state) VALUES(?,?,?,'PREPARED')",command.operationId(),command.facilityId(),command.source());
        }
        return new Prepared(command.operationId(),impact(command.facilityId()));
    }
    @Transactional(readOnly=true) public java.util.List<Reservation> impact(UUID facility) {
        var now=Timestamp.from(clock.instant());
        var reservations=repo.jdbc().query("""
            SELECT r.id,r.court_id,r.starts_at,r.ends_at,r.state,b.id AS booking_id
            FROM slot_reservations r LEFT JOIN bookings b ON b.reservation_id=r.id
            WHERE r.facility_id=? AND r.ends_at>? AND
              (r.state='BOOKED' OR (r.state='HOLD' AND r.expires_at>?))
            ORDER BY r.starts_at,r.id
            """,(r,n)->new Reservation(r.getObject("id",UUID.class),r.getObject("booking_id",UUID.class),
                r.getObject("court_id",UUID.class),r.getTimestamp("starts_at").toInstant(),
                r.getTimestamp("ends_at").toInstant(),r.getString("state")),facility,now,now);
        return reservations;
    }
    @Transactional public void release(Command command) {
        validate(command);lockFacility(command.facilityId());
        var old=repo.jdbc().queryForList("SELECT * FROM configuration_change_guards WHERE operation_id=?",command.operationId());
        if(!old.isEmpty())binding(old.getFirst(),command);
        // Tombstone prevents an in-flight prepare from arriving AFTER a successful rollback release.
        repo.jdbc().update("""
            INSERT INTO configuration_change_guards(operation_id,facility_id,source,state,released_at)
            VALUES(?,?,?,'RELEASED',NOW()) ON CONFLICT(operation_id)
            DO UPDATE SET state='RELEASED',released_at=COALESCE(configuration_change_guards.released_at,NOW())
            """,command.operationId(),command.facilityId(),command.source());
    }
    private void binding(java.util.Map<String,Object> row,Command command) {
        if(!row.get("facility_id").equals(command.facilityId())||!row.get("source").equals(command.source()))
            throw new ForbiddenException("Configuration operation binding differs");
    }
    private void validate(Command c) {
        if(c.operationId()==null||c.facilityId()==null||c.source()==null||!java.util.Set.of("facility","schedule").contains(c.source()))
            throw new IllegalArgumentException("Invalid configuration guard command");
    }
}
