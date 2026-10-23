package com.sporthub.booking.service;

import static com.sporthub.booking.web.BookingDtos.*;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sporthub.booking.repository.BookingRepository;
import com.sporthub.common.exception.*;
import com.sporthub.common.security.RemoteIdentity.Caller;
import com.sporthub.common.security.Signatures;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class BookingCreation {
  private final BookingRepository repo;
  private final BookingDependencies dependencies;
  private final Clock clock;
  private final ConfigurationGuardService configuration;
  private final BookingRefundPolicies refundPolicies;
  private final BookingIdempotency keys;
  private final BookingReads reads;
  private final BookingHolds holds;

  public BookingCreation(
      BookingRepository repo,
      BookingDependencies dependencies,
      Clock clock,
      ConfigurationGuardService configuration,
      BookingRefundPolicies refundPolicies,
      BookingIdempotency keys,
      BookingReads reads,
      BookingHolds holds) {
    this.repo = repo;
    this.dependencies = dependencies;
    this.clock = clock;
    this.configuration = configuration;
    this.refundPolicies = refundPolicies;
    this.keys = keys;
    this.reads = reads;
    this.holds = holds;
  }

  @Transactional
  public Booking create(
      CreateInput input, String key, Caller caller, String token, boolean counter) {
    keys.checkKey(key);
    repo.lock(caller.id());
    String fingerprint = Signatures.sha256(repo.write(input) + "|" + counter);
    UUID previous = keys.idempotent(caller.id(), "BOOKING", key, fingerprint);
    if (previous != null) {
      var old = repo.find(previous);
      if (counter)
        dependencies.facilityPermission(old.facilityId(), caller, token, "BOOKING_CREATE_COUNTER");
      else caller.requireRole("CUSTOMER");
      return reads.replayView(old, caller);
    }
    var h = holds.ownHold(input.holdId(), caller, token);
    repo.lock(h.courtId());
    if (repo.jdbc()
            .queryForObject(
                "SELECT count(*) FROM bookings WHERE reservation_id=?", Integer.class, h.id())
        > 0) throw new ConflictException("Hold already has a booking");
    holds.expireCourt(h.courtId());
    h = repo.hold(h.id());
    if (!h.state().equals("HOLD") || !h.expiresAt().isAfter(clock.instant()))
      throw new ConflictException("Hold expired or was released");
    if (repo.jdbc()
            .queryForObject(
                "SELECT count(*) FROM bookings WHERE reservation_id=?", Integer.class, h.id())
        > 0)
      throw new ConflictException(
          "A booking already uses this hold; retry with the original Idempotency-Key");
    if (counter) {
      dependencies.facilityPermission(h.facilityId(), caller, token, "BOOKING_CREATE_COUNTER");
      if (input.guestName() == null
          || input.guestName().isBlank()
          || input.guestPhone() == null
          || !input.guestPhone().matches("\\+?[0-9]{8,15}"))
        throw new IllegalArgumentException("Guest name and phone are required");
    } else caller.requireRole("CUSTOMER");
    configuration.requireAccepting(h.facilityId());
    var refundPolicy = refundPolicies.fromAcceptedQuote(h.quote(), h.facilityId());
    var live =
        dependencies.quote(
            new HoldInput(
                h.courtId(), h.startsAt(), h.endsAt(), h.quote().path("amount").decimalValue()));
    holds.validateQuote(
        live,
        new HoldInput(
            h.courtId(), h.startsAt(), h.endsAt(), h.quote().path("amount").decimalValue()));
    if (!h.expiresAt().isAfter(clock.instant()))
      throw new ConflictException("Hold expired during price validation");
    if (!h.facilityId().toString().equals(live.path("facilityId").asText())
        || !h.courtId().toString().equals(live.path("courtId").asText()))
      throw new ConflictException("Quote scope changed");
    if (!h.quote().path("currency").asText().equals(live.path("currency").asText())
        || live.path("amount").decimalValue().compareTo(h.quote().path("amount").decimalValue())
            != 0) throw new ConflictException("Price changed. Review price and create a new hold");
    UUID linkedCustomer =
        counter ? dependencies.counterCustomer(h.facilityId(), input.guestPhone(), token) : null;
    if (counter)
      dependencies.facilityPermission(h.facilityId(), caller, token, "BOOKING_CREATE_COUNTER");
    if (!h.expiresAt().isAfter(clock.instant()))
      throw new ConflictException("Hold expired during customer validation");
    UUID holder = counter ? linkedCustomer : caller.id();
    live = live.deepCopy();
    ((ObjectNode) live).set("refundPolicy", refundPolicy.deepCopy());
    UUID id = UUID.randomUUID();
    repo.auditContext(caller.id(), counter ? "COUNTER_API" : "BOOKING_API");
    repo.jdbc()
        .update(
            "INSERT INTO"
                + " bookings(id,reservation_id,facility_id,court_id,created_by,customer_id,current_holder_id,starts_at,ends_at,status,source,guest_name,guest_phone,amount,currency,price_snapshot,hold_expires_at)"
                + " VALUES(?,?,?,?,?,?,?,?,?,'PENDING',?,?,?,?,?,?::jsonb,?)",
            id,
            h.id(),
            h.facilityId(),
            h.courtId(),
            caller.id(),
            holder,
            holder,
            Timestamp.from(h.startsAt()),
            Timestamp.from(h.endsAt()),
            counter ? "OFFLINE_COUNTER" : "ONLINE",
            counter ? input.guestName().trim() : input.guestName(),
            input.guestPhone(),
            live.path("amount").decimalValue(),
            live.path("currency").asText(),
            repo.write(live),
            Timestamp.from(h.expiresAt()));
    if (counter) {
      repo.jdbc()
          .update(
              "INSERT INTO"
                  + " counter_booking_guests(booking_id,operator_id,linked_customer_id,guest_name,guest_phone)"
                  + " VALUES(?,?,?,?,?)",
              id,
              caller.id(),
              linkedCustomer,
              input.guestName().trim(),
              input.guestPhone());
      repo.jdbc().update("UPDATE slot_reservations SET holder_id=? WHERE id=?", holder, h.id());
    }
    refundPolicies.store(repo.find(id), refundPolicy, caller.id());
    repo.history(
        id,
        caller.id(),
        "BOOKING_CREATED",
        Map.of("source", counter ? "OFFLINE_COUNTER" : "ONLINE"));
    keys.remember(caller.id(), "BOOKING", key, fingerprint, id);
    return reads.projectEffective(repo.find(id));
  }

  @Transactional
  public Booking counter(CounterInput input, String key, Caller caller, String token) {
    if (input.guestName() == null
        || input.guestName().isBlank()
        || input.guestName().length() > 180
        || input.guestPhone() == null
        || !input.guestPhone().matches("\\+?[0-9]{8,15}"))
      throw new IllegalArgumentException("Guest name and phone are required");
    if (input.paymentMethod() != null && !input.paymentMethod().equals("DEMO"))
      throw new ConflictException(
          "BLOCKED_RULE: cash/POS collection or a real provider is not configured");
    keys.checkKey(key);
    repo.lock(caller.id());
    String fingerprint =
        Signatures.sha256(
            repo.write(
                new CounterInput(
                    input.courtId(),
                    input.startsAt(),
                    input.endsAt(),
                    input.expectedAmount().stripTrailingZeros(),
                    input.guestName().trim(),
                    input.guestPhone(),
                    input.paymentMethod(),
                    input.refundPolicyHash())));
    UUID previous = keys.idempotent(caller.id(), "COUNTER_BOOKING", key, fingerprint);
    if (previous != null) {
      var b = repo.find(previous);
      dependencies.facilityPermission(b.facilityId(), caller, token, "BOOKING_CREATE_COUNTER");
      return reads.replayView(b, caller);
    }
    var hold =
        holds.hold(
            new HoldInput(
                input.courtId(), input.startsAt(), input.endsAt(), input.expectedAmount()),
            "counter-hold-" + Signatures.sha256(key),
            caller,
            token,
            true);
    refundPolicies.requireExpectedPolicy(hold.quote(), hold.facilityId(), input.refundPolicyHash());
    var booking =
        create(
            new CreateInput(hold.id(), input.guestName().trim(), input.guestPhone()),
            "counter-create-" + Signatures.sha256(key),
            caller,
            token,
            true);
    keys.remember(caller.id(), "COUNTER_BOOKING", key, fingerprint, booking.id());
    return reads.projectEffective(booking);
  }
}
