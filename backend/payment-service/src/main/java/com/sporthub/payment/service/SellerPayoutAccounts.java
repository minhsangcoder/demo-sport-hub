package com.sporthub.payment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sporthub.common.exception.*;
import com.sporthub.common.security.RemoteIdentity.Caller;
import com.sporthub.common.security.Signatures;
import com.sporthub.payment.provider.MovementAdapters;
import com.sporthub.payment.repository.PaymentRepository;
import jakarta.validation.constraints.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

@Service @Transactional(readOnly=true)
public class SellerPayoutAccounts {
    public record Input(UUID accountId,@NotBlank @Size(max=40) String provider,
                        @NotBlank @Size(max=60) String maskedDestination,@NotBlank @Size(max=200) String consentReference) {}
    public record Destination(UUID id,UUID accountId,int revision,String provider,String currency,String moneyClass,
                              String maskedLabel,String verificationStatus,boolean revoked,Instant createdAt,Instant verifiedAt) {}
    public record Verified(UUID revisionId,UUID sellerId,String provider,String providerAccount,String currency,String moneyClass,String maskedLabel,boolean verified) {}
    private final PaymentRepository repo;private final MovementAdapters adapters;private final ObjectMapper json;
    public SellerPayoutAccounts(PaymentRepository repo,MovementAdapters adapters,ObjectMapper json){this.repo=repo;this.adapters=adapters;this.json=json;}
    public List<Destination> mine(Caller caller){caller.requireRole("CUSTOMER");return destinations("WHERE a.owner_id=? ORDER BY r.created_at DESC,r.id",caller.id());}
    @Transactional
    public Destination register(Input input,String key,Caller caller){
        caller.requireRole("CUSTOMER");if(key==null||!key.matches("[A-Za-z0-9_.:-]{1,100}"))throw new IllegalArgumentException("A valid Idempotency-Key is required");repo.lock(caller.id());
        String fingerprint=Signatures.sha256(write(input));var prior=repo.jdbc().queryForList("SELECT fingerprint,revision_id FROM beneficiary_registration_requests WHERE owner_id=? AND idempotency_key=?",caller.id(),key);
        if(!prior.isEmpty()){
            if(!fingerprint.equals(prior.getFirst().get("fingerprint")))throw new ConflictException("Beneficiary request key was used with different input");return own((UUID)prior.getFirst().get("revision_id"),caller);
        }
        var capability=adapters.capabilities().stream().filter(c->c.provider().equals(input.provider())).findFirst().orElseThrow(()->new ConflictException("Beneficiary verification adapter is unavailable"));
        var adapter=adapters.require(capability.provider(),capability.account(),capability.moneyClass());
        UUID account=input.accountId();
        if(account!=null){
            var rows=repo.jdbc().queryForList("SELECT * FROM seller_payout_accounts WHERE id=? FOR UPDATE",account);
            if(rows.isEmpty()||!caller.id().equals(rows.getFirst().get("owner_id")))throw new ForbiddenException("Payout account belongs to another Customer");
            if(!capability.provider().equals(rows.getFirst().get("provider"))||!capability.account().equals(rows.getFirst().get("provider_account"))||!capability.moneyClass().name().equals(rows.getFirst().get("money_class")))throw new ConflictException("Account provider scope cannot be changed");
        } else {
            account=UUID.randomUUID();repo.jdbc().update("INSERT INTO seller_payout_accounts(id,owner_id,provider,provider_account,currency,money_class) VALUES(?,?,?,?,'VND',?)",account,caller.id(),capability.provider(),capability.account(),capability.moneyClass().name());
        }
        var verified=adapter.verifyBeneficiary(caller.id(),input.maskedDestination(),input.consentReference());
        if(!verified.ownerId().equals(caller.id())||verified.token()==null||verified.token().isBlank()||verified.proofReference()==null||verified.verifiedAt()==null)throw new ConflictException("Provider did not verify the beneficiary ownership");
        int revision=repo.jdbc().queryForObject("SELECT COALESCE(max(revision),0)+1 FROM seller_payout_account_revisions WHERE account_id=?",Integer.class,account);UUID id=UUID.randomUUID();
        repo.jdbc().update("INSERT INTO seller_payout_account_revisions(id,account_id,owner_id,revision,provider,provider_account,provider_token,masked_label,verification_status,consent_reference,verified_at,verification_proof) VALUES(?,?,?,?,?,?,?,?,'VERIFIED',?,?,?::jsonb)",id,account,caller.id(),revision,capability.provider(),capability.account(),verified.token(),verified.maskedLabel(),input.consentReference(),Timestamp.from(verified.verifiedAt()),write(Map.of("provider",capability.provider(),"providerAccount",capability.account(),"ownerId",verified.ownerId(),"proofReference",verified.proofReference(),"moneyClass",capability.moneyClass().name())));
        repo.jdbc().update("INSERT INTO beneficiary_registration_requests(owner_id,idempotency_key,fingerprint,revision_id) VALUES(?,?,?,?)",caller.id(),key,fingerprint,id);return own(id,caller);
    }
    public Destination own(UUID id,Caller caller){caller.requireRole("CUSTOMER");var rows=destinations("WHERE r.id=? AND a.owner_id=?",id,caller.id());if(rows.isEmpty())throw new ForbiddenException("Payout destination is outside your account");return rows.getFirst();}
    @Transactional
    public Destination revoke(UUID id,String reason,Caller caller){
        var owned=own(id,caller);if(reason==null||reason.isBlank()||reason.length()>500)throw new IllegalArgumentException("Supply a revocation reason");repo.lock(owned.accountId());
        repo.jdbc().update("INSERT INTO seller_payout_account_revocations(revision_id,actor_id,reason) VALUES(?,?,?) ON CONFLICT DO NOTHING",id,caller.id(),reason);return own(id,caller);
    }
    public Verified verified(UUID revision,UUID seller){
        var rows=repo.jdbc().queryForList("SELECT r.*,a.currency,a.money_class FROM seller_payout_account_revisions r JOIN seller_payout_accounts a ON a.id=r.account_id LEFT JOIN seller_payout_account_revocations v ON v.revision_id=r.id WHERE r.id=? AND a.owner_id=? AND r.owner_id=a.owner_id AND r.verification_status='VERIFIED' AND v.revision_id IS NULL",revision,seller);
        if(rows.isEmpty())throw new ConflictException("Select a verified, active payout destination belonging to the seller");var r=rows.getFirst();
        adapters.require(r.get("provider").toString(),r.get("provider_account").toString(),FinancialLedger.MoneyClass.valueOf(r.get("money_class").toString()));
        return new Verified(revision,seller,r.get("provider").toString(),r.get("provider_account").toString(),r.get("currency").toString(),r.get("money_class").toString(),r.get("masked_label").toString(),true);
    }
    private List<Destination> destinations(String where,Object...args){return repo.jdbc().query("SELECT r.id,r.account_id,r.revision,r.provider,a.currency,a.money_class,r.masked_label,r.verification_status,r.created_at,r.verified_at,v.revision_id IS NOT NULL revoked FROM seller_payout_account_revisions r JOIN seller_payout_accounts a ON a.id=r.account_id LEFT JOIN seller_payout_account_revocations v ON v.revision_id=r.id "+where,(r,n)->new Destination(r.getObject("id",UUID.class),r.getObject("account_id",UUID.class),r.getInt("revision"),r.getString("provider"),r.getString("currency"),r.getString("money_class"),r.getString("masked_label"),r.getString("verification_status"),r.getBoolean("revoked"),r.getTimestamp("created_at").toInstant(),r.getTimestamp("verified_at")==null?null:r.getTimestamp("verified_at").toInstant()),args);}
    private String write(Object value){try{return json.writeValueAsString(value);}catch(Exception ex){throw new IllegalArgumentException("Beneficiary evidence could not be encoded",ex);}}
}
