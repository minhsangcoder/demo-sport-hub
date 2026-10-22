package com.sporthub.facility.web;

import com.sporthub.common.dto.ApiResponse;
import com.sporthub.common.security.RemoteIdentity;
import com.sporthub.facility.service.FacilityRefundPolicyService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class FacilityRefundPolicyController {
    private final FacilityRefundPolicyService policies;
    private final RemoteIdentity identity;

    public FacilityRefundPolicyController(FacilityRefundPolicyService policies, RemoteIdentity identity) {
        this.policies = policies;
        this.identity = identity;
    }

    @GetMapping("/owner/facilities/{id}/refund-policy")
    public ApiResponse<FacilityRefundPolicyService.Policy> own(@PathVariable UUID id, HttpServletRequest request) {
        return ApiResponse.success(policies.own(id, identity.current(request)));
    }

    @PutMapping("/owner/facilities/{id}/refund-policy")
    public ApiResponse<FacilityRefundPolicyService.Policy> update(@PathVariable UUID id,
            @Valid @RequestBody FacilityRefundPolicyService.Input input, HttpServletRequest request) {
        return ApiResponse.success(policies.update(id, input, identity.current(request)));
    }

    @GetMapping("/facilities/{id}/refund-policy")
    public ApiResponse<FacilityRefundPolicyService.Policy> publicPolicy(@PathVariable UUID id) {
        return ApiResponse.success(policies.publicPolicy(id));
    }
}
