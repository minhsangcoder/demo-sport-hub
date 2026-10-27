package com.sporthub.booking;

import org.springframework.test.context.*;
import org.testcontainers.containers.GenericContainer;

@TestPropertySource(properties = "SPORTHUB_HOLD_PROTECTION_ENABLED=true")
abstract class HoldProtectionTestSupport extends PeerMarketIntegrationTest {
  private static final GenericContainer<?> redis =
      new GenericContainer<>("redis:7-alpine")
          .withExposedPorts(6379)
          .withCommand("redis-server", "--requirepass", "test");

  static {
    redis.start();
  }

  @DynamicPropertySource
  static void policyRedis(DynamicPropertyRegistry values) {
    values.add("spring.data.redis.host", redis::getHost);
    values.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
  }
}
