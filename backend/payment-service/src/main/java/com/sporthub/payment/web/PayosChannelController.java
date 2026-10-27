package com.sporthub.payment.web;

import com.sporthub.common.dto.ApiResponse;
import com.sporthub.common.security.RemoteIdentity;
import com.sporthub.payment.service.PayosChannels;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/payments/facilities/{id}/channel")
public class PayosChannelController {
  private final PayosChannels service;
  private final RemoteIdentity identity;

  public PayosChannelController(PayosChannels service, RemoteIdentity identity) {
    this.service = service;
    this.identity = identity;
  }

  @GetMapping
  public ApiResponse<PayosChannels.Channel> read(@PathVariable UUID id, HttpServletRequest r) {
    return ApiResponse.success(service.own(id, identity.current(r), r.getHeader("Authorization")));
  }

  @PutMapping
  public ApiResponse<PayosChannels.Channel> configure(
      @PathVariable UUID id,
      @Valid @RequestBody PayosChannels.Input input,
      @RequestHeader("Idempotency-Key") String key,
      HttpServletRequest r) {
    return ApiResponse.success(
        service.configure(id, input, key, identity.current(r), r.getHeader("Authorization")));
  }

  @PostMapping("/verify")
  public ApiResponse<PayosChannels.Channel> verify(
      @PathVariable UUID id,
      @Valid @RequestBody PayosChannels.Version input,
      @RequestHeader("Idempotency-Key") String key,
      HttpServletRequest r) {
    return ApiResponse.success(
        service.verify(
            id, input.version(), key, identity.current(r), r.getHeader("Authorization")));
  }

  @PostMapping("/disable")
  public ApiResponse<PayosChannels.Channel> disable(
      @PathVariable UUID id,
      @Valid @RequestBody PayosChannels.Version input,
      @RequestHeader("Idempotency-Key") String key,
      HttpServletRequest r) {
    return ApiResponse.success(
        service.disable(
            id, input.version(), key, identity.current(r), r.getHeader("Authorization")));
  }
}
