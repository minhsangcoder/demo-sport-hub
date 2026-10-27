package com.sporthub.common.money;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Service-only commands. The caller is supplied by Transfer's live Identity check. */
public final class PeerMarketContract {
  private PeerMarketContract() {}

  public record Quote(UUID bookingId, BigDecimal price, Instant deadline, UUID bankRevisionId)
      implements PeerResaleContract.Input {}

  public record Publish(UUID listingId, long expectedVersion, JsonNode terms)
      implements PeerResaleContract.Input {}

  public record Acquire(
      UUID listingId,
      UUID acquisitionId,
      long listingVersion,
      String sellerTermsHash,
      Instant requestedAt)
      implements PeerResaleContract.Input {}

  public record Withdraw(UUID listingId, long version) implements PeerResaleContract.Input {}

  public record Result(UUID listingId, String action, JsonNode input)
      implements PeerResaleContract.Input {}
}
