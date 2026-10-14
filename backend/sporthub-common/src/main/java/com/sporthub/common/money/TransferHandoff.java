package com.sporthub.common.money;

import com.sporthub.common.security.Signatures;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;

/** Canonical identity of an ownership command, shared by the sender and Booking receipt. */
public final class TransferHandoff {
    private TransferHandoff(){}
    public static String fingerprint(UUID listing,UUID booking,UUID seller,UUID acquisition,UUID buyer,UUID payment,
        BigDecimal amount,String currency,Instant paidAt,Instant expiresAt,Instant deadline){
        for(Object id:new Object[]{listing,booking,seller,acquisition,buyer,payment,paidAt,expiresAt,deadline,amount})if(id==null)throw new IllegalArgumentException("Handoff identity is required");
        Money.positive(amount,currency);
        if(amount.stripTrailingZeros().scale()>0)throw new IllegalArgumentException("Transfer requires integral VND");
        return Signatures.sha256(listing+"|"+booking+"|"+seller+"|"+acquisition+"|"+buyer+"|"+payment+"|"+
            amount.stripTrailingZeros().toPlainString()+"|"+currency+"|"+time(paidAt)+"|"+time(expiresAt)+"|"+time(deadline));
    }
    private static Instant time(Instant value){return value.truncatedTo(ChronoUnit.MICROS);}
}
