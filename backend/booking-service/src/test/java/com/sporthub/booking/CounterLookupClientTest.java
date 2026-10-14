package com.sporthub.booking;
import com.sporthub.booking.service.BookingDependencies;
import com.sporthub.common.exception.ForbiddenException;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
class CounterLookupClientTest {
 private HttpServer server;private BookingDependencies client;private String body;private int status;private UUID facility;
 @BeforeEach void start()throws Exception{facility=UUID.randomUUID();status=200;server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);server.createContext("/api/v1/users/counter-customer-lookup",e->{assertThat(e.getRequestMethod()).isEqualTo("POST");assertThat(e.getRequestHeaders().getFirst("Authorization")).isEqualTo("Bearer synthetic");String input=new String(e.getRequestBody().readAllBytes(),StandardCharsets.UTF_8);assertThat(input).contains(facility.toString(),"0901234567");byte[] b=body.getBytes(StandardCharsets.UTF_8);e.getResponseHeaders().add("Content-Type","application/json");e.sendResponseHeaders(status,b.length);e.getResponseBody().write(b);e.close();});server.start();String url="http://127.0.0.1:"+server.getAddress().getPort();client=new BookingDependencies(url,url,url);}
 @AfterEach void stop(){server.stop(0);}
 @Test void verifiedMatchAndExplicitNoMatchHaveDistinctSafeMeanings(){UUID customer=UUID.randomUUID();body="{\"data\":{\"customerId\":\""+customer+"\"}}";assertThat(client.counterCustomer(facility,"0901234567","Bearer synthetic")).isEqualTo(customer);body="{\"data\":{\"customerId\":null}}";assertThat(client.counterCustomer(facility,"0901234567","Bearer synthetic")).isNull();}
 @Test void malformedUnauthorizedAndUnavailableNeverFallBackToWalkIn(){for(String value:new String[]{"{}","{\"data\":{}}","{\"data\":{\"customerId\":\"invalid\"}}"}){body=value;assertThatThrownBy(()->client.counterCustomer(facility,"0901234567","Bearer synthetic")).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);}status=403;body="{\"token\":\"secret-remote-response\"}";assertThatThrownBy(()->client.counterCustomer(facility,"0901234567","Bearer synthetic")).isInstanceOf(ForbiddenException.class).hasMessageNotContaining("secret-remote-response");status=500;assertThatThrownBy(()->client.counterCustomer(facility,"0901234567","Bearer synthetic")).isInstanceOf(org.springframework.web.server.ResponseStatusException.class).hasMessageNotContaining("secret-remote-response");}
}
