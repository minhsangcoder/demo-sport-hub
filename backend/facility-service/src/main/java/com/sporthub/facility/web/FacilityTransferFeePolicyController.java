package com.sporthub.facility.web;

import com.sporthub.common.dto.ApiResponse;
import com.sporthub.common.security.RemoteIdentity;
import com.sporthub.facility.service.FacilityTransferFeePolicyService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class FacilityTransferFeePolicyController {
    private final FacilityTransferFeePolicyService policies;
    private final RemoteIdentity identity;

    public FacilityTransferFeePolicyController(FacilityTransferFeePolicyService policies, RemoteIdentity identity) {
        this.policies = policies;
        this.identity = identity;
    }

    @GetMapping("/owner/facilities/{id}/transfer-fee-policy")
    public ApiResponse<FacilityTransferFeePolicyService.Policy> own(@PathVariable UUID id, HttpServletRequest request) {
        return ApiResponse.success(policies.own(id, identity.current(request)));
    }

    @PutMapping("/owner/facilities/{id}/transfer-fee-policy")
    public ApiResponse<FacilityTransferFeePolicyService.Policy> update(@PathVariable UUID id,
            @Valid @RequestBody FacilityTransferFeePolicyService.Input input, HttpServletRequest request) {
        return ApiResponse.success(policies.update(id, input, identity.current(request)));
    }

    @GetMapping("/facilities/{id}/transfer-fee-policy")
    public ApiResponse<FacilityTransferFeePolicyService.Policy> publicPolicy(@PathVariable UUID id) {
        return ApiResponse.success(policies.publicPolicy(id));
    }
}
