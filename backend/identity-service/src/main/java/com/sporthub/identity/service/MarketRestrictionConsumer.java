package com.sporthub.identity.service;

import com.fasterxml.jackson.databind.*;
import com.sporthub.common.security.*;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Versioned display state; delayed events never authorize a market action. */
@Service
public class MarketRestrictionConsumer {
  public static final String QUEUE = "sporthub.identity.market-restrictions";
  private final JdbcTemplate jdbc;
  private final ObjectMapper json;

  public MarketRestrictionConsumer(JdbcTemplate jdbc, ObjectMapper json) {
    this.jdbc = jdbc;
    this.json = json;
  }

  @RabbitListener(queues = QUEUE)
  @Transactional
  public void receive(Message message) {
    try {
      apply(json.readTree(message.getBody()));
    } catch (java.io.IOException invalid) {
      throw new AmqpRejectAndDontRequeueException("Invalid market restriction", invalid);
    }
  }

  @Transactional
  public void apply(JsonNode event) {
    var body = event.path("payload");
    UUID principal, eventId;
    Instant until;
    try {
      if (!"booking-service".equals(event.path("producer").asText())
          || !"market.restriction.changed".equals(event.path("eventType").asText())
          || event.path("eventVersion").asInt() != 1
          || !body.isObject()
          || body.size() != 5
          || !body.path("version").isIntegralNumber()
          || body.path("version").asLong() < 1
          || !body.path("strikeCount").isInt()
          || body.path("strikeCount").asInt() < 0
          || !body.path("permanent").isBoolean()
          || body.path("permanent").asBoolean() != (body.path("strikeCount").asInt() >= 3))
        throw new IllegalArgumentException();
      principal = UUID.fromString(body.path("principalId").asText());
      eventId = UUID.fromString(event.path("eventId").asText());
      if (!principal.toString().equals(event.path("aggregateId").asText()))
        throw new IllegalArgumentException();
      until =
          body.path("blockedUntil").isNull()
              ? null
              : Instant.parse(body.path("blockedUntil").asText());
      if ((body.path("strikeCount").asInt() == 0) != (until == null))
        throw new IllegalArgumentException();
    } catch (Exception invalid) {
      throw new AmqpRejectAndDontRequeueException("Invalid market restriction facts", invalid);
    }
    jdbc.queryForObject(
        "SELECT pg_advisory_xact_lock(hashtextextended(?,0))", Object.class, principal.toString());
    if (!Boolean.TRUE.equals(
        jdbc.queryForObject(
            "SELECT EXISTS(SELECT 1 FROM users WHERE id=?)", Boolean.class, principal)))
      throw new AmqpRejectAndDontRequeueException("Unknown restriction principal");
    if (jdbc.update(
            "INSERT INTO event_inbox(consumer,event_id) VALUES('market-restriction',?) ON CONFLICT"
                + " DO NOTHING",
            eventId)
        == 0) return;
    long version = body.path("version").asLong();
    String fingerprint = Signatures.sha256(CanonicalCommandJson.write(json, body));
    var current =
        jdbc.queryForList(
            "SELECT version,fingerprint FROM user_market_restrictions WHERE user_id=?", principal);
    if (!current.isEmpty()) {
      long previous = ((Number) current.getFirst().get("version")).longValue();
      if (version < previous) return;
      if (version == previous) {
        if (!fingerprint.equals(current.getFirst().get("fingerprint")))
          throw new AmqpRejectAndDontRequeueException("Conflicting market restriction version");
        return;
      }
    }
    jdbc.update(
        """
INSERT INTO user_market_restrictions(user_id,version,strike_count,permanent,blocked_until,fingerprint,updated_at)
VALUES(?,?,?,?,?,?,?) ON CONFLICT(user_id) DO UPDATE SET version=EXCLUDED.version,
strike_count=EXCLUDED.strike_count,permanent=EXCLUDED.permanent,blocked_until=EXCLUDED.blocked_until,
fingerprint=EXCLUDED.fingerprint,updated_at=EXCLUDED.updated_at
""",
        principal,
        version,
        body.path("strikeCount").asInt(),
        body.path("permanent").asBoolean(),
        until == null ? null : Timestamp.from(until),
        fingerprint,
        Timestamp.from(Instant.now()));
  }

  @Configuration
  static class Messaging {
    @Bean
    org.springframework.amqp.core.Queue marketRestrictionsQueue() {
      return QueueBuilder.durable(QUEUE).deadLetterExchange("sporthub.dlx").build();
    }

    @Bean
    Binding marketRestrictionsBinding() {
      return BindingBuilder.bind(marketRestrictionsQueue())
          .to(new TopicExchange("sporthub.booking"))
          .with("market.restriction.changed");
    }
  }
}
