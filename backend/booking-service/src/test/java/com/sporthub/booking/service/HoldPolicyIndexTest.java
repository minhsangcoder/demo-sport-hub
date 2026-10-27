package com.sporthub.booking.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.sporthub.booking.repository.BookingRepository;
import com.sporthub.common.exception.ActionPolicyException;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.data.redis.connection.*;
import org.springframework.data.redis.connection.lettuce.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.testcontainers.containers.GenericContainer;

class HoldPolicyIndexTest {
  static GenericContainer<?> redis;
  static LettuceConnectionFactory connection;
  static StringRedisTemplate template;
  static HoldPolicyIndex index;

  @BeforeAll
  static void start() {
    redis = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);
    redis.start();
    connection = factory(redis.getHost(), redis.getMappedPort(6379));
    template = new StringRedisTemplate(connection);
    index = new HoldPolicyIndex(template);
  }

  static LettuceConnectionFactory factory(String host, int port) {
    var settings =
        LettuceClientConfiguration.builder()
            .commandTimeout(Duration.ofSeconds(1))
            .shutdownTimeout(Duration.ZERO)
            .build();
    var result =
        new LettuceConnectionFactory(new RedisStandaloneConfiguration(host, port), settings);
    result.afterPropertiesSet();
    result.start();
    return result;
  }

  @AfterAll
  static void stop() {
    connection.destroy();
    redis.stop();
  }

  @Test
  void sourceDedupAndRevisionFenceRejectAnOlderRebuildWithoutTrustingDeliveryOrder() {
    var user = UUID.randomUUID();
    var source = UUID.randomUUID();
    var at = Instant.parse("2026-10-25T00:00:00Z");
    var fact = new HoldPenaltyPolicy.Fact(source, HoldPenaltyPolicy.Outcome.ABANDONED, at, 1);
    var old = new HoldPolicyLedger.Snapshot(UUID.randomUUID(), List.of(fact, fact));
    index.publish(user, old);
    index.publish(user, old);
    assertThat(template.opsForZSet().zCard(HoldPolicyIndex.prefix(user) + "window")).isEqualTo(1);
    assertThat(template.opsForHash().size(HoldPolicyIndex.prefix(user) + "sources")).isEqualTo(1);
    var latest = new HoldPolicyLedger.Snapshot(UUID.randomUUID(), List.of());
    index.publish(user, latest);
    index.publish(user, old);
    assertThat(index.current(user, latest.revision())).isFalse();
    index.publish(user, latest);
    assertThat(index.current(user, latest.revision())).isTrue();
    assertThat(template.opsForZSet().zCard(HoldPolicyIndex.prefix(user) + "window")).isZero();
  }

  @Test
  void uncommittedFactsCannotPublishARestrictionOrLeaveAGhostCounter() {
    var user = UUID.randomUUID();
    TransactionSynchronizationManager.setActualTransactionActive(true);
    try {
      assertThatThrownBy(
              () ->
                  index.publish(user, new HoldPolicyLedger.Snapshot(UUID.randomUUID(), List.of())))
          .isInstanceOf(IllegalStateException.class);
    } finally {
      TransactionSynchronizationManager.setActualTransactionActive(false);
    }
    assertThat(template.hasKey(HoldPolicyIndex.prefix(user) + "revision")).isFalse();
  }

  @Test
  void unavailableRedisFailsClosedWithTheServerRetryTime() {
    var unavailable = factory("127.0.0.1", 1);
    try {
      var ledger = mock(HoldPolicyLedger.class);
      var user = UUID.randomUUID();
      when(ledger.prepare(user))
          .thenReturn(new HoldPolicyLedger.Snapshot(UUID.randomUUID(), List.of()));
      var now = Instant.parse("2026-10-25T00:00:00Z");
      var protection =
          new HoldProtection(
              true,
              ledger,
              new HoldPolicyIndex(new StringRedisTemplate(unavailable)),
              mock(BookingRepository.class),
              Clock.fixed(now, ZoneOffset.UTC));
      assertThatThrownBy(() -> protection.prepare(user))
          .isInstanceOfSatisfying(
              ActionPolicyException.class,
              error -> {
                assertThat(error.status()).isEqualTo(503);
                assertThat(error.code()).isEqualTo("HOLD_POLICY_UNAVAILABLE");
                assertThat(error.details().get("retryAt")).isEqualTo(now.plusSeconds(5));
              });
      verify(ledger).prepare(user);
    } finally {
      unavailable.destroy();
    }
  }
}
