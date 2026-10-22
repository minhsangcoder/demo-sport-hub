package com.sporthub.common.money;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sporthub.common.security.Signatures;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

/** Shared immutable financial identity used by Transfer and Payment. No earned fee is inferred here. */
public final class TransferFinancialTerms {
    private TransferFinancialTerms() {}
    public static ObjectNode seller(ObjectMapper json,UUID booking,UUID facility,UUID court,UUID seller,BigDecimal price,Instant deadline,JsonNode beneficiary,JsonNode refundPolicy){
        var fee=TransferFeeTerms.quote(price);var p=json.createObjectNode();p.put("bookingId",booking.toString()).put("facilityId",facility.toString()).put("courtId",court.toString()).put("sellerId",seller.toString());
        p.put("policyVersion",fee.policyVersion()).put("feeBps",fee.feeBps()).put("rounding",fee.rounding()).put("currency",fee.currency()).put("amount",fee.amount().toPlainString()).put("quotedFeeAmount",fee.quotedFeeAmount().toPlainString()).put("quotedSellerNetAmount",fee.quotedSellerNetAmount().toPlainString());
        if(beneficiary==null||!beneficiary.path("verified").isBoolean()||!beneficiary.path("verified").booleanValue()||!seller.toString().equals(beneficiary.path("sellerId").asText())||!beneficiary.path("currency").asText().equals("VND"))throw new IllegalArgumentException("Verified seller beneficiary required");
        for(String field:List.of("revisionId","provider","providerAccount","moneyClass"))p.put(field.equals("revisionId")?"beneficiaryRevisionId":field,beneficiary.path(field).asText());
        p.put("deadline",deadline.truncatedTo(ChronoUnit.MILLIS).toString());p.set("refundPolicy",refundPolicy==null?json.nullNode():refundPolicy.deepCopy());
        p.put("termsHash",sellerHash(p));validateSeller(p);return p;
    }
    public static ObjectNode acquisition(JsonNode sellerTerms,UUID listing,long listingVersion,UUID acquisition,UUID buyer,Instant expiresAt,Instant acceptedAt){
        validateSeller(sellerTerms);ObjectNode p=sellerTerms.deepCopy();p.put("sellerTermsHash",sellerTerms.path("termsHash").asText());
        p.put("listingId",listing.toString()).put("listingVersion",listingVersion).put("acquisitionId",acquisition.toString()).put("buyerId",buyer.toString()).put("expiresAt",expiresAt.toString()).put("acceptedAt",acceptedAt.toString());
        p.put("termsHash",acquisitionHash(p));validateAcquisition(p);return p;
    }
    public static void validateSeller(JsonNode p){
        if(DemoTransferTerms.isDemo(p)){DemoTransferTerms.validateSeller(p);return;}
        if(p==null||!p.isObject())throw new IllegalArgumentException("Financial transfer terms required");
        for(String field:List.of("bookingId","facilityId","courtId","sellerId","beneficiaryRevisionId"))UUID.fromString(p.path(field).asText());
        if(!p.path("policyVersion").isIntegralNumber()||!p.path("policyVersion").canConvertToLong()||p.path("policyVersion").asLong()!=1||!p.path("feeBps").isIntegralNumber()||!p.path("feeBps").canConvertToInt()||p.path("feeBps").asInt()!=200||!p.path("rounding").asText().equals("HALF_UP")||!p.path("currency").asText().equals("VND"))throw new IllegalArgumentException("Unsupported transfer fee policy");
        var fee=TransferFeeTerms.quote(decimal(p,"amount"));if(fee.quotedFeeAmount().compareTo(decimal(p,"quotedFeeAmount"))!=0||fee.quotedSellerNetAmount().compareTo(decimal(p,"quotedSellerNetAmount"))!=0)throw new IllegalArgumentException("Transfer P/F/N terms differ from the fixed fee policy");
        if(!Set.of("REAL","DEMO").contains(p.path("moneyClass").asText())||p.path("provider").asText().isBlank()||p.path("providerAccount").asText().isBlank())throw new IllegalArgumentException("Verified provider money class required");
        Instant.parse(p.path("deadline").asText());var policy=p.path("refundPolicy");
        if(!policy.path("configured").isBoolean()||!policy.path("configured").booleanValue()||!policy.path("version").isIntegralNumber()||!policy.path("version").canConvertToLong()||policy.path("version").asLong()<=0||!policy.path("rateBps").isIntegralNumber()||!policy.path("rateBps").canConvertToInt()||policy.path("rateBps").asInt()<0||policy.path("rateBps").asInt()>10000||!policy.path("currency").asText().equals("VND")||!policy.path("rounding").asText().equals("HALF_UP"))throw new IllegalArgumentException("Accepted booking refund policy required");
        UUID.fromString(policy.path("id").asText());
        if(!Signatures.sha256(p.path("facilityId").asText()+"|"+policy.path("version").asLong()+"|"+policy.path("rateBps").asInt()+"|VND|HALF_UP|FACILITY_POLICY_CANCELLATION").equals(policy.path("termsHash").asText()))throw new IllegalArgumentException("Refund policy scope hash differs");
        String expected=p.has("sellerTermsHash")?p.path("sellerTermsHash").asText():p.path("termsHash").asText();if(!sellerHash(p).equals(expected))throw new IllegalArgumentException("Seller terms hash differs");
    }
    public static void validateAcquisition(JsonNode p){
        validateSeller(p);for(String field:List.of("listingId","acquisitionId","buyerId"))UUID.fromString(p.path(field).asText());
        if(p.path("buyerId").asText().equals(p.path("sellerId").asText())||!p.path("listingVersion").isIntegralNumber()||p.path("listingVersion").asLong()<0||!p.hasNonNull("acceptedAt"))throw new IllegalArgumentException("Acquisition buyer/version required");
        Instant accepted=Instant.parse(p.path("acceptedAt").asText()),expiry=Instant.parse(p.path("expiresAt").asText()),deadline=Instant.parse(p.path("deadline").asText());
        if(!accepted.isBefore(expiry)||expiry.isAfter(deadline)||!acquisitionHash(p).equals(p.path("termsHash").asText()))throw new IllegalArgumentException("Acquisition terms identity or window differs");
    }
    public static BigDecimal decimal(JsonNode p,String field){try{return new BigDecimal(p.path(field).asText());}catch(NumberFormatException ex){throw new IllegalArgumentException("Exact decimal financial terms required",ex);}}
    private static String sellerHash(JsonNode p){
        return Signatures.sha256("SELLER_TERMS|"+String.join("|",List.of(p.path("bookingId").asText(),p.path("facilityId").asText(),p.path("courtId").asText(),p.path("sellerId").asText(),p.path("policyVersion").asText(),p.path("feeBps").asText(),p.path("currency").asText(),p.path("rounding").asText(),decimal(p,"amount").stripTrailingZeros().toPlainString(),decimal(p,"quotedFeeAmount").stripTrailingZeros().toPlainString(),decimal(p,"quotedSellerNetAmount").stripTrailingZeros().toPlainString(),p.path("deadline").asText(),p.path("beneficiaryRevisionId").asText(),p.path("provider").asText(),p.path("providerAccount").asText(),p.path("moneyClass").asText(),p.path("refundPolicy").path("termsHash").asText())));
    }
    private static String acquisitionHash(JsonNode p){return Signatures.sha256("ACQUISITION_TERMS|"+p.path("sellerTermsHash").asText()+"|"+p.path("listingId").asText()+"|"+p.path("listingVersion").asLong()+"|"+p.path("acquisitionId").asText()+"|"+p.path("buyerId").asText()+"|"+p.path("expiresAt").asText()+"|"+p.path("acceptedAt").asText());}
    public static JsonNode publicListing(JsonNode p){
        if(p==null||p.isNull())return null;var view=com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
        for(String field:List.of("policyVersion","feeBps","rounding","currency","amount","quotedFeeAmount","quotedSellerNetAmount","termsHash","refundPolicy"))view.set(field,p.path(field).deepCopy());
        if(DemoTransferTerms.isDemo(p))for(String field:List.of("mode","receiverFacilityId","receiverName","feePolicyHash"))view.set(field,p.path(field).deepCopy());return view;
    }
}
