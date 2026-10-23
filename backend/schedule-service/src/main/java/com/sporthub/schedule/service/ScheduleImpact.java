package com.sporthub.schedule.service;

import static com.sporthub.schedule.web.ScheduleDtos.*;

import com.sporthub.common.exception.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(
    readOnly = true,
    isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
public class ScheduleImpact {
  private final FacilityClient facilities;
  private final ScheduleAccess access;
  private final ScheduleReads reads;

  public ScheduleImpact(FacilityClient facilities, ScheduleAccess access, ScheduleReads reads) {
    this.facilities = facilities;
    this.access = access;
    this.reads = reads;
  }

  public void ensureNoImpact(
      UUID facility,
      UUID scope,
      List<com.sporthub.common.schedule.ConfigurationGuardProtocol.Reservation> reservations,
      String token) {
    var contexts = new HashMap<UUID, FacilityClient.Context>();
    var affected =
        new ArrayList<com.sporthub.common.schedule.ConfigurationGuardProtocol.Reservation>();
    for (var reservation : reservations) {
      if (scope != null && !scope.equals(reservation.courtId())) continue;
      var context =
          contexts.computeIfAbsent(reservation.courtId(), id -> facilities.context(id, token));
      access.checkFacility(facility, context);
      var day = reservation.startsAt().atZone(context.timezone()).toLocalDate();
      Instant cursor = reservation.startsAt();
      boolean maintenance =
          context.maintenance().stream()
              .anyMatch(
                  m ->
                      reservation.startsAt().isBefore(m.endsAt())
                          && reservation.endsAt().isAfter(m.startsAt()));
      if (!maintenance)
        for (var slot : reads.preview(context, day).slots()) {
          if (!slot.endsAt().isAfter(cursor) || !slot.startsAt().isBefore(reservation.endsAt()))
            continue;
          String reason = Objects.toString(slot.reason(), "");
          if (Set.of(
                  "CLOSED_EXCEPTION",
                  "COURT_DISABLED",
                  "FACILITY_UNAVAILABLE",
                  "TIMEZONE_TRANSITION")
              .contains(reason)) break;
          if (reservation.state().equals("BOOKED")) {
            // A confirmed booking keeps its original grid and price. Only temporal coverage
            // matters.
            if (slot.startsAt().isAfter(cursor)) break;
            cursor =
                slot.endsAt().isAfter(reservation.endsAt()) ? reservation.endsAt() : slot.endsAt();
          } else {
            if (!slot.startsAt().equals(cursor) || slot.endsAt().isAfter(reservation.endsAt()))
              break;
            cursor = slot.endsAt();
          }
          if (cursor.equals(reservation.endsAt())) break;
        }
      // Price eligibility is intentionally excluded: existing price snapshots remain immutable.
      if (!cursor.equals(reservation.endsAt())) affected.add(reservation);
    }
    if (!affected.isEmpty()) throw new ConfigurationImpactException(affected);
  }
}
