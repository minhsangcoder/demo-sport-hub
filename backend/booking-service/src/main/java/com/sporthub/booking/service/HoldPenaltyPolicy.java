package com.sporthub.booking.service;

import java.time.*;
import java.util.*;

/** Rebuilds the same restrictions from facts, independently of delivery order or Redis uptime. */
final class HoldPenaltyPolicy {
  enum Outcome {
    ABANDONED,
    SUCCEEDED
  }

  record Fact(UUID sourceId, Outcome outcome, Instant at, long position) {}

  record Result(Instant holdBlockedUntil, Instant onlineBlockedUntil) {}

  static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

  private HoldPenaltyPolicy() {}

  static Result calculate(Collection<Fact> input, Instant now) {
    var unique = new HashMap<UUID, Fact>();
    for (var fact : input) {
      Objects.requireNonNull(fact.sourceId());
      Objects.requireNonNull(fact.at());
      Objects.requireNonNull(fact.outcome());
      var old = unique.putIfAbsent(fact.sourceId(), fact);
      if (old != null && !old.equals(fact))
        throw new IllegalArgumentException("Conflicting admission fact");
    }
    var ordered =
        unique.values().stream()
            .filter(fact -> !fact.at().isAfter(now))
            .sorted(Comparator.comparing(Fact::at).thenComparingLong(Fact::position))
            .toList();
    var consecutive = new ArrayDeque<Instant>();
    var daily = new HashMap<LocalDate, Integer>();
    Instant holdUntil = null, onlineUntil = null;
    for (var fact : ordered) {
      if (fact.outcome() == Outcome.SUCCEEDED) {
        consecutive.clear();
        continue;
      }
      while (!consecutive.isEmpty()
          && !consecutive.getFirst().isAfter(fact.at().minusSeconds(3600)))
        consecutive.removeFirst();
      consecutive.addLast(fact.at());
      if (consecutive.size() >= 3) holdUntil = later(holdUntil, fact.at().plusSeconds(1800));
      int total = daily.merge(fact.at().atZone(ZONE).toLocalDate(), 1, Integer::sum);
      if (total == 5) onlineUntil = later(onlineUntil, fact.at().plusSeconds(86400));
    }
    return new Result(active(holdUntil, now), active(onlineUntil, now));
  }

  private static Instant later(Instant a, Instant b) {
    return a == null || b.isAfter(a) ? b : a;
  }

  private static Instant active(Instant until, Instant now) {
    return until != null && until.isAfter(now) ? until : null;
  }
}
