package com.sporthub.payment.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sporthub.common.exception.*;
import com.sporthub.common.security.Signatures;
import com.sporthub.payment.repository.PaymentRepository;
import com.sporthub.payment.service.FinancialLedger;
import com.sporthub.payment.service.FinancialLedger.MoneyClass;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Durable local bank simulator. Its transaction commits before simulated response loss. */
@Component @Profile("(local | demo) & !prod & !production")
public class DemoMovementProvider implements MovementProvider {
    public enum Fault { SUCCESS, ACCEPT_ONLY, DEBIT_ONLY, FAIL_NO_OUTFLOW, TIMEOUT_AFTER_SUCCESS, TIMEOUT_BEFORE_ACCEPT, UNKNOWN_FOREVER }
    private final PaymentRepository repo;private final ObjectMapper json;private final Clock clock;private final String secret;private final TransactionTemplate bank;
    private final Map<UUID,Fault> faults=new ConcurrentHashMap<>();
    public DemoMovementProvider(PaymentRepository repo,ObjectMapper json,Clock clock,PlatformTransactionManager manager,
                                @Value("${sporthub.payment.demo.movement-secret:${sporthub.payment.demo.callback-secret}}") String secret){
        if(secret==null||secret.length()<32)throw new IllegalArgumentException("Demo movement signing key must contain at least 32 characters");
        this.repo=repo;this.json=json;this.clock=clock;this.secret=secret;this.bank=new TransactionTemplate(manager);bank.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }
    public Capabilities capabilities(){return new Capabilities("DEMO","demo-main","local",MoneyClass.DEMO,Duration.ofDays(7),true,true,true,true,true,true,true,false);}
    public Beneficiary verifyBeneficiary(UUID owner,String maskedDestination,String consentReference){
        if(owner==null||maskedDestination==null||!maskedDestination.matches("DEMO [A-Za-z0-9 -]{1,40}")||consentReference==null||consentReference.isBlank())throw new IllegalArgumentException("Supply an explicit DEMO destination label and consent reference; no real bank details");
        return new Beneficiary(owner,"DEMO-BENEFICIARY-"+UUID.randomUUID(),"DEMO •••• "+maskedDestination.substring(Math.max(0,maskedDestination.length()-4)),"DEMO-VERIFY-"+UUID.randomUUID(),clock.instant());
    }
    /** Programmatic fault injection for tests; there is no production HTTP control surface. */
    public void fault(UUID order,Fault fault){faults.put(order,fault);}
    public Event submit(Command command){
        validate(command);Fault fault=faults.getOrDefault(command.orderId(),Fault.SUCCESS);
        if(fault==Fault.TIMEOUT_BEFORE_ACCEPT||fault==Fault.UNKNOWN_FOREVER)throw new Uncertain("DEMO_RESPONSE_UNKNOWN");
        Event result=bank.execute(tx->{
            repo.lock(command.attemptId());var prior=repo.jdbc().queryForList("SELECT request_fingerprint,result::text FROM demo_provider_movement_commands WHERE provider_account=? AND region=? AND idempotency_key=?","demo-main","local",command.idempotencyKey());
            if(!prior.isEmpty()){
                if(!command.fingerprint().equals(prior.getFirst().get("request_fingerprint")))throw new ConflictException("Demo idempotency key payload differs");
                return read(prior.getFirst().get("result").toString());
            }
            String kind=switch(fault){case ACCEPT_ONLY->"ACCEPTED";case DEBIT_ONLY->"DEBITED";case FAIL_NO_OUTFLOW->"FAILED_NO_OUTFLOW";default->"FINAL";};
            Event event=signed(command,kind,command.amount(),"DEMO-"+command.attemptId()+"-"+kind,clock.instant());
            repo.jdbc().update("INSERT INTO demo_provider_movement_commands(attempt_id,order_id,provider_account,region,idempotency_key,request_fingerprint,request_body,result) VALUES(?,?,'demo-main','local',?,?,?::jsonb,?::jsonb)",command.attemptId(),command.orderId(),command.idempotencyKey(),command.fingerprint(),write(command),write(event));
            persist(event);return event;
        });
        if(fault==Fault.TIMEOUT_AFTER_SUCCESS)throw new Uncertain("DEMO_LOST_FINAL_RESPONSE");return result;
    }
    public Optional<Event> query(Command command){
        validateScope(command);var rows=repo.jdbc().queryForList("SELECT request_fingerprint,result::text FROM demo_provider_movement_commands WHERE provider_account='demo-main' AND region='local' AND idempotency_key=?",command.idempotencyKey());
        if(rows.isEmpty())return Optional.empty();if(!command.fingerprint().equals(rows.getFirst().get("request_fingerprint")))throw new ConflictException("Query command differs from the original payload");
        var newer=repo.jdbc().queryForList("SELECT body::text FROM demo_provider_movement_events WHERE attempt_id=? AND body->>'kind' IN('ACCEPTED','DEBITED','FINAL','FAILED_NO_OUTFLOW') ORDER BY occurred_at DESC,CASE body->>'kind' WHEN 'FINAL' THEN 4 WHEN 'FAILED_NO_OUTFLOW' THEN 3 WHEN 'DEBITED' THEN 2 ELSE 1 END DESC,event_id DESC LIMIT 1",command.attemptId());
        return Optional.of(read(newer.isEmpty()?rows.getFirst().get("result").toString():newer.getFirst().get("body").toString()));
    }
    /** Simulate a later independently signed provider fact, including a partial bank return. */
    public Event event(Command command,String kind,BigDecimal amount,String uniqueReference){
        validateScope(command);if(!Set.of("DEBITED","FINAL","FAILED_NO_OUTFLOW","RETURN","DEBIT_REVERSED","RETURN_AVAILABLE").contains(kind))throw new IllegalArgumentException("Unknown simulated provider fact");
        FinancialLedger.wholePositive(amount);if(amount.compareTo(command.amount())>0)throw new IllegalArgumentException("Simulated fact exceeds the command");
        return bank.execute(tx->{repo.lock(command.attemptId());if(query(command).isEmpty())throw new ConflictException("Simulated command has not reached the provider");var event=signed(command,kind,amount,"DEMO-EVENT-"+uniqueReference,clock.instant());persist(event);return event;});
    }
    public void verify(Event event){
        if(event==null||!event.provider().equals("DEMO")||!event.account().equals("demo-main")||!event.region().equals("local")||!event.currency().equals("VND")||event.occurredAt().isAfter(clock.instant().plusSeconds(300))||!Signatures.matches(sign(event),event.signature()))throw new ForbiddenException("Invalid DEMO movement proof");
    }
    public List<Event> statement(Instant from,Instant to){
        if(from==null||to==null||!from.isBefore(to)||Duration.between(from,to).compareTo(Duration.ofDays(31))>0)throw new IllegalArgumentException("Statement window must be at most 31 days");
        return repo.jdbc().query("SELECT body::text FROM demo_provider_movement_events WHERE occurred_at>=? AND occurred_at<? ORDER BY occurred_at,event_id",(r,n)->read(r.getString(1)),Timestamp.from(from),Timestamp.from(to));
    }
    private void validate(Command command){validateScope(command);if(!command.keyExpiresAt().isAfter(clock.instant()))throw new ConflictException("Provider idempotency scope expired; query or review the existing attempt");}
    private void validateScope(Command command){
        if(command==null||command.moneyClass()!=MoneyClass.DEMO||!"VND".equals(command.currency())||command.counterparty()==null||command.counterparty().isBlank()||command.idempotencyKey()==null||command.fingerprint()==null||command.fingerprint().length()!=64)throw new ForbiddenException("DEMO adapter only accepts bound simulated commands");FinancialLedger.wholePositive(command.amount());
        if(!command.fingerprint().equals(MovementProvider.fingerprint(command.orderId(),command.attemptId(),command.kind(),command.amount(),command.currency(),command.moneyClass(),command.counterparty(),command.idempotencyKey(),command.keyExpiresAt())))throw new ForbiddenException("Movement command fingerprint is invalid");
    }
    private Event signed(Command c,String kind,BigDecimal amount,String eventId,Instant at){
        var event=new Event("DEMO","demo-main","local",eventId,"DEMO-MOVEMENT-"+c.attemptId(),c.orderId(),c.attemptId(),kind,amount.setScale(0),c.currency(),c.counterparty(),at,null);
        return new Event(event.provider(),event.account(),event.region(),event.eventId(),event.reference(),event.orderId(),event.attemptId(),event.kind(),event.amount(),event.currency(),event.counterparty(),event.occurredAt(),sign(event));
    }
    private String sign(Event e){return Signatures.hmac(secret,"MOVEMENT|"+e.provider()+"|"+e.account()+"|"+e.region()+"|"+e.eventId()+"|"+e.reference()+"|"+e.orderId()+"|"+e.attemptId()+"|"+e.kind()+"|"+e.amount().stripTrailingZeros().toPlainString()+"|"+e.currency()+"|"+e.counterparty()+"|"+e.occurredAt());}
    private void persist(Event event){
        var prior=repo.jdbc().queryForList("SELECT body::text FROM demo_provider_movement_events WHERE event_id=?",event.eventId());
        if(!prior.isEmpty()){if(!read(prior.getFirst().get("body").toString()).equals(event))throw new ConflictException("Conflicting simulated provider event");return;}
        repo.jdbc().update("INSERT INTO demo_provider_movement_events(event_id,attempt_id,body,occurred_at) VALUES(?,?,?::jsonb,?)",event.eventId(),event.attemptId(),write(event),Timestamp.from(event.occurredAt()));
    }
    private Event read(String value){try{return json.readValue(value,Event.class);}catch(Exception ex){throw new IllegalStateException("Invalid simulated provider evidence",ex);}}
    private String write(Object value){try{return json.writeValueAsString(value);}catch(Exception ex){throw new IllegalStateException("Could not encode simulated provider evidence",ex);}}
}
