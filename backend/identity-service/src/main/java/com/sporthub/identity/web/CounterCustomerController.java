package com.sporthub.identity.web;
import com.sporthub.identity.domain.Role;
import com.sporthub.identity.security.AuthenticatedUser;
import com.sporthub.identity.service.StaffFacilityAccess;
import com.sporthub.common.dto.ApiResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;

/** Exact verified-phone matching only; no contact/name/status enumeration. */
@RestController @RequestMapping("/api/v1/users/counter-customer-lookup")
public class CounterCustomerController {
 private final JdbcTemplate jdbc;private final StaffFacilityAccess access;
 public CounterCustomerController(JdbcTemplate jdbc,StaffFacilityAccess access){this.jdbc=jdbc;this.access=access;}
 public record Input(@NotNull UUID facilityId,@NotBlank @Pattern(regexp="\\+?[0-9]{8,15}") String phone){}
 public record Match(UUID customerId){}
 @PostMapping @Transactional(readOnly=true)
 public ApiResponse<Match> lookup(@Valid @RequestBody Input input,@AuthenticationPrincipal AuthenticatedUser actor,HttpServletRequest request){
  boolean staff=actor.roles().contains(Role.STAFF)&&Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM staff_facility_bindings b JOIN staff_permissions p ON p.binding_id=b.id WHERE b.staff_user_id=? AND b.facility_id=? AND b.is_active AND p.permission='BOOKING_CREATE_COUNTER' AND NOT EXISTS(SELECT 1 FROM staff_profiles s WHERE s.binding_id=b.id AND NOT s.is_active))",Boolean.class,actor.userId(),input.facilityId()));
  if(!staff)access.owner(input.facilityId(),actor,request.getHeader("Authorization"));
  var ids=jdbc.queryForList("SELECT u.id FROM users u WHERE u.phone=? AND u.phone_verified AND u.status='ACTIVE' AND EXISTS(SELECT 1 FROM user_roles r WHERE r.user_id=u.id AND r.role='CUSTOMER') AND NOT EXISTS(SELECT 1 FROM user_roles r WHERE r.user_id=u.id AND r.role='ADMIN')",UUID.class,input.phone());
  return ApiResponse.success(new Match(ids.size()==1?ids.getFirst():null));
 }
}
