package com.sporthub.booking.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sporthub.booking.repository.BookingRepository;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class BookingAvailabilityService {
  private final BookingRepository repo;
  private final BookingDependencies dependencies;
  private final Clock clock;
  private final ConfigurationGuardService configuration;
  private final int advanceDays;

  public BookingAvailabilityService(
      BookingRepository repo,
      BookingDependencies dependencies,
      Clock clock,
      ConfigurationGuardService configuration,
      @Value("${booking.advance-days:90}") int advanceDays) {
    this.repo = repo;
    this.dependencies = dependencies;
    this.clock = clock;
    this.configuration = configuration;
    this.advanceDays = advanceDays;
  }

  public JsonNode availability(UUID court, String date) {
    LocalDate day;
    try {
      day = LocalDate.parse(date);
    } catch (DateTimeException ex) {
      throw new IllegalArgumentException("Use a valid ISO play date");
    }
    var preview = (ObjectNode) dependencies.preview(court, day.toString()).deepCopy();
    UUID facility =
        preview.hasNonNull("facilityId")
            ? UUID.fromString(preview.path("facilityId").asText())
            : null;
    if (facility != null) {
      configuration.lockReservationFacility(facility);
      preview = (ObjectNode) dependencies.preview(court, day.toString()).deepCopy();
    }
    Instant observed = clock.instant(), horizon = observed.plusSeconds(advanceDays * 86400L);
    var zone = ZoneId.of(preview.path("timezone").asText("Asia/Ho_Chi_Minh"));
    LocalDate today = observed.atZone(zone).toLocalDate();
    if (day.isBefore(today) || day.isAfter(horizon.atZone(zone).toLocalDate()))
      throw new IllegalArgumentException("Choose a date inside the booking window");
    boolean updating =
        facility != null
            && repo.jdbc()
                    .queryForObject(
                        "SELECT count(*) FROM configuration_change_guards WHERE facility_id=? AND"
                            + " state='PREPARED'",
                        Integer.class,
                        facility)
                > 0;
    var range = reservationWindow(preview, day, zone);
    var rows =
        repo.jdbc()
            .queryForList(
                "SELECT starts_at,ends_at,state FROM slot_reservations WHERE court_id=?"
                    + " AND state IN('BOOKED','HOLD')"
                    + " AND tstzrange(starts_at,ends_at,'[)')"
                    + " && tstzrange(?::timestamptz,?::timestamptz,'[)')"
                    + " AND (state='BOOKED' OR expires_at>?)",
                court,
                Timestamp.from(range.from()),
                Timestamp.from(range.to()),
                Timestamp.from(observed));
    for (var slot : preview.withArray("slots"))
      if (slot.path("state").asText().equals("ELIGIBLE")) {
        Instant start = Instant.parse(slot.path("startsAt").asText()),
            end = Instant.parse(slot.path("endsAt").asText());
        String reason = null;
        if (!start.isAfter(observed)) reason = "PAST_SLOT";
        else if (start.isAfter(horizon)) reason = "BOOKING_WINDOW";
        else if (updating) reason = "CONFIGURATION_UPDATING";
        else
          for (var row : rows)
            if (start.isBefore(((Timestamp) row.get("ends_at")).toInstant())
                && end.isAfter(((Timestamp) row.get("starts_at")).toInstant())) {
              reason = row.get("state").equals("HOLD") ? "HELD" : "BOOKED";
              break;
            }
        ((ObjectNode) slot).put("state", reason == null ? "AVAILABLE" : "BLOCKED");
        if (reason != null) ((ObjectNode) slot).put("reason", reason);
      }
    preview
        .put("observedAt", observed.toString())
        .put("configurationUpdating", updating)
        .put("bookingWindowEndsAt", horizon.toString());
    return preview;
  }

  private record ReservationWindow(Instant from, Instant to) {}

  private ReservationWindow reservationWindow(JsonNode preview, LocalDate day, ZoneId zone) {
    Instant from = day.atStartOfDay(zone).toInstant();
    Instant to = day.plusDays(1).atStartOfDay(zone).toInstant();
    for (var slot : preview.path("slots")) {
      if (!slot.path("state").asText().equals("ELIGIBLE")) continue;
      Instant start = Instant.parse(slot.path("startsAt").asText());
      Instant end = Instant.parse(slot.path("endsAt").asText());
      if (start.isBefore(from)) from = start;
      if (end.isAfter(to)) to = end;
    }
    return new ReservationWindow(from, to);
  }
}
