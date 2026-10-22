package com.sporthub.payment.service;

import com.sporthub.payment.repository.PaymentRepository;
import org.slf4j.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.UUID;

@Component
public class MovementWorker {
    private static final Logger log=LoggerFactory.getLogger(MovementWorker.class);
    private final PaymentRepository repo;private final MovementExecutions executions;private final Clock clock;private final boolean submitEnabled;
    public MovementWorker(PaymentRepository repo,MovementExecutions executions,Clock clock,@Value("${sporthub.finance.movements.submit-enabled:false}") boolean submitEnabled){this.repo=repo;this.executions=executions;this.clock=clock;this.submitEnabled=submitEnabled;}
    @Scheduled(initialDelayString="${sporthub.finance.movements.delay-ms:10000}",fixedDelayString="${sporthub.finance.movements.delay-ms:10000}")
    public void run(){
        // The kill switch stops new submissions; existing proof queries continue.
        if(submitEnabled)for(var row:repo.jdbc().queryForList("SELECT id FROM money_movement_orders WHERE state IN('NEW','FUNDING_PENDING','READY','FAILED_RETRYABLE','RETURNED') AND next_attempt_at<=? ORDER BY next_attempt_at,id LIMIT 20",Timestamp.from(clock.instant()))){UUID id=(UUID)row.get("id");try{executions.claim(id).ifPresent(attempt->executions.dispatch(attempt,true));}catch(RuntimeException failure){log.warn("Movement claim requires attention: {} ({})",id,failure.getClass().getSimpleName());}}
        for(var row:repo.jdbc().queryForList("SELECT id FROM money_movement_attempts WHERE submit_count>0 AND state IN('SUBMITTING','PROCESSING','UNKNOWN') AND next_query_at<=? ORDER BY next_query_at,id LIMIT 20",Timestamp.from(clock.instant()))){UUID id=(UUID)row.get("id");try{executions.dispatch(id,submitEnabled);}catch(RuntimeException failure){log.warn("Movement query requires attention: {} ({})",id,failure.getClass().getSimpleName());}}
    }
}
