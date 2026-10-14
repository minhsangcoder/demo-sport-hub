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
  try{
   if(stage.equals("HANDOFF"))handoff(r,command);
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
  }else if(stage.equals("UNLOCK")){
   repo.jdbc().update("UPDATE transfer_listings SET state=?,workflow='READY',active_acquisition_id=NULL,last_error=NULL,attempts=0,version=version+1,updated_at=NOW() WHERE id=?",r.deadline().isAfter(clock.instant())?"ACTIVE":"EXPIRED",id);
  }else repo.jdbc().update("UPDATE transfer_listings SET workflow='READY',last_error=NULL,attempts=0,version=version+1,updated_at=NOW() WHERE id=?",id);
  repo.audit(r,null,"TRANSFER_WORKFLOW_APPLIED",Map.of("workflow",stage));
 });}
 private Map<String,Object> command(Row r){
  var c=new HashMap<String,Object>();c.put("listingId",r.id());c.put("bookingId",r.bookingId());c.put("sellerId",r.sellerId());c.put("price",r.price());c.put("deadline",r.deadline());
  if(r.activeAcquisitionId()!=null){var a=repo.acquisition(r.activeAcquisitionId());c.put("acquisitionId",a.id());c.put("buyerId",a.buyerId());c.put("expiresAt",a.expiresAt());if(r.workflow().equals("HANDOFF")){var p=repo.read(repo.jdbc().queryForObject("SELECT payment_payload::text FROM transfer_acquisitions WHERE id=?",String.class,a.id()));c.put("paymentId",a.paymentId());c.put("paidAt",p.path("paidAt").asText());c.put("currency",a.currency());c.put("price",a.amount());}}
  return c;
 }
 private void handoff(Row r,Map<String,Object> command){
  var a=repo.acquisition(r.activeAcquisitionId());var paid=Instant.parse(command.get("paidAt").toString());
  String expected=TransferHandoff.fingerprint(r.id(),r.bookingId(),r.sellerId(),a.id(),a.buyerId(),a.paymentId(),a.amount(),a.currency(),paid,a.expiresAt(),r.deadline());
  var proof=dependencies.receipt(r.id());
  if(proof==null)throw new TransferDependencies.Unavailable("BOOKING_PROTOCOL_ERROR");
  if(proof.path("state").asText().equals("COMPLETED")){verifyCompleted(proof,expected);return;}
  if(!proof.path("state").asText().equals("LOCKED")||!proof.path("acquisitionId").asText().equals(a.id().toString())||!proof.path("buyerId").asText().equals(a.buyerId().toString()))throw new ConflictException("Booking lease no longer belongs to this acquisition");
  dependencies.command("complete",command);verifyCompleted(dependencies.receipt(r.id()),expected);
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
}
