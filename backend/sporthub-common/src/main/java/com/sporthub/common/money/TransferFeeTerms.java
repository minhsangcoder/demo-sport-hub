package com.sporthub.common.money;
import java.math.*;

/** Whole-VND quote; earned fee and seller entitlement arise only after verified handoff. */
public record TransferFeeTerms(long policyVersion,int feeBps,String currency,String rounding,
                               BigDecimal amount,BigDecimal quotedFeeAmount,BigDecimal quotedSellerNetAmount) {
    /** Compatibility for immutable legacy terms only. New terms must supply the Owner policy. */
    @Deprecated
    public static TransferFeeTerms quote(BigDecimal price){return quote(price,200,1);}
    public static TransferFeeTerms quote(BigDecimal price,int feeBps,long policyVersion){
        Money.positive(price,"VND");
        if(feeBps<0||feeBps>10000||policyVersion<1)throw new IllegalArgumentException("An explicit facility transfer fee policy is required");
        if(price.stripTrailingZeros().scale()>0)throw new IllegalArgumentException("New transfer terms require whole VND; legacy fractional money needs review");
        BigDecimal amount=price.setScale(0),fee=amount.multiply(BigDecimal.valueOf(feeBps)).divide(BigDecimal.valueOf(10000),0,RoundingMode.HALF_UP);
        return new TransferFeeTerms(policyVersion,feeBps,"VND","HALF_UP",amount,fee,amount.subtract(fee));
    }
}
