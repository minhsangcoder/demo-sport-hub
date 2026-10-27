package com.sporthub.booking;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import ch.qos.logback.classic.*;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sporthub.booking.service.BookingAvailabilityService;
import java.nio.file.*;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;

@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AvailabilityBatchIntegrationTest extends BookingIntegrationTest {
  @Autowired BookingAvailabilityService availability;

  private ObjectNode preview(UUID id) {
    var node =
        json.createObjectNode()
            .put("courtId", id.toString())
            .put("facilityId", facility.toString())
            .put("timezone", "Asia/Ho_Chi_Minh");
    var slots = node.putArray("slots");
    for (var at : List.of(start, end))
      slots
          .addObject()
          .put("startsAt", at.toString())
          .put("endsAt", at.plusSeconds(3600).toString())
          .put("amount", 150000)
          .put("currency", "VND")
          .put("state", "ELIGIBLE");
    return node;
  }

  private LocalDate fixtures() {
    var day = now.atZone(ZoneId.of("Asia/Ho_Chi_Minh")).toLocalDate().plusDays(1);
    start = day.atTime(15, 0).atZone(ZoneId.of("Asia/Ho_Chi_Minh")).toInstant();
    end = start.plusSeconds(3600);
    ((ObjectNode) quote).put("startsAt", start.toString()).put("endsAt", end.toString());
    service.hold(input(), key(), user, "token", false);
    when(dependencies.preview(any(), any())).thenAnswer(call -> preview(call.getArgument(0)));
    return day;
  }

  @Test
  void availabilityBatchMatchesSingleReadsAndUsesOneGuardAndOneBoundedSnapshot() throws Exception {
    var day = fixtures();
    UUID other = UUID.randomUUID();
    var dates = new LinkedHashMap<UUID, LocalDate>();
    dates.put(court, day);
    dates.put(other, day);
    var logger = (Logger) LoggerFactory.getLogger(org.springframework.jdbc.core.JdbcTemplate.class);
    var previous = logger.getLevel();
    var log = new ListAppender<ILoggingEvent>();
    log.start();
    logger.addAppender(log);
    logger.setLevel(Level.DEBUG);
    BookingAvailabilityService.Batch batch;
    String snapshot;
    clearInvocations(dependencies);
    try {
      batch = availability.discovery(facility, dates);
      var statements =
          log.list.stream()
              .map(ILoggingEvent::getFormattedMessage)
              .filter(line -> line.startsWith("Executing prepared SQL statement ["))
              .toList();
      var snapshots =
          statements.stream()
              .filter(
                  line ->
                      line.contains(
                          "SELECT court_id,starts_at,ends_at,state FROM slot_reservations"))
              .toList();
      assertThat(snapshots).hasSize(1);
      assertThat(
              statements.stream()
                  .filter(
                      line ->
                          line.contains("SELECT EXISTS(SELECT 1 FROM configuration_change_guards")))
          .hasSize(1);
      snapshot =
          snapshots
              .getFirst()
              .substring(
                  "Executing prepared SQL statement [".length(), snapshots.getFirst().length() - 1);
    } finally {
      logger.detachAppender(log);
      logger.setLevel(previous);
      log.stop();
    }
    verify(dependencies, times(2)).preview(any(), any());
    assertThat(batch.error()).isNull();
    assertThat(batch.previews().get(court))
        .isEqualTo(availability.availability(court, day.toString()));
    assertThat(batch.previews().get(other))
        .isEqualTo(availability.availability(other, day.toString()));
    assertThat(batch.previews().get(court).path("slots").get(0).path("reason").asText())
        .isEqualTo("HELD");
    assertThat(batch.previews().get(court).path("slots").get(1).path("state").asText())
        .isEqualTo("AVAILABLE");
    var zone = ZoneId.of("Asia/Ho_Chi_Minh");
    var plan =
        repo.jdbc()
            .queryForObject(
                "EXPLAIN (ANALYZE,BUFFERS,FORMAT JSON) " + snapshot,
                String.class,
                com.sporthub.booking.repository.BookingRepository.uuidArray(dates.keySet()),
                Timestamp.from(day.atStartOfDay(zone).toInstant()),
                Timestamp.from(day.plusDays(1).atStartOfDay(zone).toInstant()),
                Timestamp.from(now));
    var output = Path.of("target/availability-batch-plan.json");
    Files.createDirectories(output.toAbsolutePath().getParent());
    Files.writeString(output, plan);
    configuration.prepare(
        new com.sporthub.common.schedule.ConfigurationGuardProtocol.Command(
            UUID.randomUUID(), facility, "schedule"));
    var prepared = availability.discovery(facility, dates);
    assertThat(prepared.previews().get(other).path("configurationUpdating").asBoolean()).isTrue();
    assertThat(prepared.previews().get(other).path("slots").get(0).path("reason").asText())
        .isEqualTo("CONFIGURATION_UPDATING");
  }

  @Test
  void availabilityBatchRetainsEarlierResultsAndRejectsAChangedFacilityWithoutInventingFreeSlots() {
    var day = fixtures();
    UUID moved = UUID.randomUUID();
    doReturn(preview(moved).put("facilityId", UUID.randomUUID().toString()))
        .when(dependencies)
        .preview(eq(moved), any());
    var dates = new LinkedHashMap<UUID, LocalDate>();
    dates.put(court, day);
    dates.put(moved, day);
    var batch = availability.discovery(facility, dates);
    assertThat(batch.error())
        .isInstanceOf(com.sporthub.common.exception.ResourceNotFoundException.class);
    assertThat(batch.previews()).containsOnlyKeys(court);
    assertThat(batch.previews().get(court).path("slots").get(0).path("reason").asText())
        .isEqualTo("HELD");
  }
}
