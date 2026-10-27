package com.sporthub.transfer.web;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class TransferDtos {
  private TransferDtos() {}

  @com.fasterxml.jackson.annotation.JsonInclude(
      com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
  public record Create(
      @NotNull UUID bookingId,
      @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 10, fraction = 2)
          BigDecimal price,
      @NotNull @Future Instant deadline,
      UUID beneficiaryRevisionId,
      @Pattern(regexp = "[a-f0-9]{64}") String acceptedTermsHash,
      UUID bankDestinationRevisionId) {
    public Create(UUID bookingId, BigDecimal price, Instant deadline) {
      this(bookingId, price, deadline, null, null, null);
    }

    public Create(
        UUID bookingId,
        BigDecimal price,
        Instant deadline,
        UUID beneficiaryRevisionId,
        String acceptedTermsHash) {
      this(bookingId, price, deadline, beneficiaryRevisionId, acceptedTermsHash, null);
    }
  }

  @com.fasterxml.jackson.annotation.JsonInclude(
      com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
  public record Edit(
      @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 10, fraction = 2)
          BigDecimal price,
      @NotNull @Future Instant deadline,
      UUID beneficiaryRevisionId,
      @Pattern(regexp = "[a-f0-9]{64}") String acceptedTermsHash,
      UUID bankDestinationRevisionId,
      @PositiveOrZero Long expectedVersion) {
    public Edit(BigDecimal price, Instant deadline) {
      this(price, deadline, null, null, null, null);
    }

    public Edit(
        BigDecimal price, Instant deadline, UUID beneficiaryRevisionId, String acceptedTermsHash) {
      this(price, deadline, beneficiaryRevisionId, acceptedTermsHash, null, null);
    }

    public Edit(
        BigDecimal price,
        Instant deadline,
        UUID beneficiaryRevisionId,
        String acceptedTermsHash,
        UUID bankDestinationRevisionId) {
      this(
          price,
          deadline,
          beneficiaryRevisionId,
          acceptedTermsHash,
          bankDestinationRevisionId,
          null);
    }
  }

  public record PeerListingVersion(@PositiveOrZero long version)
      implements com.sporthub.common.money.PeerResaleContract.Input {}

  public record Acquire(@Pattern(regexp = "[a-f0-9]{64}") String acceptedTermsHash) {}

  // Marketplace deliberately excludes seller identity, booking ID, phone and QR.
  public record Listing(
      UUID id,
      UUID facilityId,
      UUID courtId,
      JsonNode venue,
      BigDecimal originalAmount,
      BigDecimal price,
      String currency,
      Instant startsAt,
      Instant endsAt,
      Instant deadline,
      String state,
      boolean own,
      boolean available,
      long version,
      @com.fasterxml.jackson.annotation.JsonInclude(
              com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
          JsonNode financeTerms) {}

  public record Acquisition(
      UUID id,
      UUID listingId,
      UUID bookingId,
      UUID buyerId,
      BigDecimal amount,
      String currency,
      Instant expiresAt,
      String state,
      UUID paymentId,
      @com.fasterxml.jackson.annotation.JsonInclude(
              com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
          JsonNode financeTerms,
      @com.fasterxml.jackson.annotation.JsonInclude(
              com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
          String protocol,
      @com.fasterxml.jackson.annotation.JsonInclude(
              com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
          UUID conversationId) {
    public Acquisition(
        UUID id,
        UUID listingId,
        UUID bookingId,
        UUID buyerId,
        BigDecimal amount,
        String currency,
        Instant expiresAt,
        String state,
        UUID paymentId,
        JsonNode financeTerms) {
      this(
          id,
          listingId,
          bookingId,
          buyerId,
          amount,
          currency,
          expiresAt,
          state,
          paymentId,
          financeTerms,
          null,
          null);
    }
  }

  public record Payable(
      UUID bookingId,
      UUID payerId,
      UUID sellerId,
      UUID acquisitionId,
      BigDecimal amount,
      String currency,
      Instant expiresAt,
      String purpose,
      @com.fasterxml.jackson.annotation.JsonInclude(
              com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
          JsonNode transferTerms) {
    public String moneyClass() {
      return transferTerms == null ? "UNKNOWN" : transferTerms.path("moneyClass").asText("UNKNOWN");
    }

    @com.fasterxml.jackson.annotation.JsonProperty("moneyClass")
    public String serializedMoneyClass() {
      return moneyClass();
    }

    @com.fasterxml.jackson.annotation.JsonProperty("facilityId")
    public UUID facilityId() {
      return transferTerms == null
          ? null
          : UUID.fromString(transferTerms.path("facilityId").asText());
    }

    @com.fasterxml.jackson.annotation.JsonProperty("receiverOwnerId")
    public UUID receiverOwnerId() {
      return com.sporthub.common.money.FacilityTransferTerms.isReal(transferTerms)
          ? UUID.fromString(transferTerms.path("receiverOwnerId").asText())
          : null;
    }

    @com.fasterxml.jackson.annotation.JsonProperty("receiverRevisionId")
    public UUID receiverRevisionId() {
      return com.sporthub.common.money.FacilityTransferTerms.isReal(transferTerms)
          ? UUID.fromString(transferTerms.path("receiverRevisionId").asText())
          : null;
    }
  }
}
