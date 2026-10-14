package com.sporthub.identity.service;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

/** Called while holding UserSecurityLock. Regranting a global role must not resurrect old employment access. */
@Component @Transactional(propagation=Propagation.MANDATORY)
public class StaffRoleRevocation {
 private final JdbcTemplate jdbc;private final ObjectMapper json;
 public StaffRoleRevocation(JdbcTemplate jdbc,ObjectMapper json){this.jdbc=jdbc;this.json=json;}
 public void revoke(UUID user,UUID actor){
  for(var before:jdbc.queryForList("SELECT id,facility_id,version FROM staff_facility_bindings WHERE staff_user_id=? AND is_active FOR UPDATE",user)){
   UUID id=(UUID)before.get("id");var permissions=jdbc.queryForList("SELECT permission FROM staff_permissions WHERE binding_id=? ORDER BY permission",String.class,id);
   jdbc.update("UPDATE staff_facility_bindings SET is_active=false,version=version+1,updated_at=NOW() WHERE id=?",id);
   audit(actor,"STAFF_BINDING",id,"ADMIN_STAFF_ACCESS_REVOKED",Map.of("facilityId",before.get("facility_id"),"active",true,"permissions",permissions,"version",before.get("version")),Map.of("facilityId",before.get("facility_id"),"active",false,"reason","STAFF role removed"));
  }
  for(var invitation:jdbc.queryForList("SELECT id,profile_id,state FROM staff_invitations WHERE target_user_id=? AND state IN ('PENDING','ACCEPTED') FOR UPDATE",user)){
   jdbc.update("UPDATE staff_invitations SET state='CANCELLED' WHERE id=?",invitation.get("id"));audit(actor,"STAFF_PROFILE",(UUID)invitation.get("profile_id"),"ADMIN_STAFF_INVITATION_CANCELLED",Map.of("invitationId",invitation.get("id"),"state",invitation.get("state")),Map.of("invitationId",invitation.get("id"),"state","CANCELLED"));
  }
 }
 private void audit(UUID actor,String type,UUID id,String action,Object before,Object after){jdbc.update("INSERT INTO audit_log(user_id,action,entity_type,entity_id,old_value,new_value) VALUES(?,?,?,?,?::jsonb,?::jsonb)",actor,action,type,id,json.valueToTree(before).toString(),json.valueToTree(after).toString());}
}
