package com.sporthub.payment.service;
import com.sporthub.payment.repository.PaymentRepository;
import com.sporthub.common.exception.*;
import com.sporthub.common.security.RemoteIdentity.Caller;
import com.sporthub.common.security.Signatures;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.*;

@Service
public class PaymentSynchronization {
    private final PaymentRepository repo;private final BookingReceiptClient booking;private final PaymentService payments;
    private final Clock clock;private final ObjectMapper json;private final int maxAttempts;
    public PaymentSynchronization(PaymentRepository repo,BookingReceiptClient booking,PaymentService payments,Clock clock,ObjectMapper json,@Value("${sporthub.payment.sync.max-attempts:10}") int maxAttempts){if(maxAttempts<1)throw new IllegalArgumentException("Sync retry budget must be positive");this.repo=repo;this.booking=booking;this.payments=payments;this.clock=clock;this.json=json;this.maxAttempts=maxAttempts;}
    @Transactional(readOnly=true) public Map<String,Object> own(UUID id,Caller caller){payments.own(id,caller);return row(id);}
    @Transactional public Map<String,Object> retry(UUID id,Caller caller){
        payments.own(id,caller);var old=repo.jdbc().queryForList("SELECT * FROM payment_booking_sync WHERE payment_id=? FOR UPDATE",id);
        if(old.isEmpty())throw new ConflictException("No verified outcome is awaiting Booking synchronization");var status=old.getFirst();
        if(status.get("state").equals("ACKNOWLEDGED"))return status;
        if(status.get("state").equals("REQUIRES_REVIEW")&&!Set.of("BOOKING_UNAVAILABLE","BOOKING_RECEIPT_MISSING").contains(Objects.toString(status.get("last_error_code"),"")))throw new ConflictException("Financial or business review cannot be resolved by replay");
        if(status.get("event_id")==null)throw new ConflictException("Original payment event is missing; reconciliation is required");
        repo.jdbc().update("UPDATE payment_booking_sync SET state='AWAITING_BOOKING',attempts=0,last_error_code=NULL,next_attempt_at=?,updated_at=NOW() WHERE payment_id=?",Timestamp.from(clock.instant()),id);
        replay((UUID)status.get("event_id"));return row(id);
    }
    @Transactional public void process(UUID id){
        var rows=repo.jdbc().queryForList("SELECT * FROM payment_booking_sync WHERE payment_id=? AND state='AWAITING_BOOKING' AND next_attempt_at<=? FOR UPDATE SKIP LOCKED",id,Timestamp.from(clock.instant()));
        if(rows.isEmpty())return;var sync=rows.getFirst();var order=repo.find(id);int attempt=((Number)sync.get("attempts")).intValue()+1;
        if(repo.jdbc().queryForObject("SELECT count(*) FROM payment_callback_reviews WHERE payment_id=? AND state='REQUIRES_REVIEW'",Integer.class,id)>0){finish(id,"REQUIRES_REVIEW","CALLBACK_REQUIRES_REVIEW",null,attempt);return;}
        try{
            var receipt=booking.receipt(id);
            if(!id.toString().equals(receipt.path("paymentId").asText())||!order.bookingId().toString().equals(receipt.path("bookingId").asText())){finish(id,"REQUIRES_REVIEW","BOOKING_RECEIPT_BINDING_MISMATCH",null,attempt);return;}
            UUID event=(UUID)sync.get("event_id");var payload=json.readTree(repo.jdbc().queryForObject("SELECT body::text FROM event_outbox WHERE event_id=?",String.class,event)).path("payload");
            String expected=Signatures.sha256(order.bookingId()+"|"+order.payerId()+"|"+payload.path("memberId").asText()+"|"+order.purpose()+"|"+sync.get("outcome")+"|"+order.amount().stripTrailingZeros().toPlainString()+"|"+order.currency()+"|"+java.time.Instant.parse(payload.path("paidAt").asText()));
            if(receipt.hasNonNull("fingerprint")&&!expected.equals(receipt.path("fingerprint").asText())){finish(id,"REQUIRES_REVIEW","BOOKING_RECEIPT_FINGERPRINT_MISMATCH",receipt.path("bookingStatus").asText(),attempt);return;}
            String decision=receipt.path("decision").asText();
            if(decision.equals("REQUIRES_REVIEW")){finish(id,"REQUIRES_REVIEW",receipt.path("reason").asText("BOOKING_REQUIRES_REVIEW"),receipt.path("bookingStatus").asText(),attempt);return;}
            if(!(sync.get("outcome").equals("SUCCESS")&&decision.equals("APPLIED")||sync.get("outcome").equals("FAILED")&&decision.equals("FAILED"))){finish(id,"REQUIRES_REVIEW","BOOKING_OUTCOME_MISMATCH",receipt.path("bookingStatus").asText(),attempt);return;}
            finish(id,"ACKNOWLEDGED",null,receipt.path("bookingStatus").asText(),attempt);
        }catch(BookingReceiptClient.Missing ex){pending(id,sync,attempt,"BOOKING_RECEIPT_MISSING");}
        catch(BookingReceiptClient.Unavailable ex){pending(id,sync,attempt,"BOOKING_UNAVAILABLE");}
        catch(com.fasterxml.jackson.core.JsonProcessingException|IllegalArgumentException ex){finish(id,"REQUIRES_REVIEW","PAYMENT_EVENT_INVALID",null,attempt);}
    }
    private void pending(UUID id,Map<String,Object> sync,int attempt,String reason){
        if(attempt>=maxAttempts){finish(id,"REQUIRES_REVIEW",reason,null,attempt);return;}
        if(reason.equals("BOOKING_RECEIPT_MISSING")&&sync.get("event_id")!=null)replay((UUID)sync.get("event_id"));
        repo.jdbc().update("UPDATE payment_booking_sync SET attempts=?,last_error_code=?,checked_at=?,next_attempt_at=?,updated_at=NOW() WHERE payment_id=?",attempt,reason,Timestamp.from(clock.instant()),Timestamp.from(clock.instant().plusSeconds(Math.min(60,1L<<Math.min(attempt,6)))),id);
    }
    private void finish(UUID id,String state,String reason,String bookingState,int attempt){repo.jdbc().update("UPDATE payment_booking_sync SET state=?,last_error_code=?,booking_status=?,attempts=?,checked_at=?,updated_at=NOW() WHERE payment_id=?",state,reason,bookingState,attempt,Timestamp.from(clock.instant()),id);}
    private void replay(UUID event){repo.jdbc().update("UPDATE event_outbox SET published_at=NULL,next_attempt_at=?,last_error=NULL WHERE event_id=?",Timestamp.from(clock.instant()),event);}
    private Map<String,Object> row(UUID id){var rows=repo.jdbc().queryForList("SELECT payment_id,event_id,outcome,state,attempts,next_attempt_at,last_error_code,booking_status,checked_at FROM payment_booking_sync WHERE payment_id=?",id);if(rows.isEmpty())throw new ResourceNotFoundException("Payment synchronization is not available");return rows.getFirst();}
}
