package com.sporthub.booking.web;
import com.sporthub.booking.service.BookingDemoFinance;
import com.sporthub.common.dto.ApiResponse;
import com.sporthub.common.security.RemoteIdentity;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController @RequestMapping("/api/v1/bookings/demo-finance")
public class BookingDemoFinanceController {
 private final BookingDemoFinance service;private final RemoteIdentity identity;
 public BookingDemoFinanceController(BookingDemoFinance service,RemoteIdentity identity){this.service=service;this.identity=identity;}
 @GetMapping("/facility/{id}") public ApiResponse<Map<String,Object>> facility(@PathVariable UUID id,HttpServletRequest r){return ApiResponse.success(service.facility(id,identity.current(r),r.getHeader("Authorization")));}
 @GetMapping("/mine") public ApiResponse<Map<String,Object>> mine(HttpServletRequest r){return ApiResponse.success(service.mine(identity.current(r)));}
 @PostMapping("/settlements/{id}/pay-seller") public ApiResponse<Map<String,Object>> pay(@PathVariable UUID id,@RequestBody BookingDemoFinance.Outcome input,@RequestHeader("Idempotency-Key") String key,HttpServletRequest r){return ApiResponse.success(service.paySeller(id,input,key,identity.current(r),r.getHeader("Authorization")));}
 @PostMapping("/refunds/{id}/complete") public ApiResponse<Map<String,Object>> refund(@PathVariable UUID id,@RequestBody BookingDemoFinance.Outcome input,@RequestHeader("Idempotency-Key") String key,HttpServletRequest r){return ApiResponse.success(service.refund(id,input,key,identity.current(r),r.getHeader("Authorization")));}
}
