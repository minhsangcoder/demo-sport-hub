package com.sporthub.booking.web;
import com.sporthub.common.time.FacilityReportWindow;
import java.time.*;
import java.util.*;
public final class DemoFacilityReportDtos {
 private DemoFacilityReportDtos(){}
 public record Money(String courtCollected,String transferFees,String operatingRevenue,String policyRefunded,String afterRefund,
                     String transferCollected,String sellerPaid,String sellerDue,String refundDue,String technicalRefunded){}
 public record Summary(long bookingCount,long fullyPaidBookingCount,String bookedMinutes,String usedMinutes,long cancelledCount,long expiredCount,Money money){}
 public record Day(LocalDate date,String courtCollected,String transferFees,String policyRefunded){}
 public record Court(UUID courtId,String courtName,long bookingCount,long fullyPaidBookingCount,String bookedMinutes,String usedMinutes,long cancelledCount,long expiredCount,Money money){}
 public record Transaction(String id,String kind,String direction,boolean revenueComponent,boolean cashflow,boolean countsTowardTotals,
                           UUID bookingId,UUID courtId,String courtName,String source,String purpose,String state,String amount,String currency,Instant effectiveAt){}
 public record Page(int page,int size,long total,int totalPages,List<Transaction> items){}
 public record Dashboard(UUID facilityId,FacilityReportWindow window,Instant asOf,Instant obligationCutoff,String mode,String label,
                         Map<String,String> dateBasis,String moneyAvailability,List<String> reasons,Summary summary,List<Day> daily,List<Court> courts,Page transactions){}
}
