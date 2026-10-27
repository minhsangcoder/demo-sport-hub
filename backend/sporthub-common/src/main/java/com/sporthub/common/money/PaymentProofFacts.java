package com.sporthub.common.money;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.*;

public final class PaymentProofFacts {
  private PaymentProofFacts() {}

  public record Binding(
      @NotNull UUID paymentId,
      @NotNull UUID bookingId,
      UUID memberId,
      @NotBlank @Pattern(regexp = "BOOKING|GROUP_CONTRIBUTION|TRANSFER") String purpose) {}

  public record Request(@NotEmpty @Size(max = 500) List<@Valid Binding> bindings) {}

  public record Fact(
      UUID paymentId,
      UUID bookingId,
      UUID payerId,
      UUID memberId,
      UUID acquisitionId,
      String purpose,
      String status,
      String amount,
      String currency,
      Instant paidAt,
      String provider,
      String moneyClass,
      boolean verified,
      boolean canonical,
      boolean review,
      String outcomeFingerprint,
      String memberText,
      String proofSource,
      List<String> reasons,
      RealEvidence realEvidence) {
    public Fact(
        UUID paymentId,
        UUID bookingId,
        UUID payerId,
        UUID memberId,
        UUID acquisitionId,
        String purpose,
        String status,
        String amount,
        String currency,
        Instant paidAt,
        String provider,
        String moneyClass,
        boolean verified,
        boolean canonical,
        boolean review,
        String outcomeFingerprint,
        String memberText,
        String proofSource,
        List<String> reasons) {
      this(
          paymentId,
          bookingId,
          payerId,
          memberId,
          acquisitionId,
          purpose,
          status,
          amount,
          currency,
          paidAt,
          provider,
          moneyClass,
          verified,
          canonical,
          review,
          outcomeFingerprint,
          memberText,
          proofSource,
          reasons,
          null);
    }
  }

  public record BankCredit(
      UUID creditId,
      UUID paymentId,
      UUID bookingId,
      UUID payerId,
      UUID memberId,
      UUID acquisitionId,
      String purpose,
      UUID facilityId,
      UUID receiverOwnerId,
      UUID channelId,
      String amount,
      Instant bankTime,
      String proofHash) {}

  public record RealEvidence(
      UUID facilityId,
      UUID receiverOwnerId,
      UUID channelId,
      UUID receiverRevisionId,
      UUID proofId,
      String proofHash,
      String amountApplied,
      String amountCollected,
      String confirmationMethod,
      List<BankCredit> bankCredits) {}

  public record Problem(UUID paymentId, String reason) {}

  public record Response(Instant capturedAt, List<Fact> facts, List<Problem> problems) {}
}
