package com.sporthub.facility.web;
import com.sporthub.facility.service.FacilityFavorites;
import com.sporthub.common.security.RemoteIdentity;
import com.sporthub.common.dto.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;
@RestController @RequestMapping("/api/v1/facilities")
public class FacilityFavoriteController {
 private final FacilityFavorites service;private final RemoteIdentity identity;
 public FacilityFavoriteController(FacilityFavorites service,RemoteIdentity identity){this.service=service;this.identity=identity;}
 @GetMapping("/favorites") public ApiResponse<FacilityFavorites.Page> list(@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size,HttpServletRequest r){return ApiResponse.success(service.list(page,size,identity.current(r)));}
 @GetMapping("/{id}/favorite") public ApiResponse<FacilityFavorites.Favorite> state(@PathVariable UUID id,HttpServletRequest r){return ApiResponse.success(service.state(id,identity.current(r)));}
 @PutMapping("/{id}/favorite") public ApiResponse<FacilityFavorites.Favorite> save(@PathVariable UUID id,HttpServletRequest r){return ApiResponse.success(service.save(id,identity.current(r)));}
 @DeleteMapping("/{id}/favorite") @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT) public void remove(@PathVariable UUID id,HttpServletRequest r){service.remove(id,identity.current(r));}
}
