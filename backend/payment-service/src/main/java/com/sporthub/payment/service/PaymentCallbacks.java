package com.sporthub.payment.service;

import static com.sporthub.payment.web.PaymentDtos.*;
import com.sporthub.payment.repository.PaymentRepository;
import com.sporthub.payment.provider.PaymentProvider;
import com.sporthub.common.event.*;
import com.sporthub.common.money.Money;
import com.sporthub.common.security.Signatures;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import jakarta.validation.Validator;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;

@Service
public class PaymentCallbacks {
    public record Result(Payment payment,String conflict,boolean forbidden){}
    private final PaymentRepository repo;private final PaymentProvider provider;private final ReliableOutbox outbox;
    private final ObjectMapper json;private final Validator validator;private final PaymentObligations obligations;private final Clock clock;private final VerifiedCollections collections;private final TransferCollectionAccounting transferCollections;
    public PaymentCallbacks(PaymentRepository repo,PaymentProvider provider,ReliableOutbox outbox,ObjectMapper json,Validator validator,PaymentObligations obligations,Clock clock,VerifiedCollections collections,TransferCollectionAccounting transferCollections){this.repo=repo;this.provider=provider;this.outbox=outbox;this.json=json;this.validator=validator;this.obligations=obligations;this.clock=clock;this.collections=collections;this.transferCollections=transferCollections;}
    @Transactional public Result process(Callback input,String signature){
        if(!validator.validate(input).isEmpty())throw new IllegalArgumentException("Invalid callback fields");
        Money.positive(input.amount(),input.currency());provider.verify(input,signature);
        repo.lockCallback(provider.name(),input.transactionId());
        var original=repo.find(input.paymentId());repo.lock(original.bookingId());repo.lock(original.id());
        var order=repo.list("SELECT * FROM payment_orders WHERE id=? FOR UPDATE",original.id()).getFirst();
        String fingerprint=Signatures.sha256(input.paymentId()+"|"+input.transactionId()+"|"+input.amount().setScale(2).toPlainString()+"|"+input.currency()+"|"+input.status());
        if(!order.provider().equals(provider.name())||!order.providerReference().equals(input.providerReference())||order.amount().compareTo(input.amount())!=0||!order.currency().equals(input.currency())){
            review(order,input,fingerprint,"CALLBACK_SCOPE_MISMATCH");return new Result(order,"Callback reference or amount mismatch",true);
        }
        var previous=repo.jdbc().queryForList("SELECT fingerprint,payment_id FROM provider_callbacks WHERE provider=? AND transaction_id=?",provider.name(),input.transactionId());
        if(!previous.isEmpty()){
            if(!previous.getFirst().get("fingerprint").equals(fingerprint)){review(order,input,fingerprint,"PROVIDER_TRANSACTION_CONFLICT");return new Result(order,"Provider transaction was reused with conflicting data",false);}
            return new Result(order,null,false);
        }
        if(Set.of("SUCCESS","FAILED").contains(order.status())){
            review(order,input,fingerprint,"FINAL_OUTCOME_CONFLICT");return new Result(order,"Payment is already final",false);
        }
        repo.jdbc().update("INSERT INTO provider_callbacks(provider,transaction_id,payment_id,fingerprint,body) VALUES(?,?,?,?,?::jsonb)",provider.name(),input.transactionId(),order.id(),fingerprint,write(input));
        Instant paid=Instant.ofEpochSecond(input.timestamp());
        int changed=repo.jdbc().update("UPDATE payment_orders SET status=?,paid_at=?,updated_at=NOW() WHERE id=? AND status IN ('PENDING','EXPIRED')",input.status(),input.status().equals("SUCCESS")?Timestamp.from(paid):null,order.id());
        if(changed!=1)throw new com.sporthub.common.exception.ConflictException("Payment changed before callback completion");
        audit(order.id(),"CALLBACK_VERIFIED",Map.of("transactionId",input.transactionId(),"status",input.status(),"previousStatus",order.status()));
        boolean canonical=input.status().equals("SUCCESS")&&obligations.settle(order);
        if(input.status().equals("SUCCESS")&&!canonical)review(order,input,fingerprint,"DUPLICATE_OBLIGATION_RECEIPT");
        com.fasterxml.jackson.databind.JsonNode demoTerms=null;
        if(order.purpose().equals("TRANSFER")){
            var terms=repo.jdbc().queryForList("SELECT snapshot::text FROM demo_transfer_payment_terms WHERE payment_id=?",order.id());
            if(!terms.isEmpty())try{demoTerms=json.readTree(terms.getFirst().get("snapshot").toString());}catch(Exception invalid){throw new IllegalStateException("Invalid stored DEMO terms",invalid);}
        }
        // Legacy escrow records remain unchanged. New DEMO transfers belong to the facility.
        if(demoTerms==null&&order.purpose().equals("TRANSFER")&&input.status().equals("SUCCESS"))repo.jdbc().update("INSERT INTO transfer_escrow(payment_id,acquisition_id,payer_id,seller_id,amount,currency,state) VALUES(?,?,?,?,?,?,'HELD_POLICY_BLOCKED') ON CONFLICT(payment_id) DO NOTHING",order.id(),order.acquisitionId(),order.payerId(),order.sellerId(),order.amount(),order.currency());
        var payload=new HashMap<String,Object>();payload.put("paymentId",order.id());payload.put("bookingId",order.bookingId());payload.put("payerId",order.payerId());payload.put("memberId",order.memberId());payload.put("acquisitionId",order.acquisitionId());payload.put("purpose",order.purpose());payload.put("amount",order.amount());payload.put("currency",order.currency());payload.put("status",input.status());payload.put("paidAt",paid);payload.put("transactionId",input.transactionId());
        payload.put("canonical",canonical);
        payload.put("provider",order.provider());
        if(demoTerms!=null)payload.put("transferTerms",demoTerms);
        var event=DomainEvent.create("payment.completed",1,"payment-service",order.id(),payload);outbox.record(RabbitMQConfig.EXCHANGE_PAYMENT,event);
        if(input.status().equals("SUCCESS")){var collection=collections.record(order,input,fingerprint,event,canonical);if(order.purpose().equals("TRANSFER"))transferCollections.collect(order,collection);}
        if(!order.purpose().equals("TRANSFER"))repo.jdbc().update("INSERT INTO payment_booking_sync(payment_id,event_id,outcome,state,next_attempt_at) VALUES(?,?,?,'AWAITING_BOOKING',?)",order.id(),event.eventId(),input.status(),Timestamp.from(clock.instant()));
        return new Result(repo.find(order.id()),null,false);
    }
    private void review(Payment order,Callback input,String fingerprint,String reason){
        if(repo.jdbc().update("INSERT INTO payment_callback_reviews(id,payment_id,fingerprint,reason,body) VALUES(?,?,?,?,?::jsonb) ON CONFLICT DO NOTHING",UUID.randomUUID(),order.id(),fingerprint,reason,write(input))>0)
            audit(order.id(),"CALLBACK_REQUIRES_REVIEW",Map.of("reason",reason,"transactionId",input.transactionId(),"observedStatus",input.status()));
    }
    private void audit(UUID payment,String action,Object details){repo.jdbc().update("INSERT INTO payment_audit(id,payment_id,action,details) VALUES(?,?,?,?::jsonb)",UUID.randomUUID(),payment,action,write(details));}
    private String write(Object value){try{return json.writeValueAsString(value);}catch(Exception ex){throw new IllegalStateException("Could not serialize payment evidence",ex);}}
}
