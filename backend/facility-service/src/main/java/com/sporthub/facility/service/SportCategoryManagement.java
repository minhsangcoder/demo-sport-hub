package com.sporthub.facility.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sporthub.common.exception.*;
import com.sporthub.common.security.RemoteIdentity.Caller;
import static com.sporthub.facility.web.FacilityDtos.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.text.Normalizer;
import java.util.*;

@Service @Transactional(readOnly=true)
public class SportCategoryManagement {
 private final JdbcTemplate jdbc;private final ObjectMapper json;private final jakarta.validation.Validator validator;
 public SportCategoryManagement(JdbcTemplate jdbc,ObjectMapper json,jakarta.validation.Validator validator){this.jdbc=jdbc;this.json=json;this.validator=validator;}
 public List<CategoryView> list(){return jdbc.query("SELECT * FROM sport_categories ORDER BY name,id",(r,n)->new CategoryView(r.getObject("id",UUID.class),r.getString("name"),r.getBoolean("active"),r.getString("description"),r.getString("icon"),r.getLong("version")));}
 public CategoryView find(UUID id){return list().stream().filter(c->c.id().equals(id)).findFirst().orElseThrow(()->new ResourceNotFoundException("Sport category not found"));}
 @Transactional public CategoryView create(CategoryInput input,Caller actor){actor.requireRole("ADMIN");validate(input);UUID id=UUID.randomUUID();jdbc.update("INSERT INTO sport_categories(id,name,active,description,icon) VALUES(?,?,?,?,?)",id,name(input.name()),input.active(),input.description(),input.icon());var result=find(id);audit(id,actor,"CREATED",null,result);return result;}
 @Transactional public CategoryView update(UUID id,CategoryUpdate input,Caller actor){
  actor.requireRole("ADMIN");if(!validator.validate(input).isEmpty())throw new IllegalArgumentException("Invalid category update");
  lock(id);var before=find(id);if(before.version()!=input.expectedVersion())throw new ConflictException("Sport category was updated; reload before saving");
  jdbc.update("UPDATE sport_categories SET name=?,active=?,description=?,icon=?,version=version+1 WHERE id=?",name(input.name()),input.active(),input.description(),input.icon(),id);
  var result=find(id);audit(id,actor,"UPDATED",before,result);return result;
 }
 @Transactional public CategoryView status(UUID id,CategoryStatus input,Caller actor){
  actor.requireRole("ADMIN");if(!validator.validate(input).isEmpty())throw new IllegalArgumentException("Invalid category status");lock(id);var before=find(id);
  if(before.version()!=input.expectedVersion())throw new ConflictException("Sport category was updated; reload before saving");
  if(before.active()==input.active())return before;
  jdbc.update("UPDATE sport_categories SET active=?,version=version+1 WHERE id=?",input.active(),id);var result=find(id);audit(id,actor,input.active()?"ACTIVATED":"DEACTIVATED",before,result);return result;
 }
 public List<Map<String,Object>> administration(Caller actor){actor.requireRole("ADMIN");return jdbc.queryForList("SELECT s.*, (SELECT count(*) FROM courts c WHERE c.sport_category_id=s.id) AS court_count,(SELECT count(DISTINCT facility_id) FROM courts c WHERE c.sport_category_id=s.id) AS facility_count FROM sport_categories s ORDER BY s.name,s.id");}
 public List<Map<String,Object>> history(UUID id,Caller actor){actor.requireRole("ADMIN");find(id);return jdbc.queryForList("SELECT * FROM sport_category_audit WHERE category_id=? ORDER BY occurred_at,id",id);}
 private void lock(UUID id){if(jdbc.queryForList("SELECT id FROM sport_categories WHERE id=? FOR UPDATE",UUID.class,id).isEmpty())throw new ResourceNotFoundException("Sport category not found");}
 private void validate(CategoryInput input){if(!validator.validate(input).isEmpty())throw new IllegalArgumentException("Invalid category input");}
 private String name(String input){String value=Normalizer.normalize(input,Normalizer.Form.NFC).strip().replaceAll("\\s+"," ");if(value.isBlank()||value.length()>100)throw new IllegalArgumentException("Invalid category name");return value;}
 private void audit(UUID id,Caller actor,String action,CategoryView before,CategoryView after){jdbc.update("INSERT INTO sport_category_audit(id,category_id,actor_id,action,before_value,after_value) VALUES(?,?,?,?,?::jsonb,?::jsonb)",UUID.randomUUID(),id,actor.id(),action,before==null?null:json.valueToTree(before).toString(),json.valueToTree(after).toString());}
}
