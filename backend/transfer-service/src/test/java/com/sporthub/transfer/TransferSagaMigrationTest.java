package com.sporthub.transfer;
import static org.assertj.core.api.Assertions.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import java.sql.*;
import java.util.UUID;
import org.testcontainers.containers.PostgreSQLContainer;

class TransferSagaMigrationTest {
 @Test void upgradePreservesHistoricalAcquisitionTermsAfterListingWasEdited()throws Exception{
  database((url,user,password)->{String schema=legacy(url,user,password);try(var c=DriverManager.getConnection(url,user,password);var s=c.createStatement()){
   s.execute("SET search_path TO "+schema+",public");seed(s,false);var f=Flyway.configure().dataSource(url,user,password).schemas(schema).defaultSchema(schema).load();f.migrate();f.validate();
   try(var r=s.executeQuery("SELECT a.amount,a.state,l.price,a.expires_at>l.deadline FROM transfer_acquisitions a JOIN transfer_listings l ON l.id=a.listing_id")){assertThat(r.next()).isTrue();assertThat(r.getBigDecimal(1)).isEqualByComparingTo("100000");assertThat(r.getString(2)).isEqualTo("CANCELLED");assertThat(r.getBigDecimal(3)).isEqualByComparingTo("80000");assertThat(r.getBoolean(4)).isTrue();}
   try(var r=s.executeQuery("SELECT count(*) FROM transfer_payment_receipts")){r.next();assertThat(r.getInt(1)).isZero();}
  }});
 }
 @Test void duplicateUnresolvedLegacyAcquisitionsStopMigrationWithoutDeletingEither()throws Exception{
  database((url,user,password)->{String schema=legacy(url,user,password);try(var c=DriverManager.getConnection(url,user,password);var s=c.createStatement()){
   s.execute("SET search_path TO "+schema+",public");seed(s,true);
   assertThatThrownBy(()->Flyway.configure().dataSource(url,user,password).schemas(schema).defaultSchema(schema).load().migrate()).hasMessageContaining("Transfer saga preflight failed");
   try(var r=s.executeQuery("SELECT count(*),sum(amount) FROM transfer_acquisitions")){r.next();assertThat(r.getInt(1)).isEqualTo(2);assertThat(r.getBigDecimal(2)).isEqualByComparingTo("200000");}
  }});
 }
 private String legacy(String url,String user,String password){String schema="transfer_saga_"+UUID.randomUUID().toString().replace("-","");Flyway.configure().dataSource(url,user,password).schemas(schema).defaultSchema(schema).target("2").load().migrate();return schema;}
 private void seed(Statement s,boolean duplicate)throws SQLException{
  s.execute("INSERT INTO transfer_listings(id,booking_id,seller_id,facility_id,court_id,snapshot,original_amount,price,currency,starts_at,ends_at,deadline,state,workflow) VALUES(gen_random_uuid(),gen_random_uuid(),gen_random_uuid(),gen_random_uuid(),gen_random_uuid(),'{}',150000,"+(duplicate?100000:80000)+",'VND',NOW()+interval '2 days',NOW()+interval '2 days 1 hour',NOW()+interval '1 hour','ACTIVE','READY')");
  for(int i=0;i<(duplicate?2:1);i++)s.execute("INSERT INTO transfer_acquisitions(id,listing_id,buyer_id,amount,currency,expires_at,state) SELECT gen_random_uuid(),id,gen_random_uuid(),100000,'VND',NOW()+interval '"+(duplicate?"30 minutes":"2 hours")+"','"+(duplicate?"PENDING":"CANCELLED")+"' FROM transfer_listings");
 }
 private interface Check{void run(String url,String user,String password)throws Exception;}
 private void database(Check check)throws Exception{PostgreSQLContainer<?> pg=null;String url=System.getenv("SPORTHUB_TRANSFER_TEST_DB_URL"),user=System.getenv("SPORTHUB_TEST_DB_USER"),password=System.getenv("SPORTHUB_TEST_DB_PASSWORD");if(url==null){pg=new PostgreSQLContainer<>("postgres:16-alpine");pg.start();url=pg.getJdbcUrl();user=pg.getUsername();password=pg.getPassword();}try{check.run(url,user,password);}finally{if(pg!=null)pg.stop();}}
}
