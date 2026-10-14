package com.sporthub.booking.service;
import com.fasterxml.jackson.databind.JsonNode;
import com.sporthub.common.exception.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import java.time.Duration;
import java.util.UUID;
@Component
public class ComplaintReferences {
 private final RestClient payment,identity,transfer;
 public ComplaintReferences(@Value("${PAYMENT_SERVICE_URL:http://localhost:8085}") String paymentUrl,@Value("${IDENTITY_SERVICE_URL:http://localhost:8081}") String identityUrl,@Value("${TRANSFER_SERVICE_URL:http://localhost:8086}") String transferUrl){var factory=new org.springframework.http.client.JdkClientHttpRequestFactory(java.net.http.HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build());factory.setReadTimeout(Duration.ofSeconds(5));payment=com.sporthub.common.money.ExactDecimalJson.restClient(RestClient.builder()).baseUrl(paymentUrl).requestFactory(factory).build();identity=RestClient.builder().baseUrl(identityUrl).requestFactory(factory).build();transfer=com.sporthub.common.money.ExactDecimalJson.restClient(RestClient.builder()).baseUrl(transferUrl).requestFactory(factory).build();}
 public JsonNode payment(UUID id,String auth){var result=read(payment,"/api/v1/admin/payments/"+id,auth).path("payment");if(!id.toString().equals(result.path("id").asText()))throw unavailable();return result;}
 public JsonNode transfer(UUID id,UUID acquisition,String auth){String path="/api/v1/transfers/admin/listings/"+id+"/reference"+(acquisition==null?"":"?acquisitionId="+acquisition);var data=read(transfer,path,auth);if(!id.toString().equals(data.path("listingId").asText()))throw unavailable();return data;}
 public void account(UUID id,String auth){var result=read(identity,"/api/v1/admin/accounts/"+id,auth).path("account");if(!id.toString().equals(result.path("id").asText()))throw unavailable();}
 private JsonNode read(RestClient client,String path,String auth){try{var result=client.get().uri(path).header("Authorization",auth).retrieve().body(JsonNode.class);if(result==null||!result.path("data").isObject())throw unavailable();return result.path("data");}catch(org.springframework.web.client.HttpClientErrorException ex){if(ex.getStatusCode().value()==404)throw new ResourceNotFoundException("Linked reference not found");throw new ForbiddenException("Linked reference access is not authorized");}catch(org.springframework.web.client.RestClientException ex){throw unavailable();}}
 private org.springframework.web.server.ResponseStatusException unavailable(){return new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,"Complaint references could not be verified");}
}
