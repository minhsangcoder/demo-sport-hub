package com.sporthub.booking.web;
import com.sporthub.booking.service.PaymentReceiptQueries;
import com.sporthub.common.dto.ApiResponse;
import com.sporthub.common.security.ServiceCalls;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;
@RestController
public class InternalPaymentReceiptController {
    private final PaymentReceiptQueries queries;private final String secret;
    public InternalPaymentReceiptController(PaymentReceiptQueries queries,@Value("${SERVICE_CALL_SECRET}") String secret){this.queries=queries;this.secret=secret;}
    @GetMapping("/api/v1/internal/bookings/payments/{paymentId}")
    public ApiResponse<PaymentReceiptQueries.Receipt> receipt(@PathVariable UUID paymentId,HttpServletRequest request){
        ServiceCalls.verify(secret,request.getMethod(),request.getRequestURI(),request.getHeader("X-Service-Time"),"",request.getHeader("X-Service-Signature"));
        return ApiResponse.success(queries.find(paymentId));
    }
}
