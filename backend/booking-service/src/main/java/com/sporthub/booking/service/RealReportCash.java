package com.sporthub.booking.service;

import static com.sporthub.booking.service.ReportState.*;
import static com.sporthub.booking.service.ReportTransactions.transaction;

import com.sporthub.common.money.PaymentProofFacts.BankCredit;
import java.math.BigDecimal;
import java.util.*;

/**
 * Native cash is counted once per credit; full application and handoff are separate recognitions.
 */
final class RealReportCash {
  static void add(
      ReportSnapshotReader.Snapshot snapshot, ReportState totals, ReportPaymentModes.Modes modes) {
    var capacities = new HashMap<UUID, BigDecimal>();
    for (var fact : modes.needed())
      if (Boolean.TRUE.equals(modes.values().get(fact.paymentId()))
          && fact.paidAt().isBefore(totals.cutoff)) capacities.put(fact.paymentId(), fact.amount());
    for (var row : snapshot.settlements())
      if (instant(row.get("handoff_at")).isBefore(totals.cutoff))
        capacities.put((UUID) row.get("payment_id"), (BigDecimal) row.get("amount"));
    var seen = new HashSet<UUID>();
    for (var credit : snapshot.bankCredits()) {
      if (!seen.add(credit.creditId()) || !in(credit.bankTime(), totals.window)) continue;
      var booking = totals.byId.get(credit.bookingId());
      if (booking == null) {
        modes.reasons().add("REAL_CREDIT_BOOKING_UNAVAILABLE");
        continue;
      }
      var amount = new BigDecimal(credit.amount());
      var counter = totals.counters.computeIfAbsent(booking.courtId(), key -> new Counter());
      totals.summary.bankCollected = totals.summary.bankCollected.add(amount);
      counter.bankCollected = counter.bankCollected.add(amount);
      if (credit.purpose().equals("TRANSFER")) {
        totals.summary.p = totals.summary.p.add(amount);
        counter.p = counter.p.add(amount);
      }
      BigDecimal prior = BigDecimal.ZERO;
      var source = modes.remote().get(credit.paymentId());
      if (source != null && source.realEvidence() != null) {
        var ordered =
            source.realEvidence().bankCredits().stream()
                .sorted(
                    Comparator.comparing(BankCredit::bankTime)
                        .thenComparing(c -> c.creditId().toString()))
                .toList();
        for (var entry : ordered) {
          if (entry.creditId().equals(credit.creditId())) break;
          prior = prior.add(new BigDecimal(entry.amount()));
        }
      } else {
        for (var row : snapshot.localCredits())
          if (credit.paymentId().equals(row.get("payment_id"))) {
            if (credit.creditId().equals(row.get("credit_id"))) break;
            prior = prior.add((BigDecimal) row.get("amount"));
          }
      }
      BigDecimal applied =
          capacities
              .getOrDefault(credit.paymentId(), BigDecimal.ZERO)
              .subtract(prior)
              .max(BigDecimal.ZERO)
              .min(amount);
      var unapplied = amount.subtract(applied);
      totals.summary.unappliedCollected = totals.summary.unappliedCollected.add(unapplied);
      counter.unappliedCollected = counter.unappliedCollected.add(unapplied);
      totals.transactions.add(
          transaction(
              "BANK:" + credit.creditId(),
              "BANK_CREDIT",
              "IN",
              false,
              true,
              true,
              booking,
              totals.names,
              credit.purpose(),
              "BANK_VERIFIED",
              amount,
              credit.bankTime()));
    }
  }
}
