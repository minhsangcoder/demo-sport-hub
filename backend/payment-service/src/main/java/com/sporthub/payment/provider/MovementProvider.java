package com.sporthub.payment.provider;

import com.sporthub.payment.service.FinancialLedger.MoneyClass;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

/** Acknowledgement, source debit and final receipt are deliberately distinct provider facts. */
public interface MovementProvider {
    record Capabilities(String provider,String account,String region,MoneyClass moneyClass,Duration keyTtl,
                        boolean queryByKey,boolean verifiedWebhooks,boolean finalPayoutProof,boolean finalRefundProof,
                        boolean fundsAvailableProof,boolean returns,boolean statement,boolean liveVerified) {}
    record Beneficiary(UUID ownerId,String token,String maskedLabel,String proofReference,Instant verifiedAt) {}
    record Command(UUID orderId,UUID attemptId,String kind,BigDecimal amount,String currency,MoneyClass moneyClass,
                   String counterparty,String idempotencyKey,String fingerprint,Instant keyExpiresAt) {}
    record Event(String provider,String account,String region,String eventId,String reference,UUID orderId,UUID attemptId,
                 String kind,BigDecimal amount,String currency,String counterparty,Instant occurredAt,String signature) {}
    Capabilities capabilities();
    Beneficiary verifyBeneficiary(UUID owner,String maskedDestination,String consentReference);
    Event submit(Command command);
    Optional<Event> query(Command command);
    void verify(Event event);
    List<Event> statement(Instant from,Instant to);
    static String fingerprint(UUID order,UUID attempt,String kind,BigDecimal amount,String currency,MoneyClass moneyClass,String counterparty,String key,Instant expiry){
        return com.sporthub.common.security.Signatures.sha256("MOVEMENT_COMMAND|"+order+"|"+attempt+"|"+kind+"|"+amount.stripTrailingZeros().toPlainString()+"|"+currency+"|"+moneyClass+"|"+counterparty+"|"+key+"|"+expiry);
    }
    final class Uncertain extends RuntimeException {public Uncertain(String code){super(code);}}
}
