package com.sporthub.schedule.service;

import static com.sporthub.schedule.web.ScheduleDtos.*;

import com.sporthub.common.exception.*;
import com.sporthub.common.security.RemoteIdentity.Caller;
import com.sporthub.schedule.repository.ScheduleRepository;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(
    readOnly = true,
    isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
public class ScheduleHours {
  private final ScheduleRepository repo;
  private final FacilityClient facilities;
  private final com.sporthub.common.schedule.ConfigurationChanges configuration;
  private final Clock clock;
  private final ScheduleAccess access;
  private final ScheduleImpact impact;

  public ScheduleHours(
      ScheduleRepository repo,
      FacilityClient facilities,
      com.sporthub.common.schedule.ConfigurationChanges configuration,
      Clock clock,
      ScheduleAccess access,
      ScheduleImpact impact) {
    this.repo = repo;
    this.facilities = facilities;
    this.configuration = configuration;
    this.clock = clock;
    this.access = access;
    this.impact = impact;
  }

  @Transactional
  public List<Hours> replaceHours(UUID facility, HoursInput input, Caller caller, String token) {
    access.scheduleManager(facility, caller, token);
    access.courtScope(facility, input.courtId(), token);
    repo.lock(facility);
    var intervals = input.intervals();
    for (var interval : intervals) {
      ScheduleValues.validateTime(interval.opensAt());
      ScheduleValues.validateTime(interval.closesAt());
      if (!interval.opensAt().isBefore(interval.closesAt()))
        throw new IllegalArgumentException("Opening time must precede closing time");
      if (Duration.between(interval.opensAt(), interval.closesAt()).toMinutes()
              % interval.slotMinutes()
          != 0) throw new IllegalArgumentException("Opening interval must contain whole slots");
      for (var other : intervals)
        if (other != interval
            && other.dayOfWeek() == interval.dayOfWeek()
            && ScheduleValues.overlap(
                interval.opensAt(), interval.closesAt(), other.opensAt(), other.closesAt()))
          throw new ConflictException("Operating intervals overlap");
    }
    var reservations = configuration.begin(facility);
    repo.jdbc()
        .update(
            "UPDATE operating_hours SET is_active=FALSE,updated_at=NOW(),updated_by=? WHERE"
                + " facility_id=? AND court_id IS NOT DISTINCT FROM ? AND is_active",
            caller.id(),
            facility,
            input.courtId());
    for (var i : intervals)
      repo.jdbc()
          .update(
              "INSERT INTO"
                  + " operating_hours(id,facility_id,court_id,day_of_week,open_time,close_time,slot_duration_minutes,created_by)"
                  + " VALUES(?,?,?,?,?,?,?,?)",
              UUID.randomUUID(),
              facility,
              input.courtId(),
              i.dayOfWeek(),
              java.sql.Time.valueOf(i.opensAt()),
              java.sql.Time.valueOf(i.closesAt()),
              i.slotMinutes(),
              caller.id());
    impact.ensureNoImpact(facility, input.courtId(), reservations, token);
    access.audit(facility, caller, "HOURS_REPLACED", input);
    return repo.hours(facility);
  }

  @Transactional
  public ExceptionView addException(
      UUID facility, ExceptionInput input, Caller caller, String token) {
    return saveException(facility, null, input, caller, token);
  }

  @Transactional
  public ExceptionView updateException(
      UUID facility, UUID id, ExceptionInput input, Caller caller, String token) {
    return saveException(facility, id, input, caller, token);
  }

  public ExceptionView saveException(
      UUID facility, UUID existingId, ExceptionInput input, Caller caller, String token) {
    access.scheduleManager(facility, caller, token);
    access.courtScope(facility, input.courtId(), token);
    repo.lock(facility);
    var previous =
        existingId == null
            ? null
            : repo.exceptions(facility).stream()
                .filter(e -> e.id().equals(existingId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Exception not found"));
    var facilityConfig = facilities.configuration(facility, token);
    if (previous != null) requireFutureException(previous, facilityConfig.timezone());
    if (input.date().isBefore(LocalDate.now(clock.withZone(facilityConfig.timezone()))))
      throw new IllegalArgumentException("Past exceptions cannot be created");
    if (input.type().equals("SPECIAL_HOURS") && facilityConfig.status().equals("SUSPENDED"))
      throw new ConflictException("Suspended facilities cannot be specially opened");
    if (input.type().equals("CLOSED") && (input.opensAt() != null || input.closesAt() != null))
      throw new IllegalArgumentException("CLOSED exceptions apply to the whole local date");
    if (input.type().equals("SPECIAL_HOURS")
        && (input.opensAt() == null
            || input.closesAt() == null
            || !input.opensAt().isBefore(input.closesAt())))
      throw new IllegalArgumentException("Special opening hours must have a valid interval");
    if (input.type().equals("SPECIAL_HOURS")) {
      ScheduleValues.validateTime(input.opensAt());
      ScheduleValues.validateTime(input.closesAt());
      var hours = repo.hours(facility);
      var overridden =
          hours.stream().filter(h -> Objects.equals(h.courtId(), input.courtId())).toList();
      if (input.courtId() != null && overridden.isEmpty())
        overridden = hours.stream().filter(h -> h.courtId() == null).toList();
      int minutes =
          overridden.stream()
              .filter(h -> h.dayOfWeek() == input.date().getDayOfWeek().getValue())
              .findFirst()
              .map(Hours::slotMinutes)
              .orElse(60);
      if (Duration.between(input.opensAt(), input.closesAt()).toMinutes() % minutes != 0)
        throw new IllegalArgumentException("Special opening interval must contain whole slots");
    }
    for (var e : repo.exceptions(facility))
      if (!e.id().equals(existingId)
          && Objects.equals(e.courtId(), input.courtId())
          && e.date().equals(input.date())) {
        if (e.type().equals("CLOSED")
            || input.type().equals("CLOSED")
            || ScheduleValues.overlap(e.opensAt(), e.closesAt(), input.opensAt(), input.closesAt()))
          throw new ConflictException("Exceptions for the same scope overlap");
      }
    var reservations = configuration.begin(facility);
    var freshConfig = facilities.configuration(facility, token);
    if (previous != null) requireFutureException(previous, freshConfig.timezone());
    if (input.date().isBefore(LocalDate.now(clock.withZone(freshConfig.timezone()))))
      throw new IllegalArgumentException("Past exceptions cannot be created");
    if (input.type().equals("SPECIAL_HOURS") && freshConfig.status().equals("SUSPENDED"))
      throw new ConflictException("Suspended facilities cannot be specially opened");
    if (input.type().equals("SPECIAL_HOURS")
        && !input
            .date()
            .atTime(input.closesAt())
            .atZone(freshConfig.timezone())
            .toInstant()
            .isAfter(clock.instant()))
      throw new IllegalArgumentException("Special opening interval has already ended");
    UUID id = existingId == null ? UUID.randomUUID() : existingId;
    repo.jdbc()
        .update(
            "INSERT INTO"
                + " exception_calendar(id,facility_id,court_id,exception_date,exception_type,open_time,close_time,reason,created_by)"
                + " VALUES(?,?,?,?,?,?,?,?,?) ON CONFLICT(id) DO UPDATE SET"
                + " court_id=EXCLUDED.court_id,exception_date=EXCLUDED.exception_date,exception_type=EXCLUDED.exception_type,open_time=EXCLUDED.open_time,close_time=EXCLUDED.close_time,reason=EXCLUDED.reason",
            id,
            facility,
            input.courtId(),
            java.sql.Date.valueOf(input.date()),
            input.type(),
            ScheduleValues.time(input.opensAt()),
            ScheduleValues.time(input.closesAt()),
            input.reason(),
            caller.id());
    if (previous == null) impact.ensureNoImpact(facility, input.courtId(), reservations, token);
    else {
      var scoped =
          reservations.stream()
              .filter(
                  r ->
                      previous.courtId() == null
                          || input.courtId() == null
                          || previous.courtId().equals(r.courtId())
                          || input.courtId().equals(r.courtId()))
              .toList();
      impact.ensureNoImpact(facility, null, scoped, token);
    }
    access.audit(
        facility,
        caller,
        previous == null ? "EXCEPTION_CREATED" : "EXCEPTION_UPDATED",
        previous == null ? input : Map.of("before", previous, "after", input));
    return repo.exceptions(facility).stream()
        .filter(e -> e.id().equals(id))
        .findFirst()
        .orElseThrow();
  }

  @Transactional
  public void removeException(UUID facility, UUID id, Caller caller, String token) {
    access.scheduleManager(facility, caller, token);
    repo.lock(facility);
    var previous =
        repo.exceptions(facility).stream()
            .filter(e -> e.id().equals(id))
            .findFirst()
            .orElseThrow(() -> new ResourceNotFoundException("Exception not found"));
    var zone = facilities.configuration(facility, token).timezone();
    var starts =
        previous
            .date()
            .atTime(previous.opensAt() == null ? LocalTime.MIDNIGHT : previous.opensAt())
            .atZone(zone)
            .toInstant();
    if (!starts.isAfter(clock.instant()))
      throw new ConflictException("Started or past exceptions cannot be changed");
    var reservations = configuration.begin(facility);
    var freshZone = facilities.configuration(facility, token).timezone();
    if (!previous
        .date()
        .atTime(previous.opensAt() == null ? LocalTime.MIDNIGHT : previous.opensAt())
        .atZone(freshZone)
        .toInstant()
        .isAfter(clock.instant()))
      throw new ConflictException("Started or past exceptions cannot be changed");
    repo.jdbc().update("DELETE FROM exception_calendar WHERE id=? AND facility_id=?", id, facility);
    impact.ensureNoImpact(facility, previous.courtId(), reservations, token);
    access.audit(facility, caller, "EXCEPTION_REMOVED", previous);
  }

  public void requireFutureException(ExceptionView previous, ZoneId zone) {
    if (!previous
        .date()
        .atTime(previous.opensAt() == null ? LocalTime.MIDNIGHT : previous.opensAt())
        .atZone(zone)
        .toInstant()
        .isAfter(clock.instant()))
      throw new ConflictException("Started or past exceptions cannot be changed");
  }
}
