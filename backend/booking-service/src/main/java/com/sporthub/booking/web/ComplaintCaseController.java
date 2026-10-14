package com.sporthub.booking.web;
import com.sporthub.booking.service.ComplaintCaseService;
import com.sporthub.common.security.RemoteIdentity;
import com.sporthub.common.dto.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController @RequestMapping("/api/v1/admin/bookings/complaints")
public class ComplaintCaseController {
 private final ComplaintCaseService service;private final RemoteIdentity identity;
 public ComplaintCaseController(ComplaintCaseService service,RemoteIdentity identity){this.service=service;this.identity=identity;}
 @PostMapping @ResponseStatus(org.springframework.http.HttpStatus.CREATED) public ApiResponse<Map<String,Object>> create(@Valid @RequestBody ComplaintCaseService.Create input,@RequestHeader(value="Idempotency-Key",required=false) String key,HttpServletRequest r){return ApiResponse.created(service.create(input,key,identity.current(r),r.getHeader("Authorization")));}
 @GetMapping public ApiResponse<List<Map<String,Object>>> list(@RequestParam(required=false) String state,@RequestParam(required=false) UUID bookingId,HttpServletRequest r){return ApiResponse.success(service.list(state,bookingId,identity.current(r)));}
 @GetMapping("/{id}") public ApiResponse<Map<String,Object>> detail(@PathVariable UUID id,HttpServletRequest r){return ApiResponse.success(service.detail(id,identity.current(r)));}
 @PostMapping("/{id}/decisions") public ApiResponse<Map<String,Object>> decide(@PathVariable UUID id,@Valid @RequestBody ComplaintCaseService.Decision input,HttpServletRequest r){return ApiResponse.success(service.decide(id,input,identity.current(r)));}
}
