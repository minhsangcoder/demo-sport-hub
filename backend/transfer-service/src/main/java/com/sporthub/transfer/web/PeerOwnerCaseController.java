package com.sporthub.transfer.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.sporthub.common.dto.ApiResponse;
import com.sporthub.common.exception.ForbiddenException;
import com.sporthub.common.money.PeerResaleContract.*;
import com.sporthub.common.security.RemoteIdentity;
import com.sporthub.transfer.service.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/transfers/facilities/{facilityId}/cases")
public class PeerOwnerCaseController {
  private final PeerBookingClient booking;
  private final RemoteIdentity identity;

  public PeerOwnerCaseController(PeerBookingClient booking, RemoteIdentity identity) {
    this.booking = booking;
    this.identity = identity;
  }

  @GetMapping
  public ApiResponse<JsonNode> list(
      @PathVariable UUID facilityId,
      @RequestParam(required = false) UUID after,
      @RequestParam(defaultValue = "50") int size,
      HttpServletRequest request) {
    identity.current(request).requireRole("OWNER");
    if (size < 1 || size > 100)
      throw new IllegalArgumentException("Chỉ tải tối đa 100 vụ việc mỗi lần.");
    return ApiResponse.success(
        booking.cases(facilityId, after, size, request.getHeader("Authorization")));
  }

  @GetMapping("/presence-context")
  public ApiResponse<JsonNode> presenceContext(
      @PathVariable UUID facilityId, HttpServletRequest request) {
    operator(request);
    return ApiResponse.success(
        booking.presenceContext(facilityId, request.getHeader("Authorization")));
  }

  @GetMapping("/{id}")
  public ApiResponse<JsonNode> detail(
      @PathVariable UUID facilityId, @PathVariable UUID id, HttpServletRequest request) {
    identity.current(request).requireRole("OWNER");
    return ApiResponse.success(
        booking.caseDetail(facilityId, id, request.getHeader("Authorization")));
  }

  @PostMapping("/{id}/presence")
  public ApiResponse<JsonNode> presence(
      @PathVariable UUID facilityId,
      @PathVariable UUID id,
      @Valid @RequestBody Presence input,
      @RequestHeader("Idempotency-Key") String key,
      HttpServletRequest request) {
    operator(request);
    PeerTransferListings.checkKey(key);
    return ApiResponse.success(
        booking.caseCommand(
            facilityId, id, "presence", input, key, request.getHeader("Authorization")));
  }

  @PostMapping("/{id}/resolve")
  public ApiResponse<JsonNode> resolve(
      @PathVariable UUID facilityId,
      @PathVariable UUID id,
      @Valid @RequestBody Resolution input,
      @RequestHeader("Idempotency-Key") String key,
      HttpServletRequest request) {
    identity.current(request).requireRole("OWNER");
    PeerTransferListings.checkKey(key);
    return ApiResponse.success(
        booking.caseCommand(
            facilityId, id, "resolve", input, key, request.getHeader("Authorization")));
  }

  @PostMapping("/{id}/fraud-correction")
  public ApiResponse<JsonNode> correctFraud(
      @PathVariable UUID facilityId,
      @PathVariable UUID id,
      @Valid @RequestBody FraudCorrection input,
      @RequestHeader("Idempotency-Key") String key,
      HttpServletRequest request) {
    identity.current(request).requireRole("OWNER");
    PeerTransferListings.checkKey(key);
    return ApiResponse.success(
        booking.caseCommand(
            facilityId, id, "fraud-correction", input, key, request.getHeader("Authorization")));
  }

  private void operator(HttpServletRequest request) {
    var actor = identity.current(request);
    if (!actor.hasRole("OWNER") && !actor.hasRole("STAFF"))
      throw new ForbiddenException("Chỉ nhân sự cơ sở có quyền mới được ghi nhận có mặt.");
  }
}
