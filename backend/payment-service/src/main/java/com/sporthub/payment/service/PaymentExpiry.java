package com.sporthub.payment.service;
import com.sporthub.payment.repository.PaymentRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.Clock;
import java.sql.Timestamp;
import java.util.UUID;
@Component
public class PaymentExpiry {
    private final PaymentRepository repo;private final PaymentService service;private final Clock clock;
    public PaymentExpiry(PaymentRepository repo,PaymentService service,Clock clock){this.repo=repo;this.service=service;this.clock=clock;}
    @Scheduled(fixedDelayString="${sporthub.payment.expiry-delay-ms:2000}",initialDelayString="${sporthub.payment.expiry-delay-ms:2000}")
    public void run(){for(UUID id:repo.jdbc().queryForList("SELECT id FROM payment_orders WHERE status='PENDING' AND expires_at<=? ORDER BY expires_at LIMIT 100",UUID.class,Timestamp.from(clock.instant())))service.expire(id);}
}
