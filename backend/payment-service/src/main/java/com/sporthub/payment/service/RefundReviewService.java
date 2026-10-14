package com.sporthub.payment.service;
import com.sporthub.payment.repository.PaymentRepository;
import com.sporthub.common.security.RemoteIdentity.Caller;
import com.sporthub.common.exception.*;
import jakarta.validation.constraints.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
@Service @Transactional(readOnly=true)
public class RefundReviewService {
 public record NoteInput(@NotBlank @Size(min=20,max=2000) String note){}
 private final PaymentRepository repo;private final PaymentService payments;private final jakarta.validation.Validator validator;
 public RefundReviewService(PaymentRepository repo,PaymentService payments,jakarta.validation.Validator validator){this.repo=repo;this.payments=payments;this.validator=validator;}
 public List<Map<String,Object>> list(Caller actor){actor.requireRole("ADMIN");return repo.jdbc().queryForList("SELECT r.*,p.booking_id,p.purpose,p.provider,p.amount AS original_amount,p.currency FROM refunds r JOIN payment_orders p ON p.id=r.payment_id ORDER BY r.created_at DESC,r.id LIMIT 200");}
 public List<Map<String,Object>> forPayment(UUID payment,Caller actor){payments.own(payment,actor);return repo.jdbc().queryForList("SELECT * FROM refunds WHERE payment_id=? ORDER BY created_at,id",payment);}
 public Map<String,Object> detail(UUID id,Caller actor){actor.requireRole("ADMIN");var refund=find(id);var result=new LinkedHashMap<String,Object>(refund);result.put("reviews",repo.jdbc().queryForList("SELECT id,actor_id,note,created_at FROM refund_review_notes WHERE refund_id=? ORDER BY created_at,id",id));result.put("executionPolicy","BLOCKED_RULE: annotation does not approve or execute a refund");return result;}
 @Transactional public Map<String,Object> review(UUID id,NoteInput input,String key,Caller actor){
  actor.requireRole("ADMIN");if(key==null||!key.matches("[A-Za-z0-9_.:-]{1,100}")||input==null||!validator.validate(input).isEmpty()||input.note().trim().length()<20)throw new IllegalArgumentException("Valid review note and Idempotency-Key are required");
  repo.lock(actor.id());var prior=repo.jdbc().queryForList("SELECT * FROM refund_review_notes WHERE actor_id=? AND idempotency_key=?",actor.id(),key);
  if(!prior.isEmpty()){var old=prior.getFirst();if(!old.get("refund_id").equals(id)||!old.get("note").equals(input.note().trim()))throw new ConflictException("Review key reused with different input");return old;}
  var refund=find(id);UUID payment=(UUID)refund.get("payment_id");repo.lock(payment);UUID note=UUID.randomUUID();
  repo.jdbc().update("INSERT INTO refund_review_notes(id,refund_id,actor_id,note,idempotency_key) VALUES(?,?,?,?,?)",note,id,actor.id(),input.note().trim(),key);
  repo.jdbc().update("INSERT INTO payment_audit(id,payment_id,actor_id,action,details) VALUES(?,?,?,'REFUND_REVIEW_NOTED',jsonb_build_object('refundId',?::uuid,'noteId',?::uuid,'policy','BLOCKED_RULE'))",UUID.randomUUID(),payment,actor.id(),id,note);
  return repo.jdbc().queryForMap("SELECT * FROM refund_review_notes WHERE id=?",note);
 }
 private Map<String,Object> find(UUID id){var rows=repo.jdbc().queryForList("SELECT * FROM refunds WHERE id=?",id);if(rows.isEmpty())throw new ResourceNotFoundException("Refund review not found");return rows.getFirst();}
}
