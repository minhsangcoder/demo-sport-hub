package com.sporthub.facility.service;
import com.sporthub.common.security.RemoteIdentity.Caller;
import com.sporthub.common.exception.ResourceNotFoundException;
import com.sporthub.facility.web.FacilityDtos.FacilityView;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.*;
@Service @Transactional(readOnly=true)
public class FacilityFavorites {
 public record PublicFacility(UUID id,String name,String phone,String addressLine,String provinceCode,String province,String wardCode,String ward,String description,String timezone,java.math.BigDecimal latitude,java.math.BigDecimal longitude,String status,Set<String> amenities){}
 public record Favorite(UUID facilityId,boolean favorite,Instant addedAt,boolean available,PublicFacility facility){}
 public record Page(List<Favorite> items,int page,int size,long total){}
 private final JdbcTemplate jdbc;private final FacilityService facilities;
 public FacilityFavorites(JdbcTemplate jdbc,FacilityService facilities){this.jdbc=jdbc;this.facilities=facilities;}
 @Transactional public Favorite save(UUID id,Caller actor){facilities.publicDetail(id);jdbc.update("INSERT INTO facility_favorites(user_id,facility_id) VALUES(?,?) ON CONFLICT DO NOTHING",actor.id(),id);return state(id,actor);}
 @Transactional public void remove(UUID id,Caller actor){jdbc.update("DELETE FROM facility_favorites WHERE user_id=? AND facility_id=?",actor.id(),id);}
 public Favorite state(UUID id,Caller actor){var rows=jdbc.queryForList("SELECT added_at FROM facility_favorites WHERE user_id=? AND facility_id=?",actor.id(),id);return projection(id,rows.isEmpty()?null:((java.sql.Timestamp)rows.getFirst().get("added_at")).toInstant());}
 public Page list(int page,int size,Caller actor){if(page<0||page>100000||size<1||size>100)throw new IllegalArgumentException("Invalid favorites page");long count=jdbc.queryForObject("SELECT count(*) FROM facility_favorites WHERE user_id=?",Long.class,actor.id());var items=jdbc.query("SELECT facility_id,added_at FROM facility_favorites WHERE user_id=? ORDER BY added_at DESC,facility_id LIMIT ? OFFSET ?",(r,n)->projection(r.getObject("facility_id",UUID.class),r.getTimestamp("added_at").toInstant()),actor.id(),size,(long)page*size);return new Page(items,page,size,count);}
 private Favorite projection(UUID id,Instant at){return facilities.publicIfAvailable(id).map(f->new Favorite(id,at!=null,at,true,new PublicFacility(f.id(),f.name(),f.phone(),f.addressLine(),f.provinceCode(),f.province(),f.wardCode(),f.ward(),f.description(),f.timezone(),f.latitude(),f.longitude(),f.status(),f.amenities()))).orElseGet(()->new Favorite(id,at!=null,at,false,null));}
}
