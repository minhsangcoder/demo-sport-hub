package com.sporthub.booking.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.sporthub.booking.repository.BookingRepository;
import com.sporthub.booking.web.BookingDtos.Booking;
import static com.sporthub.booking.web.DemoFacilityReportDtos.*;
import com.sporthub.common.exception.ForbiddenException;
import com.sporthub.common.money.PaymentProofFacts;
import com.sporthub.common.security.RemoteIdentity.Caller;
import com.sporthub.common.time.FacilityReportWindow;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.*;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;

/** One application snapshot, optionally enriched with existing legacy payment facts after the TX. */
@Service
public class FacilityDemoReportingService {
 private record Snapshot(List<Booking> bookings,LocalAppliedCollections.Snapshot applied,List<Map<String,Object>> collections,
                         List<Map<String,Object>> settlements,List<Map<String,Object>> refunds,List<Map<String,Object>> events){}
 private final BookingRepository repo;private final BookingDependencies dependencies;private final LocalAppliedCollections applied;
 private final PaymentEvidenceClient evidence;private final Clock clock;private final TransactionTemplate read;
 public FacilityDemoReportingService(BookingRepository repo,BookingDependencies dependencies,LocalAppliedCollections applied,PaymentEvidenceClient evidence,Clock clock,PlatformTransactionManager manager){
  this.repo=repo;this.dependencies=dependencies;this.applied=applied;this.evidence=evidence;this.clock=clock;read=new TransactionTemplate(manager);read.setReadOnly(true);read.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
 }
 public Dashboard dashboard(UUID facility,LocalDate from,LocalDate to,int page,int size,Caller actor,String token){
  actor.requireRole("OWNER");
  if(page<0||size<1||size>100)throw new IllegalArgumentException("page must be nonnegative and size must be 1..100");
  var profile=dependencies.ownerFacility(facility,actor,token);
  if(profile==null||!facility.toString().equals(profile.path("id").asText())||!actor.id().toString().equals(profile.path("ownerId").asText()))throw new ForbiddenException("Facility is outside your ownership");
  var window=FacilityReportWindow.of(from,to,profile.path("timezone").asText(),clock);Instant asOf=clock.instant(),cutoff=asOf.isBefore(window.to())?asOf:window.to();
  var names=new LinkedHashMap<UUID,String>();var inventory=dependencies.ownerCourts(facility,token);
  if(inventory!=null&&inventory.isArray())for(var c:inventory)names.put(UUID.fromString(c.path("id").asText()),c.path("name").asText());
  var s=read.execute(status->{var bookings=repo.list("SELECT * FROM bookings WHERE facility_id=?",facility);
   return new Snapshot(bookings,applied.load(bookings),repo.jdbc().queryForList("SELECT c.* FROM booking_demo_collections c JOIN bookings b ON b.id=c.booking_id WHERE b.facility_id=?",facility),
    repo.jdbc().queryForList("SELECT s.*,h.paid_at purchase_paid_at FROM demo_transfer_settlements s JOIN transfer_handoffs h ON h.acquisition_id=s.acquisition_id WHERE s.facility_id=?",facility),
    repo.jdbc().queryForList("SELECT * FROM demo_refund_requests WHERE facility_id=?",facility),repo.jdbc().queryForList("SELECT e.*,b.court_id,b.source FROM demo_money_events e JOIN bookings b ON b.id=e.booking_id WHERE e.facility_id=?",facility));});
  var byId=new HashMap<UUID,Booking>();s.bookings().forEach(b->byId.put(b.id(),b));
  var local=new HashMap<UUID,Map<String,Object>>();s.collections().forEach(c->local.put((UUID)c.get("payment_id"),c));
  var needed=s.applied().facts().stream().filter(f->{var b=byId.get(f.bookingId());return in(f.paidAt(),window)||in(b.startsAt(),window)||overlaps(b,window);}).toList();
  var unknown=needed.stream().filter(LocalAppliedCollections.Fact::verified).filter(f->!local.containsKey(f.paymentId())).map(f->new PaymentProofFacts.Binding(f.paymentId(),f.bookingId(),f.memberId(),f.purpose())).toList();
  var remote=unknown.isEmpty()?new PaymentEvidenceClient.Result(Map.of(),Map.of(),List.of(),List.of(),0):evidence.read(unknown);
  var reasons=new TreeSet<String>();var modes=new HashMap<UUID,Boolean>();
  for(var booking:s.bookings()){
   var assessment=s.applied().assessments().get(booking.id());
   boolean relevant=in(booking.paidAt(),window)||in(booking.startsAt(),window)||overlaps(booking,window);
   boolean expectsPaid=Set.of("CONFIRMED","CHECKED_IN","COMPLETED").contains(booking.status())
    ||booking.status().equals("CANCELLED")&&(booking.paymentId()!=null||booking.paidAt()!=null);
   if(relevant&&expectsPaid&&assessment!=null&&!assessment.fullyPaid())reasons.add("APPLIED_TOTAL_NEEDS_REVIEW");
  }
  for(var fact:needed){
   boolean demo=false;
   if(!fact.verified())reasons.add("APPLIED_RECEIPT_NEEDS_REVIEW");
   else if(local.containsKey(fact.paymentId())){
    var c=local.get(fact.paymentId());demo=fact.fingerprint().equals(c.get("fingerprint"))&&fact.payerId().equals(c.get("payer_id"))&&fact.amount().compareTo((BigDecimal)c.get("amount"))==0&&fact.paidAt().equals(instant(c.get("paid_at")));
    if(!demo)reasons.add("APPLICATION_COLLECTION_BINDING_DIFFERS");
   }else{
    var f=remote==null?null:remote.facts().get(fact.paymentId());
    if(f!=null&&f.verified()&&f.canonical()&&f.payerId().equals(fact.payerId())&&new BigDecimal(f.amount()).compareTo(fact.amount())==0&&f.currency().equals(fact.currency())&&Objects.equals(f.paidAt(),fact.paidAt())&&Objects.equals(f.outcomeFingerprint(),fact.fingerprint())){
     demo=f.moneyClass().equals("DEMO");if(!demo)reasons.add("NON_DEMO_PAYMENT_EXCLUDED");
    }else reasons.add("PAYMENT_APPLICATION_FACTS_UNAVAILABLE");
   }modes.put(fact.paymentId(),demo);
  }
  if(remote!=null)reasons.addAll(remote.reasons());
  var counters=new LinkedHashMap<UUID,Counter>();names.forEach((id,name)->counters.put(id,new Counter()));var summary=new Counter();
  var daily=new LinkedHashMap<LocalDate,Counter>();for(var day=window.fromDate();!day.isAfter(window.toDate());day=day.plusDays(1))daily.put(day,new Counter());
  var transactions=new ArrayList<Transaction>();
  for(var b:s.bookings()){
   var c=counters.computeIfAbsent(b.courtId(),id->new Counter());names.putIfAbsent(b.courtId(),"Sân đã lưu trữ");
   var assessment=s.applied().assessments().get(b.id());boolean fullyPaid=assessment!=null&&assessment.fullyPaid();
   if(in(b.startsAt(),window)){summary.bookings++;c.bookings++;if(fullyPaid){summary.full++;c.full++;}if(b.status().equals("CANCELLED")){summary.cancelled++;c.cancelled++;}if(b.status().equals("EXPIRED")){summary.expired++;c.expired++;}}
   if(fullyPaid&&Set.of("CONFIRMED","CHECKED_IN","COMPLETED").contains(b.status())){
    var booked=minutes(max(b.startsAt(),window.from()),min(b.endsAt(),window.to()));summary.booked=summary.booked.add(booked);c.booked=c.booked.add(booked);
    if(b.checkedInAt()!=null&&Set.of("CHECKED_IN","COMPLETED").contains(b.status())){var used=minutes(max(b.startsAt(),b.checkedInAt(),window.from()),min(b.endsAt(),window.to(),asOf));summary.used=summary.used.add(used);c.used=c.used.add(used);}
   }
  }
  for(var f:needed)if(Boolean.TRUE.equals(modes.get(f.paymentId()))&&in(f.paidAt(),window)){
   var b=byId.get(f.bookingId());var c=counters.get(b.courtId());summary.r=summary.r.add(f.amount());c.r=c.r.add(f.amount());day(daily,f.paidAt(),window).r=day(daily,f.paidAt(),window).r.add(f.amount());
   transactions.add(transaction("COURT:"+f.paymentId(),"COURT_COLLECTION","IN",true,true,true,b,names,f.purpose(),"SUCCESS",f.amount(),f.paidAt()));
  }
  var purchaseIds=new HashSet<UUID>();
  for(var row:s.collections())if(row.get("purpose").equals("TRANSFER")){
   UUID payment=(UUID)row.get("payment_id");Instant paid=instant(row.get("paid_at"));purchaseIds.add(payment);
   if(in(paid,window)){var b=byId.get((UUID)row.get("booking_id"));var amount=(BigDecimal)row.get("amount");summary.p=summary.p.add(amount);counters.get(b.courtId()).p=counters.get(b.courtId()).p.add(amount);transactions.add(transaction("TRANSFER:"+payment,"TRANSFER_COLLECTION","IN",false,true,true,b,names,"TRANSFER","SUCCESS",amount,paid));}
  }
  for(var row:s.settlements()){
   var b=byId.get((UUID)row.get("booking_id"));var c=counters.get(b.courtId());var fee=(BigDecimal)row.get("fee_amount");var net=(BigDecimal)row.get("seller_net_amount");Instant handed=instant(row.get("handoff_at")),paid=instant(row.get("paid_at"));
   if(in(handed,window)){summary.f=summary.f.add(fee);c.f=c.f.add(fee);var d=day(daily,handed,window);d.f=d.f.add(fee);if(fee.signum()>0)transactions.add(transaction("FEE:"+row.get("acquisition_id"),"FACILITY_TRANSFER_FEE","RECOGNITION",true,false,true,b,names,"TRANSFER","SUCCESS",fee,handed));}
   if(handed.isBefore(cutoff)&&(paid==null||!paid.isBefore(cutoff))){summary.sellerDue=summary.sellerDue.add(net);c.sellerDue=c.sellerDue.add(net);}
   UUID payment=(UUID)row.get("payment_id");Instant purchasePaid=instant(row.get("purchase_paid_at"));
   if(!purchaseIds.contains(payment)&&in(purchasePaid,window)){var amount=(BigDecimal)row.get("amount");summary.p=summary.p.add(amount);c.p=c.p.add(amount);transactions.add(transaction("TRANSFER:"+payment,"TRANSFER_COLLECTION","IN",false,true,true,b,names,"TRANSFER","SUCCESS",amount,purchasePaid));}
  }
  for(var row:s.refunds()){
   Instant created=instant(row.get("created_at")),refunded=instant(row.get("refunded_at"));var amount=(BigDecimal)row.get("target_amount");
   if(created.isBefore(cutoff)&&(refunded==null||!refunded.isBefore(cutoff))){summary.refundDue=summary.refundDue.add(amount);var c=counters.get(byId.get((UUID)row.get("booking_id")).courtId());c.refundDue=c.refundDue.add(amount);}
  }
  for(var row:s.events()){
   Instant at=instant(row.get("performed_at"));if(!in(at,window))continue;var b=byId.get((UUID)row.get("booking_id"));var c=counters.get(b.courtId());var amount=(BigDecimal)row.get("amount");String kind=row.get("kind").toString();boolean success=row.get("outcome").equals("SUCCESS");
   if(success){if(kind.equals("POLICY_REFUND")){summary.h=summary.h.add(amount);c.h=c.h.add(amount);var d=day(daily,at,window);d.h=d.h.add(amount);}else if(kind.equals("SELLER_PAYOUT")){summary.sellerPaid=summary.sellerPaid.add(amount);c.sellerPaid=c.sellerPaid.add(amount);}else{summary.technicalRefunded=summary.technicalRefunded.add(amount);c.technicalRefunded=c.technicalRefunded.add(amount);}}
   transactions.add(transaction("EXECUTION:"+row.get("id"),kind,"OUT",kind.equals("POLICY_REFUND"),true,success,b,names,kind.equals("SELLER_PAYOUT")?"TRANSFER":kind,row.get("outcome").toString(),amount,at));
  }
  transactions.sort(Comparator.comparing(Transaction::effectiveAt).thenComparing(Transaction::id).reversed());
  int offset=(int)Math.min((long)page*size,transactions.size());var pages=new Page(page,size,transactions.size(),(transactions.size()+size-1)/size,List.copyOf(transactions.subList(offset,Math.min(offset+size,transactions.size()))));
  var courtRows=new ArrayList<Court>();counters.forEach((id,c)->courtRows.add(new Court(id,names.getOrDefault(id,"Sân đã lưu trữ"),c.bookings,c.full,decimal(c.booked),decimal(c.used),c.cancelled,c.expired,c.money())));courtRows.sort(Comparator.comparing(Court::courtName).thenComparing(Court::courtId));
  var days=new ArrayList<Day>();daily.forEach((date,c)->days.add(new Day(date,decimal(c.r),decimal(c.f),decimal(c.h))));
  String availability=reasons.isEmpty()?"COMPLETE":transactions.stream().anyMatch(Transaction::countsTowardTotals)?"PARTIAL":"UNAVAILABLE";
  return new Dashboard(facility,window,asOf,cutoff,"DEMO","Dữ liệu DEMO — không phát sinh giao dịch tiền thật",
   Map.of("courtCollected","paidAt","transferCollected","paidAt","transferFees","handoffAt","sellerPaid","demoPaidAt","policyRefunded","demoRefundedAt","bookings","startsAt; current status at asOf","obligations","created before cutoff minus successful executions before cutoff"),availability,List.copyOf(reasons),new Summary(summary.bookings,summary.full,decimal(summary.booked),decimal(summary.used),summary.cancelled,summary.expired,summary.money()),days,courtRows,pages);
 }
 private Transaction transaction(String id,String kind,String direction,boolean revenue,boolean cashflow,boolean counts,Booking b,Map<UUID,String> names,String purpose,String state,BigDecimal amount,Instant at){return new Transaction(id,kind,direction,revenue,cashflow,counts,b.id(),b.courtId(),names.getOrDefault(b.courtId(),"Sân đã lưu trữ"),b.source(),purpose,state,decimal(amount),"VND",at);}
 private static final class Counter {
  long bookings,full,cancelled,expired;BigDecimal booked=BigDecimal.ZERO,used=BigDecimal.ZERO,r=BigDecimal.ZERO,f=BigDecimal.ZERO,h=BigDecimal.ZERO,p=BigDecimal.ZERO,sellerPaid=BigDecimal.ZERO,sellerDue=BigDecimal.ZERO,refundDue=BigDecimal.ZERO,technicalRefunded=BigDecimal.ZERO;
  Money money(){return new Money(decimal(r),decimal(f),decimal(r.add(f)),decimal(h),decimal(r.add(f).subtract(h)),decimal(p),decimal(sellerPaid),decimal(sellerDue),decimal(refundDue),decimal(technicalRefunded));}
 }
 private static boolean in(Instant at,FacilityReportWindow w){return at!=null&&!at.isBefore(w.from())&&at.isBefore(w.to());}
 private static boolean overlaps(Booking b,FacilityReportWindow w){return b.startsAt().isBefore(w.to())&&b.endsAt().isAfter(w.from());}
 private static Counter day(Map<LocalDate,Counter> days,Instant at,FacilityReportWindow window){return days.get(at.atZone(ZoneId.of(window.timezone())).toLocalDate());}
 private static BigDecimal minutes(Instant start,Instant end){return end.isAfter(start)?BigDecimal.valueOf(Duration.between(start,end).toMillis()).divide(BigDecimal.valueOf(60000),4,RoundingMode.HALF_UP).stripTrailingZeros():BigDecimal.ZERO;}
 private static Instant min(Instant... values){return Arrays.stream(values).min(Comparator.naturalOrder()).orElseThrow();}
 private static Instant max(Instant... values){return Arrays.stream(values).max(Comparator.naturalOrder()).orElseThrow();}
 private static Instant instant(Object at){return at instanceof Timestamp t?t.toInstant():at instanceof OffsetDateTime t?t.toInstant():null;}
 private static String decimal(BigDecimal amount){return amount.stripTrailingZeros().toPlainString();}
}
