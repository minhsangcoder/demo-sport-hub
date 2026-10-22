package com.sporthub.payment.web;
import com.sporthub.payment.service.LegacyFinanceInventory;
import com.sporthub.common.dto.ApiResponse;
import com.sporthub.common.security.RemoteIdentity;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController @RequestMapping("/api/v1/payments/admin/finance")
public class LegacyFinanceController {
    private final LegacyFinanceInventory service;private final RemoteIdentity identity;
    public LegacyFinanceController(LegacyFinanceInventory service,RemoteIdentity identity){this.service=service;this.identity=identity;}
    @GetMapping("/legacy-transfers") public ApiResponse<LegacyFinanceInventory.Page> list(@RequestParam(required=false) UUID afterId,@RequestParam(defaultValue="100") int limit,HttpServletRequest r){return ApiResponse.success(service.list(identity.current(r),afterId,limit));}
}
