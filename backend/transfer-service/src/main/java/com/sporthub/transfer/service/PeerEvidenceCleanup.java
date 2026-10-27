package com.sporthub.transfer.service;

import com.sporthub.transfer.repository.TransferRepository;
import jakarta.annotation.PreDestroy;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Never blocks the financial scheduler or removes READY/referenced evidence. */
@Component
public class PeerEvidenceCleanup implements AutoCloseable {
  private final TransferRepository repo;
  private final PeerBookingClient booking;
  private final PeerEvidenceStorage storage;
  private final Clock clock;
  private final TransactionTemplate tx;
  private final ExecutorService worker =
      Executors.newSingleThreadExecutor(
          Thread.ofPlatform().daemon().name("peer-evidence-cleanup").factory());
  private final AtomicBoolean running = new AtomicBoolean();
  private Instant after = Instant.EPOCH;
  private UUID afterId = new UUID(0, 0);

  public PeerEvidenceCleanup(
      TransferRepository repo,
      PeerBookingClient booking,
      PeerEvidenceStorage storage,
      Clock clock,
      PlatformTransactionManager manager) {
    this.repo = repo;
    this.booking = booking;
    this.storage = storage;
    this.clock = clock;
    tx = new TransactionTemplate(manager);
  }

  @Scheduled(
      fixedDelayString = "${transfer.peer-evidence-cleanup-ms:30000}",
      initialDelayString = "${transfer.peer-evidence-cleanup-ms:30000}")
  public void tick() {
    if (running.compareAndSet(false, true))
      worker.execute(
          () -> {
            try {
              drain();
            } finally {
              running.set(false);
            }
          });
  }

  public void drain() {
    var now = clock.instant();
    var rows =
        repo.jdbc()
            .queryForList(
                "SELECT * FROM transfer_evidence WHERE ((state='STAGED' AND created_at<?) OR"
                    + " (state='PURGING' AND cleanup_after<=?)) AND (created_at,id)>(?,?) ORDER BY"
                    + " created_at,id LIMIT 50",
                Timestamp.from(now.minusSeconds(86400)),
                Timestamp.from(now),
                Timestamp.from(after),
                afterId);
    if (rows.isEmpty()) {
      after = Instant.EPOCH;
      afterId = new UUID(0, 0);
      return;
    }
    for (var row : rows) {
      after = ((Timestamp) row.get("created_at")).toInstant();
      afterId = (UUID) row.get("id");
      try {
        clean(row);
      } catch (RuntimeException ignored) {
        /* Retain on missing authority or storage. */
      }
    }
  }

  private void clean(Map<String, Object> row) {
    UUID id = (UUID) row.get("id"),
        acquisition = (UUID) row.get("acquisition_id"),
        actor = (UUID) row.get("uploader_id");
    var permission = booking.retention(acquisition);
    if (!acquisition.toString().equals(permission.path("acquisitionId").asText())
        || !permission.path("canDiscardStaged").isBoolean()
        || !permission.path("canDiscardStaged").booleanValue()) return;
    if ("STAGED".equals(row.get("state"))) {
      tx.executeWithoutResult(
          status -> {
            repo.lock(actor);
            repo.jdbc()
                .update(
                    "UPDATE transfer_evidence SET state='PURGING',cleanup_after=? WHERE id=? AND"
                        + " state='STAGED' AND created_at<? AND NOT EXISTS(SELECT 1 FROM"
                        + " peer_case_evidence WHERE evidence_id=?) AND NOT EXISTS(SELECT 1 FROM"
                        + " peer_payment_claims WHERE ?=ANY(evidence_ids))",
                    Timestamp.from(clock.instant().plusSeconds(30)),
                    id,
                    Timestamp.from(clock.instant().minusSeconds(86400)),
                    id,
                    id);
          });
      return;
    }
    storage.remove(row.get("object_key").toString());
    // Keep a tombstone and repeat deletion, covering a stalled writer which resumes after cleanup.
    tx.executeWithoutResult(
        status -> {
          repo.lock(actor);
          repo.jdbc()
              .update(
                  "UPDATE transfer_evidence SET cleanup_after=? WHERE id=? AND state='PURGING'",
                  Timestamp.from(clock.instant().plusSeconds(3600)),
                  id);
        });
  }

  @Override
  @PreDestroy
  public void close() {
    worker.shutdownNow();
  }
}
