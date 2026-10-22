package com.sporthub.transfer.web;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
public final class TransferDtos {
 private TransferDtos(){}
 @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
 public record Create(@NotNull UUID bookingId,@NotNull @DecimalMin(value="0",inclusive=false) @Digits(integer=10,fraction=2) BigDecimal price,@NotNull @Future Instant deadline,UUID beneficiaryRevisionId,@Pattern(regexp="[a-f0-9]{64}") String acceptedTermsHash){
  public Create(UUID bookingId,BigDecimal price,Instant deadline){this(bookingId,price,deadline,null,null);}
 }
 @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
 public record Edit(@NotNull @DecimalMin(value="0",inclusive=false) @Digits(integer=10,fraction=2) BigDecimal price,@NotNull @Future Instant deadline,UUID beneficiaryRevisionId,@Pattern(regexp="[a-f0-9]{64}") String acceptedTermsHash){
  public Edit(BigDecimal price,Instant deadline){this(price,deadline,null,null);}
 }
 public record Acquire(@Pattern(regexp="[a-f0-9]{64}") String acceptedTermsHash){}
 // Marketplace deliberately excludes seller identity, booking ID, phone and QR.
 public record Listing(UUID id,UUID facilityId,UUID courtId,JsonNode venue,BigDecimal originalAmount,BigDecimal price,String currency,
  Instant startsAt,Instant endsAt,Instant deadline,String state,boolean own,boolean available,long version,
  @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL) JsonNode financeTerms){}
 public record Acquisition(UUID id,UUID listingId,UUID bookingId,UUID buyerId,BigDecimal amount,String currency,Instant expiresAt,String state,UUID paymentId,
  @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL) JsonNode financeTerms){}
 public record Payable(UUID bookingId,UUID payerId,UUID sellerId,UUID acquisitionId,BigDecimal amount,String currency,Instant expiresAt,String purpose,
  @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL) JsonNode transferTerms){}
}
