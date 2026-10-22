package com.sporthub.booking.web;
import com.sporthub.booking.service.BookingPolicyCancellation;
import com.sporthub.common.dto.ApiResponse;
import com.sporthub.common.security.RemoteIdentity;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController @RequestMapping("/api/v1/bookings")
public class BookingPolicyCancellationController {
 private final BookingPolicyCancellation service;private final RemoteIdentity identity;
 public BookingPolicyCancellationController(BookingPolicyCancellation service,RemoteIdentity identity){this.service=service;this.identity=identity;}
 @PostMapping("/{id}/cancellation-quote") public ApiResponse<Map<String,Object>> quote(@PathVariable UUID id,HttpServletRequest r){return ApiResponse.success(service.quote(id,identity.current(r),r.getHeader("Authorization")));}
 @PostMapping("/{id}/cancel-with-policy") public ApiResponse<Map<String,Object>> cancel(@PathVariable UUID id,@RequestBody BookingPolicyCancellation.Input input,@RequestHeader("Idempotency-Key") String key,HttpServletRequest r){return ApiResponse.success(service.cancel(id,input,key,identity.current(r),r.getHeader("Authorization")));}
}
