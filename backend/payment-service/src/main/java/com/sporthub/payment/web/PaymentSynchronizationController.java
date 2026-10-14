package com.sporthub.payment.web;
import com.sporthub.payment.service.PaymentSynchronization;
import com.sporthub.common.security.RemoteIdentity;
import com.sporthub.common.dto.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController @RequestMapping("/api/v1/payments/{id}/synchronization")
public class PaymentSynchronizationController {
    private final PaymentSynchronization sync;private final RemoteIdentity identity;
    public PaymentSynchronizationController(PaymentSynchronization sync,RemoteIdentity identity){this.sync=sync;this.identity=identity;}
    @GetMapping public ApiResponse<Map<String,Object>> status(@PathVariable UUID id,HttpServletRequest request){return ApiResponse.success(sync.own(id,identity.current(request)));}
    @PostMapping("/retry") public ApiResponse<Map<String,Object>> retry(@PathVariable UUID id,HttpServletRequest request){return ApiResponse.success(sync.retry(id,identity.current(request)));}
}
