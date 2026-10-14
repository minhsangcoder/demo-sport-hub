package com.sporthub.identity.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.sporthub.common.exception.*;
import com.sporthub.identity.domain.Role;
import com.sporthub.identity.security.AuthenticatedUser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import java.time.Duration;
import java.util.UUID;

/** Facility remains authoritative for ownership; bounded calls fail closed. */
@Component
public class StaffFacilityAccess {
 private final RestClient facility;
 public StaffFacilityAccess(@Value("${FACILITY_SERVICE_URL:http://localhost:8082}") String url) {
  var factory=new org.springframework.http.client.JdkClientHttpRequestFactory(java.net.http.HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build());
  factory.setReadTimeout(Duration.ofSeconds(5)); facility=RestClient.builder().baseUrl(url).requestFactory(factory).build();
 }
 public void owner(UUID id,AuthenticatedUser actor,String token) {
  if(actor==null||!actor.roles().contains(Role.OWNER)||token==null||!token.startsWith("Bearer "))throw new ForbiddenException("Owner role required");
  try {
   var response=facility.get().uri("/api/v1/owner/facilities/"+id).header("Authorization",token).retrieve().body(JsonNode.class);
   var data=response==null?null:response.get("data");
   if(data==null||!id.toString().equals(data.path("id").asText())||!actor.userId().toString().equals(data.path("ownerId").asText()))throw new ForbiddenException("Facility is outside your ownership");
  }catch(org.springframework.web.client.HttpClientErrorException ex){throw new ForbiddenException("Facility is outside your ownership");}
  catch(org.springframework.web.client.RestClientException ex){throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,"Facility ownership could not be verified");}
 }
}
