package com.sporthub.booking.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sporthub.booking.service.ConfigurationGuardService;
import com.sporthub.common.dto.ApiResponse;
import com.sporthub.common.schedule.ConfigurationGuardProtocol.*;
import com.sporthub.common.security.ServiceCalls;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController @RequestMapping("/api/v1/internal/bookings/configuration")
public class InternalConfigurationController {
    private final ConfigurationGuardService service;
    private final ObjectMapper json;
    private final String secret;
    public InternalConfigurationController(ConfigurationGuardService service,ObjectMapper json,
            @Value("${SERVICE_CALL_SECRET}") String secret) {this.service=service;this.json=json;this.secret=secret;}
    @PostMapping("/prepare") public ApiResponse<Prepared> prepare(@RequestBody String body,HttpServletRequest r)throws Exception {
        verify(body,r);return ApiResponse.success(service.prepare(json.readValue(body,Command.class)));
    }
    @PostMapping("/release") public ApiResponse<Map<String,Boolean>> release(@RequestBody String body,HttpServletRequest r)throws Exception {
        verify(body,r);service.release(json.readValue(body,Command.class));return ApiResponse.success(Map.of("released",true));
    }
    private void verify(String body,HttpServletRequest r) {
        ServiceCalls.verify(secret,r.getMethod(),r.getRequestURI(),r.getHeader("X-Service-Time"),body,r.getHeader("X-Service-Signature"));
    }
    public record ImpactInput(java.util.UUID facilityId){}
    @PostMapping("/impact") public ApiResponse<java.util.List<Reservation>> impact(@RequestBody String body,HttpServletRequest r)throws Exception {
        verify(body,r);var input=json.readValue(body,ImpactInput.class);if(input.facilityId()==null)throw new IllegalArgumentException("Facility is required");return ApiResponse.success(service.impact(input.facilityId()));
    }
}
