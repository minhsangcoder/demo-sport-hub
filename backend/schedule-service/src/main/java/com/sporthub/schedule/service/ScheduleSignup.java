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
public class ScheduleSignup {
  private final ScheduleRepository repo;
  private final SchedulePrices prices;
  private final ScheduleAccess access;
  private final ScheduleReads reads;

  public ScheduleSignup(
      ScheduleRepository repo, SchedulePrices prices, ScheduleAccess access, ScheduleReads reads) {
    this.repo = repo;
    this.prices = prices;
    this.access = access;
    this.reads = reads;
  }

  @Transactional
  public Map<String, Object> prepareSignup(
      UUID facility,
      UUID application,
      UUID user,
      String timezone,
      com.sporthub.common.dto.OwnerSignupSetup setup) {
    setup.validate();
    var zone = ZoneId.of(timezone);
    repo.lock(facility);
    var bindings =
        repo.jdbc()
            .queryForList(
                "SELECT application_id FROM application_configuration_freeze WHERE facility_id=?",
                UUID.class,
                facility);
    if (!bindings.isEmpty()) {
      if (!application.equals(bindings.getFirst()))
        throw new ForbiddenException("Configuration belongs to another application");
      return Map.of("prepared", true);
    }
    if (!repo.hours(facility).isEmpty() || !repo.rules(facility).isEmpty())
      throw new ConflictException("Signup cannot replace existing configuration");
    for (var day : new TreeSet<>(setup.days())) {
      repo.jdbc()
          .update(
              "INSERT INTO"
                  + " operating_hours(id,facility_id,day_of_week,open_time,close_time,slot_duration_minutes,created_by)"
                  + " VALUES(?,?,?,?,?,?,?)",
              UUID.randomUUID(),
              facility,
              day,
              ScheduleValues.time(setup.opensAt()),
              ScheduleValues.time(setup.closesAt()),
              setup.slotMinutes(),
              user);
      repo.jdbc()
          .update(
              "INSERT INTO"
                  + " pricing_rules(id,facility_id,day_of_week,start_time,end_time,price_per_slot,priority,label,effective_from,currency,created_by)"
                  + " VALUES(?,?,?,?,?,?,0,'Initial signup price',?,'VND',?)",
              UUID.randomUUID(),
              facility,
              day,
              ScheduleValues.time(setup.opensAt()),
              ScheduleValues.time(setup.closesAt()),
              setup.pricePerSlot(),
              ScheduleValues.date(LocalDate.now(zone)),
              user);
    }
    repo.jdbc()
        .update(
            "INSERT INTO application_configuration_freeze(facility_id,application_id,frozen)"
                + " VALUES(?,?,false)",
            facility,
            application);
    for (var rule : repo.rules(facility)) prices.recordPriceVersion(rule, user);
    access.audit(
        facility,
        new Caller(user, "Applicant", Set.of("CUSTOMER"), Map.of()),
        "SIGNUP_CONFIGURATION_CREATED",
        Map.of("applicationId", application));
    return Map.of("prepared", true);
  }

  @Transactional
  public Map<String, Object> freezeApplication(
      UUID facility, UUID application, List<FacilityClient.Context> contexts) {
    repo.lock(facility);
    var hours = repo.hours(facility);
    var prices = repo.rules(facility);
    if (hours.isEmpty() || prices.isEmpty())
      throw new ConflictException("Configure operating hours and prices before submitting");
    boolean playable = false;
    for (var context : contexts) {
      if (!facility.equals(context.facilityId()))
        throw new ForbiddenException("Foreign court context");
      if (!context.enabled()) continue;
      var today = LocalDate.now(context.timezone());
      for (int day = 0; day <= 7; day++)
        for (var slot : reads.preview(context, today.plusDays(day)).slots()) {
          if (Set.of("PRICE_NOT_CONFIGURED", "AMBIGUOUS_PRICE")
              .contains(Objects.toString(slot.reason(), "")))
            throw new ConflictException("Configured slots must have an unambiguous price");
          if (slot.state().equals("ELIGIBLE") && slot.startsAt().isAfter(Instant.now()))
            playable = true;
        }
    }
    if (!playable)
      throw new ConflictException("Configure a future playable slot before submitting");
    int bound =
        repo.jdbc()
            .update(
                "INSERT INTO application_configuration_freeze(facility_id,application_id,frozen)"
                    + " VALUES(?,?,true) ON CONFLICT(facility_id) DO UPDATE SET frozen=true WHERE"
                    + " application_configuration_freeze.application_id=EXCLUDED.application_id",
                facility,
                application);
    if (bound != 1)
      throw new ConflictException("Configuration belongs to another Owner application");
    return Map.of("hours", hours, "prices", prices, "exceptions", repo.exceptions(facility));
  }

  @Transactional
  public Map<String, Object> releaseApplication(UUID facility, UUID application) {
    repo.lock(facility);
    repo.jdbc()
        .update(
            "UPDATE application_configuration_freeze SET frozen=false WHERE facility_id=? AND"
                + " application_id=?",
            facility,
            application);
    return Map.of("released", true);
  }
}
