package com.sporthub.booking.web;
import com.sporthub.booking.service.BookingReportingService;
import com.sporthub.common.dto.ApiResponse;
import com.sporthub.common.security.RemoteIdentity;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
import org.springframework.format.annotation.DateTimeFormat;
import java.time.Instant;
import java.util.*;
@RestController @RequestMapping("/api/v1/bookings/reports/facility")
public class BookingReportingController {
 private final BookingReportingService service;private final RemoteIdentity identity;
 public BookingReportingController(BookingReportingService service,RemoteIdentity identity){this.service=service;this.identity=identity;}
 @GetMapping("/{id}/activity") public ApiResponse<Map<String,Object>> activity(@PathVariable UUID id,@RequestParam(required=false) Instant from,@RequestParam(required=false) Instant to,@RequestParam(defaultValue="UTC") String timezone,HttpServletRequest r){return ApiResponse.success(service.activity(id,from,to,timezone,identity.current(r),r.getHeader("Authorization")));}
}
