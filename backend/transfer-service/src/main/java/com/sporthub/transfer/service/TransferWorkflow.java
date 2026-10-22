package com.sporthub.transfer.service;

import com.sporthub.transfer.repository.TransferRepository;
import com.sporthub.transfer.repository.TransferRepository.Row;
import com.sporthub.common.exception.*;
import com.sporthub.common.event.*;
import com.sporthub.common.money.TransferHandoff;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;

/** Durable local command + idempotent Booking receipt; there is no cross-database transaction. */
@Service
public class TransferWorkflow {
 private final TransferRepository repo;private final TransferDependencies dependencies;private final TransactionTemplate tx;
 private final Clock clock;private final ReliableOutbox outbox;private final int maxAttempts;
 public TransferWorkflow(TransferRepository repo,TransferDependencies dependencies,PlatformTransactionManager manager,Clock clock,ReliableOutbox outbox,@Value("${transfer.workflow.max-attempts:10}") int maxAttempts){
  if(maxAttempts<1||maxAttempts>100)throw new IllegalArgumentException("Transfer retry budget must be 1..100");
  this.repo=repo;this.dependencies=dependencies;this.tx=new TransactionTemplate(manager);this.clock=clock;this.outbox=outbox;this.maxAttempts=maxAttempts;
 }
 @Scheduled(fixedDelayString="${transfer.workflow-delay-ms:2000}",initialDelayString="${transfer.workflow-delay-ms:2000}") public void tick(){
  var ids=repo.jdbc().queryForList("SELECT id FROM transfer_listings WHERE (workflow NOT IN ('READY','REVIEW') AND next_attempt_at<=?) OR (workflow='READY' AND state IN ('ACTIVE','LOCKED') AND deadline<=?) OR (workflow='READY' AND state='LOCKED' AND active_acquisition_id IN (SELECT id FROM transfer_acquisitions WHERE state='PENDING' AND expires_at<=?)) ORDER BY updated_at,id LIMIT 20",UUID.class,Timestamp.from(clock.instant()),Timestamp.from(clock.instant()),Timestamp.from(clock.instant()));
  for(var id:ids)try{sync(id);}catch(RuntimeException ex){/* The uncommitted command remains durable for the next tick. */}
 }
 public void sync(UUID id){tx.executeWithoutResult(status->{
  repo.lock(id);var r=repo.find(id);if(r.workflow().equals("REVIEW"))return;
  // Expiration precedes unpublished/lock/edit commands too, not only READY rows.
  if(!r.workflow().equals("HANDOFF")&&!Set.of("COMPLETED","WITHDRAWN","EXPIRED","PENDING_AUDIT").contains(r.state())){
   if(r.state().equals("LOCKED")){
    var a=repo.acquisition(r.activeAcquisitionId());
    if(a.state().equals("PENDING")&&(!a.expiresAt().isAfter(clock.instant())||!r.deadline().isAfter(clock.instant()))){
     repo.jdbc().update("UPDATE transfer_acquisitions SET state='EXPIRED',updated_at=NOW() WHERE id=?",a.id());
     repo.jdbc().update("UPDATE transfer_listings SET workflow='UNLOCK',next_attempt_at=? WHERE id=?",Timestamp.from(clock.instant()),id);r=repo.find(id);
    }
   }
   if(!r.deadline().isAfter(clock.instant())){repo.jdbc().update("UPDATE transfer_listings SET state='EXPIRED',workflow='WITHDRAW',next_attempt_at=? WHERE id=?",Timestamp.from(clock.instant()),id);r=repo.find(id);}
  }
  if(r.workflow().equals("READY"))return;
  var command=command(r);String stage=r.workflow();String action=switch(stage){case "REGISTER"->"register";case "EDIT"->"edit";case "LOCK"->"lock";case "UNLOCK"->"unlock";case "WITHDRAW"->"withdraw";case "HANDOFF"->"complete";default->throw new IllegalStateException("Unknown transfer workflow");};
  JsonNode handoffProof=null;
  try{
   if(stage.equals("HANDOFF"))handoffProof=handoff(r,command);
   else dependencies.command(action,command);
  }catch(ConflictException ex){
   if(stage.equals("HANDOFF"))businessReview(r,"HANDOFF_REJECTED");
   else if(stage.equals("LOCK")){
    repo.jdbc().update("UPDATE transfer_acquisitions SET state='FAILED',updated_at=NOW() WHERE id=? AND state='PENDING'",r.activeAcquisitionId());
    repo.jdbc().update("UPDATE transfer_listings SET workflow='UNLOCK',last_error='BOOKING_PRECONDITION_REJECTED',attempts=0,next_attempt_at=? WHERE id=?",Timestamp.from(clock.instant()),id);
   }else if(Set.of("REGISTER","EDIT").contains(stage)){
    repo.jdbc().update("UPDATE transfer_listings SET state='WITHDRAWN',workflow='WITHDRAW',last_error='BOOKING_PRECONDITION_REJECTED',attempts=0,next_attempt_at=? WHERE id=?",Timestamp.from(clock.instant()),id);
   }else retry(r,"BOOKING_PRECONDITION_REJECTED");return;
  }catch(RuntimeException ex){retry(r,ex instanceof TransferDependencies.Unavailable u?u.getReason():"BOOKING_UNAVAILABLE");return;}
  // Local DB/outbox errors must roll back; do not catch and write inside an aborted transaction.
  // A committed Booking receipt repairs ambiguous remote success on the next attempt.
  if(stage.equals("HANDOFF")){
   var a=repo.acquisition(r.activeAcquisitionId());
   repo.jdbc().update("UPDATE transfer_acquisitions SET state='SUCCESS',updated_at=NOW() WHERE id=?",a.id());
   repo.jdbc().update("UPDATE transfer_listings SET state='COMPLETED',workflow='READY',last_error=NULL,attempts=0,version=version+1,updated_at=NOW() WHERE id=?",id);
   repo.audit(r,a.buyerId(),"TRANSFER_COMPLETED",Map.of("paymentId",a.paymentId(),"acquisitionId",a.id(),"sellerId",r.sellerId(),"buyerId",a.buyerId(),"amount",a.amount(),"currency",a.currency()));
   if(a.financeTerms()!=null){
    var payload=new LinkedHashMap<String,Object>();payload.put("acquisitionId",a.id());payload.put("listingId",r.id());payload.put("bookingId",r.bookingId());payload.put("facilityId",r.facilityId());payload.put("courtId",r.courtId());payload.put("sellerId",r.sellerId());payload.put("buyerId",a.buyerId());payload.put("paymentId",a.paymentId());payload.put("amount",a.amount());payload.put("currency",a.currency());payload.put("termsHash",a.financeTerms().path("termsHash").asText());payload.put("decisionRef",a.id());payload.put("commandFingerprint",handoffProof.path("commandFingerprint").asText());payload.put("handoffAt",handoffProof.path("decidedAt").asText());payload.put("bookingVersion",handoffProof.path("bookingVersion").asLong());
    outbox.record(RabbitMQConfig.EXCHANGE_PAYMENT,DomainEvent.create("transfer.handoff.completed",1,"transfer-service",a.id(),payload));
   }
  }else if(stage.equals("UNLOCK")){
   repo.jdbc().update("UPDATE transfer_listings SET state=?,workflow='READY',active_acquisition_id=NULL,last_error=NULL,attempts=0,version=version+1,updated_at=NOW() WHERE id=?",r.deadline().isAfter(clock.instant())?"ACTIVE":"EXPIRED",id);
  }else repo.jdbc().update("UPDATE transfer_listings SET workflow='READY',last_error=NULL,attempts=0,version=version+1,updated_at=NOW() WHERE id=?",id);
  repo.audit(r,null,"TRANSFER_WORKFLOW_APPLIED",Map.of("workflow",stage));
 });}
 private Map<String,Object> command(Row r){
  var c=new HashMap<String,Object>();c.put("listingId",r.id());c.put("bookingId",r.bookingId());c.put("sellerId",r.sellerId());c.put("price",r.price());c.put("deadline",r.deadline());
  if(r.activeAcquisitionId()!=null){var finance=repo.acquisition(r.activeAcquisitionId()).financeTerms();if(com.sporthub.common.money.DemoTransferTerms.isDemo(finance))c.put("financeTerms",finance);}
  if(r.activeAcquisitionId()!=null){var a=repo.acquisition(r.activeAcquisitionId());c.put("acquisitionId",a.id());c.put("buyerId",a.buyerId());c.put("expiresAt",a.expiresAt());if(r.workflow().equals("HANDOFF")){var p=repo.read(repo.jdbc().queryForObject("SELECT payment_payload::text FROM transfer_acquisitions WHERE id=?",String.class,a.id()));c.put("paymentId",a.paymentId());c.put("paidAt",p.path("paidAt").asText());c.put("currency",a.currency());c.put("price",a.amount());}}
  return c;
 }
 private JsonNode handoff(Row r,Map<String,Object> command){
  var a=repo.acquisition(r.activeAcquisitionId());var paid=Instant.parse(command.get("paidAt").toString());
  String expected=TransferHandoff.fingerprint(r.id(),r.bookingId(),r.sellerId(),a.id(),a.buyerId(),a.paymentId(),a.amount(),a.currency(),paid,a.expiresAt(),r.deadline());
  var proof=dependencies.receipt(r.id());
  if(proof==null)throw new TransferDependencies.Unavailable("BOOKING_PROTOCOL_ERROR");
  if(proof.path("state").asText().equals("COMPLETED"))verifyCompleted(proof,expected);
  else{
   if(!proof.path("state").asText().equals("LOCKED")||!proof.path("acquisitionId").asText().equals(a.id().toString())||!proof.path("buyerId").asText().equals(a.buyerId().toString()))throw new ConflictException("Booking lease no longer belongs to this acquisition");
   dependencies.command("complete",command);proof=dependencies.receipt(r.id());verifyCompleted(proof,expected);
  }
  if(a.financeTerms()!=null){var decision=dependencies.decision(a.id());if(decision==null||!decision.path("decision").asText().equals("COMPLETED")||!expected.equals(decision.path("commandFingerprint").asText())||!a.id().toString().equals(decision.path("acquisitionId").asText())||!a.paymentId().toString().equals(decision.path("paymentId").asText())||!decision.hasNonNull("decidedAt"))throw new TransferDependencies.Unavailable("BOOKING_DECISION_PROOF_UNAVAILABLE");return decision;}
  return proof;
 }
 private void verifyCompleted(JsonNode proof,String expected){
  if(proof==null||!proof.path("state").asText().equals("COMPLETED"))throw new TransferDependencies.Unavailable("BOOKING_PROOF_UNAVAILABLE");
  if(proof.path("commandFingerprint").isNull()||proof.path("commandFingerprint").isMissingNode())throw new TransferDependencies.Unavailable("LEGACY_HANDOFF_RECEIPT_MISSING");
  if(!expected.equals(proof.path("commandFingerprint").asText()))throw new ConflictException("Handoff receipt does not match the verified payment");
 }
 private void retry(Row r,String code){
  int attempts=repo.jdbc().queryForObject("SELECT attempts FROM transfer_listings WHERE id=?",Integer.class,r.id())+1;long delay=Math.min(60,1L<<Math.min(attempts,6));
  if(attempts>=maxAttempts){
   if(r.workflow().equals("HANDOFF"))repo.jdbc().update("UPDATE transfer_acquisitions SET state='PENDING_AUDIT',updated_at=NOW() WHERE id=?",r.activeAcquisitionId());
   repo.jdbc().update("UPDATE transfer_listings SET state=CASE WHEN workflow='HANDOFF' THEN 'PENDING_AUDIT' ELSE state END,retry_workflow=workflow,workflow='REVIEW',attempts=?,last_error=?,updated_at=NOW() WHERE id=?",attempts,"TECHNICAL_RETRY_EXHAUSTED:"+code,r.id());
   repo.audit(r,null,"TRANSFER_TECHNICAL_REVIEW",Map.of("code",code,"attempts",attempts));
  }else repo.jdbc().update("UPDATE transfer_listings SET attempts=?,next_attempt_at=?,last_error=?,updated_at=NOW() WHERE id=?",attempts,Timestamp.from(clock.instant().plusSeconds(delay)),code,r.id());
 }
 private void businessReview(Row r,String reason){
  var candidate=repo.acquisition(r.activeAcquisitionId());
  if(com.sporthub.common.money.DemoTransferTerms.isDemo(candidate.financeTerms())){
   JsonNode decision;try{decision=dependencies.abort(candidate.id(),command(r),reason);}catch(RuntimeException unavailable){retry(r,"BOOKING_ABORT_UNAVAILABLE");return;}
   if(decision!=null&&decision.path("decision").asText().equals("COMPLETED")){retry(r,"BOOKING_COMMITTED_RESPONSE_LOST");return;}
   if(!validAbort(r,candidate,decision,candidate.paymentId())){retry(r,"BOOKING_ABORT_PROOF_UNAVAILABLE");return;}
   repo.jdbc().update("UPDATE transfer_acquisitions SET state='FAILED',abort_decision=?::jsonb,updated_at=NOW() WHERE id=?",repo.write(decision),candidate.id());
   repo.jdbc().update("UPDATE transfer_listings SET workflow='UNLOCK',attempts=0,last_error='HANDOFF_ABORTED',next_attempt_at=?,updated_at=NOW() WHERE id=?",Timestamp.from(clock.instant()),r.id());
   repo.audit(r,candidate.buyerId(),"DEMO_HANDOFF_ABORTED_FULL_REFUND_DUE",Map.of("paymentId",candidate.paymentId(),"acquisitionId",candidate.id(),"amount",candidate.amount()));return;
  }
  var a=repo.acquisition(r.activeAcquisitionId());repo.jdbc().update("UPDATE transfer_acquisitions SET state='PENDING_AUDIT',updated_at=NOW() WHERE id=?",a.id());
  repo.jdbc().update("UPDATE transfer_listings SET state='PENDING_AUDIT',workflow='REVIEW',last_error=?,updated_at=NOW() WHERE id=?",reason,r.id());
  // Ownership may already have moved: reconciliation, never an automatic refund.
  repo.jdbc().update("INSERT INTO transfer_reconciliation(id,listing_id,payment_id,payer_id,reason) VALUES(?,?,?,?,?) ON CONFLICT(payment_id) DO NOTHING",UUID.randomUUID(),r.id(),a.paymentId(),a.buyerId(),reason);
  repo.audit(r,a.buyerId(),"HANDOFF_REQUIRES_REVIEW",Map.of("paymentId",a.paymentId(),"reason",reason));
 }
 public void review(Row r,UUID payment,UUID payer,String reason){
  int inserted=repo.jdbc().update("INSERT INTO transfer_reconciliation(id,listing_id,payment_id,payer_id,reason) VALUES(?,?,?,?,?) ON CONFLICT(payment_id) DO NOTHING",UUID.randomUUID(),r.id(),payment,payer,reason);
  if(inserted>0){repo.audit(r,payer,"PAYMENT_REQUIRES_REVIEW",Map.of("paymentId",payment,"reason",reason));outbox.record(RabbitMQConfig.EXCHANGE_PAYMENT,DomainEvent.create("transfer.refund.review.requested",1,"transfer-service",r.id(),Map.of("paymentId",payment,"payerId",payer,"bookingId",r.bookingId(),"reason",reason)));}
 }
 public void reviewFinancial(Row r,UUID acquisition,UUID payment,UUID payer,String reason){
  int inserted=repo.jdbc().update("INSERT INTO transfer_reconciliation(id,listing_id,payment_id,payer_id,reason) VALUES(?,?,?,?,?) ON CONFLICT(payment_id) DO NOTHING",UUID.randomUUID(),r.id(),payment,payer,reason);
  if(inserted>0){repo.audit(r,payer,"FINANCIAL_COLLECTION_REQUIRES_REVIEW",Map.of("paymentId",payment,"acquisitionId",acquisition,"reason",reason));outbox.record(RabbitMQConfig.EXCHANGE_PAYMENT,DomainEvent.create("transfer.collection.review.required",1,"transfer-service",acquisition,Map.of("paymentId",payment,"acquisitionId",acquisition,"payerId",payer,"bookingId",r.bookingId(),"reason",reason)));}
 }
 public void resolveLateDemoPayment(Row r,UUID acquisition,UUID payment,JsonNode paid){
  var a=repo.acquisition(acquisition);if(!com.sporthub.common.money.DemoTransferTerms.isDemo(a.financeTerms()))return;
  var command=new LinkedHashMap<String,Object>();command.put("listingId",r.id());command.put("bookingId",r.bookingId());command.put("sellerId",r.sellerId());command.put("price",a.amount());command.put("deadline",r.deadline());command.put("acquisitionId",a.id());command.put("buyerId",a.buyerId());command.put("expiresAt",a.expiresAt());command.put("paymentId",payment);command.put("paidAt",paid.path("paidAt").asText());command.put("currency",a.currency());command.put("financeTerms",a.financeTerms());
  var decision=dependencies.abort(a.id(),command,"DEMO payment cannot be handed over after cancellation or expiry");
  if(!validAbort(r,a,decision,payment))return; // A completed or uncertain handoff must never be refunded as an abort.
  repo.jdbc().update("UPDATE transfer_acquisitions SET state=CASE WHEN state='PENDING' THEN 'FAILED' ELSE state END,abort_decision=?::jsonb,updated_at=NOW() WHERE id=?",repo.write(decision),a.id());
  if(a.id().equals(r.activeAcquisitionId()))repo.jdbc().update("UPDATE transfer_listings SET workflow='UNLOCK',next_attempt_at=?,last_error='HANDOFF_ABORTED',updated_at=NOW() WHERE id=?",Timestamp.from(clock.instant()),r.id());
 }
 private boolean validAbort(Row r,com.sporthub.transfer.web.TransferDtos.Acquisition a,JsonNode d,UUID payment){
  return d!=null&&d.path("decision").asText().equals("ABORTED")&&d.path("origin").asText().equals("ACQUISITION_DECISION")&&a.id().toString().equals(d.path("acquisitionId").asText())&&r.id().toString().equals(d.path("listingId").asText())&&r.bookingId().toString().equals(d.path("bookingId").asText())&&r.sellerId().toString().equals(d.path("sellerId").asText())&&a.buyerId().toString().equals(d.path("buyerId").asText())&&payment.toString().equals(d.path("paymentId").asText())&&a.amount().compareTo(d.path("amount").decimalValue())==0&&a.currency().equals(d.path("currency").asText());
 }
}
