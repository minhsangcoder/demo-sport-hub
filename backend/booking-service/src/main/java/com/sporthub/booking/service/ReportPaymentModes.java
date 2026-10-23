package com.sporthub.booking.service;

import static com.sporthub.booking.service.ReportState.*;
import static com.sporthub.booking.web.DemoFacilityReportDtos.*;

import com.sporthub.common.money.PaymentProofFacts;
import java.math.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;

@Service
public class ReportPaymentModes {
  record Modes(
      List<LocalAppliedCollections.Fact> needed,
      Map<UUID, Boolean> values,
      SortedSet<String> reasons) {}

  private final PaymentEvidenceClient evidence;

  public ReportPaymentModes(PaymentEvidenceClient evidence) {
    this.evidence = evidence;
  }

  Modes assess(ReportSnapshotReader.Snapshot s, ReportState t) {
    var byId = t.byId;
    var window = t.window;
    var local = new HashMap<UUID, Map<String, Object>>();
    s.collections().forEach(c -> local.put((UUID) c.get("payment_id"), c));
    var needed =
        s.applied().facts().stream()
            .filter(
                f -> {
                  var b = byId.get(f.bookingId());
                  return in(f.paidAt(), window) || in(b.startsAt(), window) || overlaps(b, window);
                })
            .toList();
    var unknown =
        needed.stream()
            .filter(LocalAppliedCollections.Fact::verified)
            .filter(f -> !local.containsKey(f.paymentId()))
            .map(
                f ->
                    new PaymentProofFacts.Binding(
                        f.paymentId(), f.bookingId(), f.memberId(), f.purpose()))
            .toList();
    var remote =
        unknown.isEmpty()
            ? new PaymentEvidenceClient.Result(Map.of(), Map.of(), List.of(), List.of(), 0)
            : evidence.read(unknown);
    var reasons = new TreeSet<String>();
    var modes = new HashMap<UUID, Boolean>();
    for (var booking : s.bookings()) {
      var assessment = s.applied().assessments().get(booking.id());
      boolean relevant =
          in(booking.paidAt(), window)
              || in(booking.startsAt(), window)
              || overlaps(booking, window);
      boolean expectsPaid =
          Set.of("CONFIRMED", "CHECKED_IN", "COMPLETED").contains(booking.status())
              || booking.status().equals("CANCELLED")
                  && (booking.paymentId() != null || booking.paidAt() != null);
      if (relevant && expectsPaid && assessment != null && !assessment.fullyPaid())
        reasons.add("APPLIED_TOTAL_NEEDS_REVIEW");
    }
    for (var fact : needed) {
      boolean demo = false;
      if (!fact.verified()) reasons.add("APPLIED_RECEIPT_NEEDS_REVIEW");
      else if (local.containsKey(fact.paymentId())) {
        var c = local.get(fact.paymentId());
        demo =
            fact.fingerprint().equals(c.get("fingerprint"))
                && fact.payerId().equals(c.get("payer_id"))
                && fact.amount().compareTo((BigDecimal) c.get("amount")) == 0
                && fact.paidAt().equals(instant(c.get("paid_at")));
        if (!demo) reasons.add("APPLICATION_COLLECTION_BINDING_DIFFERS");
      } else {
        var f = remote == null ? null : remote.facts().get(fact.paymentId());
        if (f != null
            && f.verified()
            && f.canonical()
            && f.payerId().equals(fact.payerId())
            && new BigDecimal(f.amount()).compareTo(fact.amount()) == 0
            && f.currency().equals(fact.currency())
            && Objects.equals(f.paidAt(), fact.paidAt())
            && Objects.equals(f.outcomeFingerprint(), fact.fingerprint())) {
          demo = f.moneyClass().equals("DEMO");
          if (!demo) reasons.add("NON_DEMO_PAYMENT_EXCLUDED");
        } else reasons.add("PAYMENT_APPLICATION_FACTS_UNAVAILABLE");
      }
      modes.put(fact.paymentId(), demo);
    }
    if (remote != null) reasons.addAll(remote.reasons());

    return new Modes(needed, modes, reasons);
  }
}
