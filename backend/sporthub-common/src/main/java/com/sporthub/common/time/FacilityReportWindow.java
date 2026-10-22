package com.sporthub.common.time;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/** Calendar dates are inclusive in the facility zone; database bounds are [from,to). */
public record FacilityReportWindow(LocalDate fromDate, LocalDate toDate, Instant from,
                                   Instant to, String timezone, String interval) {
    public static FacilityReportWindow of(LocalDate fromDate, LocalDate toDate,
                                          String facilityTimezone, Clock clock) {
        if (facilityTimezone == null || !ZoneId.getAvailableZoneIds().contains(facilityTimezone)) {
            throw new IllegalArgumentException("A valid facility IANA timezone is required");
        }
        if ((fromDate == null) != (toDate == null)) {
            throw new IllegalArgumentException("Supply both fromDate and toDate");
        }
        var zone = ZoneId.of(facilityTimezone);
        if (fromDate == null) {
            toDate = clock.instant().atZone(zone).toLocalDate();
            fromDate = toDate.minusDays(6);
        }
        if (fromDate.isAfter(toDate)) {
            throw new IllegalArgumentException("fromDate must not be after toDate");
        }
        if(java.time.temporal.ChronoUnit.DAYS.between(fromDate,toDate)>=366){
            throw new IllegalArgumentException("Report range must contain no more than 366 calendar dates");
        }
        return new FacilityReportWindow(fromDate,toDate,fromDate.atStartOfDay(zone).toInstant(),
                toDate.plusDays(1).atStartOfDay(zone).toInstant(),facilityTimezone,"[from,to)");
    }
}
