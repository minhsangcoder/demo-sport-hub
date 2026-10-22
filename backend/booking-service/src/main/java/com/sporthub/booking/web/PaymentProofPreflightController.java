package com.sporthub.booking.web;
import com.sporthub.booking.service.*;
import com.sporthub.common.dto.ApiResponse;
import com.sporthub.common.security.RemoteIdentity;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController @RequestMapping("/api/v1/bookings/facility")
public class PaymentProofPreflightController {
    private final LocalAppliedCollections service;private final BookingDependencies dependencies;private final RemoteIdentity identity;
    public PaymentProofPreflightController(LocalAppliedCollections service,BookingDependencies dependencies,RemoteIdentity identity){this.service=service;this.dependencies=dependencies;this.identity=identity;}
    @GetMapping("/{id}/payment-proof-preflight") public ApiResponse<LocalAppliedCollections.Preflight> read(@PathVariable UUID id,@RequestParam(required=false) UUID afterId,@RequestParam(defaultValue="100") int limit,HttpServletRequest r){
        var actor=identity.current(r);actor.requireRole("OWNER");dependencies.ownerFacility(id,actor,r.getHeader("Authorization"));return ApiResponse.success(service.preflight(id,afterId,limit));
    }
}
