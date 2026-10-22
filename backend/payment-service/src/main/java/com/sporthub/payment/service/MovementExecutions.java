package com.sporthub.payment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sporthub.common.exception.*;
import com.sporthub.common.security.Signatures;
import com.sporthub.payment.provider.*;
import com.sporthub.payment.provider.MovementProvider.*;
import com.sporthub.payment.repository.PaymentRepository;
import com.sporthub.payment.service.FinancialLedger.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.*;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;

/** A claim/reservation commits before any provider I/O. Unknown results retain the same attempt/key. */
@Service
public class MovementExecutions {
    private final PaymentRepository repo;
    private final FinancialLedger ledger;
    private final MovementAdapters adapters;
    private final ObjectMapper json;
    private final Clock clock;
    private final TransactionTemplate transactions;
    public MovementExecutions(PaymentRepository repo,FinancialLedger ledger,MovementAdapters adapters,ObjectMapper json,Clock clock,PlatformTransactionManager manager){
        this.repo=repo;this.ledger=ledger;this.adapters=adapters;this.json=json;this.clock=clock;transactions=new TransactionTemplate(manager);
    }
    public Optional<UUID> claim(UUID orderId){
        return transactions.execute(tx->{
            var order=order(orderId);var existing=repo.jdbc().queryForList("SELECT id FROM money_movement_attempts WHERE order_id=? AND state IN('SUBMITTING','PROCESSING','UNKNOWN','REVIEW')",orderId);
            if(!existing.isEmpty())return Optional.of((UUID)existing.getFirst().get("id"));
            BigDecimal due=amount(order,"amount").subtract(amount(order,"executed_total")).add(amount(order,"returned_total"));
            if(due.signum()<=0||Set.of("FAILED_FINAL","REVIEW").contains(order.get("state")))return Optional.empty();
            String kind=text(order,"kind");if(!Set.of("SELLER_PAYOUT","BUYER_REFUND").contains(kind))return Optional.empty(); // Facility refunds require their authorization/funding protocol.
            var adapter=adapters.require(text(order,"provider"),text(order,"provider_account"),moneyClass(order));var capabilities=adapter.capabilities();
            if(!capabilities.queryByKey()||!capabilities.fundsAvailableProof()||!capabilities.finalPayoutProof()&&kind.equals("SELLER_PAYOUT")||!capabilities.finalRefundProof()&&kind.equals("BUYER_REFUND"))throw new ConflictException("Adapter does not certify this movement protocol");
            var buckets=repo.jdbc().queryForList("SELECT * FROM financial_funding_buckets WHERE payment_id=? AND scope_kind='TRANSFER_COLLECTION' FOR UPDATE",order.get("payment_id"));
            if(buckets.isEmpty()){state(orderId,"FUNDING_PENDING");return Optional.empty();}
            UUID bucket=(UUID)buckets.getFirst().get("id");
            BigDecimal reserved=repo.jdbc().queryForObject("SELECT COALESCE(sum(remaining_amount),0) FROM financial_funding_reservations WHERE bucket_id=?",BigDecimal.class,bucket);
            BigDecimal available=ledger.debitBalance(account("PROVIDER_AVAILABLE",bucket,order));
            if(available.subtract(reserved).compareTo(due)<0){state(orderId,"FUNDING_PENDING");return Optional.empty();}
            UUID destination=(UUID)order.get("beneficiary_revision_id");String counterparty;
            if(kind.equals("SELLER_PAYOUT")){
                var rows=repo.jdbc().queryForList("SELECT provider_token FROM seller_payout_account_revisions r WHERE id=? AND owner_id=? AND verification_status='VERIFIED' AND NOT EXISTS(SELECT 1 FROM seller_payout_account_revocations x WHERE x.revision_id=r.id)",destination,order.get("payee_id"));
                if(rows.isEmpty()){state(orderId,"REVIEW");return Optional.empty();}counterparty=text(rows.getFirst(),"provider_token");
            }else counterparty="ORIGINAL_PAYMENT:"+order.get("payment_id");
            UUID attempt=UUID.randomUUID();String key="movement:"+attempt;Instant at=clock.instant().truncatedTo(java.time.temporal.ChronoUnit.MICROS),expiry=at.plus(capabilities.keyTtl());
            var command=new Command(orderId,attempt,kind,due,"VND",moneyClass(order),counterparty,key,MovementProvider.fingerprint(orderId,attempt,kind,due,"VND",moneyClass(order),counterparty,key,expiry),expiry);
            int number=repo.jdbc().queryForObject("SELECT COALESCE(max(attempt_no),0)+1 FROM money_movement_attempts WHERE order_id=?",Integer.class,orderId);
            repo.jdbc().update("""
                INSERT INTO money_movement_attempts(id,order_id,attempt_no,amount,beneficiary_revision_id,funding_bucket_id,provider,provider_account,region,idempotency_key,request_fingerprint,request_body,key_expires_at,state,lease_until,next_query_at)
                VALUES(?,?,?,?,?,?,?,?,?,?,?,?::jsonb,?,'SUBMITTING',?,?)
                """,attempt,orderId,number,due,destination,bucket,capabilities.provider(),capabilities.account(),capabilities.region(),key,command.fingerprint(),write(command),Timestamp.from(expiry),Timestamp.from(at),Timestamp.from(at));
            repo.jdbc().update("INSERT INTO financial_funding_reservations(attempt_id,bucket_id,original_amount,remaining_amount) VALUES(?,?,?,?)",attempt,bucket,due,due);
            repo.jdbc().update("INSERT INTO movement_command_outbox(attempt_id,request_fingerprint) VALUES(?,?)",attempt,command.fingerprint());
            post("MOVEMENT_RESERVED",attempt.toString(),order,at,Map.of("attemptId",attempt),new Line(account(payableKind(order),payableScope(order),order),Side.D,due),new Line(account(flightKind(order),orderId,order),Side.C,due));
            state(orderId,"SUBMITTING");return Optional.of(attempt);
        });
    }
    public void dispatch(UUID attemptId,boolean allowSubmit){
        if(TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("Provider dispatch cannot hold a database transaction");
        var lease=transactions.execute(tx->{
            var seed=repo.jdbc().queryForMap("SELECT order_id FROM money_movement_attempts WHERE id=?",attemptId);order((UUID)seed.get("order_id"));var attempt=attempt(attemptId);
            if(Set.of("SUCCEEDED","FAILED_NO_OUTFLOW","REVIEW").contains(attempt.get("state"))||instant(attempt,"lease_until").isAfter(clock.instant()))return null;
            repo.jdbc().update("UPDATE money_movement_attempts SET lease_until=?,next_query_at=?,version=version+1 WHERE id=?",Timestamp.from(clock.instant().plusSeconds(40)),Timestamp.from(clock.instant().plusSeconds(15)),attemptId);return attempt;
        });
        if(lease==null)return;
        var command=command(lease);var adapter=adapters.require(text(lease,"provider"),text(lease,"provider_account"),command.moneyClass());
        try{
            int sent=((Number)lease.get("submit_count")).intValue();
            if(sent>0){var observed=adapter.query(command);if(observed.isPresent()){accept(observed.get(),"QUERY");return;}}
            if(!allowSubmit||!command.keyExpiresAt().isAfter(clock.instant())||sent>=3){unknown(attemptId,"QUERY_OR_REVIEW_REQUIRED");return;}
            boolean allowed=transactions.execute(tx->{
                order(command.orderId());var attempt=attempt(attemptId);if(Set.of("SUCCEEDED","FAILED_NO_OUTFLOW","REVIEW").contains(attempt.get("state")))return false;
                repo.jdbc().update("UPDATE money_movement_attempts SET submit_count=submit_count+1,submitted_at=COALESCE(submitted_at,?),version=version+1 WHERE id=?",Timestamp.from(clock.instant()),attemptId);return true;
            });
            if(!allowed)return;
            // Callback may arrive now: the immutable command, attempt and reserve already exist.
            var event=adapter.submit(command);accept(event,"SUBMIT");
            repo.jdbc().update("UPDATE movement_command_outbox SET dispatched_at=COALESCE(dispatched_at,clock_timestamp()) WHERE attempt_id=?",attemptId);
        }catch(MovementProvider.Uncertain ex){unknown(attemptId,ex.getMessage());}
        catch(org.springframework.web.client.RestClientException ex){unknown(attemptId,"PROVIDER_TRANSPORT_UNKNOWN");}
    }
    public void accept(Event event,String source){
        if(event==null||!Set.of("WEBHOOK","QUERY","STATEMENT","SUBMIT").contains(source))throw new IllegalArgumentException("Verified provider source required");
        var seed=repo.jdbc().queryForMap("SELECT order_id,request_body::text,provider,provider_account,region FROM money_movement_attempts WHERE id=?",event.attemptId());var command=command(seed);
        var adapter=adapters.require(text(seed,"provider"),text(seed,"provider_account"),command.moneyClass());adapter.verify(event);
        if(!event.orderId().equals(command.orderId())||!event.provider().equals(seed.get("provider"))||!event.account().equals(seed.get("provider_account"))||!event.region().equals(seed.get("region"))||!event.currency().equals(command.currency())||!event.counterparty().equals(command.counterparty())||event.amount()==null||event.amount().signum()<=0||event.amount().compareTo(command.amount())>0||!Set.of("ACCEPTED","DEBITED","FINAL","FAILED_NO_OUTFLOW","RETURN","RETURN_AVAILABLE","DEBIT_REVERSED").contains(event.kind()))throw new ForbiddenException("Movement proof is outside its immutable command binding");
        transactions.executeWithoutResult(tx->{
            order(command.orderId());var attempt=attempt(event.attemptId());
            if(attempt.get("provider_reference")!=null&&!attempt.get("provider_reference").equals(event.reference()))throw new ConflictException("Provider reference changed for an existing attempt");
            String fingerprint=Signatures.sha256(write(event));UUID id=UUID.randomUUID();
            var prior=repo.jdbc().queryForList("SELECT id,fingerprint FROM money_movement_events WHERE provider=? AND provider_account=? AND event_id=?",event.provider(),event.account(),event.eventId());
            if(!prior.isEmpty()){if(!prior.getFirst().get("fingerprint").equals(fingerprint))throw new ConflictException("Provider event replay differs");id=(UUID)prior.getFirst().get("id");}
            else repo.jdbc().update("INSERT INTO money_movement_events(id,provider,provider_account,event_id,attempt_id,order_id,source,fingerprint,event_kind,amount,currency,counterparty_reference,proof,occurred_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?::jsonb,?)",id,event.provider(),event.account(),event.eventId(),event.attemptId(),event.orderId(),source,fingerprint,event.kind(),event.amount(),event.currency(),event.counterparty(),write(event),Timestamp.from(event.occurredAt()));
            replay(event.attemptId());
        });
    }
    private void replay(UUID attemptId){
        // Revisit early final/return/availability facts without rewriting their signed receipt.
        for(int pass=0;pass<3;pass++){
            boolean progressed=false;
            var waiting=repo.jdbc().queryForList("SELECT e.id,e.proof::text FROM money_movement_events e WHERE e.attempt_id=? AND NOT EXISTS(SELECT 1 FROM movement_event_applications a WHERE a.event_id=e.id) ORDER BY e.occurred_at,CASE e.event_kind WHEN 'DEBITED' THEN 1 WHEN 'FINAL' THEN 2 WHEN 'RETURN' THEN 3 WHEN 'RETURN_AVAILABLE' THEN 4 ELSE 0 END,e.id",attemptId);
            for(var row:waiting){Event fact=read(text(row,"proof"),Event.class);if(applyFact(fact,(UUID)row.get("id"))){repo.jdbc().update("INSERT INTO movement_event_applications(event_id) VALUES(?) ON CONFLICT DO NOTHING",row.get("id"));progressed=true;}}
            if(!progressed)break;
        }
    }
    private boolean applyFact(Event event,UUID eventId){
        var order=order(event.orderId());var attempt=attempt(event.attemptId());UUID bucket=(UUID)attempt.get("funding_bucket_id");BigDecimal total=amount(attempt,"amount"),debit=amount(attempt,"debit_amount"),finished=amount(attempt,"final_amount"),returned=amount(attempt,"returned_amount");
        String current=text(attempt,"state"),next=current;BigDecimal finalDelta=BigDecimal.ZERO,returnDelta=BigDecimal.ZERO;
        var proof=Map.of("eventId",event.eventId(),"providerReference",event.reference(),"signature",event.signature());
        switch(event.kind()){
            case "ACCEPTED"->{if(!event.amount().equals(total)&&event.amount().compareTo(total)!=0)throw new ConflictException("Accepted amount differs");if(!Set.of("SUCCEEDED","FAILED_NO_OUTFLOW","REVIEW").contains(current))next="PROCESSING";}
            case "DEBITED"->{
                if(event.amount().compareTo(total)!=0)throw new ConflictException("Debit amount differs");
                if(current.equals("FAILED_NO_OUTFLOW")){review(order,event,"DEBIT_AFTER_FINAL_FAILURE");return false;}
                if(debit.signum()==0){release(event.attemptId());post("MOVEMENT_DEBIT",event.attemptId().toString(),order,event.occurredAt(),proof,new Line(account("OUTGOING_IN_TRANSIT",event.attemptId(),order),Side.D,total),new Line(account("PROVIDER_AVAILABLE",bucket,order),Side.C,total));debit=total;}
                if(finished.signum()==0)next="PROCESSING";
            }
            case "FINAL"->{
                if(event.amount().compareTo(total)!=0)throw new ConflictException("Final amount differs");
                if(current.equals("FAILED_NO_OUTFLOW")){review(order,event,"FINAL_AFTER_FINAL_FAILURE");return false;}
                if(finished.signum()==0){release(event.attemptId());post("MOVEMENT_FINAL",event.attemptId().toString(),order,event.occurredAt(),proof,new Line(account(flightKind(order),event.orderId(),order),Side.D,total),new Line(account(debit.signum()==0?"PROVIDER_AVAILABLE":"OUTGOING_IN_TRANSIT",debit.signum()==0?bucket:event.attemptId(),order),Side.C,total));finalDelta=total;finished=total;debit=total;}
                next="SUCCEEDED";
            }
            case "FAILED_NO_OUTFLOW"->{
                if(debit.signum()>0||finished.signum()>0){review(order,event,"FAILURE_CONTRADICTS_OUTFLOW");return false;}
                if(!current.equals("FAILED_NO_OUTFLOW")){release(event.attemptId());post("MOVEMENT_FAILED",event.attemptId().toString(),order,event.occurredAt(),proof,new Line(account(flightKind(order),event.orderId(),order),Side.D,total),new Line(account(payableKind(order),payableScope(order),order),Side.C,total));}
                next="FAILED_NO_OUTFLOW";
            }
            case "RETURN"->{
                if(finished.signum()==0)return false;
                if(returned.add(event.amount()).compareTo(finished)>0){review(order,event,"RETURN_EXCEEDS_FINAL");return false;}
                post("MOVEMENT_RETURN",eventId.toString(),order,event.occurredAt(),proof,new Line(account("PROVIDER_CLEARING",bucket,order),Side.D,event.amount()),new Line(account(payableKind(order),payableScope(order),order),Side.C,event.amount()));returnDelta=event.amount();returned=returned.add(returnDelta);
            }
            case "RETURN_AVAILABLE"->{
                BigDecimal already=repo.jdbc().queryForObject("SELECT COALESCE(sum(e.amount),0) FROM money_movement_events e JOIN movement_event_applications a ON a.event_id=e.id WHERE e.attempt_id=? AND e.event_kind='RETURN_AVAILABLE'",BigDecimal.class,event.attemptId());
                if(already.add(event.amount()).compareTo(returned)>0)return false;
                post("MOVEMENT_RETURN_AVAILABLE",eventId.toString(),order,event.occurredAt(),proof,new Line(account("PROVIDER_AVAILABLE",bucket,order),Side.D,event.amount()),new Line(account("PROVIDER_CLEARING",bucket,order),Side.C,event.amount()));
            }
            case "DEBIT_REVERSED"->{review(order,event,"DEBIT_REVERSAL_REQUIRES_STOP_AND_RETURN_PROOF");return false;}
            default->throw new IllegalArgumentException("Unsupported provider fact");
        }
        repo.jdbc().update("UPDATE money_movement_attempts SET provider_reference=COALESCE(provider_reference,?),state=?,debit_amount=?,final_amount=?,returned_amount=?,final_at=CASE WHEN ?>0 THEN COALESCE(final_at,?) ELSE final_at END,version=version+1 WHERE id=?",event.reference(),next,debit,finished,returned,finished,Timestamp.from(event.occurredAt()),event.attemptId());
        String orderState=next.equals("FAILED_NO_OUTFLOW")?"FAILED_RETRYABLE":next;
        BigDecimal paid=amount(order,"executed_total").add(finalDelta),back=amount(order,"returned_total").add(returnDelta);
        if(next.equals("SUCCEEDED"))orderState=paid.subtract(back).compareTo(amount(order,"amount"))==0?"SUCCEEDED":"RETURNED";
        var active=repo.jdbc().queryForList("SELECT state FROM money_movement_attempts WHERE order_id=? AND state IN('SUBMITTING','PROCESSING','UNKNOWN','REVIEW')",event.orderId());if(!active.isEmpty())orderState=text(active.getFirst(),"state");
        repo.jdbc().update("UPDATE money_movement_orders SET state=?,executed_total=?,returned_total=?,version=version+1,updated_at=clock_timestamp() WHERE id=?",orderState,paid,back,event.orderId());return true;
    }
    private void unknown(UUID attemptId,String code){transactions.executeWithoutResult(tx->{var seed=repo.jdbc().queryForMap("SELECT order_id FROM money_movement_attempts WHERE id=?",attemptId);UUID order=(UUID)seed.get("order_id");order(order);var attempt=attempt(attemptId);if(Set.of("SUCCEEDED","FAILED_NO_OUTFLOW","REVIEW").contains(attempt.get("state")))return;repo.jdbc().update("UPDATE money_movement_attempts SET state='UNKNOWN',last_error_code=?,version=version+1 WHERE id=?",code==null?"PROVIDER_UNKNOWN":code.substring(0,Math.min(80,code.length())),attemptId);state(order,"UNKNOWN");});}
    private void review(Map<String,Object> order,Event event,String reason){
        repo.jdbc().update("INSERT INTO financial_reconciliation_cases(id,case_key,category,payment_id,settlement_id,movement_id,reason) VALUES(?,?,'MOVEMENT_PROOF_CONFLICT',?,?,?,?) ON CONFLICT(case_key) DO NOTHING",UUID.randomUUID(),"movement:"+event.attemptId()+":"+event.eventId(),order.get("payment_id"),order.get("settlement_id"),order.get("id"),reason);
        state(event.orderId(),"REVIEW");
        repo.jdbc().update("UPDATE money_movement_attempts SET state='REVIEW',last_error_code=?,version=version+1 WHERE id=? AND state NOT IN('SUCCEEDED','FAILED_NO_OUTFLOW','REVIEW')",reason,event.attemptId());
    }
    private void post(String type,String reference,Map<String,Object> order,Instant at,Object proof,Line... lines){ledger.post(new Effect(type,reference,(UUID)order.get("payment_id"),(UUID)order.get("settlement_id"),(UUID)order.get("id"),"VND",moneyClass(order),at,null,proof,List.of(lines)));}
    private void release(UUID attempt){repo.jdbc().update("UPDATE financial_funding_reservations SET remaining_amount=0,version=version+1 WHERE attempt_id=? AND remaining_amount>0",attempt);}
    private void state(UUID order,String state){repo.jdbc().update("UPDATE money_movement_orders SET state=?,version=version+1,updated_at=clock_timestamp() WHERE id=?",state,order);}
    private String payableKind(Map<String,Object> order){return text(order,"kind").equals("SELLER_PAYOUT")?"SELLER_PAYABLE":text(order,"kind").equals("FACILITY_POLICY_REFUND")?"FACILITY_REFUND_PAYABLE":"BUYER_REFUND_PAYABLE";}
    private String flightKind(Map<String,Object> order){return text(order,"kind").equals("SELLER_PAYOUT")?"PAYOUT_IN_FLIGHT":"REFUND_IN_FLIGHT";}
    private UUID payableScope(Map<String,Object> order){return text(order,"kind").equals("SELLER_PAYOUT")?(UUID)order.get("settlement_id"):(UUID)order.get("id");}
    private Account account(String kind,UUID scope,Map<String,Object> order){return new Account(kind,scope,text(order,"provider"),text(order,"provider_account"),"VND",moneyClass(order));}
    private MoneyClass moneyClass(Map<String,Object> row){return MoneyClass.valueOf(text(row,"money_class"));}
    private Map<String,Object> order(UUID id){return repo.jdbc().queryForMap("SELECT * FROM money_movement_orders WHERE id=? FOR UPDATE",id);}
    private Map<String,Object> attempt(UUID id){return repo.jdbc().queryForMap("SELECT * FROM money_movement_attempts WHERE id=? FOR UPDATE",id);}
    private BigDecimal amount(Map<String,Object> row,String field){return (BigDecimal)row.get(field);}
    private String text(Map<String,Object> row,String field){return Objects.toString(row.get(field));}
    private Instant instant(Map<String,Object> row,String field){return ((Timestamp)row.get(field)).toInstant();}
    private Command command(Map<String,Object> row){return read(text(row,"request_body"),Command.class);}
    private <T>T read(String value,Class<T> type){try{return json.readValue(value,type);}catch(Exception ex){throw new IllegalStateException("Stored movement proof is malformed",ex);}}
    private String write(Object value){try{return json.writeValueAsString(value);}catch(Exception ex){throw new IllegalArgumentException("Movement proof cannot be encoded",ex);}}
}
