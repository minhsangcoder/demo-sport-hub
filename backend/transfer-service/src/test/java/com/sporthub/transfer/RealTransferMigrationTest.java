package com.sporthub.transfer;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.sql.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

class RealTransferMigrationTest {
  @Test
  void realOwnerFeesAllowThreeAndSevenPercentWithoutReclassifyingLegacy() throws Exception {
    try (var pg = new PostgreSQLContainer<>("postgres:16-alpine")) {
      pg.start();
      Flyway.configure()
          .dataSource(pg.getJdbcUrl(), pg.getUsername(), pg.getPassword())
          .target("6")
          .load()
          .migrate();
      try (var c = pg.createConnection("");
          var s = c.createStatement()) {
        s.execute(
            "INSERT INTO"
                + " transfer_listings(id,booking_id,seller_id,facility_id,court_id,snapshot,original_amount,price,currency,starts_at,ends_at,deadline,state,workflow)"
                + " VALUES(gen_random_uuid(),gen_random_uuid(),gen_random_uuid(),gen_random_uuid(),gen_random_uuid(),'{}',100000,90000,'VND',now()+interval"
                + " '2 days',now()+interval '49 hours',now()+interval '1 day','ACTIVE','READY')");
        var all =
            Flyway.configure()
                .dataSource(pg.getJdbcUrl(), pg.getUsername(), pg.getPassword())
                .target("7")
                .load();
        all.migrate();
        all.validate();
        assertThat(all.info().current().getVersion().toString()).isEqualTo("7");
        try (var rows = s.executeQuery("SELECT finance_terms IS NULL FROM transfer_listings")) {
          rows.next();
          assertThat(rows.getBoolean(1)).isTrue();
        }
        for (int fee : new int[] {300, 700}) {
          UUID listing = UUID.randomUUID(),
              booking = UUID.randomUUID(),
              facility = UUID.randomUUID(),
              court = UUID.randomUUID(),
              seller = UUID.randomUUID();
          Instant deadline = Instant.now().plusSeconds(3600).truncatedTo(ChronoUnit.MILLIS);
          ObjectNode terms = new ObjectMapper().createObjectNode();
          terms
              .put("bookingId", booking.toString())
              .put("facilityId", facility.toString())
              .put("courtId", court.toString())
              .put("sellerId", seller.toString());
          terms
              .put("mode", "REAL")
              .put("protocol", "FACILITY_REAL_V1")
              .put("provider", "PAYOS")
              .put("moneyClass", "REAL");
          terms
              .put("receiverFacilityId", facility.toString())
              .put("receiverOwnerId", UUID.randomUUID().toString())
              .put("receiverName", "Synthetic facility");
          terms
              .put("channelId", UUID.randomUUID().toString())
              .put("receiverRevisionId", UUID.randomUUID().toString())
              .put("sellerDestinationRevisionId", UUID.randomUUID().toString())
              .put("sellerDestinationHash", "a".repeat(64));
          terms
              .put("policyVersion", 1)
              .put("feeBps", fee)
              .put("feePolicyHash", "b".repeat(64))
              .put("amount", "100000")
              .put("quotedFeeAmount", Integer.toString(fee * 10))
              .put("quotedSellerNetAmount", Integer.toString(100000 - fee * 10));
          terms
              .put("currency", "VND")
              .put("rounding", "HALF_UP")
              .put("termsHash", "c".repeat(64))
              .put("deadline", deadline.toString())
              .putObject("refundPolicy")
              .put("rateBps", 8000);
          try (var insert =
              c.prepareStatement(
                  "INSERT INTO"
                      + " transfer_listings(id,booking_id,seller_id,facility_id,court_id,snapshot,original_amount,price,currency,starts_at,ends_at,deadline,state,workflow,finance_terms)"
                      + " VALUES(?,?,?,?,?,'{}',100000,100000,'VND',now()+interval '2"
                      + " days',now()+interval '49 hours',?,'ACTIVE','READY',?::jsonb)")) {
            insert.setObject(1, listing);
            insert.setObject(2, booking);
            insert.setObject(3, seller);
            insert.setObject(4, facility);
            insert.setObject(5, court);
            insert.setTimestamp(6, Timestamp.from(deadline));
            insert.setString(7, terms.toString());
            assertThat(insert.executeUpdate()).isEqualTo(1);
          }
          s.execute(
              "UPDATE transfer_listings SET state='LOCKED',workflow='LOCK',version=version+1 WHERE"
                  + " id='"
                  + listing
                  + "'");
          assertThatThrownBy(
                  () ->
                      s.execute(
                          "UPDATE transfer_listings SET"
                              + " finance_terms=jsonb_set(finance_terms,'{feeBps}','200') WHERE"
                              + " id='"
                              + listing
                              + "'"))
              .isInstanceOf(SQLException.class);
        }
        s.execute(
            "CREATE TEMP TABLE preserved_finance AS SELECT id,to_jsonb(l) original FROM"
                + " transfer_listings l");
        var latest =
            Flyway.configure()
                .dataSource(pg.getJdbcUrl(), pg.getUsername(), pg.getPassword())
                .load();
        latest.migrate();
        latest.validate();
        assertThat(latest.info().current().getVersion().toString()).isEqualTo("8");
        try (var rows =
            s.executeQuery(
                "SELECT bool_and((to_jsonb(l)-'protocol')=p.original AND l.protocol='LEGACY_OWNER')"
                    + " FROM transfer_listings l JOIN preserved_finance p ON p.id=l.id")) {
          rows.next();
          assertThat(rows.getBoolean(1)).isTrue();
        }
      }
      var fresh =
          Flyway.configure()
              .dataSource(pg.getJdbcUrl(), pg.getUsername(), pg.getPassword())
              .schemas("fresh")
              .defaultSchema("fresh")
              .load();
      fresh.migrate();
      fresh.validate();
    }
  }
}
