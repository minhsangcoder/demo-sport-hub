package com.sporthub.booking;

import static org.assertj.core.api.Assertions.*;

import java.sql.SQLException;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

class PeerSchemaMigrationTest {
  @Test
  void upgradePreservesLegacyGuardsAndAddsExclusiveClaimsAndBankHistory() throws Exception {
    try (var pg =
        new PostgreSQLContainer<>("postgres:16-alpine")
            .withInitScript("booking-test-prerequisites.sql")) {
      pg.start();
      Flyway.configure()
          .dataSource(pg.getJdbcUrl(), pg.getUsername(), pg.getPassword())
          .target("17")
          .load()
          .migrate();
      try (var connection = pg.createConnection("");
          var sql = connection.createStatement()) {
        sql.execute(
            "CREATE TEMP TABLE ids AS SELECT gen_random_uuid() booking,gen_random_uuid()"
                + " reservation,gen_random_uuid() facility,gen_random_uuid()"
                + " court,gen_random_uuid() seller,gen_random_uuid() buyer,gen_random_uuid()"
                + " listing,gen_random_uuid() acquisition");
        sql.execute(
            "INSERT INTO"
                + " slot_reservations(id,court_id,facility_id,holder_id,starts_at,ends_at,expires_at,state,quote)"
                + " SELECT reservation,court,facility,seller,now()+interval '1 day',now()+interval"
                + " '25 hours',now()+interval '10 minutes','HOLD','{\"original\":true}' FROM ids");
        sql.execute(
            "INSERT INTO"
                + " bookings(id,reservation_id,facility_id,court_id,created_by,customer_id,current_holder_id,starts_at,ends_at,status,source,amount,currency,price_snapshot,hold_expires_at)"
                + " SELECT"
                + " booking,reservation,facility,court,seller,seller,seller,r.starts_at,r.ends_at,'PENDING','ONLINE',120000,'VND','{\"original\":true}',r.expires_at"
                + " FROM ids JOIN slot_reservations r ON r.id=reservation");
        sql.execute(
            "INSERT INTO"
                + " transfer_leases(listing_id,booking_id,seller_id,price,deadline,state,buyer_id,acquisition_id,expires_at)"
                + " SELECT listing,booking,seller,100000,now()+interval '23"
                + " hours','LOCKED',buyer,acquisition,now()+interval '5 minutes' FROM ids");
        sql.execute(
            "INSERT INTO"
                + " slot_reservations(id,court_id,facility_id,holder_id,starts_at,ends_at,expires_at,state,quote)"
                + " SELECT gen_random_uuid(),gen_random_uuid(),facility,seller,now()+interval '2"
                + " days',now()+interval '49 hours',now()+interval '10"
                + " minutes','HOLD','{\"original\":true}' FROM ids");
        sql.execute(
            "INSERT INTO"
                + " slot_reservations(id,court_id,facility_id,holder_id,starts_at,ends_at,expires_at,state,quote)"
                + " SELECT gen_random_uuid(),gen_random_uuid(),facility,seller,now()+interval '3"
                + " days',now()+interval '73 hours',now()+interval '10"
                + " minutes','HOLD','{\"admissionChannel\":\"COUNTER\"}' FROM ids");
        var latest =
            Flyway.configure()
                .dataSource(pg.getJdbcUrl(), pg.getUsername(), pg.getPassword())
                .load();
        latest.migrate();
        latest.validate();
        try (var rows =
            sql.executeQuery(
                "SELECT count(*),bool_and(grandfather AND NOT protection_eligible),count(*)"
                    + " FILTER(WHERE kind='ONLINE_BOOKING'),count(*) FILTER(WHERE"
                    + " kind='LEGACY_ACQUISITION') FROM user_hold_claims")) {
          assertThat(rows.next()).isTrue();
          assertThat(rows.getInt(1)).isEqualTo(3);
          assertThat(rows.getBoolean(2)).isTrue();
          assertThat(rows.getInt(3)).isEqualTo(2);
          assertThat(rows.getInt(4)).isEqualTo(1);
        }
        try (var rows =
            sql.executeQuery(
                "SELECT (SELECT count(*) FROM booking_abandonment_events),(SELECT count(*) FROM"
                    + " booking_admission_exemptions),(SELECT count(*) FROM slot_reservations WHERE"
                    + " state='HOLD')")) {
          assertThat(rows.next()).isTrue();
          assertThat(rows.getInt(1)).isZero();
          assertThat(rows.getInt(2)).isZero();
          assertThat(rows.getInt(3)).isEqualTo(3);
        }
        try (var rows = sql.executeQuery("SELECT protocol FROM transfer_leases")) {
          assertThat(rows.next()).isTrue();
          assertThat(rows.getString(1)).isEqualTo("LEGACY_OWNER");
        }
        assertThatThrownBy(
                () -> sql.execute("UPDATE transfer_leases SET protocol='PEER_RESALE_V1'"))
            .isInstanceOf(SQLException.class);
        sql.execute(
            "UPDATE transfer_leases SET state='LOCKED',buyer_id=(SELECT buyer FROM"
                + " ids),acquisition_id=(SELECT acquisition FROM ids),expires_at=now()+interval '5"
                + " minutes'");
        assertThatThrownBy(() -> sql.execute("UPDATE transfer_leases SET state='COMPLETED'"))
            .isInstanceOf(SQLException.class);
        sql.execute("UPDATE transfer_leases SET state='RELEASED'");
        sql.execute(
            "INSERT INTO"
                + " transfer_leases(listing_id,booking_id,seller_id,price,deadline,state,protocol)"
                + " SELECT gen_random_uuid(),booking,seller,100000,now()+interval '23"
                + " hours','ACTIVE','PEER_RESALE_V1' FROM ids");
        sql.execute(
            "UPDATE transfer_leases SET state='LOCKED',buyer_id=(SELECT buyer FROM"
                + " ids),acquisition_id=gen_random_uuid(),expires_at=now()+interval '5 minutes'"
                + " WHERE protocol='PEER_RESALE_V1'");
        assertThatThrownBy(
                () ->
                    sql.execute(
                        "UPDATE transfer_leases SET state='COMPLETED' WHERE"
                            + " protocol='PEER_RESALE_V1'"))
            .isInstanceOf(SQLException.class);
        sql.execute(
            "INSERT INTO"
                + " user_hold_claims(id,principal_id,kind,source_id,court_id,facility_id,starts_at,ends_at,expires_at,phase,state,created_at,updated_at)"
                + " SELECT"
                + " gen_random_uuid(),seller,'ONLINE_BOOKING',gen_random_uuid(),court,facility,now()+interval"
                + " '1 day',now()+interval '25 hours',now()+interval '10"
                + " minutes','HOLD','ACTIVE',now(),now() FROM ids");
        assertThatThrownBy(
                () ->
                    sql.execute(
                        "INSERT INTO"
                            + " user_hold_claims(id,principal_id,kind,source_id,court_id,facility_id,starts_at,ends_at,expires_at,phase,state,created_at,updated_at)"
                            + " SELECT"
                            + " gen_random_uuid(),seller,'PEER_ACQUISITION',gen_random_uuid(),court,facility,now()+interval"
                            + " '1 day',now()+interval '25 hours',now()+interval '5"
                            + " minutes','WAITING_PAYMENT','ACTIVE',now(),now() FROM ids"))
            .isInstanceOf(SQLException.class);
        sql.execute(
            "INSERT INTO"
                + " user_bank_profiles(principal_id,initialized,reconciliation_required,updated_at)"
                + " SELECT seller,TRUE,TRUE,now() FROM ids");
        assertThatThrownBy(() -> sql.execute("UPDATE user_bank_profiles SET initialized=FALSE"))
            .isInstanceOf(SQLException.class);
        assertThatThrownBy(() -> sql.execute("DELETE FROM user_bank_profiles"))
            .isInstanceOf(SQLException.class);
        try (var rows =
            sql.executeQuery(
                "SELECT"
                    + " b.customer_id=b.created_by,b.current_holder_id=b.created_by,b.amount,b.price_snapshot->>'original',r.quote->>'original'"
                    + " FROM bookings b JOIN slot_reservations r ON r.id=b.reservation_id")) {
          assertThat(rows.next()).isTrue();
          assertThat(rows.getBoolean(1)).isTrue();
          assertThat(rows.getBoolean(2)).isTrue();
          assertThat(rows.getBigDecimal(3)).isEqualByComparingTo("120000");
          assertThat(rows.getString(4)).isEqualTo("true");
          assertThat(rows.getString(5)).isEqualTo("true");
        }
      }
    }
  }
}
