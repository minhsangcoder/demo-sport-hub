package com.sporthub.payment.web;
import com.sporthub.payment.service.SellerPayoutAccounts;
import com.sporthub.common.dto.ApiResponse;
import com.sporthub.common.security.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
public class SellerPayoutAccountController {
    public record Revoke(@NotBlank @Size(max=500) String reason) {}
    private final SellerPayoutAccounts service;private final RemoteIdentity identity;private final String secret;
    public SellerPayoutAccountController(SellerPayoutAccounts service,RemoteIdentity identity,@Value("${SERVICE_CALL_SECRET}") String secret){this.service=service;this.identity=identity;this.secret=secret;}
    @GetMapping("/api/v1/payments/payout-accounts") public ApiResponse<List<SellerPayoutAccounts.Destination>> mine(HttpServletRequest r){return ApiResponse.success(service.mine(identity.current(r)));}
    @PostMapping("/api/v1/payments/payout-accounts") public ApiResponse<SellerPayoutAccounts.Destination> create(@Valid @RequestBody SellerPayoutAccounts.Input input,@RequestHeader(value="Idempotency-Key",required=false) String key,HttpServletRequest r){return ApiResponse.success(service.register(input,key,identity.current(r)));}
    @PostMapping("/api/v1/payments/payout-accounts/{id}/revoke") public ApiResponse<SellerPayoutAccounts.Destination> revoke(@PathVariable UUID id,@Valid @RequestBody Revoke input,HttpServletRequest r){return ApiResponse.success(service.revoke(id,input.reason(),identity.current(r)));}
    @PostMapping("/api/v1/internal/finance/beneficiaries/verify") public ApiResponse<SellerPayoutAccounts.Verified> verify(@RequestBody String body,HttpServletRequest r) throws Exception {
        ServiceCalls.verify(secret,r.getMethod(),r.getRequestURI(),r.getHeader("X-Service-Time"),body,r.getHeader("X-Service-Signature"));var json=new com.fasterxml.jackson.databind.ObjectMapper().readTree(body);
        return ApiResponse.success(service.verified(UUID.fromString(json.path("revisionId").asText()),UUID.fromString(json.path("sellerId").asText())));
    }
}
