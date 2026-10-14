package com.sporthub.schedule;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import java.sql.DriverManager;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

class PriceRevisionMigrationTest {
    @Test void v4UpgradePreservesLegacyTypeAndSnapshotsOriginalPriceVersion()throws Exception {
        PostgreSQLContainer<?> temporary=null;String url=System.getenv("SPORTHUB_SCHEDULE_TEST_DB_URL"),user=System.getenv("SPORTHUB_TEST_DB_USER"),password=System.getenv("SPORTHUB_TEST_DB_PASSWORD");
        if(url==null){temporary=new PostgreSQLContainer<>("postgres:16-alpine");temporary.start();url=temporary.getJdbcUrl();user=temporary.getUsername();password=temporary.getPassword();}
        try {
            String schema="price_upgrade_"+UUID.randomUUID().toString().replace("-","");UUID rule=UUID.randomUUID();
            Flyway.configure().dataSource(url,user,password).schemas(schema).defaultSchema(schema).target("4").load().migrate();
            try(var c=DriverManager.getConnection(url,user,password);var statement=c.createStatement()){
                statement.execute("INSERT INTO "+schema+".pricing_rules(id,facility_id,court_type,day_of_week,start_time,end_time,price_per_slot,version) VALUES('"+rule+"',gen_random_uuid(),'Legacy indoor',1,'06:00','08:00',120000.25,7)");
            }
            var latest=Flyway.configure().dataSource(url,user,password).schemas(schema).defaultSchema(schema).load();latest.migrate();latest.validate();
            try(var c=DriverManager.getConnection(url,user,password);var statement=c.createStatement();var rows=statement.executeQuery("SELECT p.court_type,p.court_type_id,v.version,v.snapshot->>'pricePerSlot',v.snapshot->>'legacyCourtType' FROM "+schema+".pricing_rules p JOIN "+schema+".pricing_rule_versions v ON v.rule_id=p.id")){
                assertThat(rows.next()).isTrue();assertThat(rows.getString(1)).isEqualTo("Legacy indoor");assertThat(rows.getObject(2)).isNull();assertThat(rows.getLong(3)).isEqualTo(7);assertThat(new java.math.BigDecimal(rows.getString(4))).isEqualByComparingTo("120000.25");assertThat(rows.getString(5)).isEqualTo("Legacy indoor");
            }
        }finally{if(temporary!=null)temporary.stop();}
    }
}
