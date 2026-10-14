package com.sporthub.common.schedule;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sporthub.common.exception.ConflictException;
import com.sporthub.common.security.ServiceCalls;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import java.time.Duration;
import java.time.Instant;
import static com.sporthub.common.schedule.ConfigurationGuardProtocol.*;

@Component
@ConditionalOnProperty(name="sporthub.configuration-changes.source")
public class ConfigurationGuardTransport {
    private final RestClient client;
    private final ObjectMapper json;
    private final String secret;
    public ConfigurationGuardTransport(@Value("${BOOKING_SERVICE_URL:http://localhost:8084}") String url,
            @Value("${SERVICE_CALL_SECRET:}") String secret,ObjectMapper json) {
        var factory=new org.springframework.http.client.JdkClientHttpRequestFactory(
            java.net.http.HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build());
        factory.setReadTimeout(Duration.ofSeconds(5));
        client=RestClient.builder().baseUrl(url).requestFactory(factory).build();this.json=json;this.secret=secret;
    }
    public Prepared prepare(Command command) {
        var data=call("prepare",command).path("data");
        try {
            var prepared=json.treeToValue(data,Prepared.class);
            if(prepared==null||!command.operationId().equals(prepared.operationId())||prepared.reservations()==null)
                throw new IllegalStateException("Invalid configuration guard response");
            validateReservations(prepared.reservations());return prepared;
        } catch(Exception ex) {throw new IllegalStateException("Booking configuration guard is unavailable",ex);}
    }
    public java.util.List<Reservation> impact(java.util.UUID facility) {
        if(secret.length()<32)throw new IllegalStateException("Private service authentication is not configured");
        String path="/api/v1/internal/bookings/configuration/impact";
        try {
            String body=json.writeValueAsString(java.util.Map.of("facilityId",facility));long time=Instant.now().getEpochSecond();
            var response=client.post().uri(path).contentType(MediaType.APPLICATION_JSON).header("X-Service-Time",Long.toString(time))
                .header("X-Service-Signature",ServiceCalls.sign(secret,"POST",path,time,body)).body(body).retrieve().body(com.fasterxml.jackson.databind.JsonNode.class);
            if(response==null||!response.path("data").isArray())throw new IllegalStateException("Invalid impact response");
            var rows=json.convertValue(response.path("data"),new com.fasterxml.jackson.core.type.TypeReference<java.util.List<Reservation>>(){});
            validateReservations(rows);return java.util.List.copyOf(rows);
        }catch(Exception ex){throw new IllegalStateException("Booking impact could not be verified",ex);}
    }
    private void validateReservations(java.util.List<Reservation> rows) {
        var ids=new java.util.HashSet<java.util.UUID>();
        for(var row:rows)if(row==null||row.reservationId()==null||row.courtId()==null||row.startsAt()==null||row.endsAt()==null||!row.startsAt().isBefore(row.endsAt())||!java.util.Set.of("HOLD","BOOKED").contains(row.state())||!ids.add(row.reservationId()))throw new IllegalStateException("Invalid reservation impact");
    }
    public void release(Command command) {
        var response=call("release",command);
        if(response==null||!response.path("data").path("released").asBoolean())
            throw new IllegalStateException("Booking configuration release was not acknowledged");
    }
    private com.fasterxml.jackson.databind.JsonNode call(String action,Command command) {
        if(secret.length()<32)throw new IllegalStateException("Private service authentication is not configured");
        String path="/api/v1/internal/bookings/configuration/"+action;
        try {
            String body=json.writeValueAsString(command);long time=Instant.now().getEpochSecond();
            return client.post().uri(path).contentType(MediaType.APPLICATION_JSON)
                .header("X-Service-Time",Long.toString(time))
                .header("X-Service-Signature",ServiceCalls.sign(secret,"POST",path,time,body))
                .body(body).retrieve().body(com.fasterxml.jackson.databind.JsonNode.class);
        } catch(org.springframework.web.client.HttpClientErrorException.Conflict ex) {
            throw new ConflictException("Another configuration change is pending at this facility");
        } catch(Exception ex) {throw new IllegalStateException("Booking configuration guard is unavailable",ex);}
    }
}
