package com.sporthub.booking.service;

import static com.sporthub.booking.service.ReportState.*;
import static com.sporthub.booking.web.DemoFacilityReportDtos.*;

import com.sporthub.booking.web.BookingDtos.Booking;
import com.sporthub.common.time.FacilityReportWindow;
import java.math.*;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.springframework.transaction.*;

final class ReportState {
  final Map<UUID, Booking> byId = new HashMap<>();
  final Map<UUID, String> names;
  final Map<UUID, Counter> counters = new LinkedHashMap<>();
  final Counter summary = new Counter();
  final Map<LocalDate, Counter> daily = new LinkedHashMap<>();
  final List<Transaction> transactions = new ArrayList<>();
  final FacilityReportWindow window;
  final Instant asOf, cutoff;
  final String mode;
  final com.sporthub.booking.web.DemoFacilityReportDtos.PeerReport peer;

  ReportState(
      ReportSnapshotReader.Snapshot s,
      Map<UUID, String> names,
      FacilityReportWindow window,
      Instant asOf,
      Instant cutoff) {
    this.names = new LinkedHashMap<>(names);
    this.window = window;
    this.asOf = asOf;
    this.cutoff = cutoff;
    this.mode = s.mode();
    this.peer = s.peer();
    s.bookings().forEach(b -> byId.put(b.id(), b));
    s.courtIds().forEach(id -> this.names.putIfAbsent(id, "Sân đã lưu trữ"));
    this.names.forEach((id, name) -> counters.put(id, new Counter()));
    for (var day = window.fromDate(); !day.isAfter(window.toDate()); day = day.plusDays(1))
      daily.put(day, new Counter());
  }

  static final class Counter {
    long bookings, full, cancelled, expired;
    BigDecimal booked = BigDecimal.ZERO,
        used = BigDecimal.ZERO,
        r = BigDecimal.ZERO,
        f = BigDecimal.ZERO,
        h = BigDecimal.ZERO,
        p = BigDecimal.ZERO,
        sellerPaid = BigDecimal.ZERO,
        sellerDue = BigDecimal.ZERO,
        refundDue = BigDecimal.ZERO,
        technicalRefunded = BigDecimal.ZERO,
        bankCollected = BigDecimal.ZERO,
        unappliedCollected = BigDecimal.ZERO;

    Money money() {
      return new Money(
          decimal(r),
          decimal(f),
          decimal(r.add(f)),
          decimal(h),
          decimal(r.add(f).subtract(h)),
          decimal(p),
          decimal(sellerPaid),
          decimal(sellerDue),
          decimal(refundDue),
          decimal(technicalRefunded),
          decimal(bankCollected),
          decimal(unappliedCollected));
    }
  }

  static boolean in(Instant at, FacilityReportWindow w) {
    return at != null && !at.isBefore(w.from()) && at.isBefore(w.to());
  }

  static boolean overlaps(Booking b, FacilityReportWindow w) {
    return b.startsAt().isBefore(w.to()) && b.endsAt().isAfter(w.from());
  }

  static Counter day(Map<LocalDate, Counter> days, Instant at, FacilityReportWindow window) {
    return days.get(at.atZone(ZoneId.of(window.timezone())).toLocalDate());
  }

  static BigDecimal minutes(Instant start, Instant end) {
    return end.isAfter(start)
        ? BigDecimal.valueOf(Duration.between(start, end).toMillis())
            .divide(BigDecimal.valueOf(60000), 4, RoundingMode.HALF_UP)
            .stripTrailingZeros()
        : BigDecimal.ZERO;
  }

  static Instant min(Instant... values) {
    return Arrays.stream(values).min(Comparator.naturalOrder()).orElseThrow();
  }

  static Instant max(Instant... values) {
    return Arrays.stream(values).max(Comparator.naturalOrder()).orElseThrow();
  }

  static Instant instant(Object at) {
    return at instanceof Timestamp t
        ? t.toInstant()
        : at instanceof OffsetDateTime t ? t.toInstant() : null;
  }

  static String decimal(BigDecimal amount) {
    return amount.stripTrailingZeros().toPlainString();
  }
}
