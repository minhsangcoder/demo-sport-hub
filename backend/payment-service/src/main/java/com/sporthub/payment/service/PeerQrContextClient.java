package com.sporthub.payment.service;

import com.fasterxml.jackson.databind.*;
import com.sporthub.common.exception.*;
import com.sporthub.common.security.ServiceCalls;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.*;

@Component
public class PeerQrContextClient {
  private final RestClient http;
  private final ObjectMapper json;
  private final String secret;

  public PeerQrContextClient(
      @Value("${BOOKING_SERVICE_URL:http://localhost:8084}") String url,
      @Value("${SERVICE_CALL_SECRET:}") String secret,
      ObjectMapper json) {
    var factory =
        new org.springframework.http.client.JdkClientHttpRequestFactory(
            java.net.http.HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build());
    factory.setReadTimeout(Duration.ofSeconds(4));
    http = RestClient.builder().baseUrl(url).requestFactory(factory).build();
    this.json = json;
    this.secret = secret;
  }

  public JsonNode instruction(UUID id, UUID actor) {
    String path = "/api/v1/internal/bookings/peer/acquisitions/" + id + "/instruction";
    String body = json.createObjectNode().put("actorId", actor.toString()).toString();
    return call("POST", path, body, null);
  }

  public JsonNode caseAccess(UUID id, String token) {
    return call(
        "GET", "/api/v1/internal/bookings/peer/acquisitions/" + id + "/case-access", "", token);
  }

  private JsonNode call(String method, String path, String body, String token) {
    if (secret.length() < 32) throw unavailable();
    long time = Instant.now().getEpochSecond();
    try {
      var request =
          http.method(HttpMethod.valueOf(method))
              .uri(path)
              .contentType(MediaType.APPLICATION_JSON)
              .header("X-Service-Time", Long.toString(time))
              .header("X-Service-Signature", ServiceCalls.sign(secret, method, path, time, body));
      if (token != null) request.header("Authorization", token);
      if (!body.isEmpty()) request.body(body);
      var value = request.retrieve().body(JsonNode.class);
      if (value == null || !value.path("data").isObject()) throw unavailable();
      return value.path("data");
    } catch (RestClientResponseException ex) {
      if (ex.getStatusCode().value() == 403)
        throw new ForbiddenException("Bạn không thuộc phiên mua này.");
      if (ex.getStatusCode().value() == 404)
        throw new ResourceNotFoundException("Không tìm thấy phiên mua.");
      if (ex.getStatusCode().value() == 409)
        throw new ConflictException("Phiên không còn nhận thanh toán. Không chuyển theo mã QR cũ.");
      throw unavailable();
    } catch (RestClientException ex) {
      throw unavailable();
    }
  }

  static ActionPolicyException unavailable() {
    return new ActionPolicyException(
        503,
        "PEER_QR_CONTEXT_UNAVAILABLE",
        "Chưa kiểm tra được phiên thanh toán. Vui lòng thử lại trong cùng phiên.",
        Map.of());
  }
}
