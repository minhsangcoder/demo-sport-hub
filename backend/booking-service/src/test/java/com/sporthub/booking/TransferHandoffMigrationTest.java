package com.sporthub.booking;
import static org.assertj.core.api.Assertions.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import java.sql.*;
import java.util.UUID;
import org.testcontainers.containers.PostgreSQLContainer;

class TransferHandoffMigrationTest {
 @Test void legacyCompletedLeaseKeepsMoneyOwnershipAndDoesNotInventReceipt()throws Exception{
  database((url,user,password)->{String schema=legacy(url,user,password);try(var c=DriverManager.getConnection(url,user,password);var s=c.createStatement()){
   s.execute("SET search_path TO "+schema+",public");seed(s,false);var f=Flyway.configure().dataSource(url,user,password).schemas(schema).defaultSchema(schema).load();f.migrate();f.validate();
   try(var r=s.executeQuery("SELECT b.amount,b.price_snapshot->>'marker',l.state,b.current_holder_id=l.buyer_id,l.price FROM bookings b JOIN transfer_leases l ON l.booking_id=b.id")){assertThat(r.next()).isTrue();assertThat(r.getBigDecimal(1)).isEqualByComparingTo("150000.25");assertThat(r.getString(2)).isEqualTo("legacy-transfer");assertThat(r.getString(3)).isEqualTo("COMPLETED");assertThat(r.getBoolean(4)).isTrue();assertThat(r.getBigDecimal(5)).isEqualByComparingTo("100000");}
   try(var r=s.executeQuery("SELECT count(*) FROM transfer_handoffs")){r.next();assertThat(r.getInt(1)).isZero();}
   var ds=new org.springframework.jdbc.datasource.DriverManagerDataSource(url+(url.contains("?")?"&":"?")+"currentSchema="+schema+",public",user,password);var jdbc=new org.springframework.jdbc.core.JdbcTemplate(ds);
   var service=new com.sporthub.booking.service.TransferLeaseService(new com.sporthub.booking.repository.BookingRepository(jdbc,new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules()),java.time.Clock.systemUTC());
   var lease=jdbc.queryForMap("SELECT * FROM transfer_leases LIMIT 1");UUID acquisition=(UUID)lease.get("acquisition_id");
   assertThat(service.decision(acquisition).get("decision")).isEqualTo("REVIEW");
   var command=new com.sporthub.booking.service.TransferLeaseService.Command((UUID)lease.get("listing_id"),(UUID)lease.get("booking_id"),(UUID)lease.get("seller_id"),null,null,acquisition,(UUID)lease.get("buyer_id"),null,null,null,null);
   var tx=new org.springframework.transaction.support.TransactionTemplate(new org.springframework.jdbc.datasource.DataSourceTransactionManager(ds));
   assertThatThrownBy(()->tx.execute(t->service.abort(command,"Cannot infer abort from missing legacy receipt"))).isInstanceOf(com.sporthub.common.exception.ConflictException.class);
   assertThat(jdbc.queryForObject("SELECT count(*) FROM transfer_acquisition_decisions",Integer.class)).isZero();
  }});
 }
 @Test void invalidLegacyLeasePriceStopsUpgradeWithoutRoundingOrChangingBooking()throws Exception{
  database((url,user,password)->{String schema=legacy(url,user,password);try(var c=DriverManager.getConnection(url,user,password);var s=c.createStatement()){
   s.execute("SET search_path TO "+schema+",public");seed(s,true);assertThatThrownBy(()->Flyway.configure().dataSource(url,user,password).schemas(schema).defaultSchema(schema).load().migrate()).hasMessageContaining("Transfer lease preflight failed");
   try(var r=s.executeQuery("SELECT price FROM transfer_leases")){r.next();assertThat(r.getBigDecimal(1)).isEqualByComparingTo("100000.25");}
  }});
 }
 private String legacy(String url,String user,String password){String schema="handoff_upgrade_"+UUID.randomUUID().toString().replace("-","");Flyway.configure().dataSource(url,user,password).schemas(schema).defaultSchema(schema).target("7").load().migrate();return schema;}
 private void seed(Statement s,boolean invalid)throws SQLException{
  s.execute("INSERT INTO slot_reservations(id,court_id,facility_id,holder_id,starts_at,ends_at,expires_at,state,quote) VALUES(gen_random_uuid(),gen_random_uuid(),gen_random_uuid(),gen_random_uuid(),NOW()+interval '2 days',NOW()+interval '2 days 1 hour',NOW()+interval '10 minutes','BOOKED','{\"marker\":\"legacy-transfer\"}')");
  s.execute("INSERT INTO bookings(id,reservation_id,facility_id,court_id,created_by,customer_id,current_holder_id,starts_at,ends_at,status,source,amount,currency,price_snapshot,hold_expires_at,paid_at) SELECT gen_random_uuid(),id,facility_id,court_id,gen_random_uuid(),holder_id,holder_id,starts_at,ends_at,'CONFIRMED','ONLINE',150000.25,'VND',quote,expires_at,NOW() FROM slot_reservations");
  s.execute("INSERT INTO transfer_leases(listing_id,booking_id,seller_id,price,deadline,state,acquisition_id,buyer_id,expires_at,payment_id) SELECT gen_random_uuid(),id,created_by,"+(invalid?"100000.25":"100000")+",starts_at,'COMPLETED',gen_random_uuid(),current_holder_id,hold_expires_at,gen_random_uuid() FROM bookings");
 }
 private interface Check{void run(String url,String user,String password)throws Exception;}
 private void database(Check check)throws Exception{PostgreSQLContainer<?> pg=null;String url=System.getenv("SPORTHUB_BOOKING_TEST_DB_URL"),user=System.getenv("SPORTHUB_TEST_DB_USER"),password=System.getenv("SPORTHUB_TEST_DB_PASSWORD");if(url==null){pg=new PostgreSQLContainer<>("postgres:16-alpine").withInitScript("booking-test-prerequisites.sql");pg.start();url=pg.getJdbcUrl();user=pg.getUsername();password=pg.getPassword();}try{check.run(url,user,password);}finally{if(pg!=null)pg.stop();}}
}
