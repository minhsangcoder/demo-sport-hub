package com.sporthub.transfer.service;
import com.sporthub.transfer.repository.TransferRepository;
import com.sporthub.transfer.repository.TransferRepository.Row;
import static com.sporthub.transfer.web.TransferDtos.*;
import com.sporthub.common.security.RemoteIdentity.Caller;
import com.sporthub.common.security.Signatures;
import com.sporthub.common.exception.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sporthub.common.money.TransferFinancialTerms;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service @Transactional(readOnly=true)
public class TransferService {
 private final TransferRepository repo;private final TransferDependencies deps;private final Clock clock;private final int lockSeconds;private final ObjectMapper json;private final boolean financeEnabled,acquisitionsEnabled;
 public TransferService(TransferRepository repo,TransferDependencies deps,Clock clock,@Value("${TRANSFER_ACQUISITION_SECONDS:600}") int lockSeconds,ObjectMapper json,@Value("${transfer.finance.enabled:true}") boolean financeEnabled,@Value("${transfer.acquisitions.enabled:true}") boolean acquisitionsEnabled){if(lockSeconds<1)throw new IllegalArgumentException("Acquisition timeout must be positive");this.repo=repo;this.deps=deps;this.clock=clock;this.lockSeconds=lockSeconds;this.json=json;this.financeEnabled=financeEnabled;this.acquisitionsEnabled=acquisitionsEnabled;}
 public JsonNode quote(Create input,Caller caller){
  caller.requireRole("CUSTOMER");if(!financeEnabled)throw new ConflictException("New transfer finance is disabled during rollout");var b=deps.booking(input.bookingId());
  if(!b.path("currentHolderId").asText().equals(caller.id().toString()))throw new ForbiddenException("Only your current booking can be transferred");validateBooking(b);price(input.price(),b.path("amount").decimalValue(),b.path("currency").asText());deadline(input.deadline(),Instant.parse(b.path("startsAt").asText()));policy(UUID.fromString(b.path("facilityId").asText()),Instant.parse(b.path("startsAt").asText()));return financialQuote(b,input.price(),input.deadline(),input.beneficiaryRevisionId(),caller.id());
 }
 public JsonNode quoteEdit(UUID id,Edit input,Caller caller){
  caller.requireRole("CUSTOMER");var listing=owner(id,caller);
  if(!financeEnabled)throw new ConflictException("New transfer finance is disabled during rollout");
  if(!listing.state().equals("ACTIVE")||!listing.workflow().equals("READY"))throw new ConflictException("Only an unlocked active listing may accept new terms");
  return quote(new Create(listing.bookingId(),input.price(),input.deadline(),null,null),caller);
 }
 private JsonNode financialQuote(JsonNode booking,BigDecimal price,Instant deadline,UUID beneficiary,UUID seller){
  try{return com.sporthub.common.money.DemoTransferTerms.seller(json,booking,deps.context(UUID.fromString(booking.path("courtId").asText())).path("facility"),deps.feePolicy(UUID.fromString(booking.path("facilityId").asText())),seller,price,deadline);}
  catch(IllegalArgumentException ex){throw new ConflictException("Review the facility transfer fee and booking refund policy: "+ex.getMessage());}
 }
 private JsonNode acceptedFinance(JsonNode booking,BigDecimal price,Instant deadline,UUID beneficiary,String expectedHash,UUID seller){
  if(!financeEnabled){if(beneficiary!=null||expectedHash!=null)throw new ConflictException("New transfer finance is disabled during rollout");return null;}
  var quote=financialQuote(booking,price,deadline,beneficiary,seller);if(!quote.path("termsHash").asText().equals(expectedHash))throw new ConflictException("Review and accept the receiving facility, P/F/N and refund policy before publishing");return quote;
 }
 @Transactional public UUID create(Create input,String key,Caller caller){
  caller.requireRole("CUSTOMER");key(key);repo.lock(caller.id());String fingerprint=Signatures.sha256(repo.write(input));var previous=repo.previous(caller.id(),"CREATE",key,fingerprint);if(previous!=null)return previous;
  repo.lock(input.bookingId());var b=deps.booking(input.bookingId());if(!b.path("currentHolderId").asText().equals(caller.id().toString()))throw new ForbiddenException("Only your current booking can be transferred");
  validateBooking(b);UUID facility=UUID.fromString(b.path("facilityId").asText()),court=UUID.fromString(b.path("courtId").asText());policy(facility,Instant.parse(b.path("startsAt").asText()));
  var original=b.path("amount").decimalValue();price(input.price(),original,b.path("currency").asText());deadline(input.deadline(),Instant.parse(b.path("startsAt").asText()));
  var finance=acceptedFinance(b,input.price(),input.deadline(),input.beneficiaryRevisionId(),input.acceptedTermsHash(),caller.id());
  if(repo.jdbc().queryForObject("SELECT count(*) FROM transfer_listings WHERE booking_id=? AND state IN ('ACTIVE','LOCKED','PENDING_AUDIT')",Integer.class,input.bookingId())>0)throw new ConflictException("Booking already has an active transfer listing");
  var context=deps.context(court);var venue=Map.of("facilityName",context.path("facility").path("name").asText(),"courtName",context.path("court").path("name").asText(),"sportCategoryId",context.path("court").path("sportCategoryId").asText(),"address",context.path("facility").path("addressLine").asText(),"provinceCode",context.path("facility").path("provinceCode"),"province",context.path("facility").path("province").asText(),"wardCode",context.path("facility").path("wardCode"),"ward",context.path("facility").path("ward").asText(),"timezone",context.path("facility").path("timezone").asText(),"amenities",context.path("facility").path("amenities"));
  UUID id=UUID.randomUUID();repo.jdbc().update("INSERT INTO transfer_listings(id,booking_id,seller_id,facility_id,court_id,snapshot,original_amount,price,currency,starts_at,ends_at,deadline,state,workflow,finance_terms) VALUES(?,?,?,?,?,?::jsonb,?,?,?,?,?,?,'ACTIVE','REGISTER',?::jsonb)",id,input.bookingId(),caller.id(),facility,court,repo.write(venue),original,input.price(),b.path("currency").asText(),Timestamp.from(Instant.parse(b.path("startsAt").asText())),Timestamp.from(Instant.parse(b.path("endsAt").asText())),Timestamp.from(input.deadline().truncatedTo(ChronoUnit.MILLIS)),finance==null?null:repo.write(finance));
  repo.remember(caller.id(),"CREATE",key,fingerprint,id);repo.audit(repo.find(id),caller.id(),"LISTING_CREATED",input);return id;
 }
 @Transactional(readOnly=true,isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
 public List<Listing> market(String q,UUID category,Instant after,Instant before,BigDecimal maxPrice,String sort,Caller caller){
  caller.requireRole("CUSTOMER");sort=sort==null?"start":sort;
  if(!Set.of("start","price","discount").contains(sort)||q!=null&&q.length()>180||after!=null&&before!=null&&after.isAfter(before)||maxPrice!=null&&maxPrice.signum()<0)throw new IllegalArgumentException("Invalid transfer search filters");
  StringBuilder sql=new StringBuilder("SELECT * FROM transfer_listings WHERE state='ACTIVE' AND workflow='READY' AND deadline>? AND starts_at>?");var args=new ArrayList<Object>();args.add(Timestamp.from(clock.instant()));args.add(Timestamp.from(clock.instant()));
  if(q!=null&&!q.isBlank()){sql.append(" AND concat_ws(' ',snapshot->>'facilityName',snapshot->>'courtName',snapshot->>'address',snapshot->>'province',snapshot->>'ward') ILIKE ?");args.add("%"+q.trim().replace("\\","\\\\").replace("%","\\%").replace("_","\\_")+"%");}
  if(category!=null){sql.append(" AND snapshot->>'sportCategoryId'=?");args.add(category.toString());}
  if(after!=null){sql.append(" AND starts_at>=?");args.add(Timestamp.from(after));}if(before!=null){sql.append(" AND starts_at<=?");args.add(Timestamp.from(before));}if(maxPrice!=null){sql.append(" AND price<=?");args.add(maxPrice);}
  sql.append(switch(sort){case "price"->" ORDER BY price,id";case "discount"->" ORDER BY (original_amount-price)/original_amount DESC,id";default->" ORDER BY starts_at,id";});sql.append(" LIMIT 100 OFFSET ?");
  var result=new ArrayList<Listing>();int offset=0;
  while(result.size()<100){var pageArgs=new ArrayList<>(args);pageArgs.add(offset);var rows=repo.rows(sql.toString(),pageArgs.toArray());for(var r:rows){if(valid(r))result.add(repo.view(r,caller.id(),true));if(result.size()==100)break;}if(rows.size()<100)break;offset+=100;}
  return result;
 }
 public List<Listing> mine(Caller caller){return repo.rows("SELECT * FROM transfer_listings WHERE seller_id=? ORDER BY created_at DESC LIMIT 100",caller.id()).stream().map(r->repo.view(r,caller.id(),false)).toList();}
 public List<Acquisition> purchases(Caller caller){return repo.jdbc().queryForList("SELECT id FROM transfer_acquisitions WHERE buyer_id=? ORDER BY created_at DESC LIMIT 100",UUID.class,caller.id()).stream().map(repo::acquisition).toList();}
 public Listing detail(UUID id,Caller caller){caller.requireRole("CUSTOMER");var r=repo.find(id);return repo.view(r,caller.id(),r.state().equals("ACTIVE")&&r.workflow().equals("READY")&&valid(r));}
 @Transactional public UUID acquire(UUID id,String key,Caller caller){return acquire(id,key,caller,null);}
 @Transactional public UUID acquire(UUID id,String key,Caller caller,String acceptedHash){
  caller.requireRole("CUSTOMER");key(key);repo.lock(caller.id());String fp=Signatures.sha256(acceptedHash==null?id.toString():id+"|"+acceptedHash);var previous=repo.previous(caller.id(),"ACQUIRE",key,fp);if(previous!=null)return previous;
  if(!acquisitionsEnabled)throw new ConflictException("New acquisitions are paused; existing money reconciliation continues");
  repo.lock(id);var r=repo.find(id);if(r.sellerId().equals(caller.id()))throw new ForbiddenException("You cannot acquire your own listing");if(!r.state().equals("ACTIVE")||!r.workflow().equals("READY")||!validBooking(r))throw new ConflictException("Listing is locked, expired or no longer eligible");Instant cutoff=policy(r.facilityId(),r.startsAt());
  if(r.financeTerms()!=null){
   if(!financeEnabled)throw new ConflictException("New financial acquisitions are paused");TransferFinancialTerms.validateSeller(r.financeTerms());
   if(!r.financeTerms().path("termsHash").asText().equals(acceptedHash))throw new ConflictException("Review the listing price and refund policy before acquiring");
   if(com.sporthub.common.money.DemoTransferTerms.isDemo(r.financeTerms())){
    var current=deps.feePolicy(r.facilityId());
    if(current==null||!current.path("configured").asBoolean()||!current.path("termsHash").asText().equals(r.financeTerms().path("feePolicyHash").asText()))throw new ConflictException("Facility fee changed; seller must review and accept updated terms before a new acquisition");
   }else throw new ConflictException("Seller must review and accept the facility DEMO fee before a new acquisition; legacy terms remain unchanged");
  }else if(financeEnabled)throw new ConflictException("Seller must explicitly accept new financial terms for this legacy listing");
  Instant expiry=clock.instant().plusSeconds(lockSeconds).truncatedTo(ChronoUnit.MILLIS);if(expiry.isAfter(r.deadline()))expiry=r.deadline();if(expiry.isAfter(cutoff))expiry=cutoff.truncatedTo(ChronoUnit.MILLIS);if(!expiry.isAfter(clock.instant()))throw new ConflictException("Transfer acquisition deadline elapsed during validation");UUID acquisition=UUID.randomUUID();
  var financial=r.financeTerms()==null?null:TransferFinancialTerms.acquisition(r.financeTerms(),id,r.version(),acquisition,caller.id(),expiry,clock.instant().truncatedTo(ChronoUnit.MICROS));
  repo.jdbc().update("INSERT INTO transfer_acquisitions(id,listing_id,buyer_id,amount,currency,expires_at,state,finance_terms) VALUES(?,?,?,?,?,?,'PENDING',?::jsonb)",acquisition,id,caller.id(),r.price(),r.currency(),Timestamp.from(expiry),financial==null?null:repo.write(financial));
  repo.jdbc().update("UPDATE transfer_listings SET state='LOCKED',workflow='LOCK',active_acquisition_id=?,attempts=0,last_error=NULL,next_attempt_at=?,version=version+1,updated_at=NOW() WHERE id=?",acquisition,Timestamp.from(clock.instant()),id);
  repo.remember(caller.id(),"ACQUIRE",key,fp,acquisition);repo.audit(r,caller.id(),"ACQUISITION_CREATED",Map.of("acquisitionId",acquisition));return acquisition;
 }
 public Acquisition acquisition(UUID id,Caller caller){var a=repo.acquisition(id);if(!a.buyerId().equals(caller.id()))throw new ForbiddenException("Acquisition belongs to another buyer");return a;}
 @Transactional public Payable payable(UUID id,Caller caller){var before=acquisition(id,caller);repo.lock(before.listingId());var a=acquisition(id,caller);var r=repo.find(a.listingId());if(financeEnabled&&!com.sporthub.common.money.DemoTransferTerms.isDemo(a.financeTerms()))throw new ConflictException("Legacy acquisition needs review or cancellation before accepting new facility DEMO terms");if(!a.state().equals("PENDING")||!a.expiresAt().isAfter(clock.instant())||!r.workflow().equals("READY")||!r.state().equals("LOCKED")||!id.equals(r.activeAcquisitionId())||!valid(r))throw new ConflictException("Transfer acquisition is no longer payable");
  var lease=deps.receipt(r.id());if(lease==null||!lease.path("state").asText().equals("LOCKED")||!lease.path("acquisitionId").asText().equals(id.toString())||!lease.path("buyerId").asText().equals(caller.id().toString())||!lease.path("bookingId").asText().equals(r.bookingId().toString())||!lease.path("sellerId").asText().equals(r.sellerId().toString())||lease.path("price").decimalValue().compareTo(a.amount())!=0||!a.currency().equals(lease.path("currency").asText())||!a.expiresAt().equals(Instant.parse(lease.path("expiresAt").asText())))throw new ConflictException("Booking does not hold the matching transfer lease");
  if(!a.expiresAt().isAfter(clock.instant()))throw new ConflictException("Transfer deadline elapsed during payable validation");return new Payable(r.bookingId(),caller.id(),r.sellerId(),id,a.amount(),a.currency(),a.expiresAt(),"TRANSFER",a.financeTerms());}
 @Transactional public void cancel(UUID id,Caller caller){var a=acquisition(id,caller);repo.lock(a.listingId());a=repo.acquisition(id);if(a.state().equals("CANCELLED"))return;if(!a.state().equals("PENDING"))throw new ConflictException("Only an unpaid pending acquisition can be cancelled");repo.jdbc().update("UPDATE transfer_acquisitions SET state='CANCELLED',updated_at=NOW() WHERE id=?",id);repo.jdbc().update("UPDATE transfer_listings SET workflow='UNLOCK',next_attempt_at=NOW(),updated_at=NOW() WHERE id=? AND active_acquisition_id=?",a.listingId(),id);repo.audit(repo.find(a.listingId()),caller.id(),"ACQUISITION_CANCELLED",Map.of("acquisitionId",id));}
 @Transactional public void edit(UUID id,Edit input,Caller caller){
  repo.lock(id);var r=owner(id,caller);boolean same=r.price().compareTo(input.price())==0&&r.deadline().equals(input.deadline().truncatedTo(ChronoUnit.MILLIS));
  if(!r.state().equals("ACTIVE")||!(r.workflow().equals("READY")||same&&r.workflow().equals("EDIT"))||!valid(r))throw new ConflictException("Only an eligible unlocked active listing can be edited");
  price(input.price(),r.originalAmount(),r.currency());deadline(input.deadline(),r.startsAt());policy(r.facilityId(),r.startsAt());
  if(r.financeTerms()!=null&&!financeEnabled)throw new ConflictException("New financial terms are paused");
  var finance=acceptedFinance(deps.booking(r.bookingId()),input.price(),input.deadline(),input.beneficiaryRevisionId(),input.acceptedTermsHash(),caller.id());
  boolean sameTerms=finance==null?r.financeTerms()==null:r.financeTerms()!=null&&finance.path("termsHash").asText().equals(r.financeTerms().path("termsHash").asText());if(same&&sameTerms)return;
  if(!r.workflow().equals("READY"))throw new ConflictException("Wait for the previous edit before accepting different terms");
  repo.jdbc().update("UPDATE transfer_listings SET price=?,deadline=?,finance_terms=?::jsonb,workflow='EDIT',attempts=0,last_error=NULL,next_attempt_at=?,version=version+1,updated_at=NOW() WHERE id=?",input.price(),Timestamp.from(input.deadline().truncatedTo(ChronoUnit.MILLIS)),finance==null?null:repo.write(finance),Timestamp.from(clock.instant()),id);
  repo.audit(r,caller.id(),"LISTING_EDITED",Map.of("previousPrice",r.price(),"previousDeadline",r.deadline(),"price",input.price(),"deadline",input.deadline()));
 }
 @Transactional public void withdraw(UUID id,Caller caller){repo.lock(id);var r=owner(id,caller);if(r.state().equals("WITHDRAWN"))return;if(!r.state().equals("ACTIVE")||!r.workflow().equals("READY"))throw new ConflictException("Only an unlocked active listing can be withdrawn");repo.jdbc().update("UPDATE transfer_listings SET state='WITHDRAWN',workflow='WITHDRAW',next_attempt_at=NOW(),version=version+1,updated_at=NOW() WHERE id=?",id);repo.audit(r,caller.id(),"LISTING_WITHDRAWN",Map.of());}
 public Map<String,Object> synchronization(UUID id,Caller caller){var r=repo.find(id);participant(r,caller);var data=repo.jdbc().queryForMap("SELECT workflow,attempts,next_attempt_at,last_error,retry_workflow FROM transfer_listings WHERE id=?",id);var result=new LinkedHashMap<String,Object>();result.put("listingId",id);result.put("state",r.state());result.put("workflow",r.workflow());result.put("attempts",data.get("attempts"));result.put("nextAttemptAt",((Timestamp)data.get("next_attempt_at")).toInstant());String error=(String)data.get("last_error");result.put("lastErrorCode",safeError(error));result.put("retryable",r.workflow().equals("REVIEW")&&data.get("retry_workflow")!=null&&error!=null&&error.startsWith("TECHNICAL_RETRY_EXHAUSTED:"));return result;}
 @Transactional public void retrySynchronization(UUID id,Caller caller){
  repo.lock(id);var r=repo.find(id);participant(r,caller);
  if(r.workflow().equals("HANDOFF")||r.state().equals("COMPLETED"))return;
  var data=repo.jdbc().queryForMap("SELECT last_error,retry_workflow FROM transfer_listings WHERE id=?",id);String error=(String)data.get("last_error"),stage=(String)data.get("retry_workflow");
  if(!r.workflow().equals("REVIEW")||stage==null||error==null||!error.startsWith("TECHNICAL_RETRY_EXHAUSTED:"))throw new ConflictException("Only technical retry exhaustion can be retried; business or financial review needs resolution");
  if(stage.equals("HANDOFF")){var a=repo.acquisition(r.activeAcquisitionId());if(!a.state().equals("PENDING_AUDIT")||a.paymentId()==null)throw new ConflictException("Verified handoff command is missing");repo.jdbc().update("UPDATE transfer_acquisitions SET state='PENDING_HANDOFF',updated_at=NOW() WHERE id=?",a.id());}
  repo.jdbc().update("UPDATE transfer_listings SET state=CASE WHEN ?='HANDOFF' THEN 'LOCKED' ELSE state END,workflow=?,attempts=0,last_error=NULL,retry_workflow=NULL,next_attempt_at=?,updated_at=NOW() WHERE id=?",stage,stage,Timestamp.from(clock.instant()),id);repo.audit(r,caller.id(),"TRANSFER_TECHNICAL_RETRY_REQUESTED",Map.of("workflow",stage));
 }
 private void participant(Row r,Caller caller){if(r.sellerId().equals(caller.id()))return;if(r.activeAcquisitionId()!=null&&repo.acquisition(r.activeAcquisitionId()).buyerId().equals(caller.id()))return;throw new ForbiddenException("Transfer synchronization belongs to another seller or buyer");}
 private String safeError(String error){if(error==null)return null;return error.matches("(?:TECHNICAL_RETRY_EXHAUSTED:)?[A-Z_]{1,80}")?error:"LEGACY_TRANSFER_ERROR";}
 private Row owner(UUID id,Caller caller){var r=repo.find(id);if(!r.sellerId().equals(caller.id()))throw new ForbiddenException("Listing belongs to another seller");return r;}
 private boolean valid(Row r){if(!validBooking(r))return false;var p=deps.policy(r.facilityId());return p!=null&&p.path("configured").asBoolean()&&p.path("enabled").asBoolean()&&r.startsAt().minusSeconds(p.path("minLeadSeconds").asLong()).isAfter(clock.instant());}
 private boolean validBooking(Row r){if(!r.deadline().isAfter(clock.instant()))return false;var b=deps.booking(r.bookingId());return b!=null&&b.path("id").asText().equals(r.bookingId().toString())&&b.path("transferBlockedReason").asText().isBlank()&&b.path("status").asText().equals("CONFIRMED")&&!b.path("paidAt").isNull()&&!b.path("paidAt").isMissingNode()&&b.path("currentHolderId").asText().equals(r.sellerId().toString())&&b.path("facilityId").asText().equals(r.facilityId().toString())&&b.path("courtId").asText().equals(r.courtId().toString())&&b.path("amount").decimalValue().compareTo(r.originalAmount())==0&&b.path("currency").asText().equals(r.currency())&&Instant.parse(b.path("startsAt").asText()).equals(r.startsAt())&&Instant.parse(b.path("endsAt").asText()).equals(r.endsAt())&&r.startsAt().isAfter(clock.instant());}
 private void validateBooking(JsonNode b){if(!b.path("transferBlockedReason").asText().isBlank())throw new ConflictException(b.path("transferBlockedReason").asText());if(!b.path("status").asText().equals("CONFIRMED")||(b.path("paidAt").isNull()||b.path("paidAt").isMissingNode())||!Instant.parse(b.path("startsAt").asText()).isAfter(clock.instant()))throw new ConflictException("A paid confirmed future booking is required");}
 private Instant policy(UUID id,Instant starts){var p=deps.policy(id);if(!p.path("configured").asBoolean())throw new ConflictException("BLOCKED_RULE: facility Owner must configure its transfer eligibility policy");Instant cutoff=starts.minusSeconds(p.path("minLeadSeconds").asLong());if(!p.path("enabled").asBoolean()||!cutoff.isAfter(clock.instant()))throw new ConflictException("Facility transfer policy does not permit this booking");return cutoff;}
 private void price(BigDecimal amount,BigDecimal original,String currency){if(amount.signum()<=0||amount.compareTo(original)>0||amount.scale()>2||currency.equals("VND")&&amount.stripTrailingZeros().scale()>0)throw new ConflictException("Transfer price must be positive and no greater than the original paid amount");}
 private void deadline(Instant value,Instant starts){if(!value.isAfter(clock.instant())||value.isAfter(starts))throw new ConflictException("Transfer deadline must be in the future and no later than play starts");}
 private void key(String key){if(key==null||!key.matches("[A-Za-z0-9_.:-]{1,100}"))throw new IllegalArgumentException("A valid Idempotency-Key is required");}
}
