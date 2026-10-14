package com.sporthub.facility.web;
import com.sporthub.facility.service.SportCategoryManagement;
import com.sporthub.common.dto.ApiResponse;
import com.sporthub.common.security.RemoteIdentity;
import static com.sporthub.facility.web.FacilityDtos.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController @RequestMapping("/api/v1/admin/sport-categories")
public class SportCategoryAdminController {
 private final SportCategoryManagement service;private final RemoteIdentity identity;
 public SportCategoryAdminController(SportCategoryManagement service,RemoteIdentity identity){this.service=service;this.identity=identity;}
 @GetMapping public ApiResponse<List<Map<String,Object>>> list(HttpServletRequest r){return ApiResponse.success(service.administration(identity.current(r)));}
 @PutMapping("/{id}") public ApiResponse<CategoryView> update(@PathVariable UUID id,@Valid @RequestBody CategoryUpdate input,HttpServletRequest r){return ApiResponse.success(service.update(id,input,identity.current(r)));}
 @PatchMapping("/{id}/status") public ApiResponse<CategoryView> status(@PathVariable UUID id,@Valid @RequestBody CategoryStatus input,HttpServletRequest r){return ApiResponse.success(service.status(id,input,identity.current(r)));}
 @GetMapping("/{id}/history") public ApiResponse<List<Map<String,Object>>> history(@PathVariable UUID id,HttpServletRequest r){return ApiResponse.success(service.history(id,identity.current(r)));}
}
