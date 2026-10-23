package com.sporthub.booking.service;

import static com.sporthub.booking.service.ReportState.*;
import static com.sporthub.booking.web.DemoFacilityReportDtos.*;

import com.sporthub.common.exception.ForbiddenException;
import com.sporthub.common.security.RemoteIdentity.Caller;
import com.sporthub.common.time.FacilityReportWindow;
import java.math.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;

@Service
public class FacilityDemoReportingService {
  private final BookingDependencies dependencies;
  private final ReportSnapshotReader reader;
  private final ReportPaymentModes paymentModes;
  private final Clock clock;

  public FacilityDemoReportingService(
      BookingDependencies dependencies,
      ReportSnapshotReader reader,
      ReportPaymentModes paymentModes,
      Clock clock) {
    this.dependencies = dependencies;
    this.reader = reader;
    this.paymentModes = paymentModes;
    this.clock = clock;
  }

  public Dashboard dashboard(
      UUID facility, LocalDate from, LocalDate to, int page, int size, Caller actor, String token) {

    actor.requireRole("OWNER");
    if (page < 0 || size < 1 || size > 100)
      throw new IllegalArgumentException("page must be nonnegative and size must be 1..100");
    var profile = dependencies.ownerFacility(facility, actor, token);
    if (profile == null
        || !facility.toString().equals(profile.path("id").asText())
        || !actor.id().toString().equals(profile.path("ownerId").asText()))
      throw new ForbiddenException("Facility is outside your ownership");
    var window = FacilityReportWindow.of(from, to, profile.path("timezone").asText(), clock);
    Instant asOf = clock.instant(), cutoff = asOf.isBefore(window.to()) ? asOf : window.to();
    var names = new LinkedHashMap<UUID, String>();
    var inventory = dependencies.ownerCourts(facility, token);
    if (inventory != null && inventory.isArray())
      for (var c : inventory)
        names.put(UUID.fromString(c.path("id").asText()), c.path("name").asText());

    var snapshot = reader.read(facility, window, cutoff);
    var totals = new ReportState(snapshot, names, window, asOf, cutoff);
    // Remote Payment enrichment runs only after the repeatable-read snapshot has committed.
    var modes = paymentModes.assess(snapshot, totals);
    ReportUsage.add(snapshot, totals);
    ReportMoney.add(snapshot, totals, modes);
    return ReportAssembler.assemble(facility, totals, modes, page, size);
  }
}
