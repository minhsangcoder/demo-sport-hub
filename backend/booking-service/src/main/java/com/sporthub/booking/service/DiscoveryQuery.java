package com.sporthub.booking.service;

import static com.sporthub.booking.service.DiscoveryService.*;

import java.time.*;
import java.util.*;

record DiscoveryQuery(
    Criteria input, LocalDate day, LocalTime after, LocalTime before, boolean origin, String sort) {
  static DiscoveryQuery of(Criteria input, Clock clock, int advanceDays) {
    text(input.q());
    var today = clock.instant().atZone(ZoneOffset.UTC).toLocalDate();
    LocalDate day;
    LocalTime after, before;
    try {
      day = input.date() == null ? today : LocalDate.parse(input.date());
      after = input.opensAfter() == null ? null : LocalTime.parse(input.opensAfter());
      before = input.closesBefore() == null ? null : LocalTime.parse(input.closesBefore());
    } catch (DateTimeException ex) {
      throw new IllegalArgumentException("Use a valid ISO play date and HH:mm time window");
    }
    if (input.date() != null
        && (day.isBefore(today.minusDays(1)) || day.isAfter(today.plusDays(advanceDays + 1L))))
      throw new IllegalArgumentException("Choose a play date inside the booking window");
    if (after != null && before != null && !after.isBefore(before))
      throw new IllegalArgumentException("The time window must end after it starts");
    if ((input.minPrice() != null && input.minPrice().signum() < 0)
        || (input.maxPrice() != null && input.maxPrice().signum() < 0)
        || (input.minPrice() != null
            && input.maxPrice() != null
            && input.minPrice().compareTo(input.maxPrice()) > 0))
      throw new IllegalArgumentException("Invalid per-slot price range");
    boolean origin = input.latitude() != null && input.longitude() != null;
    if ((input.latitude() != null || input.longitude() != null) && !origin
        || origin
            && (!Double.isFinite(input.latitude())
                || !Double.isFinite(input.longitude())
                || Math.abs(input.latitude()) > 90
                || Math.abs(input.longitude()) > 180)
        || input.radiusKm() != null
            && (!origin
                || !Double.isFinite(input.radiusKm())
                || input.radiusKm() <= 0
                || input.radiusKm() > 500))
      throw new IllegalArgumentException(
          "Supply valid latitude/longitude and a radius of up to 500 km");
    String sort = input.sort() == null ? "NAME" : input.sort();
    if (!Set.of("NAME", "PRICE_ASC", "PRICE_DESC", "DISTANCE").contains(sort)
        || sort.equals("DISTANCE") && !origin)
      throw new IllegalArgumentException(
          "Choose a valid sort; distance requires latitude/longitude");
    return new DiscoveryQuery(input, day, after, before, origin, sort);
  }

  private static void text(String value) {
    if (value != null && value.length() > 180)
      throw new IllegalArgumentException("Search text must not exceed 180 characters");
  }
}
