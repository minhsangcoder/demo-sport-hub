package com.sporthub.booking.web;
import com.sporthub.booking.service.LegacyRefundPolicyAdoption;
import com.sporthub.common.dto.ApiResponse;
import com.sporthub.common.security.RemoteIdentity;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController @RequestMapping("/api/v1/bookings")
public class LegacyRefundPolicyController {
    private final LegacyRefundPolicyAdoption service;private final RemoteIdentity identity;
    public LegacyRefundPolicyController(LegacyRefundPolicyAdoption service,RemoteIdentity identity){this.service=service;this.identity=identity;}
    @GetMapping("/facility/{id}/refund-policy-preflight") public ApiResponse<List<LegacyRefundPolicyAdoption.Candidate>> preflight(@PathVariable UUID id,HttpServletRequest r){return ApiResponse.success(service.preflight(id,identity.current(r),r.getHeader("Authorization")));}
    @PostMapping("/{id}/refund-policy-adoptions") public ApiResponse<LegacyRefundPolicyAdoption.Offer> propose(@PathVariable UUID id,@Valid @RequestBody LegacyRefundPolicyAdoption.Input input,HttpServletRequest r){return ApiResponse.success(service.propose(id,input,identity.current(r),r.getHeader("Authorization")));}
    @GetMapping("/refund-policy-adoptions/{id}") public ApiResponse<LegacyRefundPolicyAdoption.Offer> view(@PathVariable UUID id,HttpServletRequest r){return ApiResponse.success(service.view(id,identity.current(r),r.getHeader("Authorization")));}
    @PostMapping("/refund-policy-adoptions/{id}/consent") public ApiResponse<LegacyRefundPolicyAdoption.Offer> consent(@PathVariable UUID id,HttpServletRequest r){return ApiResponse.success(service.consent(id,identity.current(r)));}
}
