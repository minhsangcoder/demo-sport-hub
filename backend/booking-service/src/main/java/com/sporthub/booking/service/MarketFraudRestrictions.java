package com.sporthub.booking.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sporthub.booking.repository.BookingRepository;
import com.sporthub.common.event.*;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.*;

/**
 * Rebuild under the principal fence; corrections append evidence instead of rewriting a finding.
 */
@Component
public class MarketFraudRestrictions {
  private final BookingRepository repo;
  private final Clock clock;
  private final ObjectMapper json;

  public MarketFraudRestrictions(BookingRepository repo, Clock clock, ObjectMapper json) {
    this.repo = repo;
    this.clock = clock;
    this.json = json;
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public void rebuild(UUID principal) {
    var facts =
        repo.jdbc()
            .queryForMap(
                """
SELECT count(*)::integer AS count,max(s.recorded_at+interval '30 days') AS until
FROM market_fraud_strikes s LEFT JOIN market_strike_corrections c USING(acquisition_id)
WHERE s.principal_id=? AND c.acquisition_id IS NULL
""",
                principal);
    int count = (Integer) facts.get("count");
    var until = (Timestamp) facts.get("until");
    long version =
        repo.jdbc()
            .queryForObject(
                """
INSERT INTO user_admission_restrictions(principal_id,market_blocked_until,market_permanent,
  strike_count,policy_version,updated_at) VALUES(?,?,?,?,1,?)
ON CONFLICT(principal_id) DO UPDATE SET market_blocked_until=EXCLUDED.market_blocked_until,
  market_permanent=EXCLUDED.market_permanent,strike_count=EXCLUDED.strike_count,
  policy_version=user_admission_restrictions.policy_version+1,updated_at=EXCLUDED.updated_at
RETURNING policy_version
""",
                Long.class,
                principal,
                until,
                count >= 3,
                count,
                Timestamp.from(clock.instant()));
    var payload =
        json.createObjectNode()
            .put("principalId", principal.toString())
            .put("version", version)
            .put("strikeCount", count)
            .put("permanent", count >= 3);
    if (until == null) payload.putNull("blockedUntil");
    else payload.put("blockedUntil", until.toInstant().toString());
    ReliableOutbox.enqueue(
        repo.jdbc(),
        json,
        RabbitMQConfig.EXCHANGE_BOOKING,
        DomainEvent.create("market.restriction.changed", 1, "booking-service", principal, payload));
  }
}
