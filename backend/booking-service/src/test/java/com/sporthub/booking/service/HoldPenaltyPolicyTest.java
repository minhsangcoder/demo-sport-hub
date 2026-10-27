package com.sporthub.booking.service;

import static org.assertj.core.api.Assertions.*;

import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class HoldPenaltyPolicyTest {
  final Instant start = Instant.parse("2026-10-25T10:00:00Z");

  HoldPenaltyPolicy.Fact fact(long seconds, boolean success, long position) {
    return new HoldPenaltyPolicy.Fact(
        UUID.randomUUID(),
        success ? HoldPenaltyPolicy.Outcome.SUCCEEDED : HoldPenaltyPolicy.Outcome.ABANDONED,
        start.plusSeconds(seconds),
        position);
  }

  @Test
  void thirdWithinRollingHourBlocksThirtyMinutesAndTheBoundaryIsExclusive() {
    var facts = List.of(fact(0, false, 1), fact(600, false, 2), fact(1200, false, 3));
    assertThat(HoldPenaltyPolicy.calculate(facts, start.plusSeconds(1200)).holdBlockedUntil())
        .isEqualTo(start.plusSeconds(3000));
    assertThat(HoldPenaltyPolicy.calculate(facts, start.plusSeconds(3000)).holdBlockedUntil())
        .isNull();
    assertThat(
            HoldPenaltyPolicy.calculate(
                    List.of(fact(0, false, 1), fact(600, false, 2), fact(3600, false, 3)),
                    start.plusSeconds(3600))
                .holdBlockedUntil())
        .isNull();
  }

  @Test
  void successResetsConsecutiveOnlyAndDoesNotLiftAnAlreadyCreatedRestriction() {
    var facts =
        List.of(
            fact(0, false, 1),
            fact(1, false, 2),
            fact(2, false, 3),
            fact(3, true, 4),
            fact(4, false, 5),
            fact(5, false, 6));
    var result = HoldPenaltyPolicy.calculate(facts, start.plusSeconds(5));
    assertThat(result.holdBlockedUntil()).isEqualTo(start.plusSeconds(1802));
    assertThat(result.onlineBlockedUntil()).isEqualTo(start.plusSeconds(86405));
    assertThat(
            HoldPenaltyPolicy.calculate(
                    List.of(
                        fact(0, false, 1), fact(1, false, 2), fact(2, true, 3), fact(3, false, 4)),
                    start.plusSeconds(3))
                .holdBlockedUntil())
        .isNull();
  }

  @Test
  void outOfOrderReplayIsDeduplicatedAndEqualTimesUseTheDurableSourceOrder() {
    var one = fact(0, false, 1);
    var two = fact(0, false, 2);
    var success = fact(0, true, 3);
    var three = fact(0, false, 4);
    var facts = List.of(three, one, success, two, one);
    assertThat(HoldPenaltyPolicy.calculate(facts, start).holdBlockedUntil()).isNull();
    assertThat(HoldPenaltyPolicy.calculate(facts, start))
        .isEqualTo(HoldPenaltyPolicy.calculate(List.of(one, two, success, three), start));
    assertThatThrownBy(
            () ->
                HoldPenaltyPolicy.calculate(
                    List.of(
                        one,
                        new HoldPenaltyPolicy.Fact(
                            one.sourceId(),
                            HoldPenaltyPolicy.Outcome.SUCCEEDED,
                            one.at(),
                            one.position())),
                    start))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void vietnamMidnightResetsDailyCountingButNotTheExistingTwentyFourHourBan() {
    Instant fifth = Instant.parse("2026-10-25T16:59:00Z");
    var facts = new ArrayList<HoldPenaltyPolicy.Fact>();
    for (int n = 0; n < 5; n++)
      facts.add(
          new HoldPenaltyPolicy.Fact(
              UUID.randomUUID(),
              HoldPenaltyPolicy.Outcome.ABANDONED,
              fifth.minusSeconds((4 - n) * 60L),
              n));
    var nextDay = Instant.parse("2026-10-25T17:01:00Z");
    assertThat(HoldPenaltyPolicy.calculate(facts, nextDay).onlineBlockedUntil())
        .isEqualTo(fifth.plusSeconds(86400));
    facts.add(
        new HoldPenaltyPolicy.Fact(
            UUID.randomUUID(), HoldPenaltyPolicy.Outcome.ABANDONED, nextDay, 6));
    assertThat(HoldPenaltyPolicy.calculate(facts, nextDay).onlineBlockedUntil())
        .isEqualTo(fifth.plusSeconds(86400));
    assertThat(HoldPenaltyPolicy.calculate(facts, fifth.plusSeconds(86400)).onlineBlockedUntil())
        .isNull();
  }
}
