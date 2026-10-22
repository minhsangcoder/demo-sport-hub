package com.sporthub.identity.service;

import com.sporthub.identity.domain.Role;
import com.sporthub.identity.security.AuthenticatedUser;
import com.sporthub.common.exception.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;
import jakarta.validation.constraints.*;
import jakarta.validation.Validator;
import java.time.Instant;
import java.util.*;

@Service @Transactional(readOnly=true)
public class StaffBindingService {
 public static final Set<String> PERMISSIONS=Set.of("BOOKING_READ","BOOKING_CREATE_COUNTER","BOOKING_PAYMENT_COUNTER","BOOKING_CHECK_IN","BOOKING_REFUND","TRANSFER_PAYOUT","SCHEDULE_READ","SCHEDULE_MANAGE","COURT_STATUS_MANAGE","COURT_MAINTENANCE_MANAGE");
 private final JdbcTemplate jdbc;private final StaffFacilityAccess access;private final UserSecurityLock security;private final ObjectMapper json;private final Validator validator;private final org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;
 public StaffBindingService(JdbcTemplate jdbc,StaffFacilityAccess access,UserSecurityLock security,ObjectMapper json,Validator validator,org.springframework.security.crypto.password.PasswordEncoder passwordEncoder){this.jdbc=jdbc;this.access=access;this.security=security;this.json=json;this.validator=validator;this.passwordEncoder=passwordEncoder;}
 public record Input(@NotBlank @Size(max=255) String identifier,@NotEmpty Set<@NotBlank String> permissions){}
 public record Binding(UUID id,UUID facilityId,UUID userId,String fullName,String email,boolean active,List<String> permissions){}
 public record ProfileInput(@NotBlank @Size(min=2,max=255) String fullName,@Email @Size(max=255) String email,@Pattern(regexp="^\\+?[0-9]{9,15}$") String phone,@NotNull Boolean active){}
 public record ProfileUpdate(@NotBlank @Size(min=2,max=255) String fullName,@Email @Size(max=255) String email,@Pattern(regexp="^\\+?[0-9]{9,15}$") String phone,@NotNull Boolean active,@NotNull @PositiveOrZero Long expectedVersion){}
 public record Profile(UUID id,UUID facilityId,String fullName,String email,String phone,boolean active,UUID bindingId,UUID userId,boolean linked,List<String> permissions,long version){}
 public record CreateAccountInput(@NotBlank @Size(min=2,max=255) String fullName,@NotBlank @Email @Size(max=255) String email,@Pattern(regexp="^\\+?[0-9]{9,15}$") String phone,@NotBlank @Size(min=6,max=72) String password,@NotEmpty Set<@NotBlank String> permissions){}
 public record ResetPasswordInput(@NotBlank @Size(min=6,max=72) String newPassword){}
 public record PermissionsInput(@NotNull Set<@NotBlank String> permissions,@NotNull @PositiveOrZero Long expectedVersion){}
 public record PermissionsView(UUID bindingId,List<String> permissions,long version){}
 public record Candidate(UUID userId,String fullName,String maskedIdentifier){}
 public record Invitation(UUID id,UUID profileId,UUID facilityId,UUID invitedBy,String staffName,List<String> permissions,String state,Instant expiresAt){}
 public record History(UUID id,UUID actorId,String action,JsonNode oldValue,JsonNode newValue,Instant createdAt){}

 public List<Binding> list(UUID facility,AuthenticatedUser actor,String token){access.owner(facility,actor,token);return bindings(facility);}
 public List<Profile> profiles(UUID facility,AuthenticatedUser actor,String token){access.owner(facility,actor,token);return jdbc.queryForList("SELECT id FROM staff_profiles WHERE facility_id=? ORDER BY full_name,id",UUID.class,facility).stream().map(this::profile).toList();}
 public Candidate lookup(UUID facility,String identifier,AuthenticatedUser actor,String token){access.owner(facility,actor,token);var id=target(identifier);String name=jdbc.queryForObject("SELECT full_name FROM user_profiles WHERE user_id=?",String.class,id);return new Candidate(id,name,mask(identifier));}
 public Set<String> catalog(UUID facility,AuthenticatedUser actor,String token){access.owner(facility,actor,token);return new TreeSet<>(PERMISSIONS);}

 @Transactional public Profile createProfile(UUID facility,ProfileInput input,AuthenticatedUser actor,String token){validate(input);access.owner(facility,actor,token);facilityLock(facility);UUID id=UUID.randomUUID();jdbc.update("INSERT INTO staff_profiles(id,facility_id,full_name,contact_email,contact_phone,is_active,created_by) VALUES(?,?,?,?,?,?,?)",id,facility,input.fullName().trim(),email(input.email()),input.phone(),input.active(),actor.userId());var result=profile(id);audit(actor.userId(),"STAFF_PROFILE_CREATED","STAFF_PROFILE",id,null,result);return result;}
 @Transactional public Profile updateProfile(UUID facility,UUID id,ProfileUpdate input,AuthenticatedUser actor,String token){validate(input);access.owner(facility,actor,token);facilityLock(facility);var before=scopedProfile(facility,id);if(before.version()!=input.expectedVersion())throw new ConflictException("Staff profile changed; reload before saving");
  if(before.bindingId()!=null&&before.linked()&&!input.active())revokeBinding(facility,before.bindingId(),actor.userId());
  if(!input.active())cancelInvitations(id,actor.userId());
  jdbc.update("UPDATE staff_profiles SET full_name=?,contact_email=?,contact_phone=?,is_active=?,version=version+1,updated_at=NOW() WHERE id=?",input.fullName().trim(),email(input.email()),input.phone(),input.active(),id);var after=profile(id);audit(actor.userId(),"STAFF_PROFILE_UPDATED","STAFF_PROFILE",id,before,after);return after;
 }
 @Transactional public Invitation invite(UUID facility,UUID profile,Input input,AuthenticatedUser actor,String token){validate(input);permissions(input.permissions());access.owner(facility,actor,token);facilityLock(facility);var p=scopedProfile(facility,profile);if(!p.active()||p.linked())throw new ConflictException("An active unlinked Staff profile is required");UUID user=target(input.identifier());security.lock(user);eligible(user);if(user.equals(actor.userId()))throw new ConflictException("Owner cannot invite their own account as Staff");
  if(Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM staff_facility_bindings WHERE staff_user_id=? AND facility_id=? AND is_active) OR EXISTS(SELECT 1 FROM staff_invitations WHERE target_user_id=? AND facility_id=? AND profile_id<>? AND state IN ('PENDING','ACCEPTED') AND expires_at>NOW())",Boolean.class,user,facility,user,facility,profile)))throw new ConflictException("Account is already linked or invited to another Staff profile at this facility");
  var open=jdbc.queryForList("SELECT id,target_user_id,permissions,expires_at FROM staff_invitations WHERE profile_id=? AND state IN ('PENDING','ACCEPTED') FOR UPDATE",profile);
  if(!open.isEmpty()) {var r=open.getFirst();if(((java.sql.Timestamp)r.get("expires_at")).toInstant().isAfter(Instant.now())){if(user.equals(r.get("target_user_id"))&&new HashSet<>(array(r.get("permissions"))).equals(input.permissions()))return invitation((UUID)r.get("id"));throw new ConflictException("Cancel the existing invitation before inviting another account or changing permissions");}cancelInvitations(profile,actor.userId());}
  UUID id=UUID.randomUUID();jdbc.update("INSERT INTO staff_invitations(id,profile_id,facility_id,target_user_id,invited_by,permissions,expires_at) VALUES(?,?,?,?,?,?,NOW()+INTERVAL '24 hours')",id,profile,facility,user,actor.userId(),input.permissions().toArray(String[]::new));audit(actor.userId(),"STAFF_INVITED","STAFF_PROFILE",profile,null,invitation(id));return invitation(id);
 }
 public List<Invitation> invitations(AuthenticatedUser actor){return jdbc.queryForList("SELECT id FROM staff_invitations WHERE target_user_id=? AND state IN ('PENDING','ACCEPTED') AND expires_at>NOW() ORDER BY created_at DESC LIMIT 100",UUID.class,actor.userId()).stream().map(this::invitation).toList();}
 @Transactional public Invitation respond(UUID id,boolean accept,AuthenticatedUser actor){security.lock(actor.userId());eligible(actor.userId());var r=invitationRow(id);if(!actor.userId().equals(r.get("target_user_id")))throw new ForbiddenException("Invitation belongs to another account");if(!((java.sql.Timestamp)r.get("expires_at")).toInstant().isAfter(Instant.now()))throw new ConflictException("Staff invitation expired");String next=accept?"ACCEPTED":"DECLINED";if(next.equals(r.get("state")))return invitation(id);if(!r.get("state").equals("PENDING"))throw new ConflictException("Staff invitation is no longer pending");jdbc.update("UPDATE staff_invitations SET state=?,responded_at=NOW() WHERE id=?",next,id);audit(actor.userId(),"STAFF_INVITATION_"+next,"STAFF_PROFILE",(UUID)r.get("profile_id"),invitationFrom(r),invitation(id));return invitation(id);}
 @Transactional public void cancelInvitation(UUID facility,UUID profile,AuthenticatedUser actor,String token){access.owner(facility,actor,token);facilityLock(facility);scopedProfile(facility,profile);cancelInvitations(profile,actor.userId());}

 /** Legacy route remains, but new/revoked links require consent. Owner always rechecks live Facility ownership. */
 @Transactional public Binding assign(UUID facility,Input input,AuthenticatedUser actor,String token){validate(input);permissions(input.permissions());access.owner(facility,actor,token);facilityLock(facility);UUID user=target(input.identifier());security.lock(user);eligible(user);
  var existing=jdbc.queryForList("SELECT id,is_active FROM staff_facility_bindings WHERE staff_user_id=? AND facility_id=? FOR UPDATE",user,facility);
  UUID binding=existing.isEmpty()?UUID.randomUUID():(UUID)existing.getFirst().get("id");boolean active=!existing.isEmpty()&&Boolean.TRUE.equals(existing.getFirst().get("is_active"));
  if(active){if(jdbc.queryForObject("SELECT count(*) FROM user_roles WHERE user_id=? AND role='STAFF'",Integer.class,user)==0)throw new ConflictException("Staff role was removed; revoke the link before requesting new consent");var p=jdbc.queryForList("SELECT id FROM staff_profiles WHERE binding_id=? AND is_active",UUID.class,binding);if(p.isEmpty())throw new ConflictException("Staff profile is inactive");var before=binding(facility,binding);if(new HashSet<>(before.permissions()).equals(input.permissions()))return before;setPermissions(binding,input.permissions());audit(actor.userId(),"STAFF_PERMISSIONS_CHANGED","STAFF_BINDING",binding,before,binding(facility,binding));return binding(facility,binding);}
  var consent=jdbc.queryForList("SELECT i.* FROM staff_invitations i JOIN staff_profiles p ON p.id=i.profile_id WHERE i.facility_id=? AND i.target_user_id=? AND i.invited_by=? AND i.state='ACCEPTED' AND i.expires_at>NOW() AND p.is_active FOR UPDATE OF i",facility,user,actor.userId());
  var matching=consent.stream().filter(r->new HashSet<>(array(r.get("permissions"))).equals(input.permissions())).toList();if(matching.size()!=1)throw new ConflictException("Account must accept the Staff invitation with these permissions before linking");var invitation=matching.getFirst();UUID profile=(UUID)invitation.get("profile_id");if(scopedProfile(facility,profile).linked())throw new ConflictException("Staff profile is already linked");
  var other=jdbc.queryForList("SELECT id FROM staff_profiles WHERE binding_id=? AND id<>?",UUID.class,binding,profile);if(!other.isEmpty())throw new ConflictException("Use the original Staff profile to reactivate this account");
  jdbc.update("INSERT INTO staff_facility_bindings(id,staff_user_id,facility_id,assigned_by) VALUES(?,?,?,?) ON CONFLICT(staff_user_id,facility_id) DO UPDATE SET is_active=true,assigned_by=EXCLUDED.assigned_by,version=staff_facility_bindings.version+1,updated_at=NOW()",binding,user,facility,actor.userId());
  setPermissions(binding,input.permissions());jdbc.update("UPDATE staff_profiles SET binding_id=?,version=version+1,updated_at=NOW() WHERE id=?",binding,profile);jdbc.update("UPDATE staff_invitations SET state='CONSUMED' WHERE id=?",invitation.get("id"));
  int granted=jdbc.update("INSERT INTO user_roles(user_id,role) VALUES(?,'STAFF') ON CONFLICT DO NOTHING",user);if(granted>0){jdbc.update("UPDATE refresh_tokens SET revoked=true,revoked_at=NOW() WHERE user_id=? AND NOT revoked",user);audit(actor.userId(),"STAFF_ROLE_GRANTED","USER",user,null,Map.of("facilityId",facility,"role","STAFF"));}
  var result=binding(facility,binding);audit(actor.userId(),"STAFF_ASSIGNED","STAFF_BINDING",binding,null,result);audit(actor.userId(),"STAFF_PROFILE_LINKED","STAFF_PROFILE",profile,null,Map.of("bindingId",binding,"userId",user,"invitationId",invitation.get("id")));return result;
 }
 @Transactional public PermissionsView updatePermissions(UUID facility,UUID binding,PermissionsInput input,AuthenticatedUser actor,String token){validate(input);permissions(input.permissions());access.owner(facility,actor,token);facilityLock(facility);var before=binding(facility,binding);security.lock(before.userId());eligible(before.userId());if(!before.active())throw new ConflictException("Staff is no longer linked");long version=jdbc.queryForObject("SELECT version FROM staff_facility_bindings WHERE id=?",Long.class,binding);if(version!=input.expectedVersion())throw new ConflictException("Staff permissions changed; reload before saving");if(!new HashSet<>(before.permissions()).equals(input.permissions())){setPermissions(binding,input.permissions());audit(actor.userId(),"STAFF_PERMISSIONS_CHANGED","STAFF_BINDING",binding,before,binding(facility,binding));}return permissionView(binding);}
 public PermissionsView permissions(UUID facility,UUID binding,AuthenticatedUser actor,String token){access.owner(facility,actor,token);binding(facility,binding);return permissionView(binding);}
 @Transactional public void revoke(UUID facility,UUID binding,AuthenticatedUser actor,String token){access.owner(facility,actor,token);facilityLock(facility);revokeBinding(facility,binding,actor.userId());}
 public List<History> history(UUID facility,UUID profile,AuthenticatedUser actor,String token){access.owner(facility,actor,token);var p=scopedProfile(facility,profile);return jdbc.query("SELECT * FROM audit_log WHERE (entity_type='STAFF_PROFILE' AND entity_id=?) OR (entity_type='STAFF_BINDING' AND (entity_id=? OR entity_id::text IN (SELECT new_value->>'bindingId' FROM audit_log WHERE entity_type='STAFF_PROFILE' AND entity_id=? AND action='STAFF_PROFILE_LINKED'))) ORDER BY created_at DESC,id DESC LIMIT 100",(r,n)->new History(r.getObject("id",UUID.class),r.getObject("user_id",UUID.class),r.getString("action"),read(r.getString("old_value")),read(r.getString("new_value")),r.getTimestamp("created_at").toInstant()),profile,p.bindingId(),profile);}

 @Transactional public Profile createAccount(UUID facility,CreateAccountInput input,AuthenticatedUser actor,String token){
  validate(input);permissions(input.permissions());access.owner(facility,actor,token);facilityLock(facility);
  String email=email(input.email());
  String phone=input.phone()==null||input.phone().isBlank()?null:input.phone().trim();
  if(email!=null&&Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM users WHERE lower(email)=?)",Boolean.class,email))){
   throw new ConflictException("Email đã được đăng ký trong hệ thống");
  }
  if(phone!=null&&Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM users WHERE phone=?)",Boolean.class,phone))){
   throw new ConflictException("Số điện thoại đã được đăng ký trong hệ thống");
  }
  UUID userId=UUID.randomUUID();
  String hash=passwordEncoder.encode(input.password());
  jdbc.update("INSERT INTO users(id,email,phone,password_hash,status,email_verified,phone_verified,created_at,updated_at) VALUES(?,?,?,?,?,?,?,NOW(),NOW())",
   userId,email,phone,hash,"ACTIVE",email!=null,phone!=null);
  jdbc.update("INSERT INTO user_profiles(user_id,full_name,created_at,updated_at) VALUES(?,?,NOW(),NOW())",userId,input.fullName().trim());
  jdbc.update("INSERT INTO user_roles(user_id,role) VALUES(?,'STAFF')",userId);
  UUID bindingId=UUID.randomUUID();
  jdbc.update("INSERT INTO staff_facility_bindings(id,staff_user_id,facility_id,assigned_by,is_active,version,created_at,updated_at) VALUES(?,?,?,?,true,1,NOW(),NOW())",
   bindingId,userId,facility,actor.userId());
  setPermissions(bindingId,input.permissions());
  UUID profileId=UUID.randomUUID();
  jdbc.update("INSERT INTO staff_profiles(id,facility_id,full_name,contact_email,contact_phone,is_active,binding_id,created_by,version,created_at,updated_at) VALUES(?,?,?,?,?,true,?,?,1,NOW(),NOW())",
   profileId,facility,input.fullName().trim(),email,phone,bindingId,actor.userId());
  audit(actor.userId(),"STAFF_ACCOUNT_CREATED","STAFF_PROFILE",profileId,null,Map.of("userId",userId,"email",Objects.toString(email,""),"facilityId",facility,"permissions",input.permissions()));
  return profile(profileId);
 }

 @Transactional public void resetPassword(UUID facility,UUID profileId,ResetPasswordInput input,AuthenticatedUser actor,String token){
  validate(input);access.owner(facility,actor,token);facilityLock(facility);
  var p=scopedProfile(facility,profileId);
  if(p.userId()==null||!p.linked())throw new ConflictException("Hồ sơ nhân viên chưa liên kết tài khoản");
  security.lock(p.userId());
  String hash=passwordEncoder.encode(input.newPassword());
  jdbc.update("UPDATE users SET password_hash=?,updated_at=NOW() WHERE id=?",hash,p.userId());
  jdbc.update("UPDATE refresh_tokens SET revoked=true,revoked_at=NOW() WHERE user_id=? AND NOT revoked",p.userId());
  audit(actor.userId(),"STAFF_PASSWORD_RESET","USER",p.userId(),null,Map.of("facilityId",facility,"profileId",profileId));
 }

 private void revokeBinding(UUID facility,UUID id,UUID actor){var before=binding(facility,id);security.lock(before.userId());if(!before.active())return;jdbc.update("UPDATE staff_facility_bindings SET is_active=false,version=version+1,updated_at=NOW() WHERE id=?",id);jdbc.queryForList("SELECT id FROM staff_profiles WHERE binding_id=?",UUID.class,id).forEach(p->cancelInvitations(p,actor));audit(actor,"STAFF_REVOKED","STAFF_BINDING",id,before,binding(facility,id));}
 private void cancelInvitations(UUID profile,UUID actor){for(UUID id:jdbc.queryForList("SELECT id FROM staff_invitations WHERE profile_id=? AND state IN ('PENDING','ACCEPTED') FOR UPDATE",UUID.class,profile)){var before=invitation(id);jdbc.update("UPDATE staff_invitations SET state='CANCELLED' WHERE id=?",id);audit(actor,"STAFF_INVITATION_CANCELLED","STAFF_PROFILE",profile,before,invitation(id));}}
 private void setPermissions(UUID binding,Set<String> permissions){jdbc.update("DELETE FROM staff_permissions WHERE binding_id=?",binding);for(var permission:new TreeSet<>(permissions))jdbc.update("INSERT INTO staff_permissions(binding_id,permission) VALUES(?,?)",binding,permission);jdbc.update("UPDATE staff_facility_bindings SET version=version+1,updated_at=NOW() WHERE id=?",binding);}
 private PermissionsView permissionView(UUID id){return new PermissionsView(id,permissionList(id),jdbc.queryForObject("SELECT version FROM staff_facility_bindings WHERE id=?",Long.class,id));}
 private List<String> permissionList(UUID id){return jdbc.queryForList("SELECT permission FROM staff_permissions WHERE binding_id=? ORDER BY permission",String.class,id);}
 private List<Binding> bindings(UUID facility){return jdbc.query("SELECT b.*,p.full_name,u.email FROM staff_facility_bindings b JOIN users u ON u.id=b.staff_user_id JOIN user_profiles p ON p.user_id=u.id WHERE b.facility_id=? ORDER BY p.full_name,b.id",(r,n)->new Binding(r.getObject("id",UUID.class),facility,r.getObject("staff_user_id",UUID.class),r.getString("full_name"),r.getString("email"),r.getBoolean("is_active"),permissionList(r.getObject("id",UUID.class))),facility);}
 private Binding binding(UUID facility,UUID id){return bindings(facility).stream().filter(b->b.id().equals(id)).findFirst().orElseThrow(()->new ResourceNotFoundException("Staff binding not found"));}
 private Profile scopedProfile(UUID facility,UUID id){var p=profile(id);if(!facility.equals(p.facilityId()))throw new ResourceNotFoundException("Staff profile not found");return p;}
 private Profile profile(UUID id){var rows=jdbc.query("SELECT p.*,b.staff_user_id,b.is_active AS binding_active FROM staff_profiles p LEFT JOIN staff_facility_bindings b ON b.id=p.binding_id WHERE p.id=?",(r,n)->new Profile(id,r.getObject("facility_id",UUID.class),r.getString("full_name"),r.getString("contact_email"),r.getString("contact_phone"),r.getBoolean("is_active"),r.getObject("binding_id",UUID.class),r.getObject("staff_user_id",UUID.class),r.getBoolean("binding_active"),r.getObject("binding_id")==null?List.of():permissionList(r.getObject("binding_id",UUID.class)),r.getLong("version")),id);if(rows.isEmpty())throw new ResourceNotFoundException("Staff profile not found");return rows.getFirst();}
 private Map<String,Object> invitationRow(UUID id){var rows=jdbc.queryForList("SELECT * FROM staff_invitations WHERE id=? FOR UPDATE",id);if(rows.isEmpty())throw new ResourceNotFoundException("Staff invitation not found");return rows.getFirst();}
 private Invitation invitation(UUID id){var rows=jdbc.queryForList("SELECT * FROM staff_invitations WHERE id=?",id);if(rows.isEmpty())throw new ResourceNotFoundException("Staff invitation not found");return invitationFrom(rows.getFirst());}
 private Invitation invitationFrom(Map<String,Object> r){return new Invitation((UUID)r.get("id"),(UUID)r.get("profile_id"),(UUID)r.get("facility_id"),(UUID)r.get("invited_by"),jdbc.queryForObject("SELECT full_name FROM staff_profiles WHERE id=?",String.class,r.get("profile_id")),array(r.get("permissions")),(String)r.get("state"),((java.sql.Timestamp)r.get("expires_at")).toInstant());}
 private UUID target(String identifier){if(identifier==null||identifier.isBlank()||identifier.length()>255)throw new IllegalArgumentException("Supply an exact email or phone number");String value=identifier.trim();var ids=jdbc.queryForList("SELECT id FROM users WHERE status='ACTIVE' AND ((lower(email)=? AND email_verified) OR (phone=? AND phone_verified)) AND NOT EXISTS(SELECT 1 FROM user_roles r WHERE r.user_id=users.id AND r.role='ADMIN')",UUID.class,value.toLowerCase(Locale.ROOT),value);if(ids.size()!=1)throw new ResourceNotFoundException("Eligible verified account not found");return ids.getFirst();}
 private void eligible(UUID user){if(!Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM users WHERE id=? AND status='ACTIVE' AND (email_verified OR phone_verified) AND NOT EXISTS(SELECT 1 FROM user_roles r WHERE r.user_id=users.id AND r.role='ADMIN'))",Boolean.class,user)))throw new ConflictException("Staff account is inactive or ineligible");}
 private void facilityLock(UUID id){jdbc.queryForObject("SELECT pg_advisory_xact_lock(hashtextextended(?,0))",Object.class,"identity-staff-facility:"+id);}
 private void permissions(Set<String> p){if(p==null||!PERMISSIONS.containsAll(p))throw new IllegalArgumentException("Unsupported Staff permission");}
 private void validate(Object input){if(input==null||!validator.validate(input).isEmpty())throw new IllegalArgumentException("Invalid Staff input");if(input instanceof ProfileInput p&&p.fullName().trim().length()<2||input instanceof ProfileUpdate u&&u.fullName().trim().length()<2)throw new IllegalArgumentException("Staff name must contain at least two characters");}
 private String email(String input){return input==null||input.isBlank()?null:input.trim().toLowerCase(Locale.ROOT);}
 private String mask(String identifier){String value=identifier.trim();int at=value.indexOf('@');return at>0?value.substring(0,1)+"***"+value.substring(at):"***"+value.substring(Math.max(0,value.length()-4));}
 private List<String> array(Object a){try{return Arrays.asList((String[])((java.sql.Array)a).getArray());}catch(java.sql.SQLException ex){throw new IllegalStateException(ex);}}
 private JsonNode read(String value){try{return value==null?null:json.readTree(value);}catch(Exception ex){throw new IllegalStateException(ex);}}
 private void audit(UUID actor,String action,String type,UUID id,Object before,Object after){jdbc.update("INSERT INTO audit_log(user_id,action,entity_type,entity_id,old_value,new_value) VALUES(?,?,?,?,?::jsonb,?::jsonb)",actor,action,type,id,before==null?null:json.valueToTree(before).toString(),after==null?null:json.valueToTree(after).toString());}
}
