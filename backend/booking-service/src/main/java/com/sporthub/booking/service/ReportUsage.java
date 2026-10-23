package com.sporthub.booking.service;

import static com.sporthub.booking.service.ReportState.*;
import static com.sporthub.booking.web.DemoFacilityReportDtos.*;

import java.math.*;
import java.time.*;
import java.util.*;
import org.springframework.transaction.*;

final class ReportUsage {
  static void add(ReportSnapshotReader.Snapshot s, ReportState t) {
    var byId = t.byId;
    var names = t.names;
    var counters = t.counters;
    var summary = t.summary;
    var daily = t.daily;
    var transactions = t.transactions;
    var window = t.window;
    var asOf = t.asOf;
    var cutoff = t.cutoff;
    for (var b : s.bookings()) {
      var c = counters.computeIfAbsent(b.courtId(), id -> new Counter());
      names.putIfAbsent(b.courtId(), "Sân đã lưu trữ");
      var assessment = s.applied().assessments().get(b.id());
      boolean fullyPaid = assessment != null && assessment.fullyPaid();
      if (in(b.startsAt(), window)) {
        summary.bookings++;
        c.bookings++;
        if (fullyPaid) {
          summary.full++;
          c.full++;
        }
        if (b.status().equals("CANCELLED")) {
          summary.cancelled++;
          c.cancelled++;
        }
        if (b.status().equals("EXPIRED")) {
          summary.expired++;
          c.expired++;
        }
      }
      if (fullyPaid && Set.of("CONFIRMED", "CHECKED_IN", "COMPLETED").contains(b.status())) {
        var booked = minutes(max(b.startsAt(), window.from()), min(b.endsAt(), window.to()));
        summary.booked = summary.booked.add(booked);
        c.booked = c.booked.add(booked);
        if (b.checkedInAt() != null && Set.of("CHECKED_IN", "COMPLETED").contains(b.status())) {
          var used =
              minutes(
                  max(b.startsAt(), b.checkedInAt(), window.from()),
                  min(b.endsAt(), window.to(), asOf));
          summary.used = summary.used.add(used);
          c.used = c.used.add(used);
        }
      }
    }
  }
}
