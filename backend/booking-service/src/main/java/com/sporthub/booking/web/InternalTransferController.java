package com.sporthub.booking.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sporthub.booking.service.TransferLeaseService;
import com.sporthub.common.dto.ApiResponse;
import com.sporthub.common.security.ServiceCalls;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/internal/bookings")
public class InternalTransferController {
  private final TransferLeaseService service;
  private final ObjectMapper json;
  private final String secret;

  public InternalTransferController(
      TransferLeaseService service,
      ObjectMapper json,
      @Value("${SERVICE_CALL_SECRET}") String secret) {
    this.service = service;
    this.json = json;
    this.secret = secret;
  }

  @GetMapping("/{id}")
  public ApiResponse<com.fasterxml.jackson.databind.JsonNode> inspect(
      @PathVariable UUID id, HttpServletRequest request) {
    verify(request, "");
    return ApiResponse.success(service.inspect(id));
  }

  @PostMapping("/market-admission/{principal}")
  public ApiResponse<com.fasterxml.jackson.databind.JsonNode> admission(
      @PathVariable UUID principal, @RequestBody String body, HttpServletRequest request) {
    verify(request, body);
    return ApiResponse.success(service.marketAdmission(principal));
  }

  @GetMapping("/transfer-leases/{listingId}")
  public ApiResponse<java.util.Map<String, Object>> receipt(
      @PathVariable UUID listingId, HttpServletRequest request) {
    verify(request, "");
    return ApiResponse.success(service.receipt(listingId));
  }

  @GetMapping("/transfer-acquisitions/{id}/decision")
  public ApiResponse<java.util.Map<String, Object>> decision(
      @PathVariable UUID id, HttpServletRequest r) {
    verify(r, "");
    return ApiResponse.success(service.decision(id));
  }

  @PostMapping("/transfer-acquisitions/{id}/abort")
  public ApiResponse<java.util.Map<String, Object>> abort(
      @PathVariable UUID id, @RequestBody String body, HttpServletRequest r) throws Exception {
    verify(r, body);
    var value = json.readTree(body);
    var command = json.treeToValue(value.path("command"), TransferLeaseService.Command.class);
    if (command == null || !id.equals(command.acquisitionId()))
      throw new IllegalArgumentException("Abort path and acquisition differ");
    return ApiResponse.success(service.abort(command, value.path("reason").asText()));
  }

  @PostMapping("/transfer/{action}")
  public ApiResponse<BookingDtos.Booking> command(
      @PathVariable String action, @RequestBody String body, HttpServletRequest request)
      throws Exception {
    verify(request, body);
    return ApiResponse.success(
        service.command(action, json.readValue(body, TransferLeaseService.Command.class)));
  }

  private void verify(HttpServletRequest r, String body) {
    ServiceCalls.verify(
        secret,
        r.getMethod(),
        r.getRequestURI(),
        r.getHeader("X-Service-Time"),
        body,
        r.getHeader("X-Service-Signature"));
  }
}
