package com.sporthub.booking.web;
import com.sporthub.booking.service.PlayGroupService;
import com.sporthub.booking.web.GroupDtos.CreateGroup;
import com.sporthub.common.security.RemoteIdentity;
import com.sporthub.common.dto.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController @RequestMapping("/api/v1/groups")
public class PlayGroupController {
 private final PlayGroupService service;private final RemoteIdentity identity;
 public PlayGroupController(PlayGroupService service,RemoteIdentity identity){this.service=service;this.identity=identity;}
 @GetMapping("/{id}/play-group") public ApiResponse<Map<String,Object>> byBooking(@PathVariable UUID id,HttpServletRequest r){return ApiResponse.success(service.byBooking(id,identity.current(r)));}
 @GetMapping("/play-groups/{id}") public ApiResponse<Map<String,Object>> detail(@PathVariable UUID id,HttpServletRequest r){return ApiResponse.success(service.detail(id,identity.current(r)));}
 @GetMapping("/play-groups/{id}/messages") public ApiResponse<List<Map<String,Object>>> messages(@PathVariable UUID id,@RequestParam(required=false) Long before,@RequestParam(defaultValue="50") int size,HttpServletRequest r){return ApiResponse.success(service.messages(id,before,size,identity.current(r)));}
 @PostMapping("/play-groups/{id}/messages") public ApiResponse<Map<String,Object>> send(@PathVariable UUID id,@Valid @RequestBody PlayGroupService.MessageInput input,@RequestHeader(value="Idempotency-Key",required=false) String key,HttpServletRequest r){return ApiResponse.success(service.send(id,input,key,identity.current(r)));}
 @PostMapping("/play-groups/{id}/actions") public ApiResponse<Map<String,Object>> action(@PathVariable UUID id,@Valid @RequestBody PlayGroupService.Action input,HttpServletRequest r){return ApiResponse.success(service.action(id,input,identity.current(r)));}
 @PostMapping("/play-groups/{id}/leave") @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT) public void leave(@PathVariable UUID id,HttpServletRequest r){service.leave(id,identity.current(r));}
 @PostMapping("/play-groups/{id}/reuse") @ResponseStatus(org.springframework.http.HttpStatus.CREATED) public ApiResponse<PlayGroupService.Reuse> reuse(@PathVariable UUID id,@Valid @RequestBody CreateGroup input,@RequestHeader(value="Idempotency-Key",required=false) String key,HttpServletRequest r){return ApiResponse.created(service.reuse(id,input,key,identity.current(r),r.getHeader("Authorization")));}
}
