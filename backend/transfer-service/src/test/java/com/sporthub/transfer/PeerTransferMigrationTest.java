package com.sporthub.transfer;

import static org.assertj.core.api.Assertions.*;

import java.sql.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

class PeerTransferMigrationTest {
  @Test
  void upgradePreservesLegacyAndRequiresPrivateReadyEvidenceForPeerDeclarations() throws Exception {
    try (var pg = new PostgreSQLContainer<>("postgres:16-alpine")) {
      pg.start();
      Flyway.configure()
          .dataSource(pg.getJdbcUrl(), pg.getUsername(), pg.getPassword())
          .target("7")
          .load()
          .migrate();
      try (var connection = pg.createConnection("");
          var sql = connection.createStatement()) {
        sql.execute(
            """
INSERT INTO transfer_listings(id,booking_id,seller_id,facility_id,court_id,snapshot,original_amount,price,currency,starts_at,ends_at,deadline,state,workflow)
VALUES(gen_random_uuid(),gen_random_uuid(),gen_random_uuid(),gen_random_uuid(),gen_random_uuid(),'{"legacy":true}',120000,100000,'VND',now()+interval '1 day',now()+interval '25 hours',now()+interval '23 hours','ACTIVE','READY')
""");
        var latest =
            Flyway.configure()
                .dataSource(pg.getJdbcUrl(), pg.getUsername(), pg.getPassword())
                .load();
        latest.migrate();
        latest.validate();
        try (var r =
            sql.executeQuery(
                "SELECT protocol,price,snapshot->>'legacy',finance_terms IS NULL FROM"
                    + " transfer_listings")) {
          r.next();
          assertThat(r.getString(1)).isEqualTo("LEGACY_OWNER");
          assertThat(r.getBigDecimal(2)).isEqualByComparingTo("100000");
          assertThat(r.getString(3)).isEqualTo("true");
          assertThat(r.getBoolean(4)).isTrue();
        }
        sql.execute(
            """
CREATE TEMP TABLE ids AS SELECT gen_random_uuid() listing,gen_random_uuid() booking,
gen_random_uuid() facility,gen_random_uuid() court,gen_random_uuid() seller,gen_random_uuid() buyer,
gen_random_uuid() acquisition,gen_random_uuid() conversation,gen_random_uuid() evidence,
now()+interval '23 hours' deadline,now()+interval '5 minutes' expiry
""");
        sql.execute(
            """
INSERT INTO transfer_listings(id,booking_id,seller_id,facility_id,court_id,snapshot,original_amount,price,currency,starts_at,ends_at,deadline,state,workflow,protocol,finance_terms)
SELECT listing,booking,seller,facility,court,'{}',120000,100000,'VND',now()+interval '1 day',now()+interval '25 hours',deadline,'ACTIVE','READY','PEER_RESALE_V1',
jsonb_build_object('protocol','PEER_RESALE_V1','mode','REAL','bookingId',booking,'facilityId',facility,'courtId',court,'sellerId',seller,'bankDestinationRevisionId',gen_random_uuid(),'destinationHash',repeat('a',64),'refundPolicy','{}'::jsonb,'termsHash',repeat('b',64),'amount','100000','currency','VND','deadline',deadline) FROM ids
""");
        sql.execute(
            "INSERT INTO transfer_conversations SELECT conversation,listing,seller,buyer,now() FROM"
                + " ids");
        sql.execute(
            """
INSERT INTO transfer_acquisitions(id,listing_id,buyer_id,amount,currency,expires_at,state,protocol,conversation_id,finance_terms)
SELECT acquisition,listing,buyer,100000,'VND',expiry,'PENDING','PEER_RESALE_V1',conversation,
(l.finance_terms-'termsHash')||jsonb_build_object('sellerTermsHash',l.finance_terms->>'termsHash','listingId',listing,'listingVersion',l.version,'acquisitionId',acquisition,'buyerId',buyer,'expiresAt',expiry,'acceptedAt',now(),'termsHash',repeat('c',64))
FROM ids JOIN transfer_listings l ON l.id=listing
""");
        assertThatThrownBy(
                () ->
                    sql.execute(
                        "UPDATE transfer_listings SET protocol='LEGACY_OWNER' WHERE"
                            + " protocol='PEER_RESALE_V1'"))
            .isInstanceOf(SQLException.class);
        assertThatThrownBy(
                () ->
                    sql.execute(
                        "UPDATE transfer_acquisitions SET state='PENDING_HANDOFF' WHERE"
                            + " protocol='PEER_RESALE_V1'"))
            .isInstanceOf(SQLException.class);
        assertThatThrownBy(
                () ->
                    sql.execute(
                        "UPDATE transfer_acquisitions SET payment_id=gen_random_uuid() WHERE"
                            + " protocol='PEER_RESALE_V1'"))
            .isInstanceOf(SQLException.class);
        sql.execute(
            """
INSERT INTO transfer_evidence(id,acquisition_id,uploader_id,object_key,sha256,content_type,byte_size,state,idempotency_key,fingerprint,created_at) SELECT evidence,acquisition,buyer,'private/object',repeat('d',64),'image/png',100,'STAGED','upload',repeat('e',64),now() FROM ids
""");
        String claim =
            "INSERT INTO peer_payment_claims SELECT acquisition,buyer,'BANK"
                + " REF',ARRAY[evidence],repeat('f',64),now() FROM ids";
        assertThatThrownBy(() -> sql.execute(claim)).isInstanceOf(SQLException.class);
        sql.execute("UPDATE transfer_evidence SET state='READY'");
        sql.execute(claim);
        assertThatThrownBy(() -> sql.execute("DELETE FROM transfer_evidence"))
            .isInstanceOf(SQLException.class);
        assertThatThrownBy(
                () -> sql.execute("UPDATE peer_payment_claims SET bank_reference='changed'"))
            .isInstanceOf(SQLException.class);
        assertThatThrownBy(
                () ->
                    sql.execute(
                        "INSERT INTO"
                            + " transfer_messages(id,conversation_id,sender_id,text,idempotency_key,fingerprint,created_at)"
                            + " SELECT"
                            + " gen_random_uuid(),conversation,gen_random_uuid(),'spam','key',repeat('a',64),now()"
                            + " FROM ids"))
            .isInstanceOf(SQLException.class)
            .satisfies(
                error -> assertThat(((SQLException) error).getSQLState()).isEqualTo("23514"));
        sql.execute(
            """
INSERT INTO transfer_messages(id,conversation_id,sender_id,text,idempotency_key,fingerprint,created_at)
SELECT gen_random_uuid(),conversation,buyer,'Đã gửi chứng từ','message',repeat('a',64),now() FROM ids
""");
        assertThatThrownBy(() -> sql.execute("DELETE FROM transfer_messages"))
            .isInstanceOf(SQLException.class);
        sql.execute(
            "ALTER TABLE ids ADD COLUMN seller_evidence UUID DEFAULT gen_random_uuid(), ADD COLUMN"
                + " return_id UUID DEFAULT gen_random_uuid()");
        sql.execute(
            "INSERT INTO"
                + " transfer_evidence(id,acquisition_id,uploader_id,object_key,sha256,content_type,byte_size,state,idempotency_key,fingerprint,created_at)"
                + " SELECT"
                + " seller_evidence,acquisition,seller,'private/return',repeat('a',64),'image/png',100,'STAGED','return-upload',repeat('b',64),now()"
                + " FROM ids");
        String sellerReturn =
            "INSERT INTO peer_return_declarations SELECT return_id,acquisition,seller,100000,'A"
                + " RETURN',ARRAY[seller_evidence],repeat('c',64),repeat('d',64),now() FROM ids";
        assertThatThrownBy(() -> sql.execute(sellerReturn)).isInstanceOf(SQLException.class);
        sql.execute("UPDATE transfer_evidence SET state='READY' WHERE object_key='private/return'");
        assertThatThrownBy(
                () ->
                    sql.execute(
                        "INSERT INTO peer_return_declarations SELECT"
                            + " return_id,acquisition,buyer,100000,'A"
                            + " RETURN',ARRAY[evidence],repeat('c',64),repeat('d',64),now() FROM"
                            + " ids"))
            .isInstanceOf(SQLException.class)
            .satisfies(
                error -> assertThat(((SQLException) error).getSQLState()).isEqualTo("23514"));
        sql.execute(sellerReturn);
        assertThatThrownBy(
                () ->
                    sql.execute(
                        "INSERT INTO peer_return_acknowledgments SELECT"
                            + " return_id,seller,repeat('e',64),now() FROM ids"))
            .isInstanceOf(SQLException.class);
        sql.execute(
            "INSERT INTO peer_return_acknowledgments SELECT return_id,buyer,repeat('e',64),now()"
                + " FROM ids");
        assertThatThrownBy(() -> sql.execute("UPDATE peer_return_declarations SET amount=1"))
            .isInstanceOf(SQLException.class);
        assertThatThrownBy(() -> sql.execute("DELETE FROM peer_return_acknowledgments"))
            .isInstanceOf(SQLException.class);
        assertThatThrownBy(
                () ->
                    sql.execute(
                        "INSERT INTO peer_return_declarations SELECT"
                            + " gen_random_uuid(),acquisition,seller,1,'ANOTHER',ARRAY[seller_evidence],repeat('c',64),repeat('f',64),now()"
                            + " FROM ids"))
            .isInstanceOf(SQLException.class);
        assertThatThrownBy(
                () ->
                    sql.execute(
                        "INSERT INTO peer_money_no_claims SELECT"
                            + " acquisition,gen_random_uuid(),repeat('f',64),now() FROM ids"))
            .isInstanceOf(SQLException.class);
        sql.execute(
            "INSERT INTO peer_money_no_claims SELECT acquisition,seller,repeat('f',64),now() FROM"
                + " ids UNION ALL SELECT acquisition,buyer,repeat('f',64),now() FROM ids");
      }
      try (var c = pg.createConnection("");
          var s = c.createStatement()) {
        s.execute("CREATE DATABASE peer_fresh");
      }
      var fresh =
          Flyway.configure()
              .dataSource(
                  pg.getJdbcUrl().replace(pg.getDatabaseName(), "peer_fresh"),
                  pg.getUsername(),
                  pg.getPassword())
              .load();
      fresh.migrate();
      fresh.validate();
      assertThat(fresh.info().current().getVersion().toString()).isEqualTo("8");
    }
  }
}
