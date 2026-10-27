package com.sporthub.booking.service;

import com.sporthub.booking.repository.BookingRepository;
import com.sporthub.common.event.*;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** One source lifecycle; Redis consumers only observe the committed outbox. */
final class AdmissionLedger {
  private AdmissionLedger() {}

  static void abandon(BookingRepository repo, UUID source, String reason, Instant effectiveAt) {
    transaction();
    if (!Set.of("CANCELLED_HOLD", "EXPIRED").contains(reason))
      throw new IllegalArgumentException("Invalid abandonment reason");
    var changed =
        repo.jdbc()
            .query(
                """
INSERT INTO booking_abandonment_events(source_id,principal_id,court_id,starts_at,ends_at,kind,reason,effective_at)
SELECT source_id,principal_id,court_id,starts_at,ends_at,kind,?,? FROM user_hold_claims c
WHERE source_id=? AND protection_eligible AND NOT grandfather AND state='RELEASED'
 AND NOT EXISTS(SELECT 1 FROM booking_admission_successes s WHERE s.source_id=c.source_id)
 AND NOT EXISTS(SELECT 1 FROM booking_admission_exemptions e WHERE e.source_id=c.source_id)
ON CONFLICT(source_id) DO NOTHING RETURNING principal_id
""",
                (r, n) -> r.getObject("principal_id", UUID.class),
                reason,
                Timestamp.from(effectiveAt),
                source);
    changed.forEach(user -> changed(repo, user, source, "ABANDONED"));
  }

  static void success(BookingRepository repo, UUID source, Instant effectiveAt) {
    transaction();
    var changed =
        repo.jdbc()
            .query(
                """
INSERT INTO booking_admission_successes(source_id,principal_id,effective_at)
SELECT source_id,principal_id,? FROM user_hold_claims c WHERE source_id=?
 AND protection_eligible AND NOT grandfather AND state='COMPLETED'
 AND NOT EXISTS(SELECT 1 FROM booking_abandonment_events e WHERE e.source_id=c.source_id)
ON CONFLICT(source_id) DO NOTHING RETURNING principal_id
""",
                (r, n) -> r.getObject("principal_id", UUID.class),
                Timestamp.from(effectiveAt),
                source);
    changed.forEach(user -> changed(repo, user, source, "SUCCEEDED"));
  }

  static void exempt(BookingRepository repo, UUID source, String reason, Instant at) {
    transaction();
    var changed =
        repo.jdbc()
            .query(
                """
INSERT INTO booking_admission_exemptions(source_id,reason,recorded_at)
SELECT source_id,?,? FROM user_hold_claims c WHERE source_id=? AND protection_eligible AND NOT grandfather
ON CONFLICT(source_id) DO NOTHING RETURNING (SELECT principal_id FROM user_hold_claims c WHERE c.source_id=booking_admission_exemptions.source_id)
""",
                (r, n) -> r.getObject(1, UUID.class),
                reason,
                Timestamp.from(at),
                source);
    changed.forEach(user -> changed(repo, user, source, "EXEMPTED"));
  }

  static void bookingClosed(
      BookingRepository repo,
      com.sporthub.booking.web.BookingDtos.Booking booking,
      String next,
      UUID actor,
      Instant now,
      Instant paidAt) {
    var sources =
        repo.jdbc()
            .queryForList(
                "SELECT c.source_id FROM user_hold_claims c JOIN bookings b ON"
                    + " b.reservation_id=c.source_id WHERE b.id=? AND c.protection_eligible AND NOT"
                    + " c.grandfather",
                UUID.class,
                booking.id());
    if (sources.isEmpty()) return;
    UUID source = sources.getFirst();
    if (next.equals("CONFIRMED")) {
      success(repo, source, paidAt);
      return;
    }
    if (!Set.of("CANCELLED", "EXPIRED").contains(next)) return;
    if (next.equals("CANCELLED") && !Objects.equals(actor, booking.currentHolderId())) {
      exempt(repo, source, "FACILITY_CANCEL", now);
      return;
    }
    boolean failed =
        Boolean.TRUE.equals(
            repo.jdbc()
                .queryForObject(
                    "SELECT EXISTS(SELECT 1 FROM booking_history WHERE booking_id=? AND"
                        + " action='PAYMENT_FAILED')",
                    Boolean.class,
                    booking.id()));
    boolean funds =
        Boolean.TRUE.equals(
            repo.jdbc()
                .queryForObject(
                    "SELECT EXISTS(SELECT 1 FROM group_contributions WHERE group_id=? AND amount>0)"
                        + " OR EXISTS(SELECT 1 FROM booking_real_collections WHERE booking_id=? AND"
                        + " amount>0)",
                    Boolean.class,
                    booking.id(),
                    booking.id()));
    if (failed || funds) {
      exempt(repo, source, failed ? "PAYMENT_FAILURE" : "FUNDS_APPLIED", now);
      return;
    }
    abandon(
        repo,
        source,
        next.equals("EXPIRED") ? "EXPIRED" : "CANCELLED_HOLD",
        next.equals("EXPIRED") ? booking.holdExpiresAt() : now);
  }

  static void paymentFailure(BookingRepository repo, UUID booking, Instant at) {
    var sources =
        repo.jdbc()
            .queryForList(
                "SELECT c.source_id FROM user_hold_claims c JOIN bookings b ON"
                    + " b.reservation_id=c.source_id WHERE b.id=? AND c.protection_eligible AND NOT"
                    + " c.grandfather",
                UUID.class,
                booking);
    sources.forEach(source -> exempt(repo, source, "PAYMENT_FAILURE", at));
  }

  private static void changed(BookingRepository repo, UUID user, UUID source, String outcome) {
    repo.jdbc()
        .update(
            """
INSERT INTO booking_admission_versions(principal_id,revision,updated_at) VALUES(?,?,clock_timestamp())
ON CONFLICT(principal_id) DO UPDATE SET revision=excluded.revision,updated_at=excluded.updated_at
""",
            user,
            UUID.randomUUID());
    var event =
        DomainEvent.create(
            "booking.admission.changed",
            1,
            "booking-service",
            user,
            Map.of("principalId", user, "sourceId", source, "outcome", outcome));
    ReliableOutbox.enqueue(repo.jdbc(), RabbitMQConfig.EXCHANGE_BOOKING, event, repo.write(event));
  }

  private static void transaction() {
    if (!TransactionSynchronizationManager.isActualTransactionActive())
      throw new IllegalStateException("Admission facts need the source transaction");
  }
}
