package com.sporthub.payment.service;

import com.sporthub.common.security.RemoteIdentity.Caller;
import com.sporthub.payment.repository.PaymentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

/** Evidence inventory only. Reading never imports assets, computes a retrospective fee or dispatches money. */
@Service @Transactional(readOnly=true)
public class LegacyFinanceInventory {
    public record Item(UUID paymentId,UUID acquisitionId,UUID bookingId,UUID buyerId,UUID sellerId,String amount,String currency,
                       String provider,String moneyClass,String state,String escrowState,long refundReviewCount,UUID bridgeSettlementId,List<String> reasons) {}
    public record Page(Instant asOf,List<Item> items,UUID nextAfterId,boolean more) {}
    private final PaymentRepository repo;private final Clock clock;
    public LegacyFinanceInventory(PaymentRepository repo,Clock clock){this.repo=repo;this.clock=clock;}
    public Page list(Caller caller,UUID after,int limit){
        caller.requireRole("ADMIN");if(limit<1||limit>500)throw new IllegalArgumentException("Inventory limit must be 1..500");
        var rows=repo.jdbc().queryForList("""
            SELECT p.*,e.state escrow_state,COALESCE(r.reviews,0) reviews,s.id settlement_id
            FROM payment_orders p LEFT JOIN transfer_escrow e ON e.payment_id=p.id
            LEFT JOIN (SELECT payment_id,count(*) reviews FROM refunds GROUP BY payment_id) r ON r.payment_id=p.id
            LEFT JOIN transfer_settlements s ON s.payment_id=p.id
            WHERE p.purpose='TRANSFER' AND NOT EXISTS(SELECT 1 FROM transfer_payment_terms t WHERE t.payment_id=p.id)
              AND (?::uuid IS NULL OR p.id>?::uuid) ORDER BY p.id LIMIT ?
            """,after,after,limit+1);
        boolean more=rows.size()>limit;var result=new ArrayList<Item>();
        for(var row:rows.subList(0,Math.min(limit,rows.size()))){
            BigDecimal amount=(BigDecimal)row.get("amount");var reasons=new ArrayList<>(List.of("MISSING_ACCEPTED_FEE_TERMS","MISSING_VERIFIED_BENEFICIARY","MISSING_HANDOFF_DECISION","MISSING_OPENING_ASSET_EVIDENCE"));
            if(amount.stripTrailingZeros().scale()>0)reasons.add("UNSUPPORTED_MONEY_PRECISION");
            result.add(new Item((UUID)row.get("id"),(UUID)row.get("acquisition_id"),(UUID)row.get("booking_id"),(UUID)row.get("payer_id"),(UUID)row.get("seller_id"),amount.toPlainString(),row.get("currency").toString(),row.get("provider").toString(),"UNKNOWN","LEGACY_REVIEW",(String)row.get("escrow_state"),((Number)row.get("reviews")).longValue(),(UUID)row.get("settlement_id"),List.copyOf(reasons)));
        }
        return new Page(clock.instant(),result,more?result.getLast().paymentId():null,more);
    }
}
