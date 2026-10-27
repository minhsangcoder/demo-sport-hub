package com.sporthub.payment.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sporthub.common.dto.ApiResponse;
import com.sporthub.common.security.ServiceCalls;
import com.sporthub.payment.service.FacilityBankCreditFacts;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

@RestController
public class FacilityBankCreditFactsController {
  private final FacilityBankCreditFacts service;
  private final ObjectMapper json;
  private final String secret;

  public FacilityBankCreditFactsController(
      FacilityBankCreditFacts service,
      ObjectMapper json,
      @Value("${SERVICE_CALL_SECRET}") String secret) {
    this.service = service;
    this.json = json;
    this.secret = secret;
  }

  @PostMapping("/api/v1/payments/internal/facilities/{id}/bank-credits")
  public ApiResponse<FacilityBankCreditFacts.Page> read(
      @PathVariable UUID id, @RequestBody String body, HttpServletRequest request)
      throws Exception {
    ServiceCalls.verify(
        secret,
        request.getMethod(),
        request.getRequestURI(),
        request.getHeader("X-Service-Time"),
        body,
        request.getHeader("X-Service-Signature"));
    return ApiResponse.success(
        service.read(id, json.readValue(body, FacilityBankCreditFacts.Request.class)));
  }
}
