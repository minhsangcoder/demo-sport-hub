package com.sporthub.common.money;

import java.math.*;
import java.util.*;

/** One rounded entitlement; deterministic largest-remainder allocation to original payments. */
public final class DemoRefundMath {
    private DemoRefundMath() {}
    public record Payment(UUID paymentId,UUID payerId,BigDecimal amount) {}
    public record Allocation(UUID paymentId,UUID payerId,
        @com.fasterxml.jackson.databind.annotation.JsonSerialize(using=com.fasterxml.jackson.databind.ser.std.ToStringSerializer.class) BigDecimal baseAmount,
        @com.fasterxml.jackson.databind.annotation.JsonSerialize(using=com.fasterxml.jackson.databind.ser.std.ToStringSerializer.class) BigDecimal refundAmount) {}
    public static BigDecimal target(BigDecimal base,int rateBps){
        if(base==null||base.signum()<0||base.stripTrailingZeros().scale()>0||rateBps<0||rateBps>10000)throw new IllegalArgumentException("Refund requires whole VND and an accepted rate");
        return base.multiply(BigDecimal.valueOf(rateBps)).divide(BigDecimal.valueOf(10000),0,RoundingMode.HALF_UP);
    }
    public static List<Allocation> allocate(List<Payment> payments,int rateBps){
        var unique=new HashSet<UUID>();BigDecimal base=BigDecimal.ZERO;
        for(var p:payments){if(p.paymentId()==null||p.payerId()==null||p.amount()==null||p.amount().signum()<=0||p.amount().stripTrailingZeros().scale()>0||!unique.add(p.paymentId()))throw new IllegalArgumentException("Unique original whole-VND payments required");base=base.add(p.amount());}
        var total=target(base,rateBps);if(base.signum()==0)return List.of();
        record Share(Payment payment,BigDecimal floor,BigDecimal remainder){}
        var shares=new ArrayList<Share>();BigDecimal allocated=BigDecimal.ZERO;
        for(var p:payments){var parts=p.amount().multiply(total).divideAndRemainder(base);var floor=parts[0].setScale(0,RoundingMode.DOWN);shares.add(new Share(p,floor,parts[1]));allocated=allocated.add(floor);}
        shares.sort(Comparator.comparing(Share::remainder).reversed().thenComparing(s->s.payment().paymentId().toString()));
        int extra=total.subtract(allocated).intValueExact();var result=new ArrayList<Allocation>();
        for(int i=0;i<shares.size();i++){var s=shares.get(i);result.add(new Allocation(s.payment().paymentId(),s.payment().payerId(),s.payment().amount(),s.floor().add(i<extra?BigDecimal.ONE:BigDecimal.ZERO)));}
        result.sort(Comparator.comparing(a->a.paymentId().toString()));return List.copyOf(result);
    }
}
