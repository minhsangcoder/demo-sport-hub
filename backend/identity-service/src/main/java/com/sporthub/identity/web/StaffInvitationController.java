package com.sporthub.identity.web;
import com.sporthub.identity.service.StaffBindingService;
import com.sporthub.identity.security.AuthenticatedUser;
import com.sporthub.common.dto.ApiResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController @RequestMapping("/api/v1/users/me/staff-invitations")
public class StaffInvitationController {
 private final StaffBindingService service;public StaffInvitationController(StaffBindingService service){this.service=service;}
 @GetMapping public ApiResponse<List<StaffBindingService.Invitation>> list(@AuthenticationPrincipal AuthenticatedUser actor){return ApiResponse.success(service.invitations(actor));}
 @PostMapping("/{id}/accept") public ApiResponse<StaffBindingService.Invitation> accept(@PathVariable UUID id,@AuthenticationPrincipal AuthenticatedUser actor){return ApiResponse.success(service.respond(id,true,actor));}
 @PostMapping("/{id}/decline") public ApiResponse<StaffBindingService.Invitation> decline(@PathVariable UUID id,@AuthenticationPrincipal AuthenticatedUser actor){return ApiResponse.success(service.respond(id,false,actor));}
}
