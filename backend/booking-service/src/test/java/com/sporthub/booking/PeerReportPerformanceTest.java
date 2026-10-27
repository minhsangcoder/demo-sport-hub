package com.sporthub.booking;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.*;
import com.sporthub.booking.service.*;
import java.lang.management.ManagementFactory;
import java.nio.file.*;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;

/** Native report/PG measurements with controlled money-source adapters, never a user's database. */
@AutoConfigureMockMvc
@EnabledIfSystemProperty(named = "sporthub.peer.performance", matches = "true")
class PeerReportPerformanceTest extends RealBookingIntegrationTest {
  @Autowired org.springframework.test.web.servlet.MockMvc http;
  @Autowired BookingBankProfiles banks;
  final List<UUID> courts =
      java.util.stream.IntStream.range(0, 12)
          .mapToObj(
              i ->
                  UUID.nameUUIDFromBytes(
                      ("peer-perf-court-" + i).getBytes(java.nio.charset.StandardCharsets.UTF_8)))
          .toList();

  void seed(int from, int to) {
    var base = dependencies.quote(input()).deepCopy();
    for (int i = from; i < to; i++) {
      court = courts.get(i % 12);
      start = now.plusSeconds((2L + i / 48) * 86400 + (i / 12 % 4) * 3600L);
      end = start.plusSeconds(3600);
      var price =
          ((com.fasterxml.jackson.databind.node.ObjectNode) base.deepCopy())
              .put("courtId", court.toString())
              .put("startsAt", start.toString())
              .put("endsAt", end.toString());
      when(dependencies.quote(any())).thenReturn(price);
      var booking = pending();
      var payment = UUID.randomUUID();
      consumer.apply(event(booking, payment, customer.id(), "BOOKING", null, booking.amount()));
      credit(booking, payment, customer.id(), booking.amount().intValueExact());
      if ((i + 1) % 200 == 0) System.out.println("PERF_NATIVE_ROWS=" + (i + 1));
    }
  }

  void sources(boolean available) {
    doReturn(json.createObjectNode())
        .when(payments)
        .credits(eq(facility), eq(owner), any(), any(), any());
    doReturn(new PaymentEvidenceClient.Result(Map.of(), Map.of(), List.of(), List.of(), 0))
        .when(reportEvidence)
        .read(anyList());
    reportSources();
    when(dependencies.ownerCourts(eq(facility), any()))
        .thenReturn(
            json.valueToTree(
                java.util.stream.IntStream.range(0, 12)
                    .mapToObj(i -> Map.of("id", courts.get(i), "name", "Sân " + i))
                    .toList()));
    when(identity.current(any())).thenReturn(ownerActor());
    if (available) {
      var credits =
          reportCredits().stream()
              .sorted(Comparator.comparing(c -> c.creditId().toString()))
              .toList();
      var byPayment =
          credits.stream().collect(java.util.stream.Collectors.groupingBy(c -> c.paymentId()));
      var facts = new HashMap<UUID, com.sporthub.common.money.PaymentProofFacts.Fact>();
      for (var row : repo.jdbc().queryForList("SELECT * FROM booking_real_collections")) {
        var id = (UUID) row.get("payment_id");
        var entries = byPayment.get(id);
        var amount = row.get("amount").toString();
        var total =
            entries.stream()
                .map(c -> new java.math.BigDecimal(c.amount()))
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
        var proof =
            new com.sporthub.common.money.PaymentProofFacts.RealEvidence(
                facility,
                owner,
                channel,
                revision,
                (UUID) row.get("proof_id"),
                row.get("proof_hash").toString(),
                amount,
                total.toPlainString(),
                "BANK_VERIFIED",
                entries);
        facts.put(
            id,
            new com.sporthub.common.money.PaymentProofFacts.Fact(
                id,
                (UUID) row.get("booking_id"),
                (UUID) row.get("payer_id"),
                null,
                null,
                "BOOKING",
                "SUCCESS",
                amount,
                "VND",
                ((Timestamp) row.get("paid_at")).toInstant(),
                "PAYOS",
                "REAL",
                true,
                true,
                false,
                row.get("fingerprint").toString(),
                null,
                "PAYOS_BANK_COLLECTION_V2",
                List.of(),
                proof));
      }
      doAnswer(
              call -> {
                List<com.sporthub.common.money.PaymentProofFacts.Binding> requested =
                    call.getArgument(0);
                var selected =
                    new HashMap<UUID, com.sporthub.common.money.PaymentProofFacts.Fact>();
                requested.forEach(
                    binding -> selected.put(binding.paymentId(), facts.get(binding.paymentId())));
                return new PaymentEvidenceClient.Result(
                    selected, Map.of(), List.of(now), List.of(), 1);
              })
          .when(reportEvidence)
          .read(anyList());
      doAnswer(
              call -> {
                Instant from = call.getArgument(2), to = call.getArgument(3);
                UUID after = call.getArgument(4);
                var matching =
                    credits.stream()
                        .filter(
                            c ->
                                !c.bankTime().isBefore(from)
                                    && c.bankTime().isBefore(to)
                                    && (after == null
                                        || c.creditId().toString().compareTo(after.toString()) > 0))
                        .toList();
                var page = matching.stream().limit(500).toList();
                var response =
                    json.createObjectNode()
                        .put("facilityId", facility.toString())
                        .put("receiverOwnerId", owner.toString())
                        .put("more", matching.size() > 500);
                response.set("credits", json.valueToTree(page));
                if (matching.size() > 500)
                  response.put("afterId", page.getLast().creditId().toString());
                return response;
              })
          .when(payments)
          .credits(eq(facility), eq(owner), any(), any(), any());
    } else {
      doThrow(
              new org.springframework.web.server.ResponseStatusException(
                  org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                  "Synthetic source unavailable"))
          .when(payments)
          .credits(eq(facility), eq(owner), any(), any(), any());
      doReturn(
              new PaymentEvidenceClient.Result(
                  Map.of(), Map.of(), List.of(), List.of("PAYMENT_SOURCE_UNAVAILABLE"), 0))
          .when(reportEvidence)
          .read(anyList());
    }
  }

  JsonNode request() {
    try {
      var response =
          http.perform(
                  org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                          "/api/v1/bookings/reports/facility/"
                              + facility
                              + "/dashboard?fromDate=2026-10-23&toDate=2026-10-23&mode=REAL&page=0&size=25")
                      .header("Authorization", "token"))
              .andReturn()
              .getResponse();
      assertThat(response.getStatus()).isEqualTo(200);
      return json.readTree(response.getContentAsString(java.nio.charset.StandardCharsets.UTF_8))
          .path("data");
    } catch (Exception failure) {
      throw new IllegalStateException(failure);
    }
  }

  Map<String, Object> timed(Supplier<Object> action) {
    var logger =
        (ch.qos.logback.classic.Logger)
            org.slf4j.LoggerFactory.getLogger(org.springframework.jdbc.core.JdbcTemplate.class);
    var previous = logger.getLevel();
    boolean additive = logger.isAdditive();
    var counter = new SqlCounter();
    counter.start();
    logger.addAppender(counter);
    logger.setAdditive(false);
    logger.setLevel(ch.qos.logback.classic.Level.DEBUG);
    var samples = new ArrayList<Double>();
    Object payload = null;
    var queryCounts = new ArrayList<Integer>();
    var os =
        (com.sun.management.OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
    long cpu;
    try {
      for (int i = 0; i < 10; i++) action.get();
      cpu = os.getProcessCpuTime();
      for (int i = 0; i < 30; i++) {
        counter.queries = 0;
        long start = System.nanoTime();
        payload = action.get();
        samples.add((System.nanoTime() - start) / 1_000_000.0);
        queryCounts.add(counter.queries);
      }
    } finally {
      logger.detachAppender(counter);
      counter.stop();
      logger.setLevel(previous);
      logger.setAdditive(additive);
    }
    var ordered = samples.stream().sorted().toList();
    return Map.of(
        "p50Ms",
        ordered.get(14),
        "p95Ms",
        ordered.get(28),
        "samplesMs",
        samples,
        "payload",
        payload,
        "cpuMs",
        (os.getProcessCpuTime() - cpu) / 1_000_000.0,
        "heapUsedBytes",
        Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory(),
        "hostFreeMemoryBytes",
        os.getFreeMemorySize(),
        "queryCountPerSample",
        queryCounts);
  }

  static class SqlCounter
      extends ch.qos.logback.core.AppenderBase<ch.qos.logback.classic.spi.ILoggingEvent> {
    int queries;

    @Override
    protected void append(ch.qos.logback.classic.spi.ILoggingEvent event) {
      if (event.getFormattedMessage().startsWith("Executing prepared SQL statement")) queries++;
    }
  }

  @Test
  void benchmarkNativeReportBeforeOrAfterWithEqualFactsAndPartialSourceControl() throws Exception {
    assertThat(System.getenv("SPORTHUB_BOOKING_TEST_DB_URL")).isNull();
    var output = new LinkedHashMap<String, Object>();
    output.put(
        "conditions",
        Map.of(
            "java",
            System.getProperty("java.version"),
            "warmups",
            10,
            "samples",
            30,
            "timezone",
            "Asia/Ho_Chi_Minh",
            "clock",
            now.plusSeconds(1).toString(),
            "moneyMode",
            "REAL",
            "transport",
            "MockMvc HTTP + Spring + isolated PostgreSQL; controlled source adapters, not full"
                + " service graph",
            "sourceOutage",
            "Same local immutable collections/credits; remote money source unavailable"));
    var sourceHashes = new TreeMap<String, String>();
    for (var name : List.of("RealReportSnapshotReader.java", "RealReportCash.java"))
      sourceHashes.put(
          name,
          com.sporthub.common.security.Signatures.sha256(
              Files.readString(Path.of("src/main/java/com/sporthub/booking/service/" + name))));
    output.put("sources", sourceHashes);
    for (int count : List.of(32, 1200)) {
      when(clock.instant()).thenReturn(now);
      seed(count == 32 ? 0 : 32, count);
      when(clock.instant()).thenReturn(now.plusSeconds(1));
      var data = new LinkedHashMap<String, Object>();
      data.put("bookings", count);
      sources(true);
      var healthy = timed(() -> request());
      assertThat(
              ((JsonNode) healthy.get("payload"))
                  .path("summary")
                  .path("money")
                  .path("courtCollected")
                  .asText())
          .isEqualTo(Integer.toString(count * 100000));
      data.put("healthy", healthy);
      sources(false);
      var partial = timed(() -> request());
      assertThat(((JsonNode) partial.get("payload")).path("moneyAvailability").asText())
          .isEqualTo("PARTIAL");
      data.put("partial", partial);
      data.put(
          "bank",
          timed(
              () -> {
                var status = banks.status(customer.id(), false);
                return Map.of(
                    "initialized",
                    status.initialized(),
                    "canRegisterFirst",
                    status.canRegisterFirst(),
                    "canChange",
                    status.canChange(),
                    "resourceCount",
                    status.responsibilities().size());
              }));
      data.put(
          "collectionPlan",
          repo.jdbc()
              .queryForList(
                  "EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON) SELECT * FROM booking_real_collections"
                      + " WHERE booking_id IN(SELECT booking_id FROM booking_money_cohorts WHERE"
                      + " facility_id=?)",
                  facility));
      data.put(
          "bankPlan",
          repo.jdbc()
              .queryForList(
                  "EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON) SELECT id FROM bookings WHERE"
                      + " current_holder_id=? AND ends_at>? AND status IN('CONFIRMED','CHECKED_IN')"
                      + " LIMIT 100",
                  customer.id(),
                  Timestamp.from(now)));
      data.put(
          "rows",
          repo.jdbc()
              .queryForMap(
                  "SELECT (SELECT count(*) FROM booking_real_collections) collections,(SELECT"
                      + " count(*) FROM booking_real_bank_credits) credits"));
      output.put(count == 32 ? "small" : "large", data);
    }
    var destination =
        Path.of(
            System.getProperty(
                "sporthub.peer.performance.output", "target/peer-report-performance.json"));
    Files.createDirectories(destination.toAbsolutePath().getParent());
    Files.writeString(
        destination, json.writerWithDefaultPrettyPrinter().writeValueAsString(output));
    System.out.println("PEER_PERFORMANCE_RESULT=" + destination.toAbsolutePath());
  }
}
