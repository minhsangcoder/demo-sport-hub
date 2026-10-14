package com.sporthub.payment.web;
import com.sporthub.payment.service.RefundReviewService;
import com.sporthub.common.security.RemoteIdentity;
import com.sporthub.common.dto.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController @RequestMapping("/api/v1")
public class RefundReviewController {
 private final RefundReviewService service;private final RemoteIdentity identity;
 public RefundReviewController(RefundReviewService service,RemoteIdentity identity){this.service=service;this.identity=identity;}
 @GetMapping("/payments/{id}/refunds") public ApiResponse<List<Map<String,Object>>> own(@PathVariable UUID id,HttpServletRequest r){return ApiResponse.success(service.forPayment(id,identity.current(r)));}
 @GetMapping("/admin/payments/refunds") public ApiResponse<List<Map<String,Object>>> list(HttpServletRequest r){return ApiResponse.success(service.list(identity.current(r)));}
 @GetMapping("/admin/payments/refunds/{id}") public ApiResponse<Map<String,Object>> detail(@PathVariable UUID id,HttpServletRequest r){return ApiResponse.success(service.detail(id,identity.current(r)));}
 @PostMapping("/admin/payments/refunds/{id}/reviews") public ApiResponse<Map<String,Object>> review(@PathVariable UUID id,@Valid @RequestBody RefundReviewService.NoteInput input,@RequestHeader(value="Idempotency-Key",required=false) String key,HttpServletRequest r){return ApiResponse.success(service.review(id,input,key,identity.current(r)));}
}
