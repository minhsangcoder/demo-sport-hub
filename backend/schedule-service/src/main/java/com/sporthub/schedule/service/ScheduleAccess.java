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
public class ScheduleAccess {
  private final ScheduleRepository repo;
  private final FacilityClient facilities;
  private final ObjectMapper json;
  private final String serviceSecret;
  private final String identityUrl;

  public ScheduleAccess(
      ScheduleRepository repo,
      FacilityClient facilities,
      ObjectMapper json,
      @org.springframework.beans.factory.annotation.Value("${SERVICE_CALL_SECRET:}")
          String serviceSecret,
      @org.springframework.beans.factory.annotation.Value(
              "${IDENTITY_SERVICE_URL:http://localhost:8081}")
          String identityUrl) {
    this.repo = repo;
    this.facilities = facilities;
    this.json = json;
    this.serviceSecret = serviceSecret;
    this.identityUrl = identityUrl;
  }

  public void reader(UUID facility, Caller caller, String token) {
    if (staffReader(facility, caller)) return;
    if (caller.hasRole("OWNER")
        || caller
            .facilityBindings()
            .getOrDefault(facility, Set.of())
            .contains("APPLICATION_READ")) {
      facilities.requireReader(facility, token);
      return;
    }
    throw new ForbiddenException("SCHEDULE_READ requires an active Staff binding at this facility");
  }

  public boolean staffReader(UUID facility, Caller caller) {
    return caller.hasRole("STAFF")
        && caller.facilityBindings().getOrDefault(facility, Set.of()).stream()
            .anyMatch(Set.of("SCHEDULE_READ", "SCHEDULE_MANAGE")::contains);
  }

  public void owner(UUID facility, Caller caller, String token) {
    if (!caller.hasRole("OWNER")
        && !caller.facilityBindings().getOrDefault(facility, Set.of()).contains("APPLICATION_EDIT"))
      throw new ForbiddenException("Owner or editable application workspace required");
    repo.lock(facility);
    var pending =
        repo.jdbc()
            .queryForList(
                "SELECT application_id FROM application_configuration_freeze WHERE facility_id=?"
                    + " AND frozen",
                UUID.class,
                facility);
    if (!pending.isEmpty()
        && !facilities.applicationCommitted(pending.getFirst(), serviceSecret, identityUrl))
      throw new ConflictException("Application schedule is frozen during review");
    facilities.requireOwner(facility, token);
  }

  public void scheduleManager(UUID facility, Caller caller, String token) {
    if (caller.hasRole("STAFF")
        && caller.facilityBindings().getOrDefault(facility, Set.of()).contains("SCHEDULE_MANAGE")) {
      repo.lock(facility);
      var pending =
          repo.jdbc()
              .queryForList(
                  "SELECT application_id FROM application_configuration_freeze WHERE facility_id=?"
                      + " AND frozen",
                  UUID.class,
                  facility);
      if (!pending.isEmpty()
          && !facilities.applicationCommitted(pending.getFirst(), serviceSecret, identityUrl))
        throw new ConflictException("Application schedule is frozen during review");
      facilities.requireScheduleManager(facility, token);
      return;
    }
    owner(facility, caller, token);
  }

  public void courtScope(UUID facility, UUID court, String token) {
    if (court != null) checkFacility(facility, facilities.context(court, token));
  }

  public void checkFacility(UUID facility, FacilityClient.Context context) {
    if (!facility.equals(context.facilityId()))
      throw new ForbiddenException("Court is outside the facility");
  }

  public void audit(UUID facility, Caller caller, String action, Object snapshot) {
    try {
      repo.jdbc()
          .update(
              "INSERT INTO schedule_audit(id,facility_id,actor_id,action,snapshot)"
                  + " VALUES(?,?,?,?,?::jsonb)",
              UUID.randomUUID(),
              facility,
              caller.id(),
              action,
              json.writeValueAsString(snapshot));
    } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
      throw new IllegalStateException("Could not record configuration audit", ex);
    }
  }
}
