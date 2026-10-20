package com.sporthub.identity.web;
import com.sporthub.identity.service.StaffBindingService;
import com.sporthub.identity.security.AuthenticatedUser;
import com.sporthub.common.dto.ApiResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.*;

@RestController @RequestMapping("/api/v1/staff-profiles/facilities/{facilityId}")
public class StaffProfileController {
 private final StaffBindingService service;public StaffProfileController(StaffBindingService service){this.service=service;}
 @GetMapping public ApiResponse<List<StaffBindingService.Profile>> list(@PathVariable UUID facilityId,@AuthenticationPrincipal AuthenticatedUser actor,HttpServletRequest r){return ApiResponse.success(service.profiles(facilityId,actor,r.getHeader("Authorization")));}
 @PostMapping public ApiResponse<StaffBindingService.Profile> create(@PathVariable UUID facilityId,@Valid @RequestBody StaffBindingService.ProfileInput input,@AuthenticationPrincipal AuthenticatedUser actor,HttpServletRequest r){return ApiResponse.success(service.createProfile(facilityId,input,actor,r.getHeader("Authorization")));}
 @PostMapping("/accounts") public ApiResponse<StaffBindingService.Profile> createAccount(@PathVariable UUID facilityId,@Valid @RequestBody StaffBindingService.CreateAccountInput input,@AuthenticationPrincipal AuthenticatedUser actor,HttpServletRequest r){return ApiResponse.success(service.createAccount(facilityId,input,actor,r.getHeader("Authorization")));}
 @PutMapping("/{profileId}") public ApiResponse<StaffBindingService.Profile> update(@PathVariable UUID facilityId,@PathVariable UUID profileId,@Valid @RequestBody StaffBindingService.ProfileUpdate input,@AuthenticationPrincipal AuthenticatedUser actor,HttpServletRequest r){return ApiResponse.success(service.updateProfile(facilityId,profileId,input,actor,r.getHeader("Authorization")));}
 @PutMapping("/{profileId}/password") @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT) public void resetPassword(@PathVariable UUID facilityId,@PathVariable UUID profileId,@Valid @RequestBody StaffBindingService.ResetPasswordInput input,@AuthenticationPrincipal AuthenticatedUser actor,HttpServletRequest r){service.resetPassword(facilityId,profileId,input,actor,r.getHeader("Authorization"));}
 @PostMapping("/{profileId}/invitations") public ApiResponse<StaffBindingService.Invitation> invite(@PathVariable UUID facilityId,@PathVariable UUID profileId,@Valid @RequestBody StaffBindingService.Input input,@AuthenticationPrincipal AuthenticatedUser actor,HttpServletRequest r){return ApiResponse.success(service.invite(facilityId,profileId,input,actor,r.getHeader("Authorization")));}
 @DeleteMapping("/{profileId}/invitations") @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT) public void cancel(@PathVariable UUID facilityId,@PathVariable UUID profileId,@AuthenticationPrincipal AuthenticatedUser actor,HttpServletRequest r){service.cancelInvitation(facilityId,profileId,actor,r.getHeader("Authorization"));}
 @GetMapping("/{profileId}/history") public ApiResponse<List<StaffBindingService.History>> history(@PathVariable UUID facilityId,@PathVariable UUID profileId,@AuthenticationPrincipal AuthenticatedUser actor,HttpServletRequest r){return ApiResponse.success(service.history(facilityId,profileId,actor,r.getHeader("Authorization")));}
}
