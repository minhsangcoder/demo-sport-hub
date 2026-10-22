package com.sporthub.booking.service;

import com.sporthub.booking.repository.BookingRepository;
import com.sporthub.booking.web.BookingDtos.Booking;
import com.sporthub.common.exception.ConflictException;
import com.sporthub.common.money.BookingPaymentFingerprint;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;

/** Bounded SQL reads shared by check-in and reports. Never calls Payment or locks per receipt. */
@Service @Transactional(readOnly=true,isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
public class LocalAppliedCollections {
    public record Fact(UUID paymentId,UUID bookingId,UUID memberId,UUID payerId,BigDecimal amount,String currency,
                       Instant paidAt,String purpose,String fingerprint,List<String> reasons){public boolean verified(){return reasons.isEmpty();}}
    public record Assessment(UUID bookingId,BigDecimal amountApplied,BigDecimal amountRequired,boolean fullyPaid,List<String> reasons) {}
    public record Snapshot(Instant capturedAt,List<Fact> facts,Map<UUID,Assessment> assessments,Set<UUID> reviewedPaymentIds) {}
    public record PreflightItem(UUID bookingId,String status,String amountVerified,String amountRequired,boolean fullyPaid,boolean reconciliationRequired,List<String> reasons) {}
    public record Preflight(Instant capturedAt,List<PreflightItem> items,UUID nextAfterId,boolean more) {}
    private final BookingRepository repo;private final Clock clock;
    public LocalAppliedCollections(BookingRepository repo,Clock clock){this.repo=repo;this.clock=clock;}
    public void requireFullyPaid(Booking booking){
        var assessment=load(List.of(booking)).assessments().get(booking.id());
        if(!assessment.fullyPaid())throw new ConflictException("Chưa xác minh đủ thanh toán để check-in. Cơ sở cần đối soát biên nhận và lịch sử thanh toán.");
    }
    public Preflight preflight(UUID facility,UUID after,int limit){
        if(limit<1||limit>500)throw new IllegalArgumentException("Preflight limit must be 1..500");
        var rows=repo.list("SELECT * FROM bookings WHERE facility_id=? AND (?::uuid IS NULL OR id>?::uuid) ORDER BY id LIMIT ?",facility,after,after,limit+1);boolean more=rows.size()>limit;var selected=rows.subList(0,Math.min(limit,rows.size()));var snapshot=load(selected);var result=new ArrayList<PreflightItem>();
        for(var booking:selected){var a=snapshot.assessments().get(booking.id());boolean expectedUnpaid=booking.status().equals("PENDING")&&a.reasons().stream().allMatch(reason->Set.of("NO_APPLIED_COLLECTION","APPLIED_TOTAL_DIFFERS","GROUP_MEMBER_TOTALS_DIFFER").contains(reason));
            result.add(new PreflightItem(booking.id(),booking.status(),a.amountApplied().toPlainString(),a.amountRequired().toPlainString(),a.fullyPaid(),!a.fullyPaid()&&!expectedUnpaid,a.reasons()));}
        return new Preflight(snapshot.capturedAt(),List.copyOf(result),more?selected.getLast().id():null,more);
    }
    public Snapshot load(Collection<Booking> bookings){
        if(bookings.isEmpty())return new Snapshot(clock.instant(),List.of(),Map.of(),Set.of());
        var byId=new LinkedHashMap<UUID,Booking>();bookings.forEach(b->byId.put(b.id(),b));String ids="{"+String.join(",",byId.keySet().stream().map(UUID::toString).toList())+"}";
        var rows=repo.jdbc().queryForList("""
          SELECT b.id booking_id,b.payment_id,NULL::uuid member_id,h.payer_id,b.amount,b.currency,b.paid_at,'BOOKING' purpose,
            r.booking_id receipt_booking,r.fingerprint,h.history_count,NULL::text history_amount,TRUE member_active,
            NULL::uuid member_user,NULL::uuid group_owner,NULL::numeric member_due,NULL::numeric member_paid,NULL::text member_state,
            (h.payer_id=b.created_by OR b.source='OFFLINE_COUNTER' AND EXISTS(SELECT 1 FROM counter_booking_guests g WHERE g.booking_id=b.id AND g.linked_customer_id=h.payer_id)) payer_valid,
            ((SELECT count(*) FROM bookings x WHERE x.payment_id=b.payment_id)+(SELECT count(*) FROM group_contributions x WHERE x.payment_id=b.payment_id)) usage_count
          FROM bookings b LEFT JOIN booking_payment_receipts r ON r.payment_id=b.payment_id
          LEFT JOIN LATERAL(SELECT count(*) history_count,(array_agg(actor_id ORDER BY created_at,id))[1] payer_id FROM booking_history
            WHERE booking_id=b.id AND action='CONFIRMED' AND details->>'paymentId'=b.payment_id::text) h ON TRUE
          WHERE b.id=ANY(?::uuid[]) AND b.payment_id IS NOT NULL AND NOT EXISTS(SELECT 1 FROM booking_groups g WHERE g.id=b.id)
          UNION ALL
          SELECT b.id,c.payment_id,c.member_id,c.payer_id,c.amount,b.currency,c.paid_at,'GROUP_CONTRIBUTION',
            r.booking_id,r.fingerprint,h.history_count,h.history_amount,m.active,m.user_id,g.owner_id,m.amount_due,m.amount_paid,m.payment_state,
            c.payer_id IN(m.user_id,g.owner_id),
            ((SELECT count(*) FROM bookings x WHERE x.payment_id=c.payment_id)+(SELECT count(*) FROM group_contributions x WHERE x.payment_id=c.payment_id))
          FROM group_contributions c JOIN bookings b ON b.id=c.group_id JOIN booking_groups g ON g.id=b.id
          LEFT JOIN group_members m ON m.id=c.member_id AND m.group_id=b.id LEFT JOIN booking_payment_receipts r ON r.payment_id=c.payment_id
          LEFT JOIN LATERAL(SELECT count(*) history_count,(array_agg(details->>'amount' ORDER BY created_at,id))[1] history_amount FROM booking_history
            WHERE booking_id=b.id AND action='GROUP_CONTRIBUTION_PAID' AND actor_id=c.payer_id AND details->>'paymentId'=c.payment_id::text AND details->>'memberId'=c.member_id::text) h ON TRUE
          WHERE b.id=ANY(?::uuid[])
          """,ids,ids);
        var facts=new ArrayList<Fact>();for(var row:rows)facts.add(fact(row));
        var groups=repo.jdbc().queryForList("""
          SELECT g.id,g.allocations_locked,COALESCE(sum(m.amount_due) FILTER(WHERE m.active),0) total_due,
            COALESCE(sum(m.amount_paid) FILTER(WHERE m.active),0) total_paid
          FROM booking_groups g LEFT JOIN group_members m ON m.group_id=g.id WHERE g.id=ANY(?::uuid[])
          GROUP BY g.id,g.allocations_locked
          """,ids);
        var groupById=new HashMap<UUID,Map<String,Object>>();groups.forEach(g->groupById.put((UUID)g.get("id"),g));
        var reviewRows=repo.jdbc().queryForList("SELECT booking_id,payment_id FROM payment_reconciliation WHERE booking_id=ANY(?::uuid[]) AND state='REQUIRES_REVIEW'",ids);
        var reviewIds=new HashSet<UUID>();var reviewedPayments=new HashSet<UUID>();reviewRows.forEach(r->{reviewIds.add((UUID)r.get("booking_id"));reviewedPayments.add((UUID)r.get("payment_id"));});
        var factByBooking=new HashMap<UUID,List<Fact>>();facts.forEach(f->factByBooking.computeIfAbsent(f.bookingId(),key->new ArrayList<>()).add(f));
        var assessments=new LinkedHashMap<UUID,Assessment>();
        for(var booking:byId.values()){
            var applied=factByBooking.getOrDefault(booking.id(),List.of());var reasons=new TreeSet<String>();BigDecimal amount=BigDecimal.ZERO;
            for(var fact:applied){if(fact.verified())amount=amount.add(fact.amount());else reasons.addAll(fact.reasons());}
            if(applied.isEmpty())reasons.add("NO_APPLIED_COLLECTION");
            if(amount.compareTo(booking.amount())!=0)reasons.add("APPLIED_TOTAL_DIFFERS");
            if(reviewIds.contains(booking.id()))reasons.add("LOCAL_RECONCILIATION_REQUIRED");
            var group=groupById.get(booking.id());if(group!=null&&(!Boolean.TRUE.equals(group.get("allocations_locked"))||((BigDecimal)group.get("total_due")).compareTo(booking.amount())!=0||((BigDecimal)group.get("total_paid")).compareTo(amount)!=0))reasons.add("GROUP_MEMBER_TOTALS_DIFFER");
            assessments.put(booking.id(),new Assessment(booking.id(),amount,booking.amount(),reasons.isEmpty(),List.copyOf(reasons)));
        }
        return new Snapshot(clock.instant(),List.copyOf(facts),Collections.unmodifiableMap(assessments),Set.copyOf(reviewedPayments));
    }
    private Fact fact(Map<String,Object> row){
        UUID booking=(UUID)row.get("booking_id"),payment=(UUID)row.get("payment_id"),payer=(UUID)row.get("payer_id"),member=(UUID)row.get("member_id");
        var amount=(BigDecimal)row.get("amount");String currency=(String)row.get("currency"),purpose=(String)row.get("purpose"),fingerprint=(String)row.get("fingerprint");Instant paid=instant(row.get("paid_at"));var reasons=new TreeSet<String>();
        if(!booking.equals(row.get("receipt_booking"))||fingerprint==null)reasons.add("MISSING_OR_WRONG_RECEIPT");
        if(((Number)row.get("history_count")).longValue()!=1||payer==null)reasons.add("MISSING_OR_AMBIGUOUS_SUCCESS_HISTORY");
        if(!Boolean.TRUE.equals(row.get("payer_valid")))reasons.add("PAYER_BINDING_DIFFERS");
        if(((Number)row.get("usage_count")).longValue()!=1)reasons.add("PAYMENT_APPLIED_MULTIPLE_TIMES");
        if(amount==null||amount.signum()<=0||!"VND".equals(currency)||paid==null)reasons.add("INVALID_APPLIED_MONEY_TIME");
        if(payer!=null&&paid!=null&&amount!=null&&fingerprint!=null){
            boolean matches=member!=null?fingerprint.equals(BookingPaymentFingerprint.of(booking,payer,member.toString(),purpose,"SUCCESS",amount,currency,paid)):
                    fingerprint.equals(BookingPaymentFingerprint.of(booking,payer,"",purpose,"SUCCESS",amount,currency,paid))||fingerprint.equals(BookingPaymentFingerprint.of(booking,payer,"null",purpose,"SUCCESS",amount,currency,paid));
            if(!matches)reasons.add("RECEIPT_FINGERPRINT_DIFFERS");
        }
        if(member!=null){
            boolean own=payer!=null&&payer.equals(row.get("member_user"));
            if(!Boolean.TRUE.equals(row.get("member_active"))||amount==null||!equalMoney(amount,row.get("member_due"))||!equalMoney(amount,row.get("member_paid"))||!(own?"PAID":"PAID_BY_OWNER").equals(row.get("member_state")))reasons.add("GROUP_MEMBER_BINDING_DIFFERS");
            try{if(new BigDecimal(Objects.toString(row.get("history_amount"),"")).compareTo(amount)!=0)reasons.add("GROUP_SUCCESS_HISTORY_AMOUNT_DIFFERS");}catch(NumberFormatException ex){reasons.add("GROUP_SUCCESS_HISTORY_AMOUNT_DIFFERS");}
        }
        return new Fact(payment,booking,member,payer,amount,currency,paid,purpose,fingerprint,List.copyOf(reasons));
    }
    private boolean equalMoney(BigDecimal expected,Object actual){return actual instanceof BigDecimal amount&&expected.compareTo(amount)==0;}
    private Instant instant(Object value){return value instanceof Timestamp t?t.toInstant():value instanceof OffsetDateTime t?t.toInstant():null;}
}
