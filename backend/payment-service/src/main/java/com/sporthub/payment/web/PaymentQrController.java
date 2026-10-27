package com.sporthub.payment.web;

import com.sporthub.common.dto.ApiResponse;
import com.sporthub.common.security.RemoteIdentity;
import com.sporthub.payment.service.PaymentQrSessions;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/payments/{id}")
public class PaymentQrController {
  private final PaymentQrSessions service;
  private final RemoteIdentity identity;

  public PaymentQrController(PaymentQrSessions service, RemoteIdentity identity) {
    this.service = service;
    this.identity = identity;
  }

  @GetMapping("/qr")
  public ApiResponse<PaymentQrSessions.Session> read(@PathVariable UUID id, HttpServletRequest r) {
    return ApiResponse.success(service.own(id, identity.current(r), r.getHeader("Authorization")));
  }

  @PostMapping("/refresh")
  public ApiResponse<PaymentQrSessions.Session> refresh(
      @PathVariable UUID id, HttpServletRequest r) {
    return ApiResponse.success(
        service.refresh(id, identity.current(r), r.getHeader("Authorization")));
  }
}
