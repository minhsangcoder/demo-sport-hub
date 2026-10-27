package com.sporthub.booking.service;

import com.sporthub.booking.repository.BookingRepository;
import com.sporthub.common.exception.ConflictException;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.*;
import org.springframework.stereotype.Component;

/** A peer usage decision changes ownership only; it never creates Owner or SportHub money flows. */
@Component
public class PeerHandoffs {
  private final BookingRepository repo;
  private final PeerUsageStore store;
  private final LocalAppliedCollections collections;
  private final Clock clock;

  public PeerHandoffs(
      BookingRepository repo,
      PeerUsageStore store,
      LocalAppliedCollections collections,
      Clock clock) {
    this.repo = repo;
    this.store = store;
    this.collections = collections;
    this.clock = clock;
  }

  public void complete(
      PeerUsageStore.Attempt a, UUID actor, String method, String reason, String fingerprint) {
    var now = clock.instant();
    var booking = repo.find(a.booking());
    if (!booking.status().equals("CONFIRMED")
        || !Objects.equals(booking.currentHolderId(), a.seller())
        || !now.isBefore(a.endsAt())
        || !booking.startsAt().equals(a.startsAt())
        || !booking.endsAt().equals(a.endsAt())
        || !booking.facilityId().equals(a.facility())
        || !booking.courtId().equals(a.court()))
      throw new ConflictException("Lượt đặt không còn đủ điều kiện bàn giao quyền sử dụng.");
    if (!BookingMoneyCohorts.isReal(booking)
        || repo.jdbc()
                .queryForObject(
                    "SELECT count(*) FROM booking_money_cohorts WHERE booking_id=? AND"
                        + " money_class='REAL'",
                    Integer.class,
                    booking.id())
            != 1
        || repo.jdbc()
                .queryForObject(
                    "SELECT count(*) FROM booking_groups WHERE id=?", Integer.class, booking.id())
            > 0)
      throw new ConflictException("P2P chỉ áp dụng cho lượt đặt REAL cá nhân đã thanh toán đủ.");
    collections.requireFullyPaid(booking);
    requireUnused(booking.id());
    if (repo.jdbc()
            .queryForObject(
                "SELECT count(*) FROM user_hold_claims WHERE source_id=? AND principal_id=? AND"
                    + " kind='PEER_ACQUISITION' AND state='ACTIVE' AND court_id=? AND starts_at=?"
                    + " AND ends_at=?",
                Integer.class,
                a.id(),
                a.buyer(),
                a.court(),
                Timestamp.from(a.startsAt()),
                Timestamp.from(a.endsAt()))
        != 1) throw new ConflictException("Phiên mua không còn lượt giữ quyền hợp lệ.");
    if (a.amount().compareTo(booking.amount()) > 0
        || repo.jdbc()
                .queryForObject(
                    """
SELECT count(*) FROM transfer_leases WHERE listing_id=? AND booking_id=? AND seller_id=? AND buyer_id=?
AND acquisition_id=? AND protocol='PEER_RESALE_V1' AND state='LOCKED' AND price=?
""",
                    Integer.class,
                    a.listing(),
                    a.booking(),
                    a.seller(),
                    a.buyer(),
                    a.id(),
                    a.amount())
            != 1) throw new ConflictException("Quyền giữ phiên mua không còn khớp.");
    boolean timelyPresence =
        repo.jdbc()
                .queryForObject(
                    "SELECT count(*) FROM peer_attendance WHERE acquisition_id=? AND"
                        + " observed_at<=?",
                    Integer.class,
                    a.id(),
                    Timestamp.from(a.startsAt()))
            > 0;
    if (method.equals("COUNTERPARTY_ACKNOWLEDGED")
            && (!actor.equals(a.seller()) || !now.isBefore(a.startsAt()))
        || !now.isBefore(a.startsAt()) && !timelyPresence)
      throw new ConflictException("Cần phân xử tại sân và có bên đã được ghi nhận đến đúng giờ.");
    store.phase(a, "HANDOFF_PENDING", method, now);
    repo.auditContext(actor, "TRANSFER_COMMAND");
    repo.jdbc()
        .update(
            """
UPDATE bookings SET customer_id=?,current_holder_id=?,
booking_code=upper(substr(replace(uuid_generate_v4()::text,'-',''),1,10)),
checkin_token_version=checkin_token_version+1,version=version+1,updated_at=NOW() WHERE id=?
""",
            a.buyer(),
            a.buyer(),
            a.booking());
    repo.jdbc()
        .update(
            "UPDATE slot_reservations SET holder_id=? WHERE id=(SELECT reservation_id FROM bookings"
                + " WHERE id=?)",
            a.buyer(),
            a.booking());
    repo.jdbc()
        .update(
            "UPDATE transfer_leases SET state='COMPLETED',updated_at=NOW() WHERE listing_id=?",
            a.listing());
    int right =
        repo.jdbc()
            .update(
                """
INSERT INTO booking_resale_rights(booking_id,consumed,acquisition_id,origin,consumed_at) VALUES(?,TRUE,?,'PEER_HANDOFF',?)
ON CONFLICT(booking_id) DO UPDATE SET consumed=TRUE,acquisition_id=EXCLUDED.acquisition_id,origin='PEER_HANDOFF',consumed_at=EXCLUDED.consumed_at
WHERE NOT booking_resale_rights.consumed
""",
                a.booking(),
                a.id(),
                Timestamp.from(now));
    if (right != 1) throw new ConflictException("Lượt đặt đã được chuyển nhượng một lần.");
    var completed = repo.find(a.booking());
    decision(a, "COMPLETED", actor, method, reason, fingerprint, completed.version(), now);
    repo.jdbc()
        .update(
            """
INSERT INTO peer_booking_handoffs(booking_id,acquisition_id,listing_id,seller_id,buyer_id,amount,currency,terms_hash,confirmation_method,booking_version,token_version,transferred_at)
VALUES(?,?,?,?,?,?,?,?,?,?,?,?)
""",
            a.booking(),
            a.id(),
            a.listing(),
            a.seller(),
            a.buyer(),
            a.amount(),
            a.currency(),
            a.termsHash(),
            method,
            completed.version(),
            completed.checkinTokenVersion(),
            Timestamp.from(now));
    store.phase(store.attempt(a.id()), "COMPLETED", method, now);
    releaseClaim(a, "COMPLETED");
    AdmissionLedger.success(repo, a.id(), now);
    repo.history(
        a.booking(),
        actor,
        "PEER_USAGE_COMPLETED",
        Map.of(
            "acquisitionId",
            a.id(),
            "previousHolderId",
            a.seller(),
            "newHolderId",
            a.buyer(),
            "confirmationMethod",
            method,
            "protocol",
            "PEER_RESALE_V1"));
  }

  public void retain(
      PeerUsageStore.Attempt a,
      String outcome,
      UUID actor,
      String method,
      String reason,
      String fingerprint) {
    var now = clock.instant();
    if (a.terminal())
      throw new ConflictException("Quyền sử dụng của phiên này đã có kết quả cuối.");
    store.phase(a, outcome, method, now);
    decision(a, outcome, actor, method, reason, fingerprint, repo.find(a.booking()).version(), now);
    repo.jdbc()
        .update(
            """
            UPDATE transfer_leases SET state=CASE WHEN deadline>? THEN 'ACTIVE' ELSE 'RELEASED' END,
            buyer_id=NULL,acquisition_id=NULL,expires_at=NULL,updated_at=NOW()
            WHERE listing_id=? AND acquisition_id=? AND protocol='PEER_RESALE_V1' AND state='LOCKED'
            """,
            Timestamp.from(now),
            a.listing(),
            a.id());
    releaseClaim(a, "RELEASED");
    if (a.phase().equals("WAITING_PAYMENT")) {
      if (actor == null || actor.equals(a.buyer()))
        AdmissionLedger.abandon(
            repo,
            a.id(),
            actor == null ? "EXPIRED" : "CANCELLED_HOLD",
            actor == null ? a.payBy() : now);
      else AdmissionLedger.exempt(repo, a.id(), "SELLER_WITHDRAWAL", now);
    }
    repo.history(
        a.booking(),
        actor,
        "PEER_USAGE_RETAINED",
        Map.of("acquisitionId", a.id(), "decision", outcome, "reason", reason));
  }

  public void requireUnused(UUID booking) {
    requireUnused(repo, booking);
  }

  public static void requireUnused(BookingRepository repo, UUID booking) {
    if (consumed(repo, booking))
      throw new ConflictException("Mỗi lượt đặt chỉ được chuyển nhượng thành công một lần.");
  }

  public static boolean consumed(BookingRepository repo, UUID booking) {
    return Boolean.TRUE.equals(
        repo.jdbc()
            .queryForObject(
                "SELECT EXISTS(SELECT 1 FROM booking_resale_rights WHERE booking_id=? AND consumed)"
                    + " OR EXISTS(SELECT 1 FROM transfer_leases WHERE booking_id=? AND"
                    + " state='COMPLETED')",
                Boolean.class,
                booking,
                booking));
  }

  private void releaseClaim(PeerUsageStore.Attempt a, String state) {
    repo.jdbc()
        .update(
            "UPDATE user_hold_claims SET state=?,phase=?,updated_at=? WHERE source_id=? AND"
                + " state='ACTIVE'",
            state,
            store.attempt(a.id()).phase(),
            Timestamp.from(clock.instant()),
            a.id());
  }

  private void decision(
      PeerUsageStore.Attempt a,
      String outcome,
      UUID actor,
      String method,
      String reason,
      String fingerprint,
      long version,
      java.time.Instant decidedAt) {
    repo.jdbc()
        .update(
            """
INSERT INTO peer_usage_decisions(acquisition_id,booking_id,listing_id,seller_id,buyer_id,amount,currency,terms_hash,decision,confirmation_method,actor_id,reason,command_fingerprint,booking_version,decided_at)
VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
""",
            a.id(),
            a.booking(),
            a.listing(),
            a.seller(),
            a.buyer(),
            a.amount(),
            a.currency(),
            a.termsHash(),
            outcome,
            method,
            actor,
            reason,
            fingerprint,
            version,
            Timestamp.from(decidedAt));
  }
}
