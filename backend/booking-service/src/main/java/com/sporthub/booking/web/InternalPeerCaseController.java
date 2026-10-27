package com.sporthub.booking.web;

import com.fasterxml.jackson.databind.*;
import com.sporthub.booking.service.*;
import com.sporthub.common.dto.ApiResponse;
import com.sporthub.common.money.PeerResaleContract;
import com.sporthub.common.security.*;
import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/internal/bookings/peer/facilities/{facility}/cases")
public class InternalPeerCaseController {
  private final PeerCaseReads cases;
  private final PeerUsageAdjudication adjudication;
  private final RemoteIdentity identity;
  private final ObjectMapper json;
  private final String secret;

  public InternalPeerCaseController(
      PeerCaseReads cases,
      PeerUsageAdjudication adjudication,
      RemoteIdentity identity,
      ObjectMapper json,
      @Value("${SERVICE_CALL_SECRET:}") String secret) {
    this.cases = cases;
    this.adjudication = adjudication;
    this.identity = identity;
    this.json = json;
    this.secret = secret;
  }

  @GetMapping
  public ApiResponse<List<JsonNode>> list(
      @PathVariable UUID facility,
      @RequestParam(required = false) UUID after,
      @RequestParam(defaultValue = "50") int size,
      HttpServletRequest request) {
    verify(request, "");
    return ApiResponse.success(
        cases.list(
            facility, after, size, identity.current(request), request.getHeader("Authorization")));
  }

  @GetMapping("/presence-context")
  public ApiResponse<List<Map<String, Object>>> presenceContext(
      @PathVariable UUID facility, HttpServletRequest request) {
    verify(request, "");
    return ApiResponse.success(
        cases.presenceContext(
            facility, identity.current(request), request.getHeader("Authorization")));
  }

  @GetMapping("/{id}")
  public ApiResponse<JsonNode> detail(
      @PathVariable UUID facility, @PathVariable UUID id, HttpServletRequest request) {
    verify(request, "");
    return ApiResponse.success(
        cases.detail(id, facility, identity.current(request), request.getHeader("Authorization")));
  }

  @PostMapping("/{id}/{action}")
  public ApiResponse<JsonNode> act(
      @PathVariable UUID facility,
      @PathVariable UUID id,
      @PathVariable String action,
      @RequestBody String body,
      HttpServletRequest request)
      throws Exception {
    verify(request, body);
    cases.requireFacility(id, facility);
    var actor = identity.current(request);
    var value = json.readTree(body);
    String key = value.path("idempotencyKey").asText();
    String token = request.getHeader("Authorization");
    return ApiResponse.success(
        switch (action) {
          case "presence" ->
              adjudication.presence(
                  id,
                  json.treeToValue(value.path("command"), PeerResaleContract.Presence.class),
                  key,
                  actor,
                  token);
          case "resolve" ->
              adjudication.resolve(
                  id,
                  json.treeToValue(value.path("command"), PeerResaleContract.Resolution.class),
                  key,
                  actor,
                  token);
          case "fraud-correction" ->
              adjudication.correctFraud(
                  id,
                  json.treeToValue(value.path("command"), PeerResaleContract.FraudCorrection.class),
                  key,
                  actor,
                  token);
          default -> throw new IllegalArgumentException("Lệnh xử lý vụ việc không hợp lệ.");
        });
  }

  private void verify(HttpServletRequest request, String body) {
    ServiceCalls.verify(
        secret,
        request.getMethod(),
        request.getRequestURI(),
        request.getHeader("X-Service-Time"),
        body,
        request.getHeader("X-Service-Signature"));
  }
}
