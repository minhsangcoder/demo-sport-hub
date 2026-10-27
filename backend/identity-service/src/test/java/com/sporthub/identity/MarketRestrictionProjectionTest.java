package com.sporthub.identity;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.*;
import com.sporthub.common.event.DomainEvent;
import com.sporthub.identity.service.MarketRestrictionConsumer;
import java.time.Instant;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

class MarketRestrictionProjectionTest {
  private JsonNode event(ObjectMapper json, UUID user, long version, int strikes) {
    var payload =
        json.createObjectNode()
            .put("principalId", user.toString())
            .put("version", version)
            .put("strikeCount", strikes)
            .put("permanent", strikes >= 3);
    if (strikes == 0) payload.putNull("blockedUntil");
    else payload.put("blockedUntil", Instant.parse("2026-11-25T00:00:00Z").toString());
    return json.valueToTree(
        DomainEvent.create("market.restriction.changed", 1, "booking-service", user, payload));
  }

  @Test
  void
      projectionOrdersVersionsDeduplicatesEventsAndAppliesAnAuditedCorrectionWithoutGrantingAccess() {
    try (var database = new PostgreSQLContainer<>("postgres:16-alpine")) {
      database.start();
      var source =
          new DriverManagerDataSource(
              database.getJdbcUrl(), database.getUsername(), database.getPassword());
      Flyway.configure().dataSource(source).load().migrate();
      var jdbc = new JdbcTemplate(source);
      var json = new ObjectMapper().findAndRegisterModules();
      var consumer = new MarketRestrictionConsumer(jdbc, json);
      var tx = new TransactionTemplate(new DataSourceTransactionManager(source));
      var user = UUID.randomUUID();
      jdbc.update(
          "INSERT INTO users(id,email,password_hash,status,email_verified)"
              + " VALUES(?,?,'unused','ACTIVE',true)",
          user,
          user + "@projection.invalid");
      var banned = event(json, user, 3, 3);
      tx.executeWithoutResult(status -> consumer.apply(banned));
      tx.executeWithoutResult(status -> consumer.apply(banned));
      tx.executeWithoutResult(status -> consumer.apply(event(json, user, 2, 1)));
      assertThat(
              jdbc.queryForObject(
                  "SELECT permanent FROM user_market_restrictions WHERE user_id=?",
                  Boolean.class,
                  user))
          .isTrue();
      assertThat(
              jdbc.queryForObject(
                  "SELECT count(*) FROM event_inbox WHERE consumer='market-restriction'",
                  Integer.class))
          .isEqualTo(2);
      tx.executeWithoutResult(status -> consumer.apply(event(json, user, 4, 2)));
      assertThat(
              jdbc.queryForObject(
                  "SELECT permanent FROM user_market_restrictions WHERE user_id=?",
                  Boolean.class,
                  user))
          .isFalse();
      assertThatThrownBy(
              () -> tx.executeWithoutResult(status -> consumer.apply(event(json, user, 4, 0))))
          .isInstanceOf(AmqpRejectAndDontRequeueException.class);
      assertThat(
              jdbc.queryForObject(
                  "SELECT strike_count FROM user_market_restrictions WHERE user_id=?",
                  Integer.class,
                  user))
          .isEqualTo(2);
      tx.executeWithoutResult(status -> consumer.apply(event(json, user, 5, 0)));
      assertThat(
              jdbc.queryForObject(
                  "SELECT blocked_until FROM user_market_restrictions WHERE user_id=?",
                  java.sql.Timestamp.class,
                  user))
          .isNull();
      var forged = (com.fasterxml.jackson.databind.node.ObjectNode) event(json, user, 6, 3);
      forged.put("producer", "transfer-service");
      assertThatThrownBy(() -> tx.executeWithoutResult(status -> consumer.apply(forged)))
          .isInstanceOf(AmqpRejectAndDontRequeueException.class);
      assertThat(jdbc.queryForObject("SELECT status FROM users WHERE id=?", String.class, user))
          .isEqualTo("ACTIVE");
    }
  }
}
