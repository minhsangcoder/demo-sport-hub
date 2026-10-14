package com.sporthub.common.time;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import java.time.*;
class ReportWindowTest {
 private final Instant now=Instant.parse("2026-10-13T17:00:00Z");
 @Test void preservesInstantsAndUsesExplicitZone(){var range=ReportWindow.of(null,null,"Asia/Ho_Chi_Minh",now);assertThat(range.to()).isEqualTo(now);assertThat(range.from()).isEqualTo(now.minus(Duration.ofDays(30)));assertThat(range.to().atZone(range.timezone()).toLocalDate()).isEqualTo(LocalDate.of(2026,10,14));}
 @Test void rejectsFractionalDayAboveMaximum(){assertThatThrownBy(()->ReportWindow.of(now,now.plus(Duration.ofDays(366)).plusSeconds(1),"UTC",now)).isInstanceOf(IllegalArgumentException.class);assertThatCode(()->ReportWindow.of(now,now.plus(Duration.ofDays(366)),"UTC",now)).doesNotThrowAnyException();}
 @Test void rejectsInvalidIntervalAndTimezone(){assertThatThrownBy(()->ReportWindow.of(now,now,"UTC",now)).isInstanceOf(IllegalArgumentException.class);assertThatThrownBy(()->ReportWindow.of(now.plusSeconds(1),now,"UTC",now)).isInstanceOf(IllegalArgumentException.class);assertThatThrownBy(()->ReportWindow.of(null,null,"invalid",now)).isInstanceOf(IllegalArgumentException.class);}
 @Test void rejectsPosixOffsetAmbiguity(){assertThatThrownBy(()->ReportWindow.of(null,null,"+07:00",now)).isInstanceOf(IllegalArgumentException.class);}
}
