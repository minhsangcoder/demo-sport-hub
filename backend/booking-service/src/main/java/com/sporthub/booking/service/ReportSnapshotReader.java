package com.sporthub.booking.service;

import com.sporthub.booking.repository.BookingRepository;
import com.sporthub.booking.web.BookingDtos.Booking;
import com.sporthub.common.time.FacilityReportWindow;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;

/** Reads one consistent application snapshot. No remote calls occur inside this transaction. */
@Service
public class ReportSnapshotReader {
  record Snapshot(
      List<Booking> bookings,
      LocalAppliedCollections.Snapshot applied,
      List<Map<String, Object>> collections,
      List<Map<String, Object>> settlements,
      List<Map<String, Object>> refunds,
      List<Map<String, Object>> events,
      List<UUID> courtIds) {}

  private final BookingRepository repo;
  private final LocalAppliedCollections applied;
  private final TransactionTemplate transaction;

  public ReportSnapshotReader(
      BookingRepository repo, LocalAppliedCollections applied, PlatformTransactionManager manager) {
    this.repo = repo;
    this.applied = applied;
    transaction = new TransactionTemplate(manager);
    transaction.setReadOnly(true);
    transaction.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
  }

  Snapshot read(UUID facility, FacilityReportWindow window, Instant cutoff) {
    return transaction.execute(
        status -> {
          var from = Timestamp.from(window.from());
          var to = Timestamp.from(window.to());
          var at = Timestamp.from(cutoff);
          var settlements =
              repo.jdbc()
                  .queryForList(
                      """
                      SELECT s.*,h.paid_at purchase_paid_at FROM demo_transfer_settlements s
                      JOIN transfer_handoffs h ON h.acquisition_id=s.acquisition_id
                      WHERE s.facility_id=? AND ((s.handoff_at>=? AND s.handoff_at<?)
                        OR (h.paid_at>=? AND h.paid_at<?)
                        OR (s.handoff_at<? AND (s.paid_at IS NULL OR s.paid_at>=?)))
                      """,
                      facility,
                      from,
                      to,
                      from,
                      to,
                      at,
                      at);
          var refunds =
              repo.jdbc()
                  .queryForList(
                      """
                      SELECT * FROM demo_refund_requests WHERE facility_id=? AND created_at<?
                        AND (refunded_at IS NULL OR refunded_at>=?)
                      """,
                      facility,
                      at,
                      at);
          var events =
              repo.jdbc()
                  .queryForList(
                      """
SELECT e.*,b.court_id,b.source FROM demo_money_events e JOIN bookings b ON b.id=e.booking_id
WHERE e.facility_id=? AND e.performed_at>=? AND e.performed_at<?
""",
                      facility,
                      from,
                      to);
          // Select bookings with usage, receipts, transfers, or obligations that can affect this
          // period.
          // Proof is subsequently loaded in full for these IDs, without a payment-date cutoff.
          var ids = new LinkedHashSet<UUID>();
          settlements.forEach(row -> ids.add((UUID) row.get("booking_id")));
          refunds.forEach(row -> ids.add((UUID) row.get("booking_id")));
          events.forEach(row -> ids.add((UUID) row.get("booking_id")));
          var extra = uuidArray(ids);
          var bookings =
              repo.list(
                  """
WITH relevant AS (
  SELECT id FROM bookings WHERE facility_id=? AND
    ((starts_at>=? AND starts_at<?) OR (paid_at>=? AND paid_at<?)
      OR (starts_at<? AND ends_at>?))
  UNION SELECT c.group_id FROM group_contributions c JOIN bookings b ON b.id=c.group_id
    WHERE b.facility_id=? AND c.paid_at>=? AND c.paid_at<?
  UNION SELECT c.booking_id FROM booking_demo_collections c JOIN bookings b ON b.id=c.booking_id
    WHERE b.facility_id=? AND c.paid_at>=? AND c.paid_at<?
  UNION SELECT id FROM bookings WHERE facility_id=? AND id=ANY(?::uuid[])
) SELECT b.* FROM bookings b JOIN relevant r ON r.id=b.id
""",
                  facility,
                  from,
                  to,
                  from,
                  to,
                  to,
                  from,
                  facility,
                  from,
                  to,
                  facility,
                  from,
                  to,
                  facility,
                  extra);
          var selected = uuidArray(bookings.stream().map(Booking::id).toList());
          var collections =
              repo.jdbc()
                  .queryForList(
                      "SELECT * FROM booking_demo_collections WHERE booking_id=ANY(?::uuid[])",
                      selected);
          // Keep zero-valued archived court rows compatible without materializing their historical
          // bookings.
          var courts =
              repo.jdbc()
                  .queryForList(
                      "SELECT DISTINCT court_id FROM bookings WHERE facility_id=?",
                      UUID.class,
                      facility);
          return new Snapshot(
              bookings, applied.load(bookings), collections, settlements, refunds, events, courts);
        });
  }

  private static String uuidArray(Collection<UUID> ids) {
    return "{" + String.join(",", ids.stream().map(UUID::toString).toList()) + "}";
  }
}
