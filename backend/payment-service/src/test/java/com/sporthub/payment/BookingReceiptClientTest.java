package com.sporthub.payment;
import static org.assertj.core.api.Assertions.*;
import com.sporthub.payment.service.BookingReceiptClient;
import com.sporthub.common.security.ServiceCalls;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
class BookingReceiptClientTest {
    @Test void readOnlyReceiptTransportSignsExactPathAndClassifiesUnavailableVsMissing()throws Exception{
        String secret="synthetic-private-key-over-thirty-two-characters";UUID id=UUID.randomUUID();var signed=new AtomicBoolean();var status=new java.util.concurrent.atomic.AtomicInteger(200);
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/api/v1/internal/bookings/payments/",exchange->{
            try{ServiceCalls.verify(secret,exchange.getRequestMethod(),exchange.getRequestURI().getPath(),exchange.getRequestHeaders().getFirst("X-Service-Time"),"",exchange.getRequestHeaders().getFirst("X-Service-Signature"));signed.set(exchange.getRequestMethod().equals("GET")&&exchange.getRequestURI().getPath().endsWith(id.toString()));
                byte[] body=("{\"data\":{\"paymentId\":\""+id+"\",\"decision\":\"APPLIED\"}}").getBytes(StandardCharsets.UTF_8);exchange.getResponseHeaders().set("Content-Type","application/json");exchange.sendResponseHeaders(status.get(),body.length);exchange.getResponseBody().write(body);
            }finally{exchange.close();}
        });server.start();try{var client=new BookingReceiptClient("http://127.0.0.1:"+server.getAddress().getPort(),secret);assertThat(client.receipt(id).path("decision").asText()).isEqualTo("APPLIED");assertThat(signed.get()).isTrue();status.set(404);assertThatThrownBy(()->client.receipt(id)).isInstanceOf(BookingReceiptClient.Missing.class);status.set(503);assertThatThrownBy(()->client.receipt(id)).isInstanceOf(BookingReceiptClient.Unavailable.class);}finally{server.stop(0);}
    }
}
