package com.sporthub.booking.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.sporthub.common.exception.ActionPolicyException;
import com.sporthub.common.security.ServiceCalls;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** Evidence metadata is loaded before taking user/court fences; images stay in Transfer storage. */
@Component
public class PeerCaseEvidenceClient {
  private final RestClient client;
  private final String secret;

  public PeerCaseEvidenceClient(
      @Value("${TRANSFER_SERVICE_URL:http://localhost:8086}") String url,
      @Value("${SERVICE_CALL_SECRET}") String secret) {
    var factory =
        new org.springframework.http.client.JdkClientHttpRequestFactory(
            java.net.http.HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build());
    factory.setReadTimeout(Duration.ofSeconds(5));
    client = RestClient.builder().baseUrl(url).requestFactory(factory).build();
    this.secret = secret;
  }

  public Set<UUID> ready(UUID acquisition, List<UUID> requested) {
    if (requested == null
        || requested.size() > 20
        || requested.stream().anyMatch(Objects::isNull)
        || requested.stream().distinct().count() != requested.size())
      throw new IllegalArgumentException("Danh sách chứng cứ cần đối chiếu không hợp lệ.");
    if (requested.isEmpty()) return Set.of();
    String path = "/api/v1/transfers/internal/peer/" + acquisition + "/evidence-references";
    String body =
        "{\"ids\":["
            + requested.stream()
                .map(id -> "\"" + id + "\"")
                .collect(java.util.stream.Collectors.joining(","))
            + "]}";
    long now = Instant.now().getEpochSecond();
    try {
      var response =
          client
              .post()
              .uri(path)
              .header("X-Service-Time", Long.toString(now))
              .header("X-Service-Signature", ServiceCalls.sign(secret, "POST", path, now, body))
              .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
              .body(body)
              .retrieve()
              .body(JsonNode.class);
      if (response == null || !response.path("data").isArray()) throw new IllegalStateException();
      var result = new HashSet<UUID>();
      for (var item : response.path("data")) {
        if (!acquisition.toString().equals(item.path("acquisitionId").asText())
            || !"READY".equals(item.path("state").asText())) throw new IllegalStateException();
        result.add(UUID.fromString(item.path("id").asText()));
      }
      if (!new HashSet<>(requested).containsAll(result)) throw new IllegalStateException();
      return Set.copyOf(result);
    } catch (org.springframework.web.client.RestClientException
        | IllegalArgumentException
        | IllegalStateException failure) {
      throw new ActionPolicyException(
          503,
          "PEER_EVIDENCE_UNAVAILABLE",
          "Chưa tải đủ chứng cứ của vụ việc. Vui lòng thử lại trước khi phân xử.",
          Map.of());
    }
  }
}
