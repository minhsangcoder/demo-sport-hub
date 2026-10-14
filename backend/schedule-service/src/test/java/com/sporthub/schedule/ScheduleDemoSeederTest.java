package com.sporthub.schedule;

import com.sun.net.httpserver.HttpServer;
import com.sporthub.schedule.service.*;
import com.sporthub.schedule.repository.ScheduleRepository;
import org.junit.jupiter.api.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ScheduleDemoSeederTest {
    private HttpServer server;
    private final UUID facility=UUID.randomUUID(),owner=UUID.randomUUID();
    private final ScheduleService service=mock(ScheduleService.class);
    private final ScheduleRepository repo=mock(ScheduleRepository.class);
    private final PlatformTransactionManager manager=mock(PlatformTransactionManager.class);
    private ScheduleDemoSeeder seeder;
    @BeforeEach void start()throws Exception {
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/",request->{
            request.getRequestBody().readAllBytes();
            String path=request.getRequestURI().getPath();
            String body=path.endsWith("login")?"{\"data\":{\"accessToken\":\"synthetic\",\"user\":{\"id\":\""+owner+"\"}}}":
                path.endsWith("facilities")?"{\"data\":[{\"id\":\""+facility+"\",\"status\":\"ACTIVE\"}]}":"{}";
            byte[] bytes=body.getBytes(StandardCharsets.UTF_8);request.getResponseHeaders().set("Content-Type","application/json");request.sendResponseHeaders(200,bytes.length);request.getResponseBody().write(bytes);request.close();
        });server.start();
        when(manager.getTransaction(any())).thenAnswer(a->new SimpleTransactionStatus());
        String url="http://127.0.0.1:"+server.getAddress().getPort();
        seeder=new ScheduleDemoSeeder(service,repo,url,url,"synthetic-password",manager);
    }
    @AfterEach void stop(){server.stop(0);}
    @Test void unavailableBookingDefersStartupAndRetriesWithoutBypassingGuard() {
        doThrow(new IllegalStateException("Booking unavailable")).doReturn(java.util.List.of()).when(service).replaceHours(eq(facility),any(),any(),anyString());
        assertThatCode(()->seeder.run(null)).doesNotThrowAnyException();
        verify(manager).rollback(any());verify(service,never()).addPrice(any(),any(),any(),anyString());
        seeder.retry();seeder.retry();
        verify(service,times(2)).replaceHours(eq(facility),any(),any(),anyString());
        verify(service,times(7)).addPrice(eq(facility),any(),any(),anyString());verify(manager).commit(any());
    }
    @Test void pricingFailureRollsBackWholeDemoSetupAndRemainsRetryable() {
        doThrow(new IllegalStateException("Pricing failed")).doReturn(null).when(service).addPrice(eq(facility),any(),any(),anyString());
        assertThatCode(()->seeder.run(null)).doesNotThrowAnyException();verify(manager).rollback(any());
        seeder.retry();verify(manager).commit(any());verify(service,times(2)).replaceHours(eq(facility),any(),any(),anyString());
    }
}
