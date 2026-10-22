package com.sporthub.payment.service;

import com.fasterxml.jackson.databind.*;
import com.sporthub.common.event.DomainEvent;
import com.sporthub.common.money.BookingPaymentFingerprint;
import com.sporthub.payment.provider.MovementAdapters;
import com.sporthub.payment.repository.PaymentRepository;
import com.sporthub.payment.web.PaymentDtos.*;
import com.sporthub.payment.service.FinancialLedger.MoneyClass;
import org.springframework.stereotype.Component;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

@Component
public class VerifiedCollections {
    public record Fact(String providerAccount,MoneyClass moneyClass,String fingerprint,Instant paidAt,Instant fundsAvailableAt,boolean canonical) {}
    private final PaymentRepository repo;private final MovementAdapters adapters;private final ObjectMapper json;
    public VerifiedCollections(PaymentRepository repo,MovementAdapters adapters,ObjectMapper json){this.repo=repo;this.adapters=adapters;this.json=json;}
    public Fact record(Payment order,Callback callback,String callbackFingerprint,DomainEvent<?> event,boolean canonical){
        var payment=repo.find(order.id());var capability=adapters.capabilities().stream().filter(c->c.provider().equals(payment.provider())).findFirst().orElse(null);
        MoneyClass moneyClass=payment.provider().equals("DEMO")?MoneyClass.DEMO:capability==null||capability.moneyClass()==MoneyClass.REAL&&!capability.liveVerified()?MoneyClass.UNKNOWN:capability.moneyClass();
        String account=capability==null?"unverified-account":capability.account();
        // DEMO explicitly models instant availability. A real adapter needs separate verified availability evidence.
        Instant available=moneyClass==MoneyClass.DEMO&&capability!=null&&capability.fundsAvailableProof()?payment.paidAt():null;
        JsonNode payload=json.valueToTree(event.payload());String fingerprint=BookingPaymentFingerprint.of(payload);
        var proof=Map.of("providerTransactionId",callback.transactionId(),"callbackFingerprint",callbackFingerprint,"outcomeFingerprint",fingerprint,"moneyClass",moneyClass.name(),"simulated",moneyClass==MoneyClass.DEMO,"fundsAvailable",available!=null);
        repo.jdbc().update("INSERT INTO verified_payment_collections(payment_id,booking_id,payer_id,member_id,acquisition_id,purpose,amount,currency,paid_at,provider,provider_account,money_class,provider_transaction_id,callback_fingerprint,outcome_fingerprint,member_text,canonical,funds_available_at,event_id,proof) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?::jsonb)",
                payment.id(),payment.bookingId(),payment.payerId(),payment.memberId(),payment.acquisitionId(),payment.purpose(),payment.amount(),payment.currency(),Timestamp.from(payment.paidAt()),payment.provider(),account,moneyClass.name(),callback.transactionId(),callbackFingerprint,fingerprint,payload.path("memberId").asText(),canonical,available==null?null:Timestamp.from(available),event.eventId(),write(proof));
        return new Fact(account,moneyClass,fingerprint,payment.paidAt(),available,canonical);
    }
    private String write(Object value){try{return json.writeValueAsString(value);}catch(Exception ex){throw new IllegalStateException("Could not encode verified collection evidence",ex);}}
}
