package com.sporthub.payment.web;
import com.sporthub.payment.service.PaymentReportingService;
import com.sporthub.common.dto.ApiResponse;
import com.sporthub.common.security.RemoteIdentity;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
import org.springframework.format.annotation.DateTimeFormat;
import java.time.Instant;
import java.util.Map;
@RestController @RequestMapping("/api/v1/admin/payments/reports")
public class PaymentReportingController {
 private final PaymentReportingService service;private final RemoteIdentity identity;
 public PaymentReportingController(PaymentReportingService service,RemoteIdentity identity){this.service=service;this.identity=identity;}
 @GetMapping("/collections") public ApiResponse<Map<String,Object>> collections(@RequestParam(required=false) Instant from,@RequestParam(required=false) Instant to,@RequestParam(defaultValue="UTC") String timezone,HttpServletRequest r){return ApiResponse.success(service.collections(from,to,timezone,identity.current(r)));}
}
