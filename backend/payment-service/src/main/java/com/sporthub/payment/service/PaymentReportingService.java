package com.sporthub.payment.service;
import com.sporthub.common.security.RemoteIdentity.Caller;
import com.sporthub.common.time.ReportWindow;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
@Service @Transactional(readOnly=true,isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
public class PaymentReportingService {
 private final JdbcTemplate jdbc;private final Clock clock;
 public PaymentReportingService(JdbcTemplate jdbc,Clock clock){this.jdbc=jdbc;this.clock=clock;}
 public Map<String,Object> collections(Instant from,Instant to,String timezone,Caller actor){
  actor.requireRole("ADMIN");var window=ReportWindow.of(from,to,timezone,clock.instant());
  var result=new LinkedHashMap<String,Object>();result.put("from",window.from());result.put("to",window.to());result.put("timezone",window.timezone().getId());result.put("interval","[from,to)");result.put("dateBasis","paidAt");
  result.put("collections",jdbc.queryForList("SELECT timezone(?,paid_at)::date AS local_date,provider,purpose,currency,count(*) AS payment_count,sum(amount) AS gross_amount FROM payment_orders WHERE status='SUCCESS' AND paid_at>=? AND paid_at<? GROUP BY local_date,provider,purpose,currency ORDER BY local_date,provider,purpose,currency",window.timezone().getId(),Timestamp.from(window.from()),Timestamp.from(window.to())));
  result.put("synchronization",jdbc.queryForList("SELECT s.state,count(*) AS payment_count FROM payment_orders p JOIN payment_booking_sync s ON s.payment_id=p.id WHERE p.status='SUCCESS' AND p.paid_at>=? AND p.paid_at<? GROUP BY s.state ORDER BY s.state",Timestamp.from(window.from()),Timestamp.from(window.to())));
  result.put("metric","VERIFIED_PROVIDER_GROSS_COLLECTIONS");result.put("demoPolicy","DEMO rows are simulated collections; never real money");result.put("settledRevenue",null);result.put("payout",null);result.put("financialPolicy","No settlement ledger: gross collections include cancelled bookings and transfer payments, grouped separately; not net revenue, completed handoff or payout");return result;
 }
}
