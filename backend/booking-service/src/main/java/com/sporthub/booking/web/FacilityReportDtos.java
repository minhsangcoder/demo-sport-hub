package com.sporthub.booking.web;

import com.sporthub.common.time.FacilityReportWindow;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Additive report contract. Amounts are decimal strings, including legacy fractional amounts. */
public final class FacilityReportDtos {
    private FacilityReportDtos() {}

    public enum MoneyClass { REAL, DEMO, UNKNOWN }
    public enum Availability { COMPLETE, PARTIAL, UNAVAILABLE }
    public enum FactKind { COURT_COLLECTION, FACILITY_POLICY_REFUND }
    public enum Direction { IN, OUT }

    public static String decimal(BigDecimal amount) {
        return amount == null ? null : amount.toPlainString();
    }

    public record Coverage(String source, Availability availability, long verifiedCount,
                           long unverifiedCount, Instant capturedAt, List<String> reasons) {}
    public record MoneyTotals(String currency, MoneyClass moneyClass, String gross,
                              String policyRefundExecuted, String afterRefund, String refundDue,
                              String refundInFlight, String refundFundingPending, String reviewAmount,
                              Availability availability) {}
    public record Summary(long bookingCount, long fullyPaidBookingCount, String bookedMinutes,
                          String usedMinutes, long cancelledCount, long expiredCount, List<MoneyTotals> money) {}
    public record Daily(LocalDate date, List<MoneyTotals> money) {}
    public record Court(UUID courtId, String courtName, long bookingCount, long fullyPaidBookingCount,
                        String bookedMinutes, String usedMinutes, long cancelledCount, long expiredCount,
                        List<MoneyTotals> money) {}
    public record RefundPolicy(UUID requestId, long policyVersion, int rateBps, String baseAmount,
                               String targetAmount, String reason, UUID authorizedBy) {}
    public record Transaction(String factId, FactKind kind, Direction direction, Instant effectiveAt,
                              Instant recordedAt, UUID bookingId, String bookingReference, UUID courtId,
                              String courtName, Instant startsAt, Instant endsAt, String source, String purpose,
                              UUID paymentId, String amount, String currency, MoneyClass moneyClass,
                              String state, boolean review, boolean cancelled, RefundPolicy refundPolicy) {}
    public record Transactions(FacilityReportWindow window, Instant asOf, List<Coverage> sourceCoverage,
                               List<Transaction> items, String nextCursor) {}
    public record Dashboard(UUID facilityId, FacilityReportWindow window, Instant asOf,
                            Map<String, String> dateBasis, List<Coverage> sourceCoverage,
                            Availability moneyAvailability, Summary summary, List<Daily> collectionsByDay,
                            List<Court> courtPerformance, Map<String, Long> statusBreakdown,
                            Transactions transactions) {}
}
