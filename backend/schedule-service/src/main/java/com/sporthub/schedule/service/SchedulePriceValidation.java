package com.sporthub.schedule.service;

import static com.sporthub.schedule.web.ScheduleDtos.*;

import com.sporthub.common.exception.*;
import com.sporthub.schedule.repository.ScheduleRepository;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(
    readOnly = true,
    isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
public class SchedulePriceValidation {
  private final ScheduleRepository repo;
  private final FacilityClient facilities;
  private final jakarta.validation.Validator validator;
  private final ScheduleAccess access;
  private final ScheduleReads reads;
  private final PricingEngine pricing = new PricingEngine();

  public SchedulePriceValidation(
      ScheduleRepository repo,
      FacilityClient facilities,
      jakarta.validation.Validator validator,
      ScheduleAccess access,
      ScheduleReads reads) {
    this.repo = repo;
    this.facilities = facilities;
    this.validator = validator;
    this.access = access;
    this.reads = reads;
  }

  public void ensurePriceUnambiguous(
      UUID facility, PriceRule candidate, UUID ignored, String token) {
    var contexts = new HashMap<UUID, FacilityClient.Context>();
    for (var rule : repo.rules(facility))
      if (!rule.id().equals(ignored)
          && pricing.conflict(
              rule,
              candidate,
              id -> contexts.computeIfAbsent(id, key -> facilities.context(key, token))))
        throw new ConflictException(
            "Price rules conflict at the same scope or precedence; change the scope or priority");
  }

  public void validatePrice(UUID facility, PriceInput i, String token) {
    if (!validator.validate(i).isEmpty())
      throw new IllegalArgumentException("Invalid pricing fields");
    com.sporthub.common.money.Money.positive(i.pricePerSlot(), i.currency());
    ScheduleValues.validateTime(i.startsAt());
    ScheduleValues.validateTime(i.endsAt());
    if (!i.startsAt().isBefore(i.endsAt()))
      throw new IllegalArgumentException("Price interval must end after it starts");
    if ((i.dayOfWeek() == null) == (i.specificDate() == null))
      throw new IllegalArgumentException("Choose exactly one weekday or specific date");
    if (i.effectiveTo() != null && i.effectiveTo().isBefore(i.effectiveFrom()))
      throw new IllegalArgumentException("Effective period is invalid");
    LocalDate day =
        i.specificDate() != null
            ? i.specificDate()
            : i.effectiveFrom()
                .plusDays(
                    Math.floorMod(i.dayOfWeek() - i.effectiveFrom().getDayOfWeek().getValue(), 7));
    if (day.isBefore(i.effectiveFrom()) || i.effectiveTo() != null && day.isAfter(i.effectiveTo()))
      throw new IllegalArgumentException("Rule has no applicable date inside its effective period");
    if (i.sportCategoryId() != null) facilities.requireCategory(i.sportCategoryId());
    if (i.courtTypeId() != null) facilities.requireType(facility, i.courtTypeId(), token);
    var hours = repo.hours(facility);
    UUID scope = i.courtId();
    if (scope != null) {
      var context = facilities.context(scope, token);
      access.checkFacility(facility, context);
      if (!context.enabled()) throw new IllegalArgumentException("Court must be enabled");
      if (i.sportCategoryId() != null && !i.sportCategoryId().equals(context.sportCategoryId())
          || i.courtTypeId() != null && !i.courtTypeId().equals(context.courtTypeId()))
        throw new IllegalArgumentException("Price scope does not match the court classification");
    }
    var overrides = hours.stream().filter(h -> scope != null && scope.equals(h.courtId())).toList();
    var base =
        overrides.isEmpty() ? hours.stream().filter(h -> h.courtId() == null).toList() : overrides;
    List<Interval> intervals =
        base.stream()
            .filter(h -> h.dayOfWeek() == day.getDayOfWeek().getValue())
            .map(h -> new Interval(h.dayOfWeek(), h.opensAt(), h.closesAt(), h.slotMinutes()))
            .toList();
    if (i.specificDate() != null) {
      var calendar = reads.calendar(facility, scope, day);
      intervals = calendar.closed() ? List.of() : calendar.intervals();
    }
    if (scope == null && (i.courtTypeId() != null || i.sportCategoryId() != null)) {
      var targets =
          facilities.courts(facility, token).stream()
              .filter(
                  c ->
                      c.enabled()
                          && (i.sportCategoryId() == null
                              || i.sportCategoryId().equals(c.sportCategoryId()))
                          && (i.courtTypeId() == null || i.courtTypeId().equals(c.courtTypeId())))
              .toList();
      if (!targets.isEmpty()) {
        for (var context : targets) {
          access.checkFacility(facility, context);
          var own = hours.stream().filter(h -> context.courtId().equals(h.courtId())).toList();
          var effective =
              own.isEmpty() ? hours.stream().filter(h -> h.courtId() == null).toList() : own;
          var selected =
              effective.stream()
                  .filter(h -> h.dayOfWeek() == day.getDayOfWeek().getValue())
                  .map(h -> new Interval(h.dayOfWeek(), h.opensAt(), h.closesAt(), h.slotMinutes()))
                  .toList();
          if (i.specificDate() != null) {
            var calendar = reads.calendar(facility, context.courtId(), day);
            selected = calendar.closed() ? List.of() : calendar.intervals();
          }
          if (priceRangeFits(i, selected)) return;
        }
        throw new IllegalArgumentException(
            "Price interval is outside every matching court operating calendar");
      }
    }
    if (!priceRangeFits(i, intervals)) {
      // Broad prices can serve separately configured courts, including a court specially opened on
      // a facility holiday.
      if (scope == null) {
        for (var context : facilities.courts(facility, token))
          if (context.enabled()
              && (i.sportCategoryId() == null
                  || i.sportCategoryId().equals(context.sportCategoryId()))
              && (i.courtTypeId() == null || i.courtTypeId().equals(context.courtTypeId()))) {
            access.checkFacility(facility, context);
            var own = hours.stream().filter(h -> context.courtId().equals(h.courtId())).toList();
            var effective =
                own.isEmpty() ? hours.stream().filter(h -> h.courtId() == null).toList() : own;
            var selected =
                effective.stream()
                    .filter(h -> h.dayOfWeek() == day.getDayOfWeek().getValue())
                    .map(
                        h ->
                            new Interval(h.dayOfWeek(), h.opensAt(), h.closesAt(), h.slotMinutes()))
                    .toList();
            if (i.specificDate() != null) {
              var calendar = reads.calendar(facility, context.courtId(), day);
              selected = calendar.closed() ? List.of() : calendar.intervals();
            }
            if (priceRangeFits(i, selected)) return;
          }
      }
      throw new IllegalArgumentException(
          "Price interval must fit effective operating hours and slot boundaries");
    }
  }

  public boolean priceRangeFits(PriceInput input, List<Interval> intervals) {
    LocalTime cursor = input.startsAt();
    for (var interval :
        intervals.stream().sorted(Comparator.comparing(Interval::opensAt)).toList()) {
      if (!interval.closesAt().isAfter(cursor)) continue;
      if (interval.opensAt().isAfter(cursor)) return false;
      LocalTime end =
          interval.closesAt().isBefore(input.endsAt()) ? interval.closesAt() : input.endsAt();
      if (Duration.between(interval.opensAt(), cursor).toMinutes() % interval.slotMinutes() != 0
          || Duration.between(interval.opensAt(), end).toMinutes() % interval.slotMinutes() != 0)
        return false;
      cursor = end;
      if (cursor.equals(input.endsAt())) return true;
    }
    return false;
  }
}
