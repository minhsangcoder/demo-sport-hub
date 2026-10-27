package com.sporthub.booking.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sporthub.common.money.FacilityPaymentClient;
import com.sporthub.common.money.PaymentProofFacts.BankCredit;
import com.sporthub.common.time.FacilityReportWindow;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
public class FacilityCreditEvidence {
  public record Result(List<BankCredit> credits, List<String> reasons) {}

  private final FacilityPaymentClient payments;
  private final ObjectMapper json;

  public FacilityCreditEvidence(FacilityPaymentClient payments, ObjectMapper json) {
    this.payments = payments;
    this.json = json;
  }

  public Result read(UUID facility, UUID owner, FacilityReportWindow window, Instant cutoff) {
    if (TransactionSynchronizationManager.isActualTransactionActive())
      throw new IllegalStateException("Bank credit facts must be read outside the report snapshot");
    if (!cutoff.isAfter(window.from())) return new Result(List.of(), List.of());
    var credits = new LinkedHashMap<UUID, BankCredit>();
    UUID after = null;
    long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(7);
    try {
      do {
        if (deadline - System.nanoTime() < java.util.concurrent.TimeUnit.SECONDS.toNanos(4))
          return new Result(List.copyOf(credits.values()), List.of("REAL_BANK_CREDITS_DEADLINE"));
        var response = payments.credits(facility, owner, window.from(), cutoff, after);
        if (response == null
            || !facility.toString().equals(response.path("facilityId").asText())
            || !owner.toString().equals(response.path("receiverOwnerId").asText())
            || !response.path("credits").isArray()
            || response.path("credits").size() > 500) throw new IllegalArgumentException();
        for (var entry : response.path("credits")) {
          var credit = json.treeToValue(entry, BankCredit.class);
          if (credit.creditId() == null
              || !facility.equals(credit.facilityId())
              || !owner.equals(credit.receiverOwnerId())
              || credit.bankTime().isBefore(window.from())
              || !credit.bankTime().isBefore(cutoff)
              || !credit.proofHash().matches("[a-f0-9]{64}")
              || new java.math.BigDecimal(credit.amount()).signum() <= 0
              || credits.putIfAbsent(credit.creditId(), credit) != null)
            throw new IllegalArgumentException();
        }
        if (!response.path("more").asBoolean()) break;
        UUID next = UUID.fromString(response.path("afterId").asText());
        if (next.equals(after) || response.path("credits").isEmpty())
          throw new IllegalArgumentException();
        after = next;
      } while (true);
      return new Result(List.copyOf(credits.values()), List.of());
    } catch (Exception ex) {
      return new Result(List.copyOf(credits.values()), List.of("REAL_BANK_CREDITS_UNAVAILABLE"));
    }
  }
}
