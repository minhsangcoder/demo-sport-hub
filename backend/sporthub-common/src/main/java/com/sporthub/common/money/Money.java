package com.sporthub.common.money;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Existing storage/API contract: NUMERIC(12,2), VND, exact per-slot amounts. */
public final class Money {
    public static final BigDecimal MAX=new BigDecimal("9999999999.99");
    private Money() {}
    public static BigDecimal positive(BigDecimal value) {
        if(value==null||value.signum()<=0||value.compareTo(MAX)>0)
            throw new IllegalArgumentException("Amount must be positive and fit NUMERIC(12,2)");
        try {return value.setScale(2,RoundingMode.UNNECESSARY);}
        catch(ArithmeticException ex){throw new IllegalArgumentException("Amount must have at most two decimal places");}
    }
    public static BigDecimal positive(BigDecimal value,String currency) {
        if(!"VND".equals(currency))throw new IllegalArgumentException("Only VND is supported");
        return positive(value);
    }
}
