package com.sporthub.booking.service;

import static com.sporthub.booking.web.BookingDtos.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.sporthub.booking.repository.BookingRepository;
import com.sporthub.common.exception.*;
import com.sporthub.common.security.RemoteIdentity.Caller;
import com.sporthub.common.security.Signatures;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class BookingHolds {
  private final BookingRepository repo;
  private final BookingDependencies dependencies;
  private final int holdSeconds;
  private final int maxPending;
  private final int advanceDays;
  private final Clock clock;
  private final GroupExpiration groups;
  private final ConfigurationGuardService configuration;
  private final BookingLifecycle lifecycle;
  private final int maxSlots;
  private final BookingRefundPolicies refundPolicies;
  private final BookingIdempotency keys;
  private final BookingReads reads;

  public BookingHolds(
      BookingRepository repo,
      BookingDependencies dependencies,
      @Value("${booking.slot-hold.ttl-seconds:600}") int holdSeconds,
      @Value("${booking.slot-hold.max-pending:5}") int maxPending,
      @Value("${booking.advance-days:90}") int advanceDays,
      Clock clock,
      GroupExpiration groups,
      ConfigurationGuardService configuration,
      BookingLifecycle lifecycle,
      @Value("${booking.slot-hold.max-slots-per-hold:10}") int maxSlots,
      BookingRefundPolicies refundPolicies,
      BookingIdempotency keys,
      BookingReads reads) {
    if (holdSeconds <= 0 || maxPending <= 0 || advanceDays <= 0 || maxSlots <= 0)
      throw new IllegalArgumentException("Booking limits must be positive");
    this.repo = repo;
    this.dependencies = dependencies;
    this.holdSeconds = holdSeconds;
    this.maxPending = maxPending;
    this.advanceDays = advanceDays;
    this.clock = clock;
    this.groups = groups;
    this.configuration = configuration;
    this.lifecycle = lifecycle;
    this.maxSlots = maxSlots;
    this.refundPolicies = refundPolicies;
    this.keys = keys;
    this.reads = reads;
  }

  @Transactional
  public Hold hold(HoldInput input, String key, Caller caller, String token, boolean counter) {
    if (!counter) caller.requireRole("CUSTOMER");
    keys.checkKey(key);
    repo.lock(caller.id());
    String fingerprint =
        Signatures.sha256(
            repo.write(
                    new HoldInput(
                        input.courtId(),
                        input.startsAt(),
                        input.endsAt(),
                        input.expectedAmount().stripTrailingZeros()))
                + "|"
                + counter);
    UUID previous = keys.idempotent(caller.id(), "HOLD", key, fingerprint);
    if (previous != null) {
      var old = repo.hold(previous);
      if (counter)
        dependencies.facilityPermission(old.facilityId(), caller, token, "BOOKING_CREATE_COUNTER");
      return old;
    }
    var now = clock.instant();
    if (!input.startsAt().isAfter(now)
        || input.startsAt().isAfter(now.plusSeconds(advanceDays * 86400L)))
      throw new IllegalArgumentException("Slot is outside the booking window");
    if (!input.startsAt().isBefore(input.endsAt()))
      throw new IllegalArgumentException("Booking interval is invalid");
    repo.lock(input.courtId());
    expireCourt(input.courtId());
    var initial = dependencies.quote(input);
    UUID facility = UUID.fromString(initial.path("facilityId").asText());
    if (counter) dependencies.facilityPermission(facility, caller, token, "BOOKING_CREATE_COUNTER");
    configuration.requireAccepting(facility);
    // The initial quote may predate a committed configuration change. Quote again under the fence.
    var quote = dependencies.quote(input);
    validateQuote(quote, input);
    if (quote.path("segments").size() > maxSlots)
      throw new IllegalArgumentException("Too many slots in one hold");
    if (!facility.toString().equals(quote.path("facilityId").asText()))
      throw new ConflictException("Court facility changed");
    now = clock.instant();
    if (!input.startsAt().isAfter(now)
        || input.startsAt().isAfter(now.plusSeconds(advanceDays * 86400L)))
      throw new ConflictException("Slot is outside the booking window after schedule validation");
    if (counter) dependencies.facilityPermission(facility, caller, token, "BOOKING_CREATE_COUNTER");
    if (quote.path("amount").decimalValue().compareTo(input.expectedAmount()) != 0)
      throw new ConflictException("Price changed. Review the updated quote before holding again");
    Long count =
        repo.jdbc()
            .queryForObject(
                "SELECT count(*) FROM slot_reservations r WHERE (holder_id=? OR EXISTS(SELECT 1"
                    + " FROM bookings b WHERE b.reservation_id=r.id AND b.created_by=? AND"
                    + " b.source='OFFLINE_COUNTER')) AND r.state='HOLD' AND r.expires_at>?",
                Long.class,
                caller.id(),
                caller.id(),
                Timestamp.from(now));
    if (count >= maxPending) throw new ConflictException("Pending hold limit reached");
    quote = refundPolicies.attachToQuote(quote, facility);
    UUID id = UUID.randomUUID();
    Instant expires = now.plusSeconds(holdSeconds);
    if (expires.isAfter(input.startsAt())) expires = input.startsAt();
    try {
      repo.jdbc()
          .update(
              "INSERT INTO"
                  + " slot_reservations(id,court_id,facility_id,holder_id,starts_at,ends_at,expires_at,state,quote)"
                  + " VALUES(?,?,?,?,?,?,?,'HOLD',?::jsonb)",
              id,
              input.courtId(),
              facility,
              caller.id(),
              Timestamp.from(input.startsAt()),
              Timestamp.from(input.endsAt()),
              Timestamp.from(expires),
              repo.write(quote));
    } catch (org.springframework.dao.DataIntegrityViolationException ex) {
      throw new ConflictException("Slot was just held or booked by another user");
    }
    keys.remember(caller.id(), "HOLD", key, fingerprint, id);
    return repo.hold(id);
  }

  public Hold ownHold(UUID id, Caller caller, String token) {
    var h = repo.hold(id);
    var bookings = repo.list("SELECT * FROM bookings WHERE reservation_id=?", id);
    if (bookings.isEmpty()) {
      if (!Objects.equals(h.holderId(), caller.id()))
        throw new ForbiddenException("Hold belongs to another user");
      return h;
    }
    var booking = reads.detail(bookings.getFirst().id(), caller, token).booking();
    return new Hold(
        h.id(),
        h.courtId(),
        h.facilityId(),
        booking.currentHolderId(),
        h.startsAt(),
        h.endsAt(),
        h.expiresAt(),
        h.state(),
        h.quote());
  }

  @Transactional
  public void releaseHold(UUID id, Caller caller, String token) {
    var h = repo.hold(id);
    repo.lock(h.courtId());
    ownHold(id, caller, token);
    if (repo.jdbc()
            .queryForObject(
                "SELECT count(*) FROM bookings WHERE reservation_id=?", Integer.class, id)
        > 0) throw new ConflictException("Cancel the booking instead");
    repo.jdbc()
        .update("UPDATE slot_reservations SET state='RELEASED' WHERE id=? AND state='HOLD'", id);
  }

  @Transactional
  public void expireCourt(UUID court) {
    repo.lock(court);
    var bookings =
        repo.list(
            "SELECT * FROM bookings WHERE court_id=? AND status='PENDING' AND hold_expires_at<=?",
            court,
            Timestamp.from(clock.instant()));
    for (var b : bookings) {
      groups.expire(b);
      lifecycle.transition(b, "EXPIRED", null, "EXPIRED", Map.of(), clock.instant(), null);
    }
    repo.jdbc()
        .update(
            "UPDATE slot_reservations SET state='RELEASED' WHERE court_id=? AND state='HOLD' AND"
                + " expires_at<=?",
            court,
            Timestamp.from(clock.instant()));
  }

  public void validateQuote(JsonNode quote, HoldInput input) {
    com.sporthub.common.money.Money.positive(
        quote.path("amount").decimalValue(), quote.path("currency").asText());
    if (!input.courtId().toString().equals(quote.path("courtId").asText())
        || !input.startsAt().toString().equals(quote.path("startsAt").asText())
        || !input.endsAt().toString().equals(quote.path("endsAt").asText()))
      throw new ConflictException("Quote does not match the requested interval");
  }
}
