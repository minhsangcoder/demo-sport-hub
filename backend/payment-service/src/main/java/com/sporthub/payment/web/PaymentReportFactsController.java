package com.sporthub.payment.web;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sporthub.common.dto.ApiResponse;
import com.sporthub.common.money.PaymentProofFacts.*;
import com.sporthub.common.security.ServiceCalls;
import com.sporthub.payment.service.PaymentReportFacts;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Validator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/internal/payments")
public class PaymentReportFactsController {
    private final PaymentReportFacts service;private final ObjectMapper json;private final Validator validator;private final String secret;
    public PaymentReportFactsController(PaymentReportFacts service,ObjectMapper json,Validator validator,@Value("${SERVICE_CALL_SECRET}") String secret){this.service=service;this.json=json;this.validator=validator;this.secret=secret;}
    @PostMapping("/report-facts") public ApiResponse<Response> read(@RequestBody String body,HttpServletRequest r)throws Exception{
        ServiceCalls.verify(secret,r.getMethod(),r.getRequestURI(),r.getHeader("X-Service-Time"),body,r.getHeader("X-Service-Signature"));var input=json.readValue(body,Request.class);
        if(input==null||!validator.validate(input).isEmpty())throw new IllegalArgumentException("Supply 1..500 valid payment bindings");return ApiResponse.success(service.read(input));
    }
}
