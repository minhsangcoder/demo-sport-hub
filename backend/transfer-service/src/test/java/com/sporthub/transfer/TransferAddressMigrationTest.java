package com.sporthub.transfer;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import java.sql.DriverManager;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

class TransferAddressMigrationTest {
 @Test void upgradeRemovesDistrictAndKeepsHistoricalVenueDetails() throws Exception {
  PostgreSQLContainer<?> temporary=null;String url=System.getenv("SPORTHUB_TRANSFER_TEST_DB_URL"),user=System.getenv("SPORTHUB_TEST_DB_USER"),password=System.getenv("SPORTHUB_TEST_DB_PASSWORD");
  if(url==null){temporary=new PostgreSQLContainer<>("postgres:16-alpine");temporary.start();url=temporary.getJdbcUrl();user=temporary.getUsername();password=temporary.getPassword();}
  try{
   String schema="address_upgrade_"+UUID.randomUUID().toString().replace("-","");var before=Flyway.configure().dataSource(url,user,password).schemas(schema).defaultSchema(schema).target("1").load();before.migrate();
   try(var c=DriverManager.getConnection(url,user,password);var st=c.createStatement()){
    st.execute("SET search_path TO "+schema);
    st.execute("INSERT INTO transfer_listings(id,booking_id,seller_id,facility_id,court_id,snapshot,original_amount,price,currency,starts_at,ends_at,deadline,state,workflow) VALUES('"+UUID.randomUUID()+"','"+UUID.randomUUID()+"','"+UUID.randomUUID()+"','"+UUID.randomUUID()+"','"+UUID.randomUUID()+"','{\"facilityName\":\"Legacy venue\",\"address\":\"123 Street\",\"province\":\"p\",\"district\":\"d\",\"amenities\":[\"Parking\"]}',100000,80000,'VND',NOW()+INTERVAL '2 days',NOW()+INTERVAL '3 days',NOW()+INTERVAL '1 day','ACTIVE','READY')");
   }
   var latest=Flyway.configure().dataSource(url,user,password).schemas(schema).defaultSchema(schema).load();latest.migrate();latest.validate();
   try(var c=DriverManager.getConnection(url,user,password);var st=c.createStatement();var rs=st.executeQuery("SELECT snapshot = '{\"facilityName\":\"Legacy venue\",\"address\":\"123 Street\",\"province\":\"p\",\"amenities\":[\"Parking\"]}'::jsonb FROM "+schema+".transfer_listings")){rs.next();assertThat(rs.getBoolean(1)).isTrue();}
  }finally{if(temporary!=null)temporary.stop();}
 }
}
