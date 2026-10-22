package com.sporthub.booking.service;

import com.sporthub.booking.repository.BookingRepository;
import com.sporthub.booking.web.BookingDtos.Booking;
import com.sporthub.common.exception.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;

@Service @Transactional
public class TransferLeaseService {
    public record Command(UUID listingId,UUID bookingId,UUID sellerId,BigDecimal price,Instant deadline,
        UUID acquisitionId,UUID buyerId,Instant expiresAt,UUID paymentId,Instant paidAt,String currency,com.fasterxml.jackson.databind.JsonNode financeTerms) {
        public Command(UUID listingId,UUID bookingId,UUID sellerId,BigDecimal price,Instant deadline,UUID acquisitionId,UUID buyerId,Instant expiresAt,UUID paymentId,Instant paidAt,String currency){this(listingId,bookingId,sellerId,price,deadline,acquisitionId,buyerId,expiresAt,paymentId,paidAt,currency,null);}
    }
    private final BookingRepository repo;private final Clock clock;private final LocalAppliedCollections collections;
    private final BookingDemoFinance demo;
    @org.springframework.beans.factory.annotation.Autowired
    public TransferLeaseService(BookingRepository repo,Clock clock,LocalAppliedCollections collections,BookingDemoFinance demo){this.repo=repo;this.clock=clock;this.collections=Objects.requireNonNull(collections);this.demo=demo;}
    public TransferLeaseService(BookingRepository repo,Clock clock,LocalAppliedCollections collections){this(repo,clock,collections,null);}
    public TransferLeaseService(BookingRepository repo,Clock clock){this(repo,clock,new LocalAppliedCollections(repo,clock));}
    @Transactional(readOnly=true) public Map<String,Object> receipt(UUID listing){
        var rows=repo.jdbc().queryForList("""
            SELECT l.*,b.currency,h.command_fingerprint,h.paid_at,h.transferred_at
            FROM transfer_leases l JOIN bookings b ON b.id=l.booking_id
            LEFT JOIN transfer_handoffs h ON h.listing_id=l.listing_id WHERE l.listing_id=?
            """,listing);
        if(rows.isEmpty())throw new ResourceNotFoundException("Transfer lease not found");
        var l=rows.getFirst();var view=new LinkedHashMap<String,Object>();
        for(String k:List.of("listing_id","booking_id","seller_id","acquisition_id","buyer_id","payment_id","price","currency","state","command_fingerprint")){
            String[] parts=k.split("_");StringBuilder name=new StringBuilder(parts[0]);for(int i=1;i<parts.length;i++)name.append(Character.toUpperCase(parts[i].charAt(0))).append(parts[i].substring(1));view.put(name.toString(),l.get(k));
        }
        for(String k:List.of("deadline","expires_at","paid_at","transferred_at")){
            String name=switch(k){case "expires_at"->"expiresAt";case "paid_at"->"paidAt";case "transferred_at"->"transferredAt";default->k;};
            view.put(name,l.get(k)==null?null:((Timestamp)l.get(k)).toInstant());
        }
        return view;
    }
    public com.fasterxml.jackson.databind.JsonNode inspect(UUID id){
        var b=repo.find(id);
        var view=(com.fasterxml.jackson.databind.node.ObjectNode)repo.read(repo.write(b));
        var policies=repo.jdbc().queryForList("SELECT * FROM booking_refund_policy_snapshots WHERE booking_id=?",id);
        if(!policies.isEmpty()){
            var p=policies.getFirst();view.set("refundPolicy",repo.read(repo.write(Map.of("configured",true,"id",p.get("policy_id"),"version",p.get("policy_version"),"rateBps",p.get("refund_rate_bps"),"currency",p.get("currency"),"rounding",p.get("rounding"),"termsHash",p.get("terms_hash")))));
        }
        if(b.currentHolderId()==null){
            view.put("transferBlockedReason","Guest walk-in bookings cannot be transferred");
            return view;
        }
        try{eligible(b,b.currentHolderId());view.put("transferBlockedReason","");}
        catch(ConflictException ex){view.put("transferBlockedReason",ex.getMessage());}
        catch(ForbiddenException ex){view.put("transferBlockedReason",ex.getMessage());}
        return view;
    }
    public Booking command(String action,Command c){
        if(c.listingId()==null||c.bookingId()==null||c.sellerId()==null)throw new IllegalArgumentException("Transfer identity is required");
        var b=repo.find(c.bookingId());repo.lock(b.courtId());b=repo.find(b.id());
        var rows=repo.jdbc().queryForList("SELECT * FROM transfer_leases WHERE listing_id=?",c.listingId());
        var lease=rows.isEmpty()?null:rows.get(0);
        if(c.acquisitionId()!=null&&Set.of("lock","complete").contains(action)){
            var decisions=repo.jdbc().queryForList("SELECT * FROM transfer_acquisition_decisions WHERE acquisition_id=?",c.acquisitionId());
            if(!decisions.isEmpty()){
                var decision=decisions.getFirst();identity(decision,c);
                if(decision.get("decision").equals("ABORTED"))throw new ConflictException("Acquisition was authoritatively aborted; it cannot lock or complete");
                if(action.equals("complete")&&fingerprint(c).equals(decision.get("command_fingerprint")))return b;
                throw new ConflictException("Acquisition already has an immutable completed decision");
            }
        }
        if(lease!=null&&(!lease.get("booking_id").equals(b.id())||!lease.get("seller_id").equals(c.sellerId())))throw new ConflictException("Transfer lease identity mismatch");
        if(action.equals("complete")&&lease!=null&&lease.get("state").equals("COMPLETED")){
            var proof=repo.jdbc().queryForList("SELECT command_fingerprint FROM transfer_handoffs WHERE listing_id=?",c.listingId());
            if(!Objects.equals(lease.get("payment_id"),c.paymentId())||!Objects.equals(lease.get("buyer_id"),c.buyerId())||!Objects.equals(lease.get("acquisition_id"),c.acquisitionId())
                ||c.price()==null||((BigDecimal)lease.get("price")).compareTo(c.price())!=0||!b.currency().equals(c.currency())
                ||(!proof.isEmpty()&&!proof.getFirst().get("command_fingerprint").equals(fingerprint(c))))throw new ConflictException("Transfer was completed with different input");return b;
        }
        if(action.equals("withdraw")&&lease==null)return b;
        if(action.equals("withdraw")||action.equals("unlock")){
            if(lease==null)throw new ResourceNotFoundException("Transfer lease not found");
            if(lease.get("state").equals("COMPLETED"))throw new ConflictException("Completed transfer cannot be released");
            if(action.equals("withdraw")&&lease.get("state").equals("RELEASED"))return b;
            if(action.equals("unlock")&&!Objects.equals(lease.get("acquisition_id"),c.acquisitionId()))return b;
            String state=action.equals("withdraw")||!instant(lease,"deadline").isAfter(clock.instant())?"RELEASED":"ACTIVE";
            repo.jdbc().update("UPDATE transfer_leases SET state=?,buyer_id=NULL,acquisition_id=NULL,expires_at=NULL,updated_at=NOW() WHERE listing_id=?",state,c.listingId());
            audit(c,action);return b;
        }
        eligible(b,c.sellerId());
        if(action.equals("register")||action.equals("edit")){
            if(c.price()==null||c.price().signum()<=0||c.price().compareTo(b.amount())>0||c.price().stripTrailingZeros().scale()>0)throw new ConflictException("Transfer price exceeds original booking amount or is not integral VND");
            if(c.deadline()==null||!c.deadline().isAfter(clock.instant())||c.deadline().isAfter(b.startsAt()))throw new ConflictException("Transfer deadline is outside booking interval");
            if(lease!=null){if(!lease.get("state").equals("ACTIVE"))throw new ConflictException("Transfer listing is locked or final");boolean same=((BigDecimal)lease.get("price")).compareTo(c.price())==0&&instant(lease,"deadline").equals(c.deadline());if(action.equals("register")){if(!same)throw new ConflictException("Listing registration input changed");return b;}if(same)return b;}
            else {
                Integer active=repo.jdbc().queryForObject("SELECT count(*) FROM transfer_leases WHERE booking_id=? AND state IN ('ACTIVE','LOCKED') AND deadline>?",Integer.class,b.id(),Timestamp.from(clock.instant()));
                if(active>0)throw new ConflictException("Booking already has an active transfer listing");
                repo.jdbc().update("UPDATE transfer_leases SET state='RELEASED' WHERE booking_id=? AND state IN ('ACTIVE','LOCKED') AND deadline<=?",b.id(),Timestamp.from(clock.instant()));
            }
            repo.jdbc().update("INSERT INTO transfer_leases(listing_id,booking_id,seller_id,price,deadline,state) VALUES(?,?,?,?,?,'ACTIVE') ON CONFLICT(listing_id) DO UPDATE SET price=EXCLUDED.price,deadline=EXCLUDED.deadline,updated_at=NOW()",c.listingId(),b.id(),c.sellerId(),c.price(),Timestamp.from(c.deadline()));audit(c,action);return b;
        }
        if(lease==null)throw new ResourceNotFoundException("Transfer lease not found");
        if(action.equals("lock")){
            if(c.acquisitionId()==null||c.buyerId()==null||c.buyerId().equals(c.sellerId())||c.expiresAt()==null||!c.expiresAt().isAfter(clock.instant())||c.expiresAt().isAfter(instant(lease,"deadline")))throw new ConflictException("Invalid transfer acquisition");
            if(lease.get("state").equals("LOCKED")&&Objects.equals(lease.get("acquisition_id"),c.acquisitionId())&&Objects.equals(lease.get("buyer_id"),c.buyerId()))return b;
            if(lease.get("state").equals("LOCKED")&&instant(lease,"expires_at").isAfter(clock.instant()))throw new ConflictException("Another buyer already locked this booking");
            if(!Set.of("ACTIVE","LOCKED").contains(lease.get("state"))||!instant(lease,"deadline").isAfter(clock.instant()))throw new ConflictException("Transfer listing is no longer active");
            repo.jdbc().update("UPDATE transfer_leases SET state='LOCKED',acquisition_id=?,buyer_id=?,expires_at=?,updated_at=NOW() WHERE listing_id=?",c.acquisitionId(),c.buyerId(),Timestamp.from(c.expiresAt()),c.listingId());audit(c,action);return b;
        }
        if(action.equals("complete")){
            if(!lease.get("state").equals("LOCKED")||!Objects.equals(lease.get("acquisition_id"),c.acquisitionId())||!Objects.equals(lease.get("buyer_id"),c.buyerId())||c.paymentId()==null||c.paidAt()==null||c.price()==null||!c.paidAt().isBefore(instant(lease,"expires_at"))||((BigDecimal)lease.get("price")).compareTo(c.price())!=0||!b.currency().equals(c.currency())||!instant(lease,"expires_at").equals(c.expiresAt())||!instant(lease,"deadline").equals(c.deadline()))throw new ConflictException("Verified payment does not match the booking transfer lease");
            String fingerprint=fingerprint(c);Instant handoffAt=clock.instant();
            repo.jdbc().update("UPDATE bookings SET customer_id=?,current_holder_id=?,booking_code=upper(substr(replace(uuid_generate_v4()::text,'-',''),1,10)),checkin_token_version=checkin_token_version+1,version=version+1,updated_at=NOW() WHERE id=?",c.buyerId(),c.buyerId(),b.id());
            repo.jdbc().update("UPDATE slot_reservations SET holder_id=? WHERE id=(SELECT reservation_id FROM bookings WHERE id=?)",c.buyerId(),b.id());
            repo.jdbc().update("UPDATE transfer_leases SET state='COMPLETED',payment_id=?,updated_at=NOW() WHERE listing_id=?",c.paymentId(),c.listingId());
            repo.jdbc().update("INSERT INTO transfer_handoffs(listing_id,booking_id,seller_id,acquisition_id,buyer_id,payment_id,amount,currency,paid_at,expires_at,deadline,command_fingerprint,transferred_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)",c.listingId(),b.id(),c.sellerId(),c.acquisitionId(),c.buyerId(),c.paymentId(),c.price(),c.currency(),Timestamp.from(c.paidAt()),Timestamp.from(c.expiresAt()),Timestamp.from(c.deadline()),fingerprint,Timestamp.from(handoffAt));
            repo.jdbc().update("INSERT INTO transfer_acquisition_decisions(acquisition_id,listing_id,booking_id,decision,command_fingerprint,payment_id,seller_id,buyer_id,amount,currency,reason,decided_at,booking_version) VALUES(?,?,?,'COMPLETED',?,?,?,?,?,?,?, ?,?)",c.acquisitionId(),c.listingId(),b.id(),fingerprint,c.paymentId(),c.sellerId(),c.buyerId(),c.price(),c.currency(),"Verified handoff committed",Timestamp.from(handoffAt),b.version()+1);
            if(c.financeTerms()!=null){if(demo==null)throw new ConflictException("DEMO finance handler is unavailable");demo.completed(c.financeTerms(),c.paymentId(),repo.find(b.id()),handoffAt);}
            repo.history(b.id(),c.buyerId(),"TRANSFER_COMPLETED",Map.of("listingId",c.listingId(),"previousHolderId",c.sellerId(),"newHolderId",c.buyerId(),"paymentId",c.paymentId(),"acquisitionId",c.acquisitionId(),"amount",c.price(),"currency",c.currency(),"paidAt",c.paidAt()));audit(c,action);return repo.find(b.id());
        }
        throw new IllegalArgumentException("Unsupported transfer command");
    }
    /** The same court lock serializes this fence with complete/check-in/cancellation. */
    public Map<String,Object> abort(Command c,String reason){
        if(c==null||c.listingId()==null||c.bookingId()==null||c.sellerId()==null||c.acquisitionId()==null||c.buyerId()==null||c.buyerId().equals(c.sellerId())||reason==null||reason.isBlank()||reason.length()>500)throw new IllegalArgumentException("Bound acquisition identity and abort reason required");
        var b=repo.find(c.bookingId());repo.lock(b.courtId());b=repo.find(b.id());
        var existing=decision(c.acquisitionId());
        if(!existing.get("decision").equals("UNKNOWN")){
            if(existing.get("decision").equals("REVIEW"))throw new ConflictException("Legacy completed acquisition has no verifiable handoff receipt");
            identity(existing,c);return existing;
        }
        var rows=repo.jdbc().queryForList("SELECT * FROM transfer_leases WHERE listing_id=?",c.listingId());var lease=rows.isEmpty()?null:rows.getFirst();
        if(lease!=null&&(!b.id().equals(lease.get("booking_id"))||!c.sellerId().equals(lease.get("seller_id"))))throw new ConflictException("Abort lease identity mismatch");
        if(lease==null&&!Objects.equals(b.currentHolderId(),c.sellerId()))throw new ConflictException("Unknown acquisition seller does not own the booking");
        if(lease!=null&&c.acquisitionId().equals(lease.get("acquisition_id"))&&!c.buyerId().equals(lease.get("buyer_id")))throw new ConflictException("Abort buyer differs from the locked acquisition");
        if(c.price()==null||c.price().signum()<=0||c.price().stripTrailingZeros().scale()>0||c.price().compareTo(b.amount())>0||!b.currency().equals(c.currency()))throw new ConflictException("Abort amount and currency must match the acquisition terms");
        String fingerprint=com.sporthub.common.security.Signatures.sha256("ABORT|"+c.listingId()+"|"+c.bookingId()+"|"+c.sellerId()+"|"+c.acquisitionId()+"|"+c.buyerId()+"|"+c.paymentId()+"|"+c.price().stripTrailingZeros().toPlainString()+"|"+c.currency());
        repo.jdbc().update("INSERT INTO transfer_acquisition_decisions(acquisition_id,listing_id,booking_id,decision,command_fingerprint,payment_id,seller_id,buyer_id,amount,currency,reason,decided_at,booking_version) VALUES(?,?,?,'ABORTED',?,?,?,?,?,?,?,?,?)",c.acquisitionId(),c.listingId(),b.id(),fingerprint,c.paymentId(),c.sellerId(),c.buyerId(),c.price(),c.currency(),reason,Timestamp.from(clock.instant()),b.version());
        if(c.financeTerms()!=null&&c.paymentId()!=null){if(demo==null)throw new ConflictException("DEMO finance handler is unavailable");demo.aborted(c.financeTerms(),c.paymentId(),b);}
        if(lease!=null&&c.acquisitionId().equals(lease.get("acquisition_id"))&&lease.get("state").equals("LOCKED")){
            repo.jdbc().update("UPDATE transfer_leases SET state=?,acquisition_id=NULL,buyer_id=NULL,expires_at=NULL,updated_at=NOW() WHERE listing_id=?",instant(lease,"deadline").isAfter(clock.instant())?"ACTIVE":"RELEASED",c.listingId());
            audit(c,"ABORTED");
        }
        return decision(c.acquisitionId());
    }
    @Transactional(readOnly=true)
    public Map<String,Object> decision(UUID acquisition){
        var stored=repo.jdbc().queryForList("SELECT * FROM transfer_acquisition_decisions WHERE acquisition_id=?",acquisition);
        if(!stored.isEmpty())return decisionView(stored.getFirst(),"ACQUISITION_DECISION");
        var handoff=repo.jdbc().queryForList("SELECT h.*,b.version booking_version,'COMPLETED'::text decision,'Existing immutable handoff receipt'::text reason,h.transferred_at decided_at FROM transfer_handoffs h JOIN bookings b ON b.id=h.booking_id WHERE h.acquisition_id=?",acquisition);
        if(!handoff.isEmpty())return decisionView(handoff.getFirst(),"LEGACY_HANDOFF_RECEIPT");
        if(repo.jdbc().queryForObject("SELECT count(*) FROM transfer_leases WHERE acquisition_id=? AND state='COMPLETED'",Integer.class,acquisition)>0)return Map.of("acquisitionId",acquisition,"decision","REVIEW","reason","LEGACY_HANDOFF_RECEIPT_MISSING");
        return Map.of("acquisitionId",acquisition,"decision","UNKNOWN");
    }
    private Map<String,Object> decisionView(Map<String,Object> row,String origin){
        var result=new LinkedHashMap<String,Object>();result.put("origin",origin);
        for(String key:List.of("acquisition_id","listing_id","booking_id","decision","command_fingerprint","payment_id","seller_id","buyer_id","amount","currency","reason","decided_at","booking_version")){
            String[] parts=key.split("_");StringBuilder name=new StringBuilder(parts[0]);for(int i=1;i<parts.length;i++)name.append(Character.toUpperCase(parts[i].charAt(0))).append(parts[i].substring(1));
            Object value=row.get(key);result.put(name.toString(),value instanceof Timestamp time?time.toInstant():value);
        }return result;
    }
    private void identity(Map<String,Object> row,Command c){
        Object listing=row.containsKey("listing_id")?row.get("listing_id"):row.get("listingId");Object booking=row.containsKey("booking_id")?row.get("booking_id"):row.get("bookingId");
        Object seller=row.containsKey("seller_id")?row.get("seller_id"):row.get("sellerId");Object buyer=row.containsKey("buyer_id")?row.get("buyer_id"):row.get("buyerId");
        if(!Objects.equals(listing,c.listingId())||!Objects.equals(booking,c.bookingId())||!Objects.equals(seller,c.sellerId())||!Objects.equals(buyer,c.buyerId()))throw new ConflictException("Acquisition decision identity mismatch");
    }
    private void eligible(Booking b,UUID seller){
        if(b.currentHolderId()==null||seller==null||!b.currentHolderId().equals(seller))throw new ForbiddenException("Only the current booking holder may list a transfer");
        if(!b.status().equals("CONFIRMED")||b.paidAt()==null||!b.startsAt().isAfter(clock.instant()))throw new ConflictException("Only paid confirmed future bookings can be transferred");
        collections.requireFullyPaid(b);
        if(b.source().equals("OFFLINE_COUNTER")&&b.currentHolderId().equals(b.createdBy())
            &&repo.jdbc().queryForObject("SELECT (SELECT count(*) FROM counter_booking_guests WHERE booking_id=? AND linked_customer_id=?)+(SELECT count(*) FROM transfer_leases WHERE booking_id=? AND buyer_id=? AND state='COMPLETED')",Integer.class,b.id(),b.currentHolderId(),b.id(),b.currentHolderId())==0)
            throw new ConflictException("BLOCKED_RULE: counter operator is not a verified usage owner; link the actual customer before transferring");
        if(repo.jdbc().queryForObject("SELECT count(*) FROM booking_groups WHERE id=?",Integer.class,b.id())>0)throw new ConflictException("BLOCKED_RULE: paid group ownership changes need an approved member policy");
        if(repo.jdbc().queryForObject("SELECT count(*) FROM payment_reconciliation WHERE booking_id=? AND state='REQUIRES_REVIEW'",Integer.class,b.id())>0)throw new ConflictException("Booking payment is under reconciliation");
    }
    private static Instant instant(Map<String,Object> row,String key){return ((Timestamp)row.get(key)).toInstant();}
    private static String fingerprint(Command c){return com.sporthub.common.money.TransferHandoff.fingerprint(c.listingId(),c.bookingId(),c.sellerId(),c.acquisitionId(),c.buyerId(),c.paymentId(),c.price(),c.currency(),c.paidAt(),c.expiresAt(),c.deadline());}
    private void audit(Command c,String action){repo.jdbc().update("INSERT INTO transfer_lease_audit(id,listing_id,action,details) VALUES(?,?,?,?::jsonb)",UUID.randomUUID(),c.listingId(),action,repo.write(c));}
}
