package com.sporthub.common.schedule;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.*;
import java.util.List;
import java.util.UUID;
import static com.sporthub.common.schedule.ConfigurationGuardProtocol.*;

/**
 * A durable Booking barrier surrounds a LOCAL configuration transaction.
 * Committed release intents retry after outages. There is deliberately no lease expiry:
 * a coordinator crash before local commit can leave an orphan barrier needing reconciliation.
 * Automatically timing that barrier out would admit bookings against an uncertain configuration.
 */
@Component
@ConditionalOnProperty(name="sporthub.configuration-changes.source")
public class ConfigurationChanges {
    private final JdbcTemplate jdbc;
    private final ConfigurationGuardTransport transport;
    private final String source;
    private final TransactionTemplate independent;
    public ConfigurationChanges(JdbcTemplate jdbc,ConfigurationGuardTransport transport,
            PlatformTransactionManager manager,@Value("${sporthub.configuration-changes.source}") String source) {
        this.jdbc=jdbc;this.transport=transport;this.source=source;
        independent=new TransactionTemplate(manager);
        independent.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }
    public List<Reservation> begin(UUID facility) {
        if(!TransactionSynchronizationManager.isActualTransactionActive()||TransactionSynchronizationManager.isCurrentTransactionReadOnly())throw new IllegalStateException("Configuration changes require a write transaction");
        for(var synchronization:TransactionSynchronizationManager.getSynchronizations())if(synchronization instanceof Change existing&&existing.command.facilityId().equals(facility)){
            if(existing.reservations==null)throw new IllegalStateException("Configuration preparation is still pending");return existing.reservations;
        }
        var command=new Command(UUID.randomUUID(),facility,source);
        jdbc.update("INSERT INTO configuration_guard_releases(operation_id,facility_id) VALUES(?,?)",command.operationId(),facility);
        var change=new Change(command);TransactionSynchronizationManager.registerSynchronization(change);
        change.reservations=List.copyOf(transport.prepare(command).reservations());return change.reservations;
    }
    private final class Change implements TransactionSynchronization {
        private final Command command;private List<Reservation> reservations;
        private Change(Command command){this.command=command;}
        @Override public void afterCompletion(int status){
            if(status==STATUS_UNKNOWN)return;
            try{
                if(status==STATUS_ROLLED_BACK)independent.executeWithoutResult(tx->jdbc.update("INSERT INTO configuration_guard_releases(operation_id,facility_id) VALUES(?,?) ON CONFLICT DO NOTHING",command.operationId(),command.facilityId()));
                transport.release(command);removeIntent(command.operationId());
            }catch(Exception unavailable){org.slf4j.LoggerFactory.getLogger(ConfigurationChanges.class).warn("Configuration barrier {} requires release/reconciliation",command.operationId());}
        }
    }
    @Scheduled(fixedDelayString="${sporthub.configuration-changes.release-delay-ms:10000}")
    public void retryCommittedReleases() {
        for(var row:jdbc.queryForList("SELECT operation_id,facility_id FROM configuration_guard_releases ORDER BY created_at LIMIT 100")) {
            var command=new Command((UUID)row.get("operation_id"),(UUID)row.get("facility_id"),source);
            try {transport.release(command);removeIntent(command.operationId());}
            catch(Exception unavailable) {break;}
        }
    }
    private void removeIntent(UUID id) {
        independent.executeWithoutResult(status->jdbc.update("DELETE FROM configuration_guard_releases WHERE operation_id=?",id));
    }
}
