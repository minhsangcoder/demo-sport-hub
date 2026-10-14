package com.sporthub.booking;
import com.sporthub.booking.service.ComplaintReferences;
import com.sporthub.common.exception.*;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

class ComplaintReferencesTest {
 @Test void authenticatedOriginalReferencesUseExactMoneyAndRejectMismatchedEnvelope()throws Exception{
  UUID payment=UUID.randomUUID(),listing=UUID.randomUUID(),account=UUID.randomUUID();var seen=new AtomicReference<String>();var response=new AtomicReference<>("{\"data\":{\"payment\":{\"id\":\""+payment+"\",\"amount\":9999999999.25}}}");var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);server.createContext("/",x->{seen.set(x.getRequestHeaders().getFirst("Authorization"));var body=response.get().getBytes(StandardCharsets.UTF_8);x.getResponseHeaders().add("Content-Type","application/json");x.sendResponseHeaders(200,body.length);x.getResponseBody().write(body);x.close();});server.start();try{String url="http://127.0.0.1:"+server.getAddress().getPort();var client=new ComplaintReferences(url,url,url);assertThat(client.payment(payment,"Bearer live-admin-token").path("amount").decimalValue()).isEqualByComparingTo("9999999999.25");assertThat(seen.get()).isEqualTo("Bearer live-admin-token");response.set("{\"data\":{\"listingId\":\""+listing+"\"}}");assertThat(client.transfer(listing,null,"Bearer token").path("listingId").asText()).isEqualTo(listing.toString());response.set("{\"data\":{\"account\":{\"id\":\""+account+"\"}}}");assertThatCode(()->client.account(account,"Bearer token")).doesNotThrowAnyException();assertThatThrownBy(()->client.payment(payment,"Bearer token")).isInstanceOf(org.springframework.web.server.ResponseStatusException.class).hasMessageContaining("503");response.set("{\"data\":[]}");assertThatThrownBy(()->client.transfer(listing,null,"Bearer token")).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);}finally{server.stop(0);}
 }
 @Test void unauthorizedMissingAndUnavailableSourcesDoNotDiscloseRemoteErrorBody()throws Exception{var code=new java.util.concurrent.atomic.AtomicInteger(403);var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);server.createContext("/",x->{var body="private upstream token/PII".getBytes(StandardCharsets.UTF_8);x.sendResponseHeaders(code.get(),body.length);x.getResponseBody().write(body);x.close();});server.start();try{String url="http://127.0.0.1:"+server.getAddress().getPort();var client=new ComplaintReferences(url,url,url);UUID id=UUID.randomUUID();assertThatThrownBy(()->client.payment(id,"Bearer token")).isInstanceOf(ForbiddenException.class).hasMessageNotContaining("PII");code.set(404);assertThatThrownBy(()->client.payment(id,"Bearer token")).isInstanceOf(ResourceNotFoundException.class);code.set(500);assertThatThrownBy(()->client.payment(id,"Bearer token")).isInstanceOf(org.springframework.web.server.ResponseStatusException.class).hasMessageNotContaining("PII");}finally{server.stop(0);}}
}
