package com.sporthub.common.time;

import org.junit.jupiter.api.Test;
import java.time.*;
import static org.assertj.core.api.Assertions.*;

class FacilityReportWindowTest {
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-20T18:00:00Z"), ZoneOffset.UTC);

    @Test void defaultsToSevenFacilityDatesIncludingToday() {
        var window = FacilityReportWindow.of(null, null, "Asia/Ho_Chi_Minh", clock);
        assertThat(window.fromDate()).isEqualTo(LocalDate.of(2026, 10, 15));
        assertThat(window.toDate()).isEqualTo(LocalDate.of(2026, 10, 21));
        assertThat(window.from()).isEqualTo(Instant.parse("2026-10-14T17:00:00Z"));
        assertThat(window.to()).isEqualTo(Instant.parse("2026-10-21T17:00:00Z"));
    }

    @Test void includesTheEntireLastDayRegardlessOfTheClockZone() {
        var from = LocalDate.of(2026, 10, 14);
        var to = LocalDate.of(2026, 10, 21);
        var window = FacilityReportWindow.of(from, to, "Asia/Ho_Chi_Minh", clock);
        assertThat(window.from()).isEqualTo(Instant.parse("2026-10-13T17:00:00Z"));
        assertThat(window.to()).isEqualTo(Instant.parse("2026-10-21T17:00:00Z"));
        assertThat(FacilityReportWindow.of(from, to, "Asia/Ho_Chi_Minh", clock.withZone(ZoneId.of("America/Los_Angeles"))))
                .isEqualTo(window);
    }

    @Test void respectsShortAndLongDstDays() {
        var spring = LocalDate.of(2026, 3, 8);
        var fall = LocalDate.of(2026, 11, 1);
        var shortDay = FacilityReportWindow.of(spring, spring, "America/New_York", clock);
        var longDay = FacilityReportWindow.of(fall, fall, "America/New_York", clock);
        assertThat(Duration.between(shortDay.from(), shortDay.to())).isEqualTo(Duration.ofHours(23));
        assertThat(Duration.between(longDay.from(), longDay.to())).isEqualTo(Duration.ofHours(25));
    }

    @Test void rejectsMissingReversedOrExcessiveInputAndOffsetZones() {
        var day = LocalDate.of(2026, 1, 1);
        assertThatThrownBy(() -> FacilityReportWindow.of(day, null, "UTC", clock)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> FacilityReportWindow.of(null, day, "UTC", clock)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> FacilityReportWindow.of(day.plusDays(1), day, "UTC", clock)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> FacilityReportWindow.of(day, day.plusDays(366), "UTC", clock)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> FacilityReportWindow.of(day, day, "+07:00", clock)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> FacilityReportWindow.of(day, day, null, clock)).isInstanceOf(IllegalArgumentException.class);
        assertThatCode(() -> FacilityReportWindow.of(day, day.plusDays(365), "UTC", clock)).doesNotThrowAnyException();
    }
    @Test void accepts366CalendarDatesEvenWhenDstMakesThemLongerThan366Days(){
        var from=LocalDate.of(2026,3,9);
        var window=FacilityReportWindow.of(from,from.plusDays(365),"America/New_York",clock);
        assertThat(window.toDate()).isEqualTo(from.plusDays(365));
        assertThat(Duration.between(window.from(),window.to())).isEqualTo(Duration.ofDays(366).plusHours(1));
        assertThatThrownBy(()->FacilityReportWindow.of(from,from.plusDays(366),"America/New_York",clock)).isInstanceOf(IllegalArgumentException.class);
    }
}
