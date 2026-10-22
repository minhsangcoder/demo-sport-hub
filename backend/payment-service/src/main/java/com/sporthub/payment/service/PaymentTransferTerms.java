package com.sporthub.payment.service;

import com.fasterxml.jackson.databind.*;
import com.sporthub.common.exception.ConflictException;
import com.sporthub.common.money.TransferFinancialTerms;
import com.sporthub.payment.repository.PaymentRepository;
import com.sporthub.payment.web.PaymentDtos.Payment;
import org.springframework.stereotype.Component;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

/** Captured only from the server-side acquisition payable, in the order creation transaction. */
@Component
public class PaymentTransferTerms {
    private final PaymentRepository repo;private final SellerPayoutAccounts beneficiaries;private final ObjectMapper json;
    public PaymentTransferTerms(PaymentRepository repo,SellerPayoutAccounts beneficiaries,ObjectMapper json){this.repo=repo;this.beneficiaries=beneficiaries;this.json=json;}
    public void capture(Payment payment,JsonNode terms){
        if(terms==null||terms.isNull())return; // An existing legacy acquisition stays on its original protocol.
        try{TransferFinancialTerms.validateAcquisition(terms);}catch(IllegalArgumentException ex){throw new ConflictException("Server acquisition financial terms could not be verified");}
        if(!payment.purpose().equals("TRANSFER")||!payment.bookingId().toString().equals(terms.path("bookingId").asText())||!payment.acquisitionId().toString().equals(terms.path("acquisitionId").asText())||!payment.sellerId().toString().equals(terms.path("sellerId").asText())||!payment.payerId().toString().equals(terms.path("buyerId").asText())||payment.amount().compareTo(TransferFinancialTerms.decimal(terms,"amount"))!=0||!payment.currency().equals(terms.path("currency").asText())||!payment.provider().equals(terms.path("provider").asText())||!payment.expiresAt().equals(Instant.parse(terms.path("expiresAt").asText())))throw new ConflictException("Payment and immutable acquisition terms differ");
        if(com.sporthub.common.money.DemoTransferTerms.isDemo(terms)){
            repo.jdbc().update("INSERT INTO demo_transfer_payment_terms(payment_id,acquisition_id,facility_id,snapshot) VALUES(?,?,?,?::jsonb)",payment.id(),payment.acquisitionId(),UUID.fromString(terms.path("facilityId").asText()),write(terms));
            return;
        }
        var beneficiary=beneficiaries.verified(UUID.fromString(terms.path("beneficiaryRevisionId").asText()),payment.sellerId());
        if(!beneficiary.provider().equals(terms.path("provider").asText())||!beneficiary.providerAccount().equals(terms.path("providerAccount").asText())||!beneficiary.moneyClass().equals(terms.path("moneyClass").asText()))throw new ConflictException("Beneficiary provider scope differs from the accepted acquisition");
        repo.jdbc().update("""
          INSERT INTO transfer_payment_terms(payment_id,acquisition_id,listing_id,booking_id,facility_id,court_id,seller_id,buyer_id,
            policy_version,fee_bps,rounding,amount,quoted_fee_amount,quoted_seller_net_amount,currency,beneficiary_revision_id,terms_hash,refund_policy,snapshot,accepted_at)
          VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?::jsonb,?::jsonb,?)
          """,payment.id(),payment.acquisitionId(),UUID.fromString(terms.path("listingId").asText()),payment.bookingId(),UUID.fromString(terms.path("facilityId").asText()),UUID.fromString(terms.path("courtId").asText()),payment.sellerId(),payment.payerId(),terms.path("policyVersion").asLong(),terms.path("feeBps").asInt(),terms.path("rounding").asText(),TransferFinancialTerms.decimal(terms,"amount"),TransferFinancialTerms.decimal(terms,"quotedFeeAmount"),TransferFinancialTerms.decimal(terms,"quotedSellerNetAmount"),payment.currency(),beneficiary.revisionId(),terms.path("termsHash").asText(),write(terms.path("refundPolicy")),write(terms),Timestamp.from(Instant.parse(terms.path("acceptedAt").asText())));
    }
    private String write(Object value){try{return json.writeValueAsString(value);}catch(Exception ex){throw new IllegalArgumentException("Could not encode payment terms",ex);}}
}
