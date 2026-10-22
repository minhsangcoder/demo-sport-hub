package com.sporthub.payment.service;

import com.fasterxml.jackson.databind.*;
import com.sporthub.common.event.*;
import com.sporthub.common.money.TransferFinancialTerms;
import com.sporthub.payment.repository.PaymentRepository;
import com.sporthub.payment.web.PaymentDtos.Payment;
import com.sporthub.payment.service.FinancialLedger.*;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Component
public class TransferCollectionAccounting {
    private final PaymentRepository repo;private final FinancialLedger ledger;private final ReliableOutbox outbox;private final ObjectMapper json;
    public TransferCollectionAccounting(PaymentRepository repo,FinancialLedger ledger,ReliableOutbox outbox,ObjectMapper json){this.repo=repo;this.ledger=ledger;this.outbox=outbox;this.json=json;}
    public void collect(Payment original,VerifiedCollections.Fact collection){
        var demo=repo.jdbc().queryForList("SELECT snapshot::text FROM demo_transfer_payment_terms WHERE payment_id=?",original.id());
        if(!demo.isEmpty()){
            if(!collection.canonical())return;
            var payment=repo.find(original.id());JsonNode terms=read(demo.getFirst().get("snapshot").toString());TransferFinancialTerms.validateAcquisition(terms);
            var payload=new LinkedHashMap<String,Object>();
            for(String field:List.of("acquisitionId","listingId","bookingId","sellerId","buyerId","termsHash","expiresAt","deadline"))payload.put(field,terms.path(field).asText());
            payload.put("paymentId",payment.id());payload.put("payerId",payment.payerId());payload.put("memberId",null);payload.put("amount",payment.amount());payload.put("currency",payment.currency());payload.put("paidAt",payment.paidAt());payload.put("purpose","TRANSFER");payload.put("status","SUCCESS");payload.put("canonicalDecision","SETTLED_OBLIGATION");payload.put("collectionFingerprint",collection.fingerprint());
            outbox.record(RabbitMQConfig.EXCHANGE_PAYMENT,DomainEvent.create("transfer.collection.verified",1,"payment-service",payment.id(),payload));
            return;
        }
        var rows=repo.jdbc().queryForList("SELECT snapshot::text FROM transfer_payment_terms WHERE payment_id=?",original.id());if(rows.isEmpty())return; // Legacy stays in review; no retrospective fee/asset.
        var payment=repo.find(original.id());JsonNode terms=read(rows.getFirst().get("snapshot").toString());TransferFinancialTerms.validateAcquisition(terms);
        UUID bucket=UUID.nameUUIDFromBytes(("COLLECTION_BUCKET|"+payment.id()).getBytes(StandardCharsets.UTF_8));
        repo.jdbc().update("INSERT INTO financial_funding_buckets(id,scope_kind,payment_id,provider,provider_account,currency,money_class) VALUES(?,'TRANSFER_COLLECTION',?,?,?,?,?) ON CONFLICT(payment_id) DO NOTHING",bucket,payment.id(),payment.provider(),collection.providerAccount(),payment.currency(),collection.moneyClass().name());
        var proof=Map.of("collectionFingerprint",collection.fingerprint(),"moneyClass",collection.moneyClass().name());
        ledger.post(new Effect("TRANSFER_COLLECTION",payment.id().toString(),payment.id(),null,null,payment.currency(),collection.moneyClass(),collection.paidAt(),null,proof,List.of(new Line(account("PROVIDER_CLEARING",bucket,payment,collection),Side.D,payment.amount()),new Line(account("BUYER_ESCROW",payment.id(),payment,collection),Side.C,payment.amount()))));
        if(collection.fundsAvailableAt()!=null)ledger.post(new Effect("COLLECTION_AVAILABLE",payment.id().toString(),payment.id(),null,null,payment.currency(),collection.moneyClass(),collection.fundsAvailableAt(),null,proof,List.of(new Line(account("PROVIDER_AVAILABLE",bucket,payment,collection),Side.D,payment.amount()),new Line(account("PROVIDER_CLEARING",bucket,payment,collection),Side.C,payment.amount()))));
        if(!collection.canonical())return;
        UUID settlement=UUID.nameUUIDFromBytes(("TRANSFER_SETTLEMENT|"+payment.acquisitionId()).getBytes(StandardCharsets.UTF_8));
        repo.jdbc().update("""
            INSERT INTO transfer_settlements(id,acquisition_id,payment_id,origin,state,provider,provider_account,money_class,currency,
              listing_id,booking_id,facility_id,court_id,seller_id,buyer_id,amount,quoted_fee_amount,quoted_seller_net_amount,fee_bps,policy_version,rounding,terms_hash,beneficiary_revision_id)
            VALUES(?,?,?,'NEW_TERMS','WAITING_HANDOFF',?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            """,settlement,payment.acquisitionId(),payment.id(),payment.provider(),collection.providerAccount(),collection.moneyClass().name(),payment.currency(),UUID.fromString(terms.path("listingId").asText()),payment.bookingId(),UUID.fromString(terms.path("facilityId").asText()),UUID.fromString(terms.path("courtId").asText()),payment.sellerId(),payment.payerId(),payment.amount(),TransferFinancialTerms.decimal(terms,"quotedFeeAmount"),TransferFinancialTerms.decimal(terms,"quotedSellerNetAmount"),terms.path("feeBps").asInt(),terms.path("policyVersion").asLong(),terms.path("rounding").asText(),terms.path("termsHash").asText(),UUID.fromString(terms.path("beneficiaryRevisionId").asText()));
        var payload=new LinkedHashMap<String,Object>();for(String field:List.of("acquisitionId","listingId","bookingId","sellerId","buyerId","termsHash","expiresAt","deadline"))payload.put(field,terms.path(field).asText());
        payload.put("paymentId",payment.id());payload.put("payerId",payment.payerId());payload.put("memberId",null);payload.put("amount",payment.amount());payload.put("currency",payment.currency());payload.put("paidAt",payment.paidAt());payload.put("purpose","TRANSFER");payload.put("status","SUCCESS");payload.put("canonicalDecision","SETTLED_OBLIGATION");payload.put("collectionFingerprint",collection.fingerprint());
        outbox.record(RabbitMQConfig.EXCHANGE_PAYMENT,DomainEvent.create("transfer.collection.verified",1,"payment-service",payment.id(),payload));
    }
    private Account account(String kind,UUID scope,Payment p,VerifiedCollections.Fact f){return new Account(kind,scope,p.provider(),f.providerAccount(),p.currency(),f.moneyClass());}
    private JsonNode read(String value){try{return json.readTree(value);}catch(Exception ex){throw new IllegalStateException("Stored transfer terms are malformed",ex);}}
}
