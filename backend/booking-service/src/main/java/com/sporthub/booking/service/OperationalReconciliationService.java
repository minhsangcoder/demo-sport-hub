package com.sporthub.booking.service;
import com.sporthub.booking.repository.BookingRepository;
import com.sporthub.common.exception.*;
import com.sporthub.common.security.RemoteIdentity.Caller;
import com.sporthub.common.security.Signatures;
import jakarta.validation.constraints.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.*;

/** Admin technical annotations. Never resolve, confirm or refund completed money from a note. */
@Service @Transactional(readOnly=true)
public class OperationalReconciliationService {
 public record Input(@NotBlank @Size(max=1000) String note){}
 public record Review(UUID id,UUID actorId,String note,Instant createdAt){}
 public record Detail(UUID id,UUID bookingId,UUID paymentId,String reason,String state,List<Review> reviews){}
 private final BookingRepository repo;
 public OperationalReconciliationService(BookingRepository repo){this.repo=repo;}
 public Detail detail(UUID id,Caller actor){actor.requireRole("ADMIN");var rows=repo.jdbc().queryForList("SELECT * FROM payment_reconciliation WHERE id=?",id);if(rows.isEmpty())throw new ResourceNotFoundException("Reconciliation case not found");var r=rows.getFirst();var reviews=repo.jdbc().query("SELECT * FROM booking_reconciliation_reviews WHERE reconciliation_id=? ORDER BY created_at,id",(s,n)->new Review(s.getObject("id",UUID.class),s.getObject("actor_id",UUID.class),s.getString("note"),s.getTimestamp("created_at").toInstant()),id);return new Detail(id,(UUID)r.get("booking_id"),(UUID)r.get("payment_id"),(String)r.get("reason"),(String)r.get("state"),reviews);}
 @Transactional public Review review(UUID id,Input input,String key,Caller actor){
  actor.requireRole("ADMIN");if(input==null||input.note()==null||input.note().isBlank()||input.note().length()>1000)throw new IllegalArgumentException("A technical review note is required");if(key==null||!key.matches("[A-Za-z0-9_.:-]{1,100}"))throw new IllegalArgumentException("A valid Idempotency-Key is required");
  repo.lock(id);var caseDetail=detail(id,actor);String fingerprint=Signatures.sha256(input.note().trim());var previous=repo.jdbc().queryForList("SELECT id,fingerprint FROM booking_reconciliation_reviews WHERE reconciliation_id=? AND actor_id=? AND idempotency_key=?",id,actor.id(),key);
  if(!previous.isEmpty()){if(!fingerprint.equals(previous.getFirst().get("fingerprint")))throw new ConflictException("Review key was used with a different note");UUID old=(UUID)previous.getFirst().get("id");return caseDetail.reviews().stream().filter(r->r.id().equals(old)).findFirst().orElseThrow();}
  UUID review=UUID.randomUUID();repo.jdbc().update("INSERT INTO booking_reconciliation_reviews(id,reconciliation_id,actor_id,note,idempotency_key,fingerprint) VALUES(?,?,?,?,?,?)",review,id,actor.id(),input.note().trim(),key,fingerprint);repo.history(caseDetail.bookingId(),actor.id(),"RECONCILIATION_REVIEWED",Map.of("caseId",id,"reviewId",review,"paymentId",caseDetail.paymentId(),"reason",caseDetail.reason(),"financialStateUnchanged",true,"changeSource","ADMIN_TECHNICAL_REVIEW"));return detail(id,actor).reviews().stream().filter(r->r.id().equals(review)).findFirst().orElseThrow();
 }
}
