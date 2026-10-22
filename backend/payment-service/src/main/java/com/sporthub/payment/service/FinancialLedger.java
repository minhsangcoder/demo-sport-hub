package com.sporthub.payment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sporthub.common.exception.ConflictException;
import com.sporthub.common.security.Signatures;
import com.sporthub.payment.repository.PaymentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

/** Append-only business subledger. A unique effect is the unit of idempotency, not a browser request. */
@Service
public class FinancialLedger {
    public enum MoneyClass { REAL, DEMO, UNKNOWN }
    public enum Side { D, C }
    public record Account(String kind,UUID scope,String provider,String providerAccount,String currency,MoneyClass moneyClass) {}
    public record Line(Account account,Side side,BigDecimal amount) {}
    public record Effect(String type,String reference,UUID paymentId,UUID settlementId,UUID movementId,
                         String currency,MoneyClass moneyClass,Instant effectiveAt,UUID reversalOf,Object evidence,List<Line> lines) {}
    private final PaymentRepository repo;private final ObjectMapper json;
    public FinancialLedger(PaymentRepository repo,ObjectMapper json){this.repo=repo;this.json=json;}
    public static BigDecimal wholePositive(BigDecimal amount){
        if(amount==null||amount.signum()<=0||amount.stripTrailingZeros().scale()>0||amount.precision()-amount.scale()>20)throw new IllegalArgumentException("Positive whole VND amount required");
        return amount.setScale(0);
    }
    @Transactional
    public UUID post(Effect effect){
        if(effect.type()==null||effect.reference()==null||effect.effectiveAt()==null||!"VND".equals(effect.currency())||effect.moneyClass()==null||effect.lines()==null||effect.lines().size()<2)throw new IllegalArgumentException("Complete financial effect required");
        BigDecimal balance=BigDecimal.ZERO;var normalized=new ArrayList<Map<String,Object>>();
        for(var line:effect.lines()){
            var amount=wholePositive(line.amount());var account=line.account();
            if(account==null||line.side()==null||!account.currency().equals(effect.currency())||account.moneyClass()!=effect.moneyClass())throw new IllegalArgumentException("Journal currency and money class must match all accounts");
            balance=balance.add(line.side()==Side.D?amount:amount.negate());
            normalized.add(Map.of("account",account,"side",line.side(),"amount",amount.toPlainString()));
        }
        if(balance.signum()!=0)throw new IllegalArgumentException("Journal debit and credit must balance");
        String fingerprint=Signatures.sha256(write(canonical(json.valueToTree(Arrays.asList(effect.type(),effect.reference(),effect.paymentId(),effect.settlementId(),effect.movementId(),effect.currency(),effect.moneyClass(),effect.effectiveAt(),effect.reversalOf(),effect.evidence(),normalized)))));
        repo.lock(UUID.nameUUIDFromBytes(("JOURNAL|"+effect.type()+"|"+effect.reference()).getBytes(StandardCharsets.UTF_8)));
        var prior=repo.jdbc().queryForList("SELECT id,evidence->>'postingFingerprint' fingerprint FROM financial_journals WHERE effect_type=? AND effect_ref=?",effect.type(),effect.reference());
        if(!prior.isEmpty()){
            if(!fingerprint.equals(prior.getFirst().get("fingerprint")))throw new ConflictException("Financial effect replay differs from the posted evidence");
            return (UUID)prior.getFirst().get("id");
        }
        UUID id=UUID.randomUUID();
        repo.jdbc().update("INSERT INTO financial_journals(id,effect_type,effect_ref,payment_id,settlement_id,movement_id,currency,money_class,effective_at,reversal_of,evidence) VALUES(?,?,?,?,?,?,?,?,?,?,?::jsonb)",
                id,effect.type(),effect.reference(),effect.paymentId(),effect.settlementId(),effect.movementId(),effect.currency(),effect.moneyClass().name(),Timestamp.from(effect.effectiveAt()),effect.reversalOf(),write(Map.of("postingFingerprint",fingerprint,"proof",effect.evidence()==null?Map.of():effect.evidence())));
        int index=0;for(var line:effect.lines())repo.jdbc().update("INSERT INTO financial_postings(journal_id,line_no,account_id,side,amount) VALUES(?,?,?,?,?)",id,++index,accountId(line.account()),line.side().name(),wholePositive(line.amount()));
        return id;
    }
    public BigDecimal debitBalance(Account account){
        var result=repo.jdbc().queryForObject("SELECT COALESCE(sum(CASE p.side WHEN 'D' THEN p.amount ELSE -p.amount END),0) FROM financial_accounts a JOIN financial_postings p ON p.account_id=a.id WHERE a.kind=? AND a.scope_id IS NOT DISTINCT FROM ?::uuid AND a.provider=? AND a.provider_account=? AND a.currency=? AND a.money_class=?",
                BigDecimal.class,account.kind(),account.scope(),account.provider(),account.providerAccount(),account.currency(),account.moneyClass().name());return result==null?BigDecimal.ZERO:result;
    }
    private UUID accountId(Account account){
        UUID id=UUID.randomUUID();repo.jdbc().update("INSERT INTO financial_accounts(id,kind,scope_id,provider,provider_account,currency,money_class) VALUES(?,?,?,?,?,?,?) ON CONFLICT DO NOTHING",id,account.kind(),account.scope(),account.provider(),account.providerAccount(),account.currency(),account.moneyClass().name());
        return repo.jdbc().queryForObject("SELECT id FROM financial_accounts WHERE kind=? AND scope_id IS NOT DISTINCT FROM ?::uuid AND provider=? AND provider_account=? AND currency=? AND money_class=?",UUID.class,account.kind(),account.scope(),account.provider(),account.providerAccount(),account.currency(),account.moneyClass().name());
    }
    private String write(Object value){try{return json.writeValueAsString(value);}catch(Exception ex){throw new IllegalArgumentException("Financial evidence could not be encoded",ex);}}
    private com.fasterxml.jackson.databind.JsonNode canonical(com.fasterxml.jackson.databind.JsonNode value){
        if(value.isObject()){
            var result=json.createObjectNode();var names=new TreeSet<String>();value.fieldNames().forEachRemaining(names::add);for(String name:names)result.set(name,canonical(value.get(name)));return result;
        }
        if(value.isArray()){var result=json.createArrayNode();value.forEach(item->result.add(canonical(item)));return result;}
        if(value.isNumber())return new com.fasterxml.jackson.databind.node.DecimalNode(value.decimalValue().stripTrailingZeros());return value;
    }
}
