package com.sporthub.payment.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sporthub.common.dto.ApiResponse;
import com.sporthub.common.security.ServiceCalls;
import com.sporthub.payment.service.*;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/payments/internal")
public class InternalBankController {
  public record DestinationRequest(UUID revisionId, UUID principalId, UUID guestBookingId) {}

  private final BankDestinations destinations;
  private final PayosChannels channels;
  private final ObjectMapper json;
  private final String secret;

  public InternalBankController(
      BankDestinations destinations,
      PayosChannels channels,
      ObjectMapper json,
      @Value("${SERVICE_CALL_SECRET:}") String secret) {
    this.destinations = destinations;
    this.channels = channels;
    this.json = json;
    this.secret = secret;
  }

  @GetMapping("/facilities/{id}/receiver")
  public ApiResponse<PayosChannels.Channel> receiver(
      @PathVariable UUID id, HttpServletRequest request) {
    verify(request, "");
    return ApiResponse.success(channels.ready(id));
  }

  @PostMapping("/bank-destinations/verify")
  public ApiResponse<BankDestinations.Destination> verifyDestination(
      @RequestBody String body, HttpServletRequest request) {
    verify(request, body);
    var input = parse(body);
    return ApiResponse.success(
        destinations.owned(input.revisionId(), input.principalId(), input.guestBookingId()));
  }

  @PostMapping("/bank-destinations/resolve")
  public ApiResponse<BankDestinations.PrivateDestination> resolveDestination(
      @RequestBody String body, HttpServletRequest request) {
    verify(request, body);
    var input = parse(body);
    return ApiResponse.success(
        destinations.privateDestination(
            input.revisionId(), input.principalId(), input.guestBookingId()));
  }

  private DestinationRequest parse(String body) {
    try {
      var input = json.readValue(body, DestinationRequest.class);
      if (input.revisionId() == null
          || (input.principalId() == null) == (input.guestBookingId() == null))
        throw new IllegalArgumentException();
      return input;
    } catch (Exception ex) {
      throw new IllegalArgumentException("Phạm vi tài khoản nhận tiền không hợp lệ.");
    }
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
