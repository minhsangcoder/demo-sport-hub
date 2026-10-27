package com.sporthub.payment.service;

import com.fasterxml.jackson.databind.*;
import com.sporthub.common.exception.ActionPolicyException;
import com.sporthub.common.money.BankProfileContract.*;
import com.sporthub.common.security.ServiceCalls;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.*;

/** Signed metadata only; no account numbers or external I/O within either service's DB fence. */
@Component
public class BankProfileClient {
  private final RestClient http;
  private final ObjectMapper json;
  private final String secret;

  public BankProfileClient(
      @Value("${BOOKING_SERVICE_URL:http://localhost:8084}") String url,
      @Value("${SERVICE_CALL_SECRET:}") String secret,
      ObjectMapper json) {
    var factory =
        new org.springframework.http.client.JdkClientHttpRequestFactory(
            java.net.http.HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build());
    factory.setReadTimeout(Duration.ofSeconds(4));
    http = RestClient.builder().baseUrl(url).requestFactory(factory).build();
    this.secret = secret;
    this.json = json;
  }

  public Status status(UUID principal, boolean existingHistory) {
    return call(
        "status",
        Map.of("principalId", principal, "existingHistory", existingHistory),
        Status.class);
  }

  public Decision activate(Activation command) {
    return call("activate", command, Decision.class);
  }

  public Status reconcile(LegacyHistory history) {
    return call("reconcile-legacy", history, Status.class);
  }

  private <T> T call(String action, Object input, Class<T> type) {
    if (secret.length() < 32) throw unavailable();
    String path = "/api/v1/internal/bookings/bank-profiles/" + action;
    try {
      String body = json.writeValueAsString(input);
      long time = Instant.now().getEpochSecond();
      var response =
          http.post()
              .uri(path)
              .contentType(MediaType.APPLICATION_JSON)
              .header("X-Service-Time", Long.toString(time))
              .header("X-Service-Signature", ServiceCalls.sign(secret, "POST", path, time, body))
              .body(body)
              .retrieve()
              .body(JsonNode.class);
      if (response == null || !response.path("data").isObject()) throw unavailable();
      return json.treeToValue(response.path("data"), type);
    } catch (RestClientResponseException ex) {
      if (Set.of(409, 503).contains(ex.getStatusCode().value())) {
        try {
          JsonNode error = json.readTree(ex.getResponseBodyAsString());
          String code = error.path("code").asText();
          if (Set.of(
                  "BANK_PROFILE_LOCKED",
                  "BANK_PROFILE_VERSION_CHANGED",
                  "BANK_PROFILE_RECONCILIATION_REQUIRED",
                  "BANK_PROFILE_COVERAGE_UNAVAILABLE")
              .contains(code))
            throw new ActionPolicyException(
                ex.getStatusCode().value(),
                code,
                error.path("message").asText(),
                error.path("details").isObject()
                    ? json.convertValue(error.path("details"), Map.class)
                    : Map.of());
        } catch (ActionPolicyException policy) {
          throw policy;
        } catch (Exception ignored) {
          throw unavailable();
        }
      }
      throw unavailable();
    } catch (ActionPolicyException ex) {
      throw ex;
    } catch (Exception ex) {
      throw unavailable();
    }
  }

  private ActionPolicyException unavailable() {
    return new ActionPolicyException(
        503,
        "BANK_PROFILE_AUTHORITY_UNAVAILABLE",
        "Chưa xác định được trạng thái tài khoản ngân hàng. Vui lòng thử lại cùng yêu cầu.",
        Map.of());
  }
}
