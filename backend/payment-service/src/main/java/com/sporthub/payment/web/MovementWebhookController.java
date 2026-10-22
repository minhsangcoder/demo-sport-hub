package com.sporthub.payment.web;
import com.sporthub.common.dto.ApiResponse;
import com.sporthub.payment.provider.MovementProvider.Event;
import com.sporthub.payment.service.MovementExecutions;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/payments/movements")
public class MovementWebhookController {
    private final MovementExecutions executions;
    public MovementWebhookController(MovementExecutions executions){this.executions=executions;}
    @PostMapping("/{provider}/webhook") public ApiResponse<Void> receive(@PathVariable String provider,@RequestBody Event event){
        if(!provider.equals(event.provider()))throw new IllegalArgumentException("Provider route differs from the signed receipt");executions.accept(event,"WEBHOOK");return ApiResponse.success(null);
    }
}
