package com.sporthub.booking.service;

import com.sporthub.booking.repository.BookingRepository;
import com.sporthub.booking.web.BookingDtos.Booking;
import com.sporthub.common.money.PaymentProofFacts.BankCredit;
import com.sporthub.common.time.FacilityReportWindow;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;

/** Normalizes REAL facts into the existing report pipeline in one local snapshot. */
@Service
public class RealReportSnapshotReader {
  private final BookingRepository repo;
  private final LocalAppliedCollections applied;
  private final TransactionTemplate tx;
  private final PeerReportReader peer;

  public RealReportSnapshotReader(
      BookingRepository repo,
      LocalAppliedCollections applied,
      PlatformTransactionManager manager,
      PeerReportReader peer) {
    this.repo = repo;
    this.applied = applied;
    this.peer = peer;
    tx = new TransactionTemplate(manager);
    tx.setReadOnly(true);
    tx.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
  }

  ReportSnapshotReader.Snapshot read(
      UUID facility,
      FacilityReportWindow window,
      Instant cutoff,
      FacilityCreditEvidence.Result evidence) {
    return read(facility, window, cutoff, evidence, 0, 25);
  }

  ReportSnapshotReader.Snapshot read(
      UUID facility,
      FacilityReportWindow window,
      Instant cutoff,
      FacilityCreditEvidence.Result evidence,
      int peerPage,
      int size) {
    return tx.execute(
        status -> {
          var from = Timestamp.from(window.from());
          var to = Timestamp.from(window.to());
          var at = Timestamp.from(cutoff);
          var settlements =
              repo.jdbc()
                  .queryForList(
                      """
SELECT s.*,h.paid_at purchase_paid_at,r.confirmed_at paid_at FROM facility_transfer_settlements s
JOIN transfer_handoffs h ON h.acquisition_id=s.acquisition_id
LEFT JOIN facility_disbursement_receipts r ON r.obligation_id=s.payout_obligation_id
WHERE s.facility_id=? AND (s.handoff_at>=? AND s.handoff_at<? OR h.paid_at>=? AND h.paid_at<? OR s.handoff_at<? AND (r.confirmed_at IS NULL OR r.confirmed_at>=?))
""",
                      facility,
                      from,
                      to,
                      from,
                      to,
                      at,
                      from);
          var refunds =
              repo.jdbc()
                  .queryForList(
                      """
SELECT o.id,o.booking_id,o.amount target_amount,o.created_at,r.confirmed_at refunded_at
FROM facility_disbursement_obligations o LEFT JOIN facility_disbursement_receipts r ON r.obligation_id=o.id
WHERE o.facility_id=? AND o.kind<>'SELLER_PAYOUT' AND o.created_at<? AND (r.confirmed_at IS NULL OR r.confirmed_at>=?)
""",
                      facility,
                      at,
                      from);
          var events =
              repo.jdbc()
                  .queryForList(
                      """
SELECT r.id,o.booking_id,o.kind,r.amount,r.confirmed_at performed_at,'SUCCESS' outcome
FROM facility_disbursement_receipts r JOIN facility_disbursement_obligations o ON o.id=r.obligation_id
WHERE o.facility_id=? AND r.confirmed_at>=? AND r.confirmed_at<?
""",
                      facility,
                      from,
                      to);
          var ids = new LinkedHashSet<UUID>();
          evidence.credits().forEach(c -> ids.add(c.bookingId()));
          for (var list : List.of(settlements, refunds, events))
            list.forEach(r -> ids.add((UUID) r.get("booking_id")));
          var bookings =
              repo.list(
                  """
SELECT b.* FROM bookings b JOIN booking_money_cohorts m ON m.booking_id=b.id
WHERE b.facility_id=? AND (b.starts_at<? AND b.ends_at>? OR b.paid_at>=? AND b.paid_at<? OR b.id=ANY(?::uuid[])
 OR EXISTS(SELECT 1 FROM booking_real_collections c WHERE c.booking_id=b.id AND c.paid_at>=? AND c.paid_at<?)
 OR EXISTS(SELECT 1 FROM booking_real_bank_credits c WHERE c.booking_id=b.id AND c.bank_time>=? AND c.bank_time<?))
""",
                  facility,
                  to,
                  from,
                  from,
                  to,
                  array(ids),
                  from,
                  to,
                  from,
                  to);
          var selected = bookings.stream().map(Booking::id).toList();
          var collections =
              repo.jdbc()
                  .queryForList(
                      "SELECT * FROM booking_real_collections WHERE booking_id=ANY(?::uuid[])",
                      array(selected));
          var localCredits =
              repo.jdbc()
                  .queryForList(
                      "SELECT * FROM booking_real_bank_credits WHERE booking_id=ANY(?::uuid[])"
                          + " ORDER BY bank_time,credit_id",
                      array(selected));
          var failed =
              new HashSet<UUID>(
                  repo.jdbc()
                      .queryForList(
                          "SELECT a.payment_id FROM facility_refund_allocations a JOIN"
                              + " facility_refund_entitlements e ON e.id=a.entitlement_id WHERE"
                              + " e.booking_id=ANY(?::uuid[]) AND e.source_key LIKE"
                              + " 'APPLICATION:%'",
                          UUID.class, array(selected)));
          var snapshot = applied.load(bookings);
          snapshot =
              new LocalAppliedCollections.Snapshot(
                  snapshot.capturedAt(),
                  snapshot.facts().stream().filter(f -> !failed.contains(f.paymentId())).toList(),
                  snapshot.assessments(),
                  snapshot.reviewedPaymentIds());
          var validIds = new HashSet<>(bookings.stream().map(Booking::id).toList());
          var reasons = new ArrayList<>(evidence.reasons());
          var credits = new LinkedHashMap<UUID, BankCredit>();
          var collectionByPayment = new HashMap<UUID, Map<String, Object>>();
          collections.forEach(row -> collectionByPayment.put((UUID) row.get("payment_id"), row));
          for (var credit : evidence.credits()) {
            if (validIds.contains(credit.bookingId())) credits.put(credit.creditId(), credit);
            else reasons.add("BANK_CREDIT_BOOKING_BINDING_UNAVAILABLE");
          }
          if (!evidence.reasons().isEmpty())
            for (var row : localCredits) {
              Instant time = ((Timestamp) row.get("bank_time")).toInstant();
              if (time.isBefore(window.from()) || !time.isBefore(cutoff)) continue;
              var collection = collectionByPayment.get(row.get("payment_id"));
              if (collection != null)
                credits.putIfAbsent(
                    (UUID) row.get("credit_id"),
                    new BankCredit(
                        (UUID) row.get("credit_id"),
                        (UUID) row.get("payment_id"),
                        (UUID) row.get("booking_id"),
                        (UUID) row.get("payer_id"),
                        (UUID) collection.get("member_id"),
                        (UUID) collection.get("acquisition_id"),
                        collection.get("purpose").toString(),
                        facility,
                        (UUID) row.get("receiver_owner_id"),
                        (UUID) row.get("channel_id"),
                        row.get("amount").toString(),
                        time,
                        row.get("proof_hash").toString()));
            }
          var courts =
              repo.jdbc()
                  .queryForList(
                      "SELECT DISTINCT court_id FROM bookings WHERE facility_id=?",
                      UUID.class,
                      facility);
          return new ReportSnapshotReader.Snapshot(
              bookings,
              snapshot,
              collections,
              settlements,
              refunds,
              events,
              courts,
              "REAL",
              List.copyOf(credits.values()),
              localCredits,
              List.copyOf(new TreeSet<>(reasons)),
              peer.read(facility, window, cutoff, peerPage, size));
        });
  }

  private org.springframework.jdbc.support.SqlArrayValue array(Collection<UUID> ids) {
    return BookingRepository.uuidArray(ids);
  }
}
