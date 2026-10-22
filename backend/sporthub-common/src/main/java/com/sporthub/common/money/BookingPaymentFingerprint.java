package com.sporthub.common.money;
import com.fasterxml.jackson.databind.JsonNode;
import com.sporthub.common.security.Signatures;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Preserve v1's distinction between an omitted member field and an explicit JSON null. */
public final class BookingPaymentFingerprint {
    private BookingPaymentFingerprint() {}
    public static String of(UUID booking,UUID payer,String memberText,String purpose,String status,BigDecimal amount,String currency,Instant paidAt){
        return Signatures.sha256(booking+"|"+payer+"|"+memberText+"|"+purpose+"|"+status+"|"+amount.stripTrailingZeros().toPlainString()+"|"+currency+"|"+paidAt);
    }
    public static String of(JsonNode payload){return of(UUID.fromString(payload.path("bookingId").asText()),UUID.fromString(payload.path("payerId").asText()),payload.path("memberId").asText(),payload.path("purpose").asText(),payload.path("status").asText(),payload.path("amount").decimalValue(),payload.path("currency").asText(),Instant.parse(payload.path("paidAt").asText()));}
}
