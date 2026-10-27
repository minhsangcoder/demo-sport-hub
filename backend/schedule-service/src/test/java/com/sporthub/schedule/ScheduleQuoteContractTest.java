package com.sporthub.schedule;

import static com.sporthub.schedule.web.ScheduleDtos.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ScheduleQuoteContractTest {
  @Test
  void hourlyExampleExposesWholeVndSegmentsAndRevisionWithoutChangingLegacyConstruction() {
    var json = new ObjectMapper().findAndRegisterModules();
    UUID facility = UUID.randomUUID(), court = UUID.randomUUID(), rule = UUID.randomUUID();
    Instant start = Instant.parse("2026-10-26T08:30:00Z"), boundary = start.plusSeconds(1800), end = boundary.plusSeconds(1800);
    var segments = List.of(
        new Slot(start, boundary, "ELIGIBLE", null, new BigDecimal("50000"), "VND", rule, 0, "15–16", List.of(), new BigDecimal("100000"), 30),
        new Slot(boundary, end, "ELIGIBLE", null, new BigDecimal("100000"), "VND", rule, 1, "16–17", List.of(), new BigDecimal("200000"), 30));
    var quote = new Quote(facility, court, start, end, new BigDecimal("150000"), "VND", "Asia/Ho_Chi_Minh", segments,
        2, "HOURLY_INTERVAL_V1", "a".repeat(64), 1, 60, "b".repeat(64), List.of(), List.of());
    var tree = json.valueToTree(quote);
    assertThat(tree.path("quoteVersion").asInt()).isEqualTo(2);
    assertThat(tree.path("amount").decimalValue()).isEqualByComparingTo("150000");
    assertThat(tree.path("segments").get(0).path("pricePerHour").asInt()).isEqualTo(100000);
    assertThat(tree.path("quoteHash").asText()).hasSize(64);
    assertThat(new Quote(facility, court, start, end, BigDecimal.ONE, "VND", "Asia/Ho_Chi_Minh", List.of()).quoteVersion())
        .isEqualTo(1);
  }
}
