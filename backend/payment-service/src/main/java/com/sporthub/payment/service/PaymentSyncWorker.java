package com.sporthub.payment.service;
import com.sporthub.payment.repository.PaymentRepository;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;
import java.time.Clock;
import java.sql.Timestamp;
import java.util.UUID;
@Component
public class PaymentSyncWorker {
    private final PaymentRepository repo;private final PaymentSynchronization sync;private final Clock clock;
    public PaymentSyncWorker(PaymentRepository repo,PaymentSynchronization sync,Clock clock){this.repo=repo;this.sync=sync;this.clock=clock;}
    @Scheduled(fixedDelayString="${sporthub.payment.sync.delay-ms:2000}",initialDelayString="${sporthub.payment.sync.delay-ms:2000}")
    public void run(){for(UUID id:repo.jdbc().queryForList("SELECT payment_id FROM payment_booking_sync WHERE state='AWAITING_BOOKING' AND next_attempt_at<=? ORDER BY next_attempt_at LIMIT 10",UUID.class,Timestamp.from(clock.instant())))sync.process(id);}
}
