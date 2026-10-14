package com.sporthub.booking.service;
import com.sporthub.booking.repository.BookingRepository;
import com.sporthub.common.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

/** Read-only processing proof. Does not confirm, refund or recreate a reservation. */
@Service @Transactional(readOnly=true)
public class PaymentReceiptQueries {
    public record Receipt(UUID paymentId,UUID bookingId,String fingerprint,String decision,String reason,String bookingStatus){}
    private final BookingRepository repo;
    public PaymentReceiptQueries(BookingRepository repo){this.repo=repo;}
    public Receipt find(UUID payment){
        var receipts=repo.jdbc().queryForList("SELECT booking_id,fingerprint FROM booking_payment_receipts WHERE payment_id=?",payment);
        UUID booking=receipts.isEmpty()?null:(UUID)receipts.getFirst().get("booking_id");
        if(booking==null){
            var ids=repo.jdbc().queryForList("""
                SELECT id FROM bookings WHERE payment_id=? UNION SELECT group_id FROM group_contributions WHERE payment_id=?
                UNION SELECT booking_id FROM payment_reconciliation WHERE payment_id=?
                UNION SELECT booking_id FROM booking_history WHERE action='PAYMENT_FAILED' AND details->>'paymentId'=?
                """,UUID.class,payment,payment,payment,payment.toString());
            if(ids.isEmpty())throw new ResourceNotFoundException("Payment receipt has not been processed");
            if(ids.size()!=1)throw new com.sporthub.common.exception.ConflictException("Legacy payment receipt binding requires review");
            booking=ids.getFirst();
        }
        var first=repo.find(booking);repo.lock(first.courtId());var b=repo.find(booking);
        var review=repo.jdbc().queryForList("SELECT reason FROM payment_reconciliation WHERE payment_id=? AND booking_id=?",payment,booking);
        String decision,reason=null;
        if(!review.isEmpty()){decision="REQUIRES_REVIEW";reason=(String)review.getFirst().get("reason");}
        else if(payment.equals(b.paymentId())||repo.jdbc().queryForObject("SELECT count(*) FROM group_contributions WHERE payment_id=? AND group_id=?",Integer.class,payment,booking)>0)decision="APPLIED";
        else if(repo.jdbc().queryForObject("SELECT count(*) FROM booking_history WHERE booking_id=? AND action='PAYMENT_FAILED' AND details->>'paymentId'=?",Integer.class,booking,payment.toString())>0)decision="FAILED";
        else{decision="REQUIRES_REVIEW";reason="RECEIPT_WITHOUT_RESULT";}
        return new Receipt(payment,booking,receipts.isEmpty()?null:(String)receipts.getFirst().get("fingerprint"),decision,reason,b.status());
    }
}
