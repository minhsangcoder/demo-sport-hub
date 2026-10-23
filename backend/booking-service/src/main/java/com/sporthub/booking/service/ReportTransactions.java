package com.sporthub.booking.service;

import static com.sporthub.booking.service.ReportState.*;
import static com.sporthub.booking.web.DemoFacilityReportDtos.*;

import com.sporthub.booking.web.BookingDtos.Booking;
import java.math.*;
import java.time.*;
import java.util.*;
import org.springframework.transaction.*;

final class ReportTransactions {
  static Transaction transaction(
      String id,
      String kind,
      String direction,
      boolean revenue,
      boolean cashflow,
      boolean counts,
      Booking b,
      Map<UUID, String> names,
      String purpose,
      String state,
      BigDecimal amount,
      Instant at) {
    return new Transaction(
        id,
        kind,
        direction,
        revenue,
        cashflow,
        counts,
        b.id(),
        b.courtId(),
        names.getOrDefault(b.courtId(), "Sân đã lưu trữ"),
        b.source(),
        purpose,
        state,
        decimal(amount),
        "VND",
        at);
  }

  static Page page(List<Transaction> transactions, int page, int size) {
    transactions.sort(
        Comparator.comparing(Transaction::effectiveAt).thenComparing(Transaction::id).reversed());
    int offset = (int) Math.min((long) page * size, transactions.size());
    return new Page(
        page,
        size,
        transactions.size(),
        (transactions.size() + size - 1) / size,
        List.copyOf(transactions.subList(offset, Math.min(offset + size, transactions.size()))));
  }
}
