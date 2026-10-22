package com.sporthub.booking.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sporthub.booking.repository.BookingRepository;
import com.sporthub.booking.service.LegacyCounterBookingCorrectionService;
import com.sporthub.booking.service.LegacyCounterBookingCorrectionService.PreflightResult;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import java.nio.file.*;
import java.util.*;

/** Standalone maintenance entry point. No Spring startup, Flyway, .env, or default connection. */
public final class LegacyCounterBookingCorrectionTool {
    private LegacyCounterBookingCorrectionTool() {}

    public static void main(String[] args) throws Exception {
        var options=new HashMap<String,String>();boolean apply=false;
        for(int i=0;i<args.length;i++){
            if(args[i].equals("--apply")){apply=true;continue;}
            if(!Set.of("--jdbc-url","--db-user","--manifest","--actor").contains(args[i])||i+1>=args.length)
                throw new IllegalArgumentException("Use --jdbc-url --db-user --manifest; writing additionally requires --apply --actor");
            if(options.put(args[i],args[++i])!=null)throw new IllegalArgumentException("Duplicate option");
        }
        for(String key:List.of("--jdbc-url","--db-user","--manifest"))
            if(!options.containsKey(key)||options.get(key).isBlank())throw new IllegalArgumentException("Missing "+key);
        String jdbcUrl=options.get("--jdbc-url");
        if(!jdbcUrl.matches("jdbc:postgresql://[^/]+/[^?]+(?:\\?.*)?")||jdbcUrl.matches("(?i).*[?&](password|user)=.*"))
            throw new IllegalArgumentException("An explicit host, port/database URL without credentials is required");
        String password=System.getenv("SPORTHUB_OWNERSHIP_DB_PASSWORD");
        if(password==null||password.isBlank())throw new IllegalArgumentException("Set SPORTHUB_OWNERSHIP_DB_PASSWORD explicitly");
        UUID actor=apply?UUID.fromString(Objects.requireNonNull(options.get("--actor"),"--actor is required for --apply")):null;
        var json=new ObjectMapper().findAndRegisterModules();var manifest=Path.of(options.get("--manifest"));
        // Validate the input before opening a database connection in write mode.
        PreflightResult approved=apply?json.readValue(Files.readString(manifest),PreflightResult.class):null;
        if(apply&&(!jdbcUrl.equals(approved.targetDatabase())||approved.candidates().stream().anyMatch(c->!c.category().equals("CONFIRMED_GUEST_LEGACY"))))
            throw new IllegalArgumentException("Approved manifest must match the database and contain only reviewed eligible candidates");
        var ds=new DriverManagerDataSource(jdbcUrl,options.get("--db-user"),password);
        var service=new LegacyCounterBookingCorrectionService(new BookingRepository(new JdbcTemplate(ds),json),new DataSourceTransactionManager(ds));
        if(apply){
            System.out.println(json.writerWithDefaultPrettyPrinter().writeValueAsString(service.correct(jdbcUrl,actor,approved.candidates())));
        }else{
            // Never overwrite a previously reviewed manifest.
            try(var output=Files.newOutputStream(manifest,StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE)){
                json.writerWithDefaultPrettyPrinter().writeValue(output,service.preflight(jdbcUrl));
            }
        }
    }
}
