package com.sporthub.booking.web;

import com.sporthub.booking.service.BookingAdmission;
import com.sporthub.common.dto.ApiResponse;
import com.sporthub.common.security.RemoteIdentity;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/bookings/admission")
public class BookingAdmissionController {
  private final BookingAdmission admission;
  private final RemoteIdentity identity;

  public BookingAdmissionController(BookingAdmission admission, RemoteIdentity identity) {
    this.admission = admission;
    this.identity = identity;
  }

  @GetMapping
  public ApiResponse<Map<String, Object>> status(HttpServletRequest request) {
    var caller = identity.current(request);
    caller.requireRole("CUSTOMER");
    return ApiResponse.success(admission.status(caller.id()));
  }
}
