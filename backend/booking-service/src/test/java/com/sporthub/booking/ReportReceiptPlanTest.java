package com.sporthub.booking;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.util.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

/** Query-plan fixture only; these SQL rows do not claim payment verification. */
class ReportReceiptPlanTest {
  @Test
  void explainCorrelatedReceiptChecksBeforeAddingOnlyTheirMeasuredIndexes() throws Exception {
    try (var pg =
        new PostgreSQLContainer<>("postgres:16-alpine")
            .withInitScript("booking-test-prerequisites.sql")) {
      pg.start();
      Flyway.configure()
          .dataSource(pg.getJdbcUrl(), pg.getUsername(), pg.getPassword())
          .load()
          .migrate();
      try (var source = new SingleConnectionDataSource(pg.createConnection(""), true)) {
        var sql = new JdbcTemplate(source);
        sql.execute(
            "CREATE TEMP TABLE perf_ids AS SELECT gen_random_uuid() booking,gen_random_uuid()"
                + " reservation,gen_random_uuid() payment,gen_random_uuid() court,gen_random_uuid()"
                + " customer,current_timestamp seeded_at,n FROM generate_series(1,1200) n");
        sql.execute(
            """
INSERT INTO slot_reservations(id,court_id,facility_id,holder_id,starts_at,ends_at,expires_at,state,quote)
SELECT reservation,court,customer,customer,seeded_at+interval '1 day',seeded_at+interval '25 hours',seeded_at,'BOOKED','{}' FROM perf_ids
""");
        sql.execute(
            """
INSERT INTO bookings(id,reservation_id,facility_id,court_id,created_by,customer_id,current_holder_id,
  starts_at,ends_at,status,source,amount,currency,price_snapshot,hold_expires_at,payment_id,paid_at)
SELECT booking,reservation,customer,court,customer,customer,customer,
  seeded_at+interval '1 day',seeded_at+interval '25 hours','CONFIRMED','ONLINE',100000,'VND','{}',seeded_at,payment,seeded_at FROM perf_ids
""");
        sql.execute(
            "INSERT INTO booking_payment_receipts(payment_id,booking_id,fingerprint) SELECT"
                + " payment,booking,repeat('a',64) FROM perf_ids");
        sql.execute(
            "INSERT INTO booking_history(id,booking_id,actor_id,action,details) SELECT"
                + " gen_random_uuid(),booking,customer,'CONFIRMED',jsonb_build_object('paymentId',payment)"
                + " FROM perf_ids");
        for (var table :
            List.of(
                "bookings", "booking_history", "booking_payment_receipts", "group_contributions"))
          sql.execute("ANALYZE " + table);
        String ids =
            "{"
                + String.join(
                    ",", sql.queryForList("SELECT booking::text FROM perf_ids", String.class))
                + "}";
        var text =
            Files.readString(
                Path.of("src/main/java/com/sporthub/booking/service/LocalAppliedCollections.java"));
        int start = text.indexOf("SELECT b.id booking_id");
        String query = text.substring(start, text.indexOf("\"\"\"", start));
        var result = new LinkedHashMap<String, Object>();
        result.put(
            "conditions",
            Map.of(
                "rows",
                1200,
                "fixture",
                "isolated SQL plan fixture only; money regression uses native service fixtures",
                "querySha256",
                com.sporthub.common.security.Signatures.sha256(query)));
        result.put(
            "before",
            sql.queryForList("EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON) " + query, ids, ids));
        var values = sql.queryForList("SELECT booking FROM perf_ids", UUID.class);
        result.put(
            "typedWithoutIndexes",
            sql.queryForList(
                "EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON) " + query,
                com.sporthub.booking.repository.BookingRepository.uuidArray(values),
                com.sporthub.booking.repository.BookingRepository.uuidArray(values)));
        sql.execute(
            "CREATE INDEX perf_booking_payment ON bookings(payment_id) WHERE payment_id IS NOT"
                + " NULL");
        sql.execute(
            "CREATE INDEX perf_confirmation_history ON"
                + " booking_history(booking_id,action,(details->>'paymentId')) WHERE action"
                + " IN('CONFIRMED','GROUP_CONTRIBUTION_PAID')");
        result.put(
            "after",
            sql.queryForList("EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON) " + query, ids, ids));
        result.put(
            "typedWithIndexes",
            sql.queryForList(
                "EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON) " + query,
                com.sporthub.booking.repository.BookingRepository.uuidArray(values),
                com.sporthub.booking.repository.BookingRepository.uuidArray(values)));
        assertThat(sql.queryForList(query, ids, ids)).hasSize(1200);
        var original = sql.queryForList(query, ids, ids);
        var typed =
            sql.queryForList(
                query,
                com.sporthub.booking.repository.BookingRepository.uuidArray(values),
                com.sporthub.booking.repository.BookingRepository.uuidArray(values));
        var order =
            Comparator.comparing((Map<String, Object> row) -> row.get("payment_id").toString());
        original.sort(order);
        typed.sort(order);
        assertThat(typed).isEqualTo(original);
        var destination =
            Path.of(
                System.getProperty("sporthub.peer.plan.output", "target/peer-receipt-plans.json"));
        Files.createDirectories(destination.toAbsolutePath().getParent());
        Files.writeString(
            destination,
            new ObjectMapper()
                .findAndRegisterModules()
                .writerWithDefaultPrettyPrinter()
                .writeValueAsString(result));
        System.out.println("PEER_RECEIPT_PLAN=" + destination.toAbsolutePath());
      }
    }
  }
}
