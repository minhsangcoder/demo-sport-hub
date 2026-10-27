package com.sporthub.schedule.web;

import com.sporthub.common.dto.ApiResponse;
import com.sporthub.common.security.RemoteIdentity;
import com.sporthub.schedule.service.FlexibleBookingPolicies;
import com.sporthub.schedule.web.ScheduleDtos.BookingPolicy;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/schedules/facilities/{id}/booking-policy")
public class FlexibleBookingPolicyController {
  private final FlexibleBookingPolicies policies;
  private final RemoteIdentity identity;

  public FlexibleBookingPolicyController(
      FlexibleBookingPolicies policies, RemoteIdentity identity) {
    this.policies = policies;
    this.identity = identity;
  }

  @GetMapping
  public ApiResponse<BookingPolicy> read(@PathVariable UUID id, HttpServletRequest request) {
    return ApiResponse.success(
        policies.read(id, identity.current(request), request.getHeader("Authorization")));
  }

  @PutMapping
  public ApiResponse<BookingPolicy> publish(
      @PathVariable UUID id,
      @Valid @RequestBody BookingPolicy input,
      @RequestHeader("Idempotency-Key") String key,
      HttpServletRequest request) {
    return ApiResponse.success(
        policies.publish(
            id, input, key, identity.current(request), request.getHeader("Authorization")));
  }
}
