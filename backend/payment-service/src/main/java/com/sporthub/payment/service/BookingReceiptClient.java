package com.sporthub.payment.service;
import com.fasterxml.jackson.databind.JsonNode;
import com.sporthub.common.security.ServiceCalls;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import java.time.*;
import java.util.UUID;
@Component
public class BookingReceiptClient {
    public static class Missing extends RuntimeException{}
    public static class Unavailable extends RuntimeException{}
    private final RestClient client;private final String secret;
    public BookingReceiptClient(@Value("${BOOKING_SERVICE_URL:http://localhost:8084}") String url,@Value("${SERVICE_CALL_SECRET}") String secret){
        this.secret=secret;var factory=new org.springframework.http.client.JdkClientHttpRequestFactory(java.net.http.HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build());factory.setReadTimeout(Duration.ofSeconds(3));
        client=com.sporthub.common.money.ExactDecimalJson.restClient(RestClient.builder()).baseUrl(url).requestFactory(factory).build();
    }
    public JsonNode receipt(UUID payment){
        String path="/api/v1/internal/bookings/payments/"+payment;long time=Instant.now().getEpochSecond();
        try{var body=client.get().uri(path).header("X-Service-Time",Long.toString(time)).header("X-Service-Signature",ServiceCalls.sign(secret,"GET",path,time,"" )).retrieve().body(JsonNode.class);
            if(body==null||!body.path("data").isObject())throw new Unavailable();return body.path("data");
        }catch(org.springframework.web.client.HttpClientErrorException ex){if(ex.getStatusCode().value()==404)throw new Missing();throw new Unavailable();}
        catch(org.springframework.web.client.RestClientException ex){throw new Unavailable();}
    }
}
