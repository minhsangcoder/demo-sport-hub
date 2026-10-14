package com.sporthub.payment;
import static org.assertj.core.api.Assertions.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import java.sql.*;
import java.util.UUID;
import org.testcontainers.containers.PostgreSQLContainer;

class TransferEscrowMigrationTest {
 @Test void upgradePreservesHeldEscrowWithoutPayoutOrChangingMoney()throws Exception{
  database((url,user,password)->{String schema=legacy(url,user,password);try(var c=DriverManager.getConnection(url,user,password);var s=c.createStatement()){
   s.execute("SET search_path TO "+schema+",public");seed(s,100000);var f=Flyway.configure().dataSource(url,user,password).schemas(schema).defaultSchema(schema).load();f.migrate();f.validate();
   try(var r=s.executeQuery("SELECT e.amount,e.state,p.status,e.payer_id=p.payer_id,e.seller_id=p.seller_id FROM transfer_escrow e JOIN payment_orders p ON p.id=e.payment_id")){assertThat(r.next()).isTrue();assertThat(r.getBigDecimal(1)).isEqualByComparingTo("100000");assertThat(r.getString(2)).isEqualTo("HELD_POLICY_BLOCKED");assertThat(r.getString(3)).isEqualTo("SUCCESS");assertThat(r.getBoolean(4)).isTrue();assertThat(r.getBoolean(5)).isTrue();}
  }});
 }
 @Test void inconsistentLegacyEscrowStopsUpgradeWithoutRepricingOrDeletingEvidence()throws Exception{
  database((url,user,password)->{String schema=legacy(url,user,password);try(var c=DriverManager.getConnection(url,user,password);var s=c.createStatement()){
   s.execute("SET search_path TO "+schema+",public");seed(s,1);assertThatThrownBy(()->Flyway.configure().dataSource(url,user,password).schemas(schema).defaultSchema(schema).load().migrate()).hasMessageContaining("Transfer escrow preflight failed");
   try(var r=s.executeQuery("SELECT e.amount,p.amount FROM transfer_escrow e JOIN payment_orders p ON p.id=e.payment_id")){r.next();assertThat(r.getBigDecimal(1)).isEqualByComparingTo("1");assertThat(r.getBigDecimal(2)).isEqualByComparingTo("100000");}
  }});
 }
 private String legacy(String url,String user,String password){String schema="escrow_upgrade_"+UUID.randomUUID().toString().replace("-","");Flyway.configure().dataSource(url,user,password).schemas(schema).defaultSchema(schema).target("7").load().migrate();return schema;}
 private void seed(Statement s,int amount)throws SQLException{
  UUID booking=UUID.randomUUID(),acquisition=UUID.randomUUID(),id=UUID.randomUUID(),payer=UUID.randomUUID(),seller=UUID.randomUUID();
  s.execute("INSERT INTO payment_obligations(booking_id,purpose,subject_id,amount,currency) VALUES('"+booking+"','TRANSFER','"+acquisition+"',100000,'VND')");
  s.execute("INSERT INTO payment_orders(id,booking_id,payer_id,acquisition_id,seller_id,purpose,amount,currency,provider,provider_reference,status,idempotency_key,expires_at,paid_at) VALUES('"+id+"','"+booking+"','"+payer+"','"+acquisition+"','"+seller+"','TRANSFER',100000,'VND','DEMO','legacy-transfer','SUCCESS','legacy-transfer',NOW()+interval '10 minutes',NOW())");
  s.execute("INSERT INTO transfer_escrow(payment_id,acquisition_id,payer_id,seller_id,amount,currency,state) VALUES('"+id+"','"+acquisition+"','"+payer+"','"+seller+"',"+amount+",'VND','HELD_POLICY_BLOCKED')");
 }
 private interface Check{void run(String url,String user,String password)throws Exception;}
 private void database(Check check)throws Exception{PostgreSQLContainer<?> pg=null;String url=System.getenv("SPORTHUB_PAYMENT_TEST_DB_URL"),user=System.getenv("SPORTHUB_TEST_DB_USER"),password=System.getenv("SPORTHUB_TEST_DB_PASSWORD");if(url==null){pg=new PostgreSQLContainer<>("postgres:16-alpine");pg.start();url=pg.getJdbcUrl();user=pg.getUsername();password=pg.getPassword();}try{check.run(url,user,password);}finally{if(pg!=null)pg.stop();}}
}
