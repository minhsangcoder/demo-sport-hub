package com.sporthub.booking;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sporthub.booking.service.OrphanGapAdvice;
import com.sporthub.common.exception.ConflictException;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class OrphanGapAdviceTest {
  private final ObjectMapper json = new ObjectMapper();
  private final OrphanGapAdvice advice = new OrphanGapAdvice();
  private final Instant base = Instant.parse("2026-10-26T08:00:00Z");

  private String at(int minutes) {
    return base.plusSeconds(minutes * 60L).toString();
  }

  private ObjectNode quote(int start, int end) {
    return json.createObjectNode()
        .put("quoteVersion", 2)
        .put("policyVersion", 1)
        .put("amount", 150000)
        .put("courtId", "court")
        .put("startsAt", at(start))
        .put("endsAt", at(end));
  }

  private ObjectNode availability(int end) {
    var result = json.createObjectNode().put("policyVersion", 1);
    var slots = result.putArray("slots");
    for (int minute = 0; minute < end; minute += 30)
      slots
          .addObject()
          .put("startsAt", at(minute))
          .put("endsAt", at(minute + 30))
          .put("state", "AVAILABLE");
    return result;
  }

  @Test
  void twoThirtyMinuteGapsWarnWithoutChangingTheAcceptedRangeOrMoney() {
    var result = advice.attach(quote(30, 90), availability(120));
    assertThat(result.path("warnings").size()).isEqualTo(2);
    assertThat(result.path("amount").intValue()).isEqualTo(150000);
    assertThat(result.path("startsAt").asText()).isEqualTo(at(30));
    assertThat(result.path("suggestions").size()).isEqualTo(3);
    assertThat(result.path("suggestions").get(0).path("startsAt").asText()).isEqualTo(at(0));
    assertThat(result.path("suggestions").get(0).path("endsAt").asText()).isEqualTo(at(120));
  }

  @Test
  void suggestionsDoNotOverlapAnotherReservationOrExceedFourHours() {
    var preview = availability(360);
    ((ObjectNode) preview.path("slots").get(0)).put("state", "BLOCKED");
    var result = advice.attach(quote(60, 300), preview);
    assertThat(result.path("warnings").size()).isEqualTo(1);
    assertThat(result.path("suggestions").size()).isBetween(1, 3);
    for (var candidate : result.path("suggestions")) {
      var from = Instant.parse(candidate.path("startsAt").asText());
      var to = Instant.parse(candidate.path("endsAt").asText());
      assertThat(from).isAfterOrEqualTo(base.plusSeconds(1800));
      assertThat(java.time.Duration.between(from, to).toMinutes()).isBetween(60L, 240L);
    }
  }

  @Test
  void aNewHoldInsideTheSelectionInvalidatesAdviceInsteadOfSuggestingOccupiedTime() {
    var preview = availability(120);
    ((ObjectNode) preview.path("slots").get(1)).put("state", "BLOCKED");
    assertThatThrownBy(() -> advice.attach(quote(30, 90), preview))
        .isInstanceOf(ConflictException.class);
  }
}
