package com.sporthub.common.money;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sporthub.common.security.Signatures;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

/** Server-owned DEMO terms. No merchant, verified beneficiary or real-money authority. */
public final class DemoTransferTerms {
    private DemoTransferTerms() {}
    public static boolean isDemo(JsonNode terms){return terms!=null&&"DEMO".equals(terms.path("mode").asText());}
    public static ObjectNode seller(ObjectMapper json,JsonNode booking,JsonNode facility,JsonNode feePolicy,
                                    UUID seller,BigDecimal price,Instant deadline){
        if(feePolicy==null||!feePolicy.path("configured").asBoolean()||!feePolicy.path("feeBps").isIntegralNumber()
                ||!feePolicy.path("version").isIntegralNumber())throw new IllegalArgumentException("Owner must configure the facility transfer fee");
        UUID facilityId=UUID.fromString(booking.path("facilityId").asText());
        if(facility==null||!facilityId.toString().equals(facility.path("id").asText()))throw new IllegalArgumentException("Receiving facility does not match booking");
        UUID owner=UUID.fromString(facility.path("ownerId").asText());
        var fee=TransferFeeTerms.quote(price,feePolicy.path("feeBps").intValue(),feePolicy.path("version").longValue());
        var p=json.createObjectNode();
        for(String field:List.of("id","facilityId","courtId"))p.put(field.equals("id")?"bookingId":field,booking.path(field).asText());
        p.put("mode","DEMO").put("provider","DEMO").put("moneyClass","DEMO").put("sellerId",seller.toString());
        p.put("receiverFacilityId",facilityId.toString()).put("receiverOwnerId",owner.toString()).put("receiverName",facility.path("name").asText());
        p.put("policyVersion",fee.policyVersion()).put("feeBps",fee.feeBps()).put("feePolicyHash",feePolicy.path("termsHash").asText());
        p.put("currency","VND").put("rounding","HALF_UP").put("amount",fee.amount().toPlainString());
        p.put("quotedFeeAmount",fee.quotedFeeAmount().toPlainString()).put("quotedSellerNetAmount",fee.quotedSellerNetAmount().toPlainString());
        p.put("deadline",deadline.truncatedTo(ChronoUnit.MILLIS).toString());p.set("refundPolicy",booking.path("refundPolicy").deepCopy());
        p.put("termsHash",hash(p));validateSeller(p);return p;
    }
    public static void validateSeller(JsonNode p){
        if(!isDemo(p)||!p.path("provider").asText().equals("DEMO")||!p.path("moneyClass").asText().equals("DEMO"))throw new IllegalArgumentException("DEMO terms required");
        for(String field:List.of("bookingId","facilityId","courtId","sellerId","receiverFacilityId","receiverOwnerId"))UUID.fromString(p.path(field).asText());
        if(!p.path("facilityId").asText().equals(p.path("receiverFacilityId").asText())||p.path("receiverName").asText().isBlank())throw new IllegalArgumentException("Receiving facility is required");
        if(!p.path("policyVersion").isIntegralNumber()||!p.path("policyVersion").canConvertToLong()||!p.path("feeBps").isIntegralNumber()||!p.path("feeBps").canConvertToInt()
                ||!p.path("currency").asText().equals("VND")||!p.path("rounding").asText().equals("HALF_UP"))throw new IllegalArgumentException("Invalid facility fee policy");
        var fee=TransferFeeTerms.quote(TransferFinancialTerms.decimal(p,"amount"),p.path("feeBps").intValue(),p.path("policyVersion").longValue());
        if(fee.quotedFeeAmount().compareTo(TransferFinancialTerms.decimal(p,"quotedFeeAmount"))!=0||fee.quotedSellerNetAmount().compareTo(TransferFinancialTerms.decimal(p,"quotedSellerNetAmount"))!=0)throw new IllegalArgumentException("Facility P/F/N differ");
        if(!Signatures.sha256(p.path("facilityId").asText()+"|"+fee.policyVersion()+"|"+fee.feeBps()+"|VND|HALF_UP|FACILITY_TRANSFER_FEE").equals(p.path("feePolicyHash").asText()))throw new IllegalArgumentException("Fee policy scope differs");
        var r=p.path("refundPolicy");UUID.fromString(r.path("id").asText());
        if(!r.path("configured").asBoolean()||!r.path("version").isIntegralNumber()||!r.path("version").canConvertToLong()||r.path("version").longValue()<1
                ||!r.path("rateBps").isIntegralNumber()||!r.path("rateBps").canConvertToInt()||r.path("rateBps").intValue()<0||r.path("rateBps").intValue()>10000
                ||!r.path("currency").asText().equals("VND")||!r.path("rounding").asText().equals("HALF_UP")
                ||!Signatures.sha256(p.path("facilityId").asText()+"|"+r.path("version").longValue()+"|"+r.path("rateBps").intValue()+"|VND|HALF_UP|FACILITY_POLICY_CANCELLATION").equals(r.path("termsHash").asText()))throw new IllegalArgumentException("Accepted booking refund policy required");
        Instant.parse(p.path("deadline").asText());
        if(!hash(p).equals(p.path(p.has("sellerTermsHash")?"sellerTermsHash":"termsHash").asText()))throw new IllegalArgumentException("DEMO seller terms hash differs");
    }
    private static String hash(JsonNode p){
        var fields=List.of("mode","bookingId","facilityId","courtId","sellerId","receiverFacilityId","receiverOwnerId","receiverName","policyVersion","feeBps","feePolicyHash","currency","rounding","amount","quotedFeeAmount","quotedSellerNetAmount","deadline");
        var values=new ArrayList<String>();for(String field:fields)values.add(p.path(field).asText());values.add(p.path("refundPolicy").path("termsHash").asText());
        return Signatures.sha256("DEMO_FACILITY_TERMS|"+String.join("|",values));
    }
}
