package com.sporthub.common.money;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.*;

/**
 * Direct counterparty declarations; no facility refund or bank-confirmed receipt is represented.
 */
public final class PeerMoneyContract {
  private PeerMoneyContract() {}

  public record Report(
      @NotBlank @Pattern(regexp = "[a-f0-9]{64}") String termsHash,
      @NotBlank @Pattern(regexp = "[^\\p{Cntrl}]{1,120}") String bankTransactionReference,
      @NotNull @Size(min = 1, max = 3) List<@NotNull UUID> evidenceIds)
      implements PeerResaleContract.Input {}

  public record ReturnInput(
      @NotBlank @Pattern(regexp = "[a-f0-9]{64}") String termsHash,
      @NotNull(message = "Cần nhập số tiền trả lại.")
          @DecimalMin(value = "1", message = "Số tiền phải từ 1 đồng.")
          @Digits(
              integer = 10,
              fraction = 0,
              message = "Số tiền phải là số nguyên, tối đa 10 chữ số.")
          BigDecimal amount,
      @NotBlank @Pattern(regexp = "[^\\p{Cntrl}]{1,120}") String bankTransactionReference,
      @NotNull @Size(min = 1, max = 3) List<@NotNull UUID> evidenceIds)
      implements PeerResaleContract.Input {}

  public record ReturnCommand(UUID returnId, ReturnInput input) {}

  public record Acknowledge(
      UUID returnId,
      @PositiveOrZero long version,
      @NotBlank @Pattern(regexp = "[a-f0-9]{64}") String termsHash)
      implements PeerResaleContract.Input {}

  public record NoClaim(@NotBlank @Pattern(regexp = "[a-f0-9]{64}") String termsHash)
      implements PeerResaleContract.Input {}

  public static void proof(String hash, String reference, List<UUID> evidence) {
    if (hash == null
        || !hash.matches("[a-f0-9]{64}")
        || reference == null
        || reference.isBlank()
        || reference.length() > 120
        || reference.chars().anyMatch(Character::isISOControl)
        || evidence == null
        || evidence.isEmpty()
        || evidence.size() > 3
        || evidence.stream().anyMatch(Objects::isNull)
        || evidence.stream().distinct().count() != evidence.size())
      throw new IllegalArgumentException(
          "Cần đúng điều khoản, mã giao dịch và từ 1 đến 3 ảnh chứng từ của phiên.");
  }
}
