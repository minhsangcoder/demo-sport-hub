package com.sporthub.payment.service;

import com.fasterxml.jackson.databind.*;
import com.sporthub.common.exception.ConflictException;
import com.sporthub.common.money.*;
import com.sporthub.payment.repository.PaymentRepository;
import com.sporthub.payment.service.FinancialLedger.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

/** The remote decision is read before entering the local allocation transaction. */
@Service
public class TransferSettlementService {
    private final PaymentRepository repo;
    private final FinancialLedger ledger;
    private final BookingReceiptClient booking;
    private final ObjectMapper json;
    private final TransactionTemplate transactions;
    public TransferSettlementService(PaymentRepository repo,FinancialLedger ledger,BookingReceiptClient booking,ObjectMapper json,PlatformTransactionManager manager){
        this.repo=repo;this.ledger=ledger;this.booking=booking;this.json=json;this.transactions=new TransactionTemplate(manager);
    }
    public void completed(JsonNode event){
        if(PeerResaleContract.isPeer(event.path("payload"))||PeerResaleContract.isPeer(event.path("payload").path("transferTerms")))return;
        require(event.path("eventType").asText().equals("transfer.handoff.completed")&&event.path("eventVersion").asInt()==1&&event.path("producer").asText().equals("transfer-service"),"Unsupported handoff envelope");
        JsonNode payload=event.path("payload");UUID eventId=id(event,"eventId"),paymentId=id(payload,"paymentId"),acquisition=id(payload,"acquisitionId");
        require(acquisition.toString().equals(event.path("aggregateId").asText()),"Handoff aggregate differs");
        // DEMO fee/payable commits with the authoritative Booking handoff, with no bank worker.
        if(repo.jdbc().queryForObject("SELECT count(*) FROM demo_transfer_payment_terms WHERE payment_id=? AND acquisition_id=?",Integer.class,paymentId,acquisition)>0)return;
        var payment=repo.find(paymentId);
        var stored=repo.jdbc().queryForList("SELECT snapshot::text FROM transfer_payment_terms WHERE payment_id=?",paymentId);
        require(!stored.isEmpty(),"Legacy transfer cannot enter the new settlement protocol");
        JsonNode terms=read(stored.getFirst().get("snapshot").toString());TransferFinancialTerms.validateAcquisition(terms);
        require(payment.purpose().equals("TRANSFER")&&payment.status().equals("SUCCESS")&&acquisition.equals(payment.acquisitionId()),"Payment has no matching successful transfer collection");
        for(String field:List.of("acquisitionId","listingId","bookingId","facilityId","courtId","sellerId","buyerId","termsHash"))require(terms.path(field).asText().equals(payload.path(field).asText()),"Handoff terms differ: "+field);
        require(TransferFinancialTerms.decimal(terms,"amount").compareTo(payload.path("amount").decimalValue())==0&&payment.currency().equals(payload.path("currency").asText()),"Handoff money differs");
        String fingerprint=TransferHandoff.fingerprint(id(terms,"listingId"),payment.bookingId(),payment.sellerId(),acquisition,payment.payerId(),paymentId,payment.amount(),payment.currency(),payment.paidAt(),Instant.parse(terms.path("expiresAt").asText()),Instant.parse(terms.path("deadline").asText()));
        var decision=booking.acquisitionDecision(acquisition);
        require(decision!=null&&decision.path("decision").asText().equals("COMPLETED")&&decision.path("origin").asText().equals("ACQUISITION_DECISION"),"Authoritative completed acquisition decision is required");
        for(String field:List.of("acquisitionId","listingId","bookingId","sellerId","buyerId","paymentId"))require(decision.path(field).asText().equals(payload.path(field).asText()),"Decision binding differs: "+field);
        require(fingerprint.equals(decision.path("commandFingerprint").asText())&&fingerprint.equals(payload.path("commandFingerprint").asText()),"Handoff fingerprint differs");
        Instant handed=Instant.parse(decision.path("decidedAt").asText());
        require(handed.equals(Instant.parse(payload.path("handoffAt").asText()))&&decision.path("bookingVersion").asLong()==payload.path("bookingVersion").asLong()&&decision.path("bookingVersion").asLong()>0&&payment.amount().compareTo(decision.path("amount").decimalValue())==0&&payment.currency().equals(decision.path("currency").asText())&&acquisition.equals(id(payload,"decisionRef")),"Handoff decision evidence differs");
        transactions.executeWithoutResult(tx->{
            repo.lock(paymentId);
            var settlement=repo.jdbc().queryForMap("SELECT s.* FROM transfer_settlements s JOIN payment_obligations o ON o.settled_payment_id=s.payment_id JOIN verified_payment_collections c ON c.payment_id=s.payment_id AND c.canonical WHERE s.payment_id=? FOR UPDATE OF s",paymentId);
            UUID settlementId=(UUID)settlement.get("id");String state=settlement.get("state").toString();
            require(Set.of("WAITING_HANDOFF","ALLOCATED").contains(state),"Acquisition cannot be allocated from this decision");
            if(state.equals("ALLOCATED")){require(fingerprint.equals(settlement.get("decision_fingerprint")),"Allocated decision differs");return;}
            if(repo.jdbc().update("INSERT INTO event_inbox(consumer,event_id) VALUES('transfer-settlement-completed',?) ON CONFLICT DO NOTHING",eventId)==0)return;
            repo.jdbc().update("UPDATE transfer_settlements SET state='ALLOCATED',decision_ref=?,decision_fingerprint=?,decided_at=?,version=version+1,updated_at=clock_timestamp() WHERE id=?",acquisition,fingerprint,Timestamp.from(handed),settlementId);
            var moneyClass=MoneyClass.valueOf(settlement.get("money_class").toString());String account=settlement.get("provider_account").toString();
            BigDecimal fee=(BigDecimal)settlement.get("quoted_fee_amount"),net=(BigDecimal)settlement.get("quoted_seller_net_amount");
            var lines=new ArrayList<Line>();lines.add(new Line(account("BUYER_ESCROW",paymentId,payment.provider(),account,moneyClass),Side.D,payment.amount()));
            lines.add(new Line(account("SELLER_PAYABLE",settlementId,payment.provider(),account,moneyClass),Side.C,net));
            if(fee.signum()>0)lines.add(new Line(account("TRANSFER_FEE_REVENUE",settlementId,payment.provider(),account,moneyClass),Side.C,fee));
            var proof=Map.of("decisionRef",acquisition,"commandFingerprint",fingerprint,"termsHash",terms.path("termsHash").asText());
            ledger.post(new Effect("TRANSFER_ALLOCATION",acquisition.toString(),paymentId,settlementId,null,payment.currency(),moneyClass,handed,null,proof,lines));
            UUID order=UUID.nameUUIDFromBytes(("SELLER_PAYOUT|"+acquisition).getBytes(StandardCharsets.UTF_8));
            repo.jdbc().update("INSERT INTO money_movement_orders(id,kind,business_key,payment_id,settlement_id,payee_id,beneficiary_revision_id,amount,currency,money_class,provider,provider_account,state,proof) VALUES(?,'SELLER_PAYOUT',?,?,?,?,?,?,?, ?,?,?,'NEW',?::jsonb)",order,"seller-payout:"+acquisition,paymentId,settlementId,payment.sellerId(),UUID.fromString(terms.path("beneficiaryRevisionId").asText()),net,payment.currency(),moneyClass.name(),payment.provider(),account,write(proof));
        });
    }
    private Account account(String kind,UUID scope,String provider,String account,MoneyClass moneyClass){return new Account(kind,scope,provider,account,"VND",moneyClass);}
    private void require(boolean condition,String message){if(!condition)throw new ConflictException(message);}
    private UUID id(JsonNode value,String field){try{return UUID.fromString(value.path(field).asText());}catch(Exception ex){throw new IllegalArgumentException("Valid "+field+" is required");}}
    private JsonNode read(String body){try{return json.readTree(body);}catch(Exception ex){throw new IllegalStateException("Stored financial terms are invalid",ex);}}
    private String write(Object value){try{return json.writeValueAsString(value);}catch(Exception ex){throw new IllegalArgumentException("Cannot encode settlement proof",ex);}}
}
