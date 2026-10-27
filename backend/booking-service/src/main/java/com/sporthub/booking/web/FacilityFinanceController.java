package com.sporthub.booking.web;

import com.sporthub.booking.service.*;
import com.sporthub.common.dto.ApiResponse;
import com.sporthub.common.security.RemoteIdentity;
import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/bookings/facility-finance")
public class FacilityFinanceController {
  private final FacilityFinanceReads reads;
  private final FacilityManualDisbursements operations;
  private final FacilityDisbursementChanges changes;
  private final RemoteIdentity identity;

  public FacilityFinanceController(
      FacilityFinanceReads reads,
      FacilityManualDisbursements operations,
      FacilityDisbursementChanges changes,
      RemoteIdentity identity) {
    this.reads = reads;
    this.operations = operations;
    this.changes = changes;
    this.identity = identity;
  }

  @GetMapping("/facility/{id}")
  public ApiResponse<Map<String, Object>> facility(@PathVariable UUID id, HttpServletRequest r) {
    return ApiResponse.success(
        reads.facility(id, identity.current(r), r.getHeader("Authorization")));
  }

  @GetMapping("/mine")
  public ApiResponse<Map<String, Object>> mine(HttpServletRequest r) {
    return ApiResponse.success(reads.mine(identity.current(r)));
  }

  @PostMapping("/obligations/{id}/prepare")
  public ApiResponse<Map<String, Object>> prepare(
      @PathVariable UUID id,
      @RequestBody FacilityManualDisbursements.Prepare input,
      @RequestHeader("Idempotency-Key") String key,
      HttpServletRequest r) {
    return ApiResponse.success(
        operations.prepare(id, input, key, identity.current(r), r.getHeader("Authorization")));
  }

  @GetMapping("/operations/{id}")
  public ApiResponse<Map<String, Object>> operation(@PathVariable UUID id, HttpServletRequest r) {
    return ApiResponse.success(
        operations.read(id, identity.current(r), r.getHeader("Authorization")));
  }

  @PostMapping("/operations/{id}/complete")
  public ApiResponse<Map<String, Object>> complete(
      @PathVariable UUID id,
      @RequestBody FacilityManualDisbursements.Complete input,
      @RequestHeader("Idempotency-Key") String key,
      HttpServletRequest r) {
    return ApiResponse.success(
        operations.complete(id, input, key, identity.current(r), r.getHeader("Authorization")));
  }

  @PostMapping("/operations/{id}/review")
  public ApiResponse<Map<String, Object>> review(
      @PathVariable UUID id,
      @RequestBody FacilityDisbursementChanges.Review input,
      @RequestHeader("Idempotency-Key") String key,
      HttpServletRequest r) {
    return ApiResponse.success(
        changes.review(id, input, key, identity.current(r), r.getHeader("Authorization")));
  }

  @PostMapping("/obligations/{id}/destination")
  public ApiResponse<Map<String, Object>> destination(
      @PathVariable UUID id,
      @RequestBody FacilityDisbursementChanges.Destination input,
      @RequestHeader("Idempotency-Key") String key,
      HttpServletRequest r) {
    return ApiResponse.success(
        changes.destination(id, input, key, identity.current(r), r.getHeader("Authorization")));
  }
}
