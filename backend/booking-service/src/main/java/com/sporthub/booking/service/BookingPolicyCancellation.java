package com.sporthub.booking.service;

import com.sporthub.booking.repository.BookingRepository;
import com.sporthub.booking.web.BookingDtos.Booking;
import com.sporthub.common.exception.*;
import com.sporthub.common.money.*;
import com.sporthub.common.security.RemoteIdentity.Caller;
import com.sporthub.common.security.Signatures;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;

/** Quote and cancel under the court lock. Money is executed separately by the facility. */
@Service
public class BookingPolicyCancellation {
    public record Input(UUID quoteId,String reason) {}
    private record Basis(BigDecimal base,List<DemoRefundMath.Payment> payments,UUID acquisition,String fingerprint) {}
    private final BookingRepository repo;private final BookingDependencies dependencies;private final LocalAppliedCollections collections;
    private final BookingLifecycle lifecycle;private final BookingDemoFinance finance;private final PaymentEvidenceClient evidence;
    private final Clock clock;private final TransactionTemplate tx;
    public BookingPolicyCancellation(BookingRepository repo,BookingDependencies dependencies,LocalAppliedCollections collections,
            BookingLifecycle lifecycle,BookingDemoFinance finance,PaymentEvidenceClient evidence,Clock clock,PlatformTransactionManager manager){
        this.repo=repo;this.dependencies=dependencies;this.collections=collections;this.lifecycle=lifecycle;this.finance=finance;this.evidence=evidence;this.clock=clock;this.tx=new TransactionTemplate(manager);
    }
    public Map<String,Object> quote(UUID id,Caller actor,String token){
        var before=repo.find(id);authority(before,actor,token);
        if(actor!=null&&actor.hasRole("CUSTOMER")&&!actor.hasRole("OWNER")&&!actor.hasRole("STAFF")){
            int transfers=repo.jdbc().queryForObject("SELECT count(*) FROM transfer_handoffs WHERE booking_id=?",Integer.class,id);
            if(transfers==0){
                throw new ConflictException("Không áp dụng hoàn tiền cho người đặt ban đầu khi tự hủy. Chính sách hoàn tiền chỉ áp dụng cho người mua lại lượt đặt qua chuyển nhượng; bạn có thể đăng chuyển nhượng lượt đặt.");
            }
        }
        var basis=basis(before);requireDemo(basis,before);
        return tx.execute(status->{repo.lock(before.courtId());var b=repo.find(id);eligible(b);var current=basis(b);
            if(!current.fingerprint().equals(basis.fingerprint()))throw new ConflictException("Payment or ownership changed; request a new quote");
            var p=policy(id);int rate=((Number)p.get("refund_rate_bps")).intValue();var allocations=DemoRefundMath.allocate(current.payments(),rate);
            var target=DemoRefundMath.target(current.base(),rate);UUID quote=UUID.randomUUID();Instant now=clock.instant(),expires=now.plusSeconds(300);
            repo.jdbc().update("""
              INSERT INTO booking_cancellation_refund_quotes(id,booking_id,booking_version,ownership_version,policy_version,policy_hash,
               base_amount,rate_bps,target_amount,currency,rounding,acquisition_id,allocations,basis_fingerprint,requested_by,created_at,expires_at)
              VALUES(?,?,?,?,?,?,?,?,?,'VND','HALF_UP',?,?::jsonb,?,?,?,?)
              """,quote,b.id(),b.version(),b.checkinTokenVersion(),p.get("policy_version"),p.get("terms_hash"),current.base(),rate,target,current.acquisition(),repo.write(allocations),current.fingerprint(),actor.id(),time(now),time(expires));
            var result=new LinkedHashMap<String,Object>();result.put("id",quote);result.put("bookingId",b.id());result.put("mode","DEMO");result.put("currency","VND");result.put("baseAmount",current.base().stripTrailingZeros().toPlainString());result.put("rateBps",rate);result.put("targetAmount",target.toPlainString());result.put("policyVersion",p.get("policy_version"));result.put("allocations",allocations);result.put("expiresAt",expires);return result;
        });
    }
    public Map<String,Object> cancel(UUID id,Input input,String key,Caller actor,String token){
        if(input==null||input.quoteId()==null||input.reason()==null||input.reason().isBlank()||input.reason().length()>500||key==null||!key.matches("[A-Za-z0-9_.:-]{1,100}"))throw new IllegalArgumentException("Quote, reason and Idempotency-Key are required");
        var before=repo.find(id);authority(before,actor,token);
        if(actor!=null&&actor.hasRole("CUSTOMER")&&!actor.hasRole("OWNER")&&!actor.hasRole("STAFF")){
            int transfers=repo.jdbc().queryForObject("SELECT count(*) FROM transfer_handoffs WHERE booking_id=?",Integer.class,id);
            if(transfers==0){
                throw new ConflictException("Không áp dụng hoàn tiền cho người đặt ban đầu khi tự hủy. Chính sách hoàn tiền chỉ áp dụng cho người mua lại lượt đặt qua chuyển nhượng; bạn có thể đăng chuyển nhượng lượt đặt.");
            }
        }
        String fingerprint=Signatures.sha256(id+"|"+input.quoteId()+"|"+input.reason());
        return tx.execute(status->{repo.lock(actor.id());repo.lock(before.courtId());var b=repo.find(id);
            var old=repo.jdbc().queryForList("SELECT fingerprint,resource_id FROM request_idempotency WHERE actor_id=? AND operation='CANCEL_WITH_POLICY' AND key=?",actor.id(),key);
            if(!old.isEmpty()){if(!fingerprint.equals(old.getFirst().get("fingerprint")))throw new ConflictException("Cancellation key reused with another request");return result(b,(UUID)old.getFirst().get("resource_id"));}
            var q=repo.jdbc().queryForList("SELECT * FROM booking_cancellation_refund_quotes WHERE id=? AND booking_id=? AND requested_by=?",input.quoteId(),id,actor.id());
            if(q.isEmpty())throw new ConflictException("Cancellation quote does not belong to this request");var quote=q.getFirst();
            if(b.status().equals("CANCELLED")){
                var existing=repo.jdbc().queryForList("SELECT id FROM demo_refund_requests WHERE source_key=?","POLICY:"+id);
                if(existing.isEmpty())throw new ConflictException("Legacy cancellation needs an explicit review");UUID refund=(UUID)existing.getFirst().get("id");remember(actor,key,fingerprint,refund);return result(b,refund);
            }
            eligible(b);var current=basis(b);var p=policy(id);
            if(!((Timestamp)quote.get("expires_at")).toInstant().isAfter(clock.instant())||((Number)quote.get("booking_version")).longValue()!=b.version()
                    ||((Number)quote.get("ownership_version")).longValue()!=b.checkinTokenVersion()||!current.fingerprint().equals(quote.get("basis_fingerprint"))
                    ||!p.get("terms_hash").equals(quote.get("policy_hash")))throw new ConflictException("Cancellation quote expired or changed; review a new quote");
            int rate=((Number)quote.get("rate_bps")).intValue();var allocations=DemoRefundMath.allocate(current.payments(),rate);var target=DemoRefundMath.target(current.base(),rate);
            if(target.compareTo((BigDecimal)quote.get("target_amount"))!=0||!repo.read(repo.write(allocations)).equals(repo.read(quote.get("allocations").toString())))throw new ConflictException("Cancellation allocation changed");
            var cancelled=lifecycle.transition(b,"CANCELLED",actor.id(),"CANCELLED",Map.of("reason",input.reason().trim(),"cancellationPolicy","FACILITY_POLICY_CANCELLATION","refundAmount",target,"refundQuoteId",input.quoteId()),clock.instant(),null);
            UUID refund=finance.createRefund(new BookingDemoFinance.Entitlement(cancelled,input.quoteId(),current.acquisition(),"POLICY_CANCELLATION","POLICY:"+id,current.base(),rate,target,((Number)p.get("policy_version")).longValue(),p.get("terms_hash").toString(),allocations,actor.id()));
            repo.jdbc().update("UPDATE booking_groups SET state='GROUP_CANCELLED' WHERE id=?",id);
            remember(actor,key,fingerprint,refund);return result(cancelled,refund);
        });
    }
    private void eligible(Booking b){
        if(!Set.of("PENDING","CONFIRMED").contains(b.status())||!clock.instant().isBefore(b.startsAt()))throw new ConflictException("Only a pending or confirmed booking may be cancelled before play");
        if(b.status().equals("PENDING")&&(!b.holdExpiresAt().isAfter(clock.instant())||!lifecycle.hasLiveHold(b,clock.instant())))throw new ConflictException("Payment hold expired or changed");
        if(repo.jdbc().queryForObject("SELECT count(*) FROM transfer_leases WHERE booking_id=? AND state IN('ACTIVE','LOCKED')",Integer.class,b.id())>0)throw new ConflictException("Resolve the active transfer before cancellation");
    }
    private void authority(Booking b,Caller actor,String token){
        boolean guest=repo.jdbc().queryForObject("SELECT count(*) FROM counter_booking_guests g WHERE g.booking_id=? AND g.linked_customer_id IS NULL AND NOT EXISTS(SELECT 1 FROM transfer_handoffs h WHERE h.booking_id=g.booking_id)",Integer.class,b.id())>0;
        if(!guest&&actor.hasRole("CUSTOMER")&&Objects.equals(b.currentHolderId(),actor.id()))return;
        if(actor.hasRole("CUSTOMER")&&repo.jdbc().queryForObject("SELECT count(*) FROM booking_groups WHERE id=? AND owner_id=?",Integer.class,b.id(),actor.id())>0)return;
        dependencies.facilityPermission(b.facilityId(),actor,token,"BOOKING_REFUND");
    }
    private Map<String,Object> policy(UUID booking){
        var rows=repo.jdbc().queryForList("SELECT * FROM booking_refund_policy_snapshots WHERE booking_id=?",booking);
        if(rows.isEmpty())throw new ConflictException("Legacy booking needs an explicit refund policy decision before quoting");return rows.getFirst();
    }
    private Basis basis(Booking b){
        var transfers=repo.jdbc().queryForList("""
          SELECT h.*,s.terms,d.booking_version FROM transfer_handoffs h
          LEFT JOIN demo_transfer_settlements s ON s.acquisition_id=h.acquisition_id
          LEFT JOIN transfer_acquisition_decisions d ON d.acquisition_id=h.acquisition_id
          WHERE h.booking_id=? ORDER BY d.booking_version DESC NULLS LAST,h.transferred_at DESC,h.acquisition_id DESC LIMIT 1
          """,b.id());
        List<DemoRefundMath.Payment> payments;UUID acquisition=null;
        if(!transfers.isEmpty()){
            var h=transfers.getFirst();if(h.get("terms")==null||!Objects.equals(h.get("buyer_id"),b.currentHolderId()))throw new ConflictException("Legacy transfer refund needs an explicit review");
            acquisition=(UUID)h.get("acquisition_id");payments=List.of(new DemoRefundMath.Payment((UUID)h.get("payment_id"),(UUID)h.get("buyer_id"),(BigDecimal)h.get("amount")));
        }else{
            var snapshot=collections.load(List.of(b));var local=snapshot.facts();
            if(local.stream().anyMatch(f->!f.verified())||!snapshot.reviewedPaymentIds().isEmpty()||b.status().equals("CONFIRMED")&&!snapshot.assessments().get(b.id()).fullyPaid())throw new ConflictException("Applied payment evidence needs review before refund");
            payments=local.stream().map(f->new DemoRefundMath.Payment(f.paymentId(),f.payerId(),f.amount())).toList();
        }
        var sorted=payments.stream().sorted(Comparator.comparing(p->p.paymentId().toString())).toList();
        BigDecimal base=payments.stream().map(DemoRefundMath.Payment::amount).reduce(BigDecimal.ZERO,BigDecimal::add);DemoRefundMath.target(base,0);
        String fingerprint=Signatures.sha256(b.id()+"|"+b.version()+"|"+b.checkinTokenVersion()+"|"+acquisition+"|"+repo.write(sorted));return new Basis(base,sorted,acquisition,fingerprint);
    }
    private void requireDemo(Basis basis,Booking b){
        if(basis.acquisition()!=null)return;
        var unknown=new ArrayList<com.sporthub.common.money.PaymentProofFacts.Binding>();
        for(var payment:basis.payments()){
            if(repo.jdbc().queryForObject("SELECT count(*) FROM booking_demo_collections WHERE payment_id=? AND booking_id=?",Integer.class,payment.paymentId(),b.id())>0)continue;
            var contribution=repo.jdbc().queryForList("SELECT member_id FROM group_contributions WHERE payment_id=? AND group_id=?",payment.paymentId(),b.id());
            UUID member=contribution.isEmpty()?null:(UUID)contribution.getFirst().get("member_id");unknown.add(new PaymentProofFacts.Binding(payment.paymentId(),b.id(),member,member==null?"BOOKING":"GROUP_CONTRIBUTION"));
        }
        if(unknown.isEmpty())return;var result=evidence.read(unknown);
        for(var p:basis.payments())if(unknown.stream().anyMatch(f->f.paymentId().equals(p.paymentId()))){
            var fact=result==null?null:result.facts().get(p.paymentId());
            if(fact==null||!fact.verified()||!fact.moneyClass().equals("DEMO")||!p.payerId().equals(fact.payerId())||p.amount().compareTo(new BigDecimal(fact.amount()))!=0)throw new ConflictException("Only verified application DEMO payments can use the simulation; legacy or other modes need review");
        }
    }
    private Map<String,Object> result(Booking b,UUID refund){return Map.of("bookingId",b.id(),"status",b.status(),"refund",finance.refundView(refund),"mode","DEMO");}
    private void remember(Caller actor,String key,String fingerprint,UUID refund){repo.jdbc().update("INSERT INTO request_idempotency(actor_id,operation,key,fingerprint,resource_id) VALUES(?,'CANCEL_WITH_POLICY',?,?,?)",actor.id(),key,fingerprint,refund);}
    private static Timestamp time(Instant instant){return Timestamp.from(instant);}
}
