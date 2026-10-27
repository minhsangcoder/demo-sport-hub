package com.sporthub.booking.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sporthub.booking.service.BookingBankProfiles;
import com.sporthub.common.dto.ApiResponse;
import com.sporthub.common.money.BankProfileContract.*;
import com.sporthub.common.security.ServiceCalls;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/internal/bookings/bank-profiles")
public class InternalBankProfileController {
  public record StatusRequest(UUID principalId, boolean existingHistory) {}

  private final BookingBankProfiles profiles;
  private final com.sporthub.booking.service.BookingBankHistory history;
  private final ObjectMapper json;
  private final String secret;

  public InternalBankProfileController(
      BookingBankProfiles profiles,
      com.sporthub.booking.service.BookingBankHistory history,
      ObjectMapper json,
      @Value("${SERVICE_CALL_SECRET:}") String secret) {
    this.profiles = profiles;
    this.history = history;
    this.json = json;
    this.secret = secret;
  }

  @PostMapping("/status")
  public ApiResponse<Status> status(@RequestBody String body, HttpServletRequest request)
      throws Exception {
    verify(request, body);
    var input = json.readValue(body, StatusRequest.class);
    if (input.principalId() == null)
      throw new IllegalArgumentException("Thiếu người có tài khoản ngân hàng.");
    return ApiResponse.success(profiles.status(input.principalId(), input.existingHistory()));
  }

  @PostMapping("/activate")
  public ApiResponse<Decision> activate(@RequestBody String body, HttpServletRequest request)
      throws Exception {
    verify(request, body);
    return ApiResponse.success(profiles.activate(json.readValue(body, Activation.class)));
  }

  @PostMapping("/reconcile-legacy")
  public ApiResponse<Status> reconcile(@RequestBody String body, HttpServletRequest request)
      throws Exception {
    verify(request, body);
    var input = json.readValue(body, LegacyHistory.class);
    history.reconcile(input);
    return ApiResponse.success(profiles.status(input.principalId(), true));
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
