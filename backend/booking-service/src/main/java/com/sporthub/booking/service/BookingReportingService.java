package com.sporthub.booking.service;
import com.sporthub.common.security.RemoteIdentity.Caller;
import com.sporthub.common.time.ReportWindow;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
@Service @Transactional(readOnly=true,isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
public class BookingReportingService {
 private final JdbcTemplate jdbc;private final Clock clock;private final BookingDependencies dependencies;
 public BookingReportingService(JdbcTemplate jdbc,Clock clock,BookingDependencies dependencies){this.jdbc=jdbc;this.clock=clock;this.dependencies=dependencies;}
 public Map<String,Object> activity(UUID facility,Instant from,Instant to,String timezone,Caller actor,String token){
  actor.requireRole("OWNER");dependencies.facilityPermission(facility,actor,token,"OWNER_REPORT");var window=ReportWindow.of(from,to,timezone,clock.instant());var result=new LinkedHashMap<String,Object>();
  var start=Timestamp.from(window.from());var end=Timestamp.from(window.to());result.put("facilityId",facility);result.put("from",window.from());result.put("to",window.to());result.put("timezone",window.timezone().getId());result.put("interval","[from,to)");result.put("dateBasis","startsAt; current status at query time");
  result.put("bookings",jdbc.queryForList("SELECT timezone(?,b.starts_at)::date AS local_date,b.court_id,b.source,b.status,b.currency,CASE WHEN g.id IS NULL THEN 'PERSONAL' ELSE 'GROUP' END AS booking_kind,count(*) AS booking_count,sum(b.amount) AS booking_value FROM bookings b LEFT JOIN booking_groups g ON g.id=b.id WHERE b.facility_id=? AND b.starts_at>=? AND b.starts_at<? GROUP BY local_date,b.court_id,b.source,b.status,b.currency,booking_kind ORDER BY local_date,b.court_id,b.source,b.status",window.timezone().getId(),facility,start,end));
  result.put("usage",jdbc.queryForList("SELECT court_id,status,COALESCE(sum(EXTRACT(EPOCH FROM LEAST(ends_at,?::timestamptz)-GREATEST(starts_at,?::timestamptz))/60),0) AS booked_minutes FROM bookings WHERE facility_id=? AND status IN ('CONFIRMED','CHECKED_IN','COMPLETED') AND starts_at<? AND ends_at>? GROUP BY court_id,status ORDER BY court_id,status",end,start,facility,end,start));
  result.put("appliedCollections",jdbc.queryForList("""
   WITH applied AS (
    SELECT b.court_id,b.source,b.currency,b.amount,b.paid_at,'BOOKING' AS purpose
      FROM bookings b JOIN booking_payment_receipts r ON r.payment_id=b.payment_id AND r.booking_id=b.id
      WHERE b.facility_id=? AND NOT EXISTS(SELECT 1 FROM booking_groups g WHERE g.id=b.id)
    UNION ALL
    SELECT b.court_id,b.source,b.currency,c.amount,c.paid_at,'GROUP_CONTRIBUTION' AS purpose
      FROM group_contributions c JOIN booking_groups g ON g.id=c.group_id JOIN bookings b ON b.id=g.id WHERE b.facility_id=?
   ) SELECT timezone(?,paid_at)::date AS local_date,court_id,source,currency,purpose,count(*) AS payment_count,sum(amount) AS gross_amount
     FROM applied WHERE paid_at>=? AND paid_at<? GROUP BY local_date,court_id,source,currency,purpose ORDER BY local_date,court_id,purpose
   """,facility,facility,window.timezone().getId(),start,end));
  result.put("providerPolicy","Provider origin is not stored in Booking; applied events may be DEMO and are not proof of real settlement");result.put("collectionDateBasis","paidAt of locally applied verified payment events; may lag Payment Service, excludes transfer acquisitions");result.put("occupancy",null);result.put("netRevenue",null);result.put("financialPolicy","No settled revenue/refund/payout ledger or historical operating-hours denominator; applied collections are not settled revenue and retain paid cancellations");result.put("usagePolicy","Booked minutes clipped to interval; cancelled/expired/pending excluded; not proof of attendance");return result;
 }
}
