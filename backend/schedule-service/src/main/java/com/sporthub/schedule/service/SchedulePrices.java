package com.sporthub.schedule.service;

import static com.sporthub.schedule.web.ScheduleDtos.*;

import com.fasterxml.jackson.databind.ObjectMapper;
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
public class SchedulePrices {
  private final ScheduleRepository repo;
  private final FacilityClient facilities;
  private final ObjectMapper json;
  private final com.sporthub.common.schedule.ConfigurationChanges configuration;
  private final ScheduleAccess access;
  private final SchedulePriceValidation validation;
  private final ScheduleReads reads;
  private final PricingEngine pricing = new PricingEngine();

  public SchedulePrices(
      ScheduleRepository repo,
      FacilityClient facilities,
      ObjectMapper json,
      com.sporthub.common.schedule.ConfigurationChanges configuration,
      ScheduleAccess access,
      SchedulePriceValidation validation,
      ScheduleReads reads) {
    this.repo = repo;
    this.facilities = facilities;
    this.json = json;
    this.configuration = configuration;
    this.access = access;
    this.validation = validation;
    this.reads = reads;
  }

  @Transactional
  public PriceRule addPrice(UUID facility, PriceInput input, Caller caller, String token) {
    access.owner(facility, caller, token);
    configuration.begin(facility);
    validation.validatePrice(facility, input, token);
    UUID id = UUID.randomUUID();
    var candidate = price(facility, id, input, 0);
    validation.ensurePriceUnambiguous(facility, candidate, null, token);
    repo.jdbc()
        .update(
            "INSERT INTO"
                + " pricing_rules(id,facility_id,court_id,sport_category_id,day_of_week,specific_date,start_time,end_time,price_per_slot,priority,label,effective_from,effective_to,currency,created_by,court_type_id)"
                + " VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
            id,
            facility,
            input.courtId(),
            input.sportCategoryId(),
            input.dayOfWeek(),
            ScheduleValues.date(input.specificDate()),
            ScheduleValues.time(input.startsAt()),
            ScheduleValues.time(input.endsAt()),
            candidate.pricePerSlot(),
            input.priority(),
            input.label().trim(),
            ScheduleValues.date(input.effectiveFrom()),
            ScheduleValues.date(input.effectiveTo()),
            input.currency(),
            caller.id(),
            input.courtTypeId());
    recordPriceVersion(candidate, caller.id());
    access.audit(facility, caller, "PRICE_CREATED", candidate);
    return candidate;
  }

  @Transactional
  public PriceRule updatePrice(
      UUID facility, UUID id, PriceInput input, long expectedVersion, Caller caller, String token) {
    access.owner(facility, caller, token);
    var previous = findPrice(facility, id);
    if (previous.version() != expectedVersion)
      throw new ConflictException("Price version changed; reload the rule before editing");
    if (!previous.active())
      throw new ConflictException("Create a new rule instead of editing a disabled price");
    if (pricing.legacy(previous) && input.courtTypeId() == null)
      throw new IllegalArgumentException(
          "Map the legacy court type explicitly before editing this rule");
    if (!input.courtTypeProvided())
      input =
          new PriceInput(
              input.courtId(),
              input.sportCategoryId(),
              input.dayOfWeek(),
              input.specificDate(),
              input.startsAt(),
              input.endsAt(),
              input.pricePerSlot(),
              input.priority(),
              input.label(),
              input.effectiveFrom(),
              input.effectiveTo(),
              input.currency(),
              previous.courtTypeId());
    configuration.begin(facility);
    validation.validatePrice(facility, input, token);
    var candidate = price(facility, id, input, previous.version() + 1);
    validation.ensurePriceUnambiguous(facility, candidate, id, token);
    recordPriceVersion(previous, caller.id());
    int changed =
        repo.jdbc()
            .update(
                "UPDATE pricing_rules SET"
                    + " court_id=?,sport_category_id=?,court_type_id=?,court_type=NULL,day_of_week=?,specific_date=?,start_time=?,end_time=?,price_per_slot=?,priority=?,label=?,effective_from=?,effective_to=?,currency=?,version=version+1,updated_by=?,updated_at=NOW()"
                    + " WHERE id=? AND facility_id=? AND version=?",
                input.courtId(),
                input.sportCategoryId(),
                input.courtTypeId(),
                input.dayOfWeek(),
                ScheduleValues.date(input.specificDate()),
                ScheduleValues.time(input.startsAt()),
                ScheduleValues.time(input.endsAt()),
                candidate.pricePerSlot(),
                input.priority(),
                input.label().trim(),
                ScheduleValues.date(input.effectiveFrom()),
                ScheduleValues.date(input.effectiveTo()),
                input.currency(),
                caller.id(),
                id,
                facility,
                expectedVersion);
    if (changed != 1) throw new ConflictException("Price version changed during update");
    recordPriceVersion(candidate, caller.id());
    access.audit(facility, caller, "PRICE_UPDATED", Map.of("before", previous, "after", candidate));
    return candidate;
  }

  @Transactional
  public void disablePrice(UUID facility, UUID id, Caller caller, String token) {
    access.owner(facility, caller, token);
    var rule = findPrice(facility, id);
    if (!rule.active()) return;
    configuration.begin(facility);
    recordPriceVersion(rule, caller.id());
    repo.jdbc()
        .update(
            "UPDATE pricing_rules SET"
                + " is_active=FALSE,version=version+1,updated_by=?,updated_at=NOW() WHERE id=? AND"
                + " facility_id=?",
            caller.id(),
            id,
            facility);
    recordPriceVersion(findPrice(facility, id), caller.id());
    access.audit(facility, caller, "PRICE_DISABLED", rule);
  }

  public List<PriceRevision> priceHistory(UUID facility, UUID id, Caller caller, String token) {
    access.reader(facility, caller, token);
    findPrice(facility, id);
    return repo.jdbc()
        .query(
            "SELECT * FROM pricing_rule_versions WHERE rule_id=? ORDER BY version",
            (r, n) ->
                new PriceRevision(
                    id,
                    r.getLong("version"),
                    readPriceSnapshot(r.getString("snapshot")),
                    r.getObject("actor_id", UUID.class),
                    r.getTimestamp("recorded_at").toInstant()),
            id);
  }

  public Preview pricingDraft(UUID facility, PricingDraft draft, Caller caller, String token) {
    access.reader(facility, caller, token);
    String readToken = access.staffReader(facility, caller) ? null : token;
    var context = facilities.context(draft.courtId(), readToken);
    access.checkFacility(facility, context);
    PriceRule previous =
        draft.replacesRuleId() == null ? null : findPrice(facility, draft.replacesRuleId());
    var input = draft.rule();
    if (previous != null && !input.courtTypeProvided())
      input =
          new PriceInput(
              input.courtId(),
              input.sportCategoryId(),
              input.dayOfWeek(),
              input.specificDate(),
              input.startsAt(),
              input.endsAt(),
              input.pricePerSlot(),
              input.priority(),
              input.label(),
              input.effectiveFrom(),
              input.effectiveTo(),
              input.currency(),
              previous.courtTypeId());
    validation.validatePrice(facility, input, readToken);
    var candidate =
        price(
            facility,
            previous == null ? new UUID(0, 0) : previous.id(),
            input,
            previous == null ? 0 : previous.version() + 1);
    var rules = new ArrayList<>(repo.rules(facility));
    if (previous != null) rules.removeIf(r -> r.id().equals(previous.id()));
    rules.add(candidate);
    return reads.preview(context, draft.date(), rules);
  }

  public PriceRule findPrice(UUID facility, UUID id) {
    return repo.rules(facility).stream()
        .filter(r -> r.id().equals(id))
        .findFirst()
        .orElseThrow(() -> new ResourceNotFoundException("Price rule not found"));
  }

  public PriceRule price(UUID facility, UUID id, PriceInput i, long version) {
    return new PriceRule(
        id,
        facility,
        i.courtId(),
        i.sportCategoryId(),
        i.dayOfWeek(),
        i.specificDate(),
        i.startsAt(),
        i.endsAt(),
        com.sporthub.common.money.Money.positive(i.pricePerSlot(), i.currency()),
        i.priority(),
        i.label().trim(),
        i.effectiveFrom(),
        i.effectiveTo(),
        i.currency(),
        true,
        version,
        i.courtTypeId(),
        null);
  }

  public com.fasterxml.jackson.databind.JsonNode readPriceSnapshot(String value) {
    try {
      return json.readTree(value);
    } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
      throw new IllegalStateException("Invalid price revision", ex);
    }
  }

  public void recordPriceVersion(PriceRule rule, UUID actor) {
    try {
      repo.jdbc()
          .update(
              "INSERT INTO pricing_rule_versions(rule_id,version,snapshot,actor_id)"
                  + " VALUES(?,?,?::jsonb,?) ON CONFLICT(rule_id,version) DO NOTHING",
              rule.id(),
              rule.version(),
              json.writeValueAsString(rule),
              actor);
    } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
      throw new IllegalStateException("Could not record price revision", ex);
    }
  }
}
