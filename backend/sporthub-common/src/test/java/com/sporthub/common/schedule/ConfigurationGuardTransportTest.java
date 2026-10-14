package com.sporthub.common.schedule;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import com.sporthub.common.security.ServiceCalls;
import com.sporthub.common.exception.ConflictException;
import org.junit.jupiter.api.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.assertj.core.api.Assertions.*;
import static com.sporthub.common.schedule.ConfigurationGuardProtocol.*;

class ConfigurationGuardTransportTest {
    private static final String SECRET="synthetic-service-secret-longer-than-thirty-two-characters";
    private final ObjectMapper json=new ObjectMapper().findAndRegisterModules();
    private final Command command=new Command(UUID.randomUUID(),UUID.randomUUID(),"schedule");
    private HttpServer server;
    private ConfigurationGuardTransport transport;
    private final AtomicBoolean signed=new AtomicBoolean();
    private final List<String> paths=new ArrayList<>();
    private volatile int responseStatus=200;
    private volatile String override;
    @BeforeEach void start()throws Exception {
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/api/v1/internal/bookings/configuration",exchange->{
            try {
                String body=new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8);
                String path=exchange.getRequestURI().getPath();paths.add(path);
                ServiceCalls.verify(SECRET,"POST",path,exchange.getRequestHeaders().getFirst("X-Service-Time"),body,exchange.getRequestHeaders().getFirst("X-Service-Signature"));
                signed.set(true);
                var received=json.readValue(body,Command.class);
                Object data=path.endsWith("prepare")?new Prepared(received.operationId(),List.of(new Reservation(UUID.randomUUID(),null,UUID.randomUUID(),Instant.parse("2027-01-01T01:00:00Z"),Instant.parse("2027-01-01T02:00:00Z"),"HOLD"))):Map.of("released",true);
                byte[] reply=(override==null?json.writeValueAsString(Map.of("data",data)):override).getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type","application/json");exchange.sendResponseHeaders(responseStatus,reply.length);exchange.getResponseBody().write(reply);
            } finally {exchange.close();}
        });
        server.start();transport=new ConfigurationGuardTransport("http://127.0.0.1:"+server.getAddress().getPort(),SECRET,json);
    }
    @AfterEach void stop(){server.stop(0);}
    @Test void contractSignsExactCommandAndReadsInstantReservations() {
        var prepared=transport.prepare(command);transport.release(command);
        assertThat(signed.get()).isTrue();assertThat(prepared.operationId()).isEqualTo(command.operationId());
        assertThat(prepared.reservations().getFirst().startsAt()).isEqualTo(Instant.parse("2027-01-01T01:00:00Z"));
        assertThat(paths).containsExactly("/api/v1/internal/bookings/configuration/prepare","/api/v1/internal/bookings/configuration/release");
    }
    @Test void malformedPrepareCannotBeTreatedAsAnEmptyImpactList() {
        override="{\"data\":{\"operationId\":\""+command.operationId()+"\"}}";
        assertThatThrownBy(()->transport.prepare(command)).isInstanceOf(IllegalStateException.class);
    }
    @Test void releaseNeedsPositiveAcknowledgment() {
        override="{\"data\":{\"released\":false}}";
        assertThatThrownBy(()->transport.release(command)).isInstanceOf(IllegalStateException.class);
    }
    @Test void busyFacilityIsAConflictAndMissingAuthenticationFailsClosed() {
        responseStatus=409;override="{\"code\":\"COMMON-002\"}";
        assertThatThrownBy(()->transport.prepare(command)).isInstanceOf(ConflictException.class);
        var unconfigured=new ConfigurationGuardTransport("http://127.0.0.1:"+server.getAddress().getPort(),"",json);
        assertThatThrownBy(()->unconfigured.prepare(command)).isInstanceOf(IllegalStateException.class);
    }
    @Test void impactReadSignsExactRequestAndDoesNotPrepareBarrier() {
        override="{\"data\":[]}";assertThat(transport.impact(command.facilityId())).isEmpty();assertThat(signed.get()).isTrue();assertThat(paths).containsExactly("/api/v1/internal/bookings/configuration/impact");
    }
    @Test void malformedImpactCannotSilentlyBecomeZeroBookings() {
        override="{\"data\":{}}";assertThatThrownBy(()->transport.impact(command.facilityId())).isInstanceOf(IllegalStateException.class);
    }
    @Test void incompleteReservationImpactFailsClosed() {
        override="{\"data\":[{\"state\":\"HOLD\"}]}";assertThatThrownBy(()->transport.impact(command.facilityId())).isInstanceOf(IllegalStateException.class);
    }
}
