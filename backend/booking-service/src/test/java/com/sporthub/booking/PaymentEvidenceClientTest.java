package com.sporthub.booking;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sporthub.booking.service.PaymentEvidenceClient;
import com.sporthub.common.money.PaymentProofFacts.*;
import com.sporthub.common.security.ServiceCalls;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;

class PaymentEvidenceClientTest {
    static final String SECRET="report-facts-synthetic-secret-over-thirty-two-characters";
    ObjectMapper json=new ObjectMapper().findAndRegisterModules();
    List<Binding> bindings(int size){var list=new ArrayList<Binding>();for(int i=0;i<size;i++)list.add(new Binding(UUID.randomUUID(),UUID.randomUUID(),null,"BOOKING"));return list;}
    @Test void fiveHundredAndOneFactsUseTwoSignedBatchesAndNeverOneRequestPerPayment()throws Exception{
        var sizes=new CopyOnWriteArrayList<Integer>();var input=bindings(501);var server=server(exchange->{
            String body=new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8);ServiceCalls.verify(SECRET,"POST",exchange.getRequestURI().getPath(),exchange.getRequestHeaders().getFirst("X-Service-Time"),body,exchange.getRequestHeaders().getFirst("X-Service-Signature"));
            var request=json.readValue(body,Request.class);sizes.add(request.bindings().size());var facts=request.bindings().stream().map(b->new Fact(b.paymentId(),b.bookingId(),UUID.randomUUID(),null,null,b.purpose(),"SUCCESS","120000.00","VND",Instant.parse("2026-10-21T00:00:00Z"),"DEMO","DEMO",true,true,false,"a".repeat(64),"null","VERIFIED_COLLECTION",List.of())).toList();
            respond(exchange,200,Map.of("data",new Response(Instant.now(),facts,List.of())));
        });
        try{var client=new PaymentEvidenceClient(url(server),SECRET,json,5000,3);var result=client.read(input);assertThat(result.facts()).hasSize(501);assertThat(result.problems()).isEmpty();assertThat(result.batchCount()).isEqualTo(2);assertThat(sizes).containsExactlyInAnyOrder(500,1);}finally{server.stop(0);}
    }
    @Test void networkFailureAndDeadlineStayUnavailableAndTransactionReadsMakeNoRequest()throws Exception{
        var calls=new AtomicInteger();var server=server(exchange->{calls.incrementAndGet();respond(exchange,503,Map.of("data",Map.of()));});
        try{
            var client=new PaymentEvidenceClient(url(server),SECRET,json,500,1);var result=client.read(bindings(2));assertThat(result.facts()).isEmpty();assertThat(result.problems()).hasSize(2);assertThat(result.reasons()).contains("PAYMENT_FACTS_UNAVAILABLE");
            int before=calls.get();TransactionSynchronizationManager.setActualTransactionActive(true);
            try{assertThatThrownBy(()->client.read(bindings(1))).isInstanceOf(IllegalStateException.class);}finally{TransactionSynchronizationManager.setActualTransactionActive(false);}
            assertThat(calls.get()).isEqualTo(before);
        }finally{server.stop(0);}
        var slow=server(exchange->{try{Thread.sleep(500);}catch(InterruptedException ex){Thread.currentThread().interrupt();}respond(exchange,200,Map.of("data",Map.of()));});
        try{var result=new PaymentEvidenceClient(url(slow),SECRET,json,100,1).read(bindings(3));assertThat(result.facts()).isEmpty();assertThat(result.problems()).hasSize(3);}finally{slow.stop(0);}
    }
    private HttpServer server(com.sun.net.httpserver.HttpHandler handler)throws Exception{
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);server.createContext("/api/v1/internal/payments/report-facts",handler);server.start();return server;
    }
    private String url(HttpServer server){return "http://127.0.0.1:"+server.getAddress().getPort();}
    private void respond(com.sun.net.httpserver.HttpExchange exchange,int status,Object body)throws java.io.IOException{byte[] bytes=json.writeValueAsBytes(body);exchange.getResponseHeaders().set("Content-Type","application/json");exchange.sendResponseHeaders(status,bytes.length);try(var output=exchange.getResponseBody()){output.write(bytes);}}
}
