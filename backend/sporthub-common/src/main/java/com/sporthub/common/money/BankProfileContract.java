package com.sporthub.common.money;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Only revision references cross the service boundary; account numbers stay in Payment. */
public final class BankProfileContract {
  private BankProfileContract() {}

  public record Responsibility(String kind, UUID resourceId, Instant effectiveUntil) {}

  public record Status(
      UUID principalId, UUID activeRevisionId, String destinationHash, long version,
      boolean initialized, boolean canRegisterFirst, boolean canChange,
      List<Responsibility> responsibilities) {}

  public record Activation(
      UUID principalId, UUID revisionId, String destinationHash, long expectedVersion,
      boolean existingHistory, String idempotencyKey, String fingerprint) {}

  public record LegacyHistory(UUID principalId, UUID soleRevisionId, String soleDestinationHash) {}

  public record Decision(
      UUID principalId, UUID revisionId, String destinationHash,
      long version, String decision, Instant decidedAt) {}
}
