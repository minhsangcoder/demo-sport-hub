package com.sporthub.booking.web;
import com.sporthub.booking.service.GroupAttendanceService;
import com.sporthub.common.security.RemoteIdentity;
import com.sporthub.common.dto.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController @RequestMapping("/api/v1/groups/{id}/attendance")
public class GroupAttendanceController {
 private final GroupAttendanceService service;private final RemoteIdentity identity;
 public GroupAttendanceController(GroupAttendanceService service,RemoteIdentity identity){this.service=service;this.identity=identity;}
 @GetMapping("/ticket") public ApiResponse<GroupAttendanceService.Ticket> own(@PathVariable UUID id,HttpServletRequest r){return ApiResponse.success(service.own(id,identity.current(r)));}
 @GetMapping("/tickets") public ApiResponse<List<GroupAttendanceService.Ticket>> all(@PathVariable UUID id,HttpServletRequest r){return ApiResponse.success(service.tickets(id,identity.current(r)));}
 @GetMapping public ApiResponse<List<Map<String,Object>>> status(@PathVariable UUID id,HttpServletRequest r){return ApiResponse.success(service.status(id,identity.current(r),r.getHeader("Authorization")));}
 @PostMapping("/scan") public ApiResponse<GroupAttendanceService.Attendance> scan(@PathVariable UUID id,@Valid @RequestBody GroupAttendanceService.Scan input,HttpServletRequest r){return ApiResponse.success(service.scan(id,input,identity.current(r),r.getHeader("Authorization")));}
 @PostMapping("/manual") public ApiResponse<GroupAttendanceService.Attendance> manual(@PathVariable UUID id,@Valid @RequestBody GroupAttendanceService.Manual input,HttpServletRequest r){return ApiResponse.success(service.manual(id,input,identity.current(r),r.getHeader("Authorization")));}
}
