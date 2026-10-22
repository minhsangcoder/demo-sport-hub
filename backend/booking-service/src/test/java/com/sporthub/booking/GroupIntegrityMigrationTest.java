package com.sporthub.booking;

import static org.assertj.core.api.Assertions.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import java.sql.*;
import java.util.UUID;

class GroupIntegrityMigrationTest {
    @Test void upgradePreservesActualReceiptsSnapshotAndLegacyInvitationGeneration()throws Exception{
        withDatabase((url,user,password)->{
            String schema=legacy(url,user,password,"100");
            var latest=Flyway.configure().dataSource(url,user,password).schemas(schema).defaultSchema(schema).load();latest.migrate();latest.validate();
            try(var c=DriverManager.getConnection(url,user,password);var s=c.createStatement()){
                s.execute("SET search_path TO "+schema+",public");
                try(var r=s.executeQuery("SELECT b.amount,b.price_snapshot->>'marker',g.invite_version,m.amount_paid,c.amount FROM bookings b JOIN booking_groups g ON g.id=b.id JOIN group_members m ON m.group_id=g.id JOIN group_contributions c ON c.member_id=m.id")){
                    assertThat(r.next()).isTrue();assertThat(r.getBigDecimal(1)).isEqualByComparingTo("100");assertThat(r.getString(2)).isEqualTo("legacy-group");assertThat(r.getLong(3)).isZero();assertThat(r.getBigDecimal(4)).isEqualByComparingTo("60");assertThat(r.getBigDecimal(5)).isEqualByComparingTo("60");
                }
                assertThatThrownBy(()->s.execute("DELETE FROM group_contributions")).isInstanceOf(SQLException.class).hasMessageContaining("immutable");
            }
        });
    }
    @Test void missingLegacyReceiptStopsUpgradeWithoutResettingPaidMember()throws Exception{
        withDatabase((url,user,password)->{
            String schema=legacy(url,user,password,"100");
            try(var c=DriverManager.getConnection(url,user,password);var s=c.createStatement()){
                s.execute("SET search_path TO "+schema+",public");s.execute("DELETE FROM group_contributions");
                assertThatThrownBy(()->Flyway.configure().dataSource(url,user,password).schemas(schema).defaultSchema(schema).load().migrate()).hasMessageContaining("Group contribution integrity failed");
                try(var r=s.executeQuery("SELECT amount_paid,payment_state FROM group_members WHERE amount_paid>0")){assertThat(r.next()).isTrue();assertThat(r.getBigDecimal(1)).isEqualByComparingTo("60");assertThat(r.getString(2)).isEqualTo("PAID");}
                try(var r=s.executeQuery("SELECT count(*) FROM information_schema.columns WHERE table_schema='"+schema+"' AND table_name='booking_groups' AND column_name='invite_version'")){r.next();assertThat(r.getInt(1)).isZero();}
            }
        });
    }
    @Test void fractionalLegacyGroupFailsPreflightWithoutRoundingSnapshot()throws Exception{
        withDatabase((url,user,password)->{
            String schema=legacy(url,user,password,"100.25");
            assertThatThrownBy(()->Flyway.configure().dataSource(url,user,password).schemas(schema).defaultSchema(schema).load().migrate()).hasMessageContaining("Group allocation integrity failed");
            try(var c=DriverManager.getConnection(url,user,password);var s=c.createStatement();var r=s.executeQuery("SELECT amount,price_snapshot->>'marker' FROM "+schema+".bookings")){
                assertThat(r.next()).isTrue();assertThat(r.getBigDecimal(1)).isEqualByComparingTo("100.25");assertThat(r.getString(2)).isEqualTo("legacy-group");
            }
        });
    }
    private String legacy(String url,String user,String password,String amount)throws Exception{
        String schema="group_integrity_"+UUID.randomUUID().toString().replace("-","");
        Flyway.configure().dataSource(url,user,password).schemas(schema).defaultSchema(schema).target("6").load().migrate();
        try(var c=DriverManager.getConnection(url,user,password);var s=c.createStatement()){
            s.execute("SET search_path TO "+schema+",public");
            s.execute("INSERT INTO slot_reservations(id,court_id,facility_id,holder_id,starts_at,ends_at,expires_at,state,quote) VALUES(gen_random_uuid(),gen_random_uuid(),gen_random_uuid(),gen_random_uuid(),NOW()+interval '2 hours',NOW()+interval '3 hours',NOW()+interval '20 minutes','HOLD','{\"marker\":\"legacy-group\"}')");
            s.execute("INSERT INTO bookings(id,reservation_id,facility_id,court_id,created_by,customer_id,current_holder_id,starts_at,ends_at,status,source,amount,currency,price_snapshot,hold_expires_at) SELECT gen_random_uuid(),id,facility_id,court_id,holder_id,holder_id,holder_id,starts_at,ends_at,'PENDING','ONLINE',"+amount+",'VND',quote,expires_at FROM slot_reservations");
            s.execute("INSERT INTO booking_groups(id,owner_id,name,state,deadline,max_members,allocations_locked) SELECT id,current_holder_id,'Legacy','GROUP_PENDING',hold_expires_at,2,true FROM bookings");
            s.execute("INSERT INTO group_members(id,group_id,user_id,display_name,amount_due,amount_paid,payment_state) SELECT gen_random_uuid(),id,current_holder_id,'Owner',60,60,'PAID' FROM bookings");
            s.execute("INSERT INTO group_members(id,group_id,user_id,display_name,amount_due,payment_state) SELECT gen_random_uuid(),id,gen_random_uuid(),'Member',40,'WAITING_FOR_PAYMENT' FROM bookings");
            s.execute("INSERT INTO group_contributions(payment_id,group_id,member_id,payer_id,amount,paid_at) SELECT gen_random_uuid(),group_id,id,user_id,60,NOW() FROM group_members WHERE amount_paid=60");
        }
        return schema;
    }
    private interface DatabaseCheck{void run(String url,String user,String password)throws Exception;}
    private void withDatabase(DatabaseCheck check)throws Exception{
        PostgreSQLContainer<?> pg=null;String url=System.getenv("SPORTHUB_BOOKING_TEST_DB_URL"),user=System.getenv("SPORTHUB_TEST_DB_USER"),password=System.getenv("SPORTHUB_TEST_DB_PASSWORD");
        if(url==null){pg=new PostgreSQLContainer<>("postgres:16-alpine").withInitScript("booking-test-prerequisites.sql");pg.start();url=pg.getJdbcUrl();user=pg.getUsername();password=pg.getPassword();}
        try{check.run(url,user,password);}finally{if(pg!=null)pg.stop();}
    }
}
