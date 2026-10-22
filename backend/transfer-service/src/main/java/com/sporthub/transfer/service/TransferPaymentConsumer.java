package com.sporthub.transfer.service;
import com.sporthub.transfer.repository.TransferRepository;
import com.sporthub.transfer.config.TransferConfig;
import com.fasterxml.jackson.databind.*;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;
@Service
public class TransferPaymentConsumer {
 private final TransferRepository repo;private final ObjectMapper json;private final Clock clock;private final TransferWorkflow workflow;
 public TransferPaymentConsumer(TransferRepository repo,ObjectMapper json,Clock clock,TransferWorkflow workflow){this.repo=repo;this.json=json;this.clock=clock;this.workflow=workflow;}
 @RabbitListener(queues=TransferConfig.PAYMENT_QUEUE) @Transactional public void receive(Message message){try{apply(json.readTree(message.getBody()));}catch(java.io.IOException ex){throw new AmqpRejectAndDontRequeueException("Malformed transfer payment event",ex);}}
 @Transactional public void apply(JsonNode event){
  boolean canonical=event.path("eventType").asText().equals("transfer.collection.verified");
  if(!canonical&&!event.path("eventType").asText().equals("payment.completed")||event.path("eventVersion").asInt()!=1||!event.path("producer").asText().equals("payment-service"))throw new AmqpRejectAndDontRequeueException("Unsupported payment envelope");
  var p=event.path("payload");if(!p.path("purpose").asText().equals("TRANSFER"))return;
  UUID acquisition,eventId,payment,payer,booking;Instant paid;
  try{
   acquisition=UUID.fromString(p.path("acquisitionId").asText());eventId=UUID.fromString(event.path("eventId").asText());payment=UUID.fromString(p.path("paymentId").asText());payer=UUID.fromString(p.path("payerId").asText());booking=UUID.fromString(p.path("bookingId").asText());paid=Instant.parse(p.path("paidAt").asText());
   if(!payment.toString().equals(event.path("aggregateId").asText())||!Set.of("SUCCESS","FAILED").contains(p.path("status").asText()))throw new IllegalArgumentException();
   com.sporthub.common.money.Money.positive(p.path("amount").decimalValue(),p.path("currency").asText());
   if(p.path("amount").decimalValue().stripTrailingZeros().scale()>0)throw new IllegalArgumentException();
  }catch(Exception ex){throw new AmqpRejectAndDontRequeueException("Invalid transfer payment payload",ex);}
  String fingerprint=com.sporthub.common.security.Signatures.sha256(acquisition+"|"+booking+"|"+payment+"|"+payer+"|"+p.path("status").asText()+"|"+p.path("amount").decimalValue().stripTrailingZeros().toPlainString()+"|"+p.path("currency").asText()+"|"+paid);
  if(canonical)fingerprint=com.sporthub.common.security.Signatures.sha256("CANONICAL_TRANSFER|"+fingerprint+"|"+p.path("termsHash").asText()+"|"+p.path("collectionFingerprint").asText());
  var links=repo.jdbc().queryForList("SELECT listing_id FROM transfer_acquisitions WHERE id=?",acquisition);
  if(links.isEmpty()){
   repo.lock(payment);if(repo.jdbc().update("INSERT INTO event_inbox(consumer,event_id) VALUES('transfer-payment',?) ON CONFLICT DO NOTHING",eventId)==0)return;
   repo.jdbc().update("INSERT INTO transfer_orphan_payments(payment_id,acquisition_id,booking_id,payer_id,fingerprint,payload,reason) VALUES(?,?,?,?,?,?::jsonb,'UNKNOWN_ACQUISITION') ON CONFLICT(payment_id,fingerprint) DO NOTHING",payment,acquisition,booking,payer,fingerprint,repo.write(p));return;
  }
  UUID listing=(UUID)links.getFirst().get("listing_id");repo.lock(listing);var a=repo.acquisition(acquisition);var r=repo.find(listing);
  // One acquisition has exactly one protocol. Old SUCCESS cannot claim a new financial cohort.
  if((a.financeTerms()!=null)!=canonical)return;
  if(repo.jdbc().update("INSERT INTO event_inbox(consumer,event_id) VALUES('transfer-payment',?) ON CONFLICT DO NOTHING",eventId)==0)return;
  if(repo.jdbc().update("INSERT INTO transfer_payment_receipts(payment_id,acquisition_id,fingerprint,payload) VALUES(?,?,?,?::jsonb) ON CONFLICT(payment_id) DO NOTHING",payment,acquisition,fingerprint,repo.write(p))==0){
   var original=repo.jdbc().queryForMap("SELECT fingerprint,payload::text FROM transfer_payment_receipts WHERE payment_id=?",payment);
   if(!fingerprint.equals(original.get("fingerprint"))){var previous=repo.read((String)original.get("payload"));if(canonical)workflow.reviewFinancial(r,acquisition,payment,UUID.fromString(previous.path("payerId").asText()),"PAYMENT_PAYLOAD_CONFLICT");else workflow.review(r,payment,UUID.fromString(previous.path("payerId").asText()),"PAYMENT_PAYLOAD_CONFLICT");}return;
  }
  // Existing legacy applied payment stays authoritative without inventing its missing receipt.
  if(Set.of("SUCCESS","PENDING_HANDOFF","PENDING_AUDIT").contains(a.state())&&payment.equals(a.paymentId()))return;
  String reason=null;
  if(canonical&&(!p.path("status").asText().equals("SUCCESS")||!p.path("canonicalDecision").asText().equals("SETTLED_OBLIGATION")||!a.financeTerms().path("termsHash").asText().equals(p.path("termsHash").asText())||!r.id().toString().equals(p.path("listingId").asText())||!r.sellerId().toString().equals(p.path("sellerId").asText())||!a.buyerId().toString().equals(p.path("buyerId").asText())||!com.sporthub.common.money.BookingPaymentFingerprint.of(p).equals(p.path("collectionFingerprint").asText())))reason="CANONICAL_COLLECTION_PROOF_MISMATCH";
  else if(!a.buyerId().equals(payer)||!r.bookingId().equals(booking))reason="PAYER_OR_BOOKING_MISMATCH";
  else if(a.amount().compareTo(p.path("amount").decimalValue())!=0||!a.currency().equals(p.path("currency").asText()))reason="AMOUNT_MISMATCH";
  else if(!a.state().equals("PENDING")||!r.state().equals("LOCKED")||!r.workflow().equals("READY")||!acquisition.equals(r.activeAcquisitionId()))reason="ACQUISITION_NOT_PENDING";
  else if(!paid.isBefore(a.expiresAt())||!clock.instant().isBefore(a.expiresAt()))reason="LATE_PAYMENT";
  if(reason!=null){if(p.path("status").asText().equals("SUCCESS")){if(canonical){workflow.reviewFinancial(r,acquisition,payment,payer,reason);if(reason.equals("LATE_PAYMENT")||reason.equals("ACQUISITION_NOT_PENDING")&&Set.of("CANCELLED","EXPIRED","FAILED").contains(a.state()))workflow.resolveLateDemoPayment(r,acquisition,payment,p);}else workflow.review(r,payment,payer,reason);}return;}
  if(p.path("status").asText().equals("FAILED")){
   // A failed attempt is final in Payment. Retain this exclusive acquisition until cancel/timeout,
   // allowing a new payment key/method against the same unchanged obligation.
   repo.audit(r,payer,"PAYMENT_FAILED",Map.of("paymentId",payment,"acquisitionId",acquisition));return;
  }
  repo.jdbc().update("UPDATE transfer_acquisitions SET state='PENDING_HANDOFF',payment_id=?,payment_payload=?::jsonb,updated_at=NOW() WHERE id=?",payment,repo.write(p),acquisition);
  repo.jdbc().update("UPDATE transfer_listings SET workflow='HANDOFF',attempts=0,last_error=NULL,next_attempt_at=?,updated_at=NOW() WHERE id=?",java.sql.Timestamp.from(clock.instant()),r.id());repo.audit(r,payer,"PAYMENT_VERIFIED",Map.of("paymentId",payment,"acquisitionId",acquisition));
 }
}
