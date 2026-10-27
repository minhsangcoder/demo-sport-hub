package com.sporthub.booking.service;

import static com.sporthub.booking.service.DiscoveryService.*;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
class DiscoveryResultMapper {
  private final BookingAvailabilityService availability;
  private final Clock clock;
  private final int advanceDays;

  DiscoveryResultMapper(
      BookingAvailabilityService availability,
      Clock clock,
      @org.springframework.beans.factory.annotation.Value("${booking.advance-days:90}")
          int advanceDays) {
    this.availability = availability;
    this.clock = clock;
    this.advanceDays = advanceDays;
  }

  record Outcome(List<Result> rows, RuntimeException error) {}

  Outcome group(DiscoveryPlanner.Group group, DiscoveryQuery query) {
    var rows = new ArrayList<Result>();
    if (group.candidates().isEmpty()) return new Outcome(rows, group.error());
    var dates = new LinkedHashMap<UUID, LocalDate>();
    for (var candidate : group.candidates()) dates.put(candidate.courtId(), candidate.playDay());
    BookingAvailabilityService.Batch batch;
    try {
      batch = availability.discovery(group.candidates().getFirst().facility().id(), dates);
    } catch (RuntimeException error) {
      return new Outcome(rows, error);
    }
    for (var c : group.candidates()) {
      var preview = batch.previews().get(c.courtId());
      if (preview == null) break;
      try {
        var row = match(c, query, preview);
        if (row != null) rows.add(row);
      } catch (RuntimeException error) {
        return new Outcome(rows, error);
      }
    }
    return new Outcome(rows, batch.error() == null ? group.error() : batch.error());
  }

  private Result match(DiscoveryPlanner.Candidate c, DiscoveryQuery query, JsonNode preview) {
    var input = query.input();
    var after = query.after();
    var before = query.before();
    var summary = c.facility();
    var court = c.court();
    var courtId = c.courtId();
    var category = c.category();
    var playDay = c.playDay();
    var distance = c.distance();
    var names = Map.of(category, c.sportName());
    var slots = new ArrayList<JsonNode>();
    var zone = ZoneId.of(summary.timezone());
    for (var slot : preview.path("slots")) {
      if (!slot.path("state").asText().equals("AVAILABLE") || !slot.path("amount").isNumber())
        continue;
      Instant start = Instant.parse(slot.path("startsAt").asText()),
          end = Instant.parse(slot.path("endsAt").asText());
      if (start.isAfter(clock.instant().plusSeconds(advanceDays * 86400L))) continue;
      var amount = slot.path("amount").decimalValue();
      if (after != null && start.atZone(zone).toLocalTime().isBefore(after)
          || before != null && end.atZone(zone).toLocalTime().isAfter(before)
          || input.minPrice() != null && amount.compareTo(input.minPrice()) < 0
          || input.maxPrice() != null && amount.compareTo(input.maxPrice()) > 0) continue;
      slots.add(slot);
    }
    if (slots.isEmpty()) return null;
    var first =
        slots.stream().min(Comparator.comparing(s -> s.path("startsAt").asText())).orElseThrow();
    var cheapest =
        slots.stream()
            .min(Comparator.comparing(s -> s.path("amount").decimalValue()))
            .orElseThrow();
    return new Result(
        summary,
        courtId,
        court.path("name").asText(),
        category,
        names.get(category),
        playDay.toString(),
        cheapest.path("amount").decimalValue(),
        cheapest.path("currency").asText("VND"),
        slots.size(),
        Instant.parse(first.path("startsAt").asText()),
        Instant.parse(first.path("endsAt").asText()),
        distance);
  }
}
