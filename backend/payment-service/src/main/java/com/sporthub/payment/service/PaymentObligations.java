package com.sporthub.payment.service;
import com.sporthub.payment.repository.PaymentRepository;
import com.sporthub.payment.web.PaymentDtos.Payment;
import com.sporthub.common.exception.ConflictException;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.util.*;

/** Local claim for an existing payable scope. No provider/Booking transaction is implied. */
@Component
public class PaymentObligations {
    private final PaymentRepository repo;
    public PaymentObligations(PaymentRepository repo){this.repo=repo;}
    public static UUID subject(UUID member,UUID acquisition){return member!=null?member:acquisition!=null?acquisition:new UUID(0,0);}
    public Map<String,Object> find(UUID booking,String purpose,UUID subject){
        var rows=repo.jdbc().queryForList("SELECT * FROM payment_obligations WHERE booking_id=? AND purpose=? AND subject_id=?",booking,purpose,subject);
        return rows.isEmpty()?null:rows.getFirst();
    }
    public void prepare(UUID booking,String purpose,UUID subject,BigDecimal amount,String currency){
        repo.jdbc().update("INSERT INTO payment_obligations(booking_id,purpose,subject_id,amount,currency) VALUES(?,?,?,?,?) ON CONFLICT DO NOTHING",booking,purpose,subject,amount,currency);
        var claim=find(booking,purpose,subject);
        if(((BigDecimal)claim.get("amount")).compareTo(amount)!=0||!currency.equals(claim.get("currency")))throw new ConflictException("Payable amount changed for an existing payment obligation");
    }
    public void current(Payment p){repo.jdbc().update("UPDATE payment_obligations SET current_payment_id=?,updated_at=NOW() WHERE booking_id=? AND purpose=? AND subject_id=?",p.id(),p.bookingId(),p.purpose(),subject(p.memberId(),p.acquisitionId()));}
    public boolean settle(Payment p){
        var claim=find(p.bookingId(),p.purpose(),subject(p.memberId(),p.acquisitionId()));
        if(claim==null)throw new ConflictException("Payment obligation is missing");
        if(claim.get("settled_payment_id")!=null)return claim.get("settled_payment_id").equals(p.id());
        repo.jdbc().update("UPDATE payment_obligations SET settled_payment_id=?,current_payment_id=?,updated_at=NOW() WHERE booking_id=? AND purpose=? AND subject_id=? AND settled_payment_id IS NULL",p.id(),p.id(),p.bookingId(),p.purpose(),subject(p.memberId(),p.acquisitionId()));return true;
    }
}
