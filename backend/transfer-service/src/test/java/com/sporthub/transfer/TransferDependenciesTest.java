package com.sporthub.transfer;
import static org.assertj.core.api.Assertions.*;
import com.sporthub.transfer.service.TransferDependencies;
import com.sporthub.common.security.ServiceCalls;
import com.sporthub.common.exception.ConflictException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

class TransferDependenciesTest {
 private final String secret="synthetic-transfer-client-service-secret-over-thirty-two-characters";
 @Test void receiptLookupUsesExactSignedPathAndReadsActualResponse()throws Exception{
  var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);var signature=new AtomicReference<String>();var time=new AtomicReference<String>();UUID id=UUID.randomUUID();String path="/api/v1/internal/bookings/transfer-leases/"+id;
  server.createContext(path,e->{signature.set(e.getRequestHeaders().getFirst("X-Service-Signature"));time.set(e.getRequestHeaders().getFirst("X-Service-Time"));byte[] body="{\"data\":{\"state\":\"COMPLETED\",\"commandFingerprint\":\"proof\"}}".getBytes(StandardCharsets.UTF_8);e.getResponseHeaders().set("Content-Type","application/json");e.sendResponseHeaders(200,body.length);e.getResponseBody().write(body);e.close();});server.start();
  try{String url="http://127.0.0.1:"+server.getAddress().getPort();var deps=new TransferDependencies(url,url,secret,new ObjectMapper());assertThat(deps.receipt(id).path("state").asText()).isEqualTo("COMPLETED");assertThat(signature.get()).isEqualTo(ServiceCalls.sign(secret,"GET",path,Long.parseLong(time.get()),""));}finally{server.stop(0);}
 }
 @Test void authenticationFailureIsTechnicalAndRemoteErrorBodyIsNeverExposed()throws Exception{
  withStatus(403,"private remote token must not leak",deps->assertThatThrownBy(()->deps.command("complete",Map.of())).isInstanceOf(TransferDependencies.Unavailable.class).hasMessageContaining("BOOKING_AUTHENTICATION_ERROR").hasMessageNotContaining("private remote token"));
 }
 @Test void onlyBusinessConflictBecomesRejectedHandoff()throws Exception{
  withStatus(409,"private seller phone must not leak",deps->assertThatThrownBy(()->deps.command("complete",Map.of())).isInstanceOf(ConflictException.class).hasMessageNotContaining("private seller phone"));
 }
 private interface Check{void run(TransferDependencies deps)throws Exception;}
 private void withStatus(int code,String text,Check check)throws Exception{
  var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);server.createContext("/",e->{byte[] body=text.getBytes(StandardCharsets.UTF_8);e.sendResponseHeaders(code,body.length);e.getResponseBody().write(body);e.close();});server.start();
  try{String url="http://127.0.0.1:"+server.getAddress().getPort();check.run(new TransferDependencies(url,url,secret,new ObjectMapper()));}finally{server.stop(0);}
 }
}
