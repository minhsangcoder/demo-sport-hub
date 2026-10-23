package com.sporthub.booking.service;

import static com.sporthub.booking.service.ReportState.*;
import static com.sporthub.booking.web.DemoFacilityReportDtos.*;

import java.math.*;
import java.time.*;
import java.util.*;
import org.springframework.transaction.*;

final class ReportAssembler {
  static Dashboard assemble(
      UUID facility, ReportState t, ReportPaymentModes.Modes assessed, int page, int size) {
    var byId = t.byId;
    var names = t.names;
    var counters = t.counters;
    var summary = t.summary;
    var daily = t.daily;
    var transactions = t.transactions;
    var window = t.window;
    var asOf = t.asOf;
    var cutoff = t.cutoff;
    var reasons = assessed.reasons();
    var pages = ReportTransactions.page(transactions, page, size);
    var courtRows = new ArrayList<Court>();
    counters.forEach(
        (id, c) ->
            courtRows.add(
                new Court(
                    id,
                    names.getOrDefault(id, "Sân đã lưu trữ"),
                    c.bookings,
                    c.full,
                    decimal(c.booked),
                    decimal(c.used),
                    c.cancelled,
                    c.expired,
                    c.money())));
    courtRows.sort(Comparator.comparing(Court::courtName).thenComparing(Court::courtId));
    var days = new ArrayList<Day>();
    daily.forEach((date, c) -> days.add(new Day(date, decimal(c.r), decimal(c.f), decimal(c.h))));
    String availability =
        reasons.isEmpty()
            ? "COMPLETE"
            : transactions.stream().anyMatch(Transaction::countsTowardTotals)
                ? "PARTIAL"
                : "UNAVAILABLE";
    return new Dashboard(
        facility,
        window,
        asOf,
        cutoff,
        "DEMO",
        "Dữ liệu DEMO — không phát sinh giao dịch tiền thật",
        Map.of(
            "courtCollected",
            "paidAt",
            "transferCollected",
            "paidAt",
            "transferFees",
            "handoffAt",
            "sellerPaid",
            "demoPaidAt",
            "policyRefunded",
            "demoRefundedAt",
            "bookings",
            "startsAt; current status at asOf",
            "obligations",
            "created before cutoff minus successful executions before cutoff"),
        availability,
        List.copyOf(reasons),
        new Summary(
            summary.bookings,
            summary.full,
            decimal(summary.booked),
            decimal(summary.used),
            summary.cancelled,
            summary.expired,
            summary.money()),
        days,
        courtRows,
        pages);
  }
}
