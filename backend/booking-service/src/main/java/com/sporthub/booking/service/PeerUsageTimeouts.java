package com.sporthub.booking.service;

import com.sporthub.booking.repository.BookingRepository;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class PeerUsageTimeouts {
  private static final UUID SYSTEM = new UUID(0, 0);
  private final BookingRepository repo;
  private final PeerUsageStore store;
  private final PeerUsageCommands commands;
  private final PeerHandoffs handoffs;
  private final Clock clock;
  private final com.fasterxml.jackson.databind.ObjectMapper json;

  public PeerUsageTimeouts(
      BookingRepository repo,
      PeerUsageStore store,
      PeerUsageCommands commands,
      PeerHandoffs handoffs,
      Clock clock,com.fasterxml.jackson.databind.ObjectMapper json) {
    this.repo = repo;
    this.store = store;
    this.commands = commands;
    this.handoffs = handoffs;
    this.clock = clock;
    this.json = json;
  }

  /** Only reduces expired responsibilities; caller already owns the court fence. */
  @org.springframework.transaction.annotation.Transactional(propagation=org.springframework.transaction.annotation.Propagation.MANDATORY)
  public void settleBookingUnderCourt(UUID booking) {
    var ids=repo.jdbc().queryForList("SELECT acquisition_id FROM peer_usage_attempts WHERE booking_id=? AND phase IN('WAITING_PAYMENT','PAYMENT_DECLARED','DISPUTED')",UUID.class,booking);
    for(var id:ids) {
      var a=store.attempt(id);
      String fingerprint=com.sporthub.common.security.Signatures.sha256("PEER_USAGE_TIMEOUT|"+id);
      if(settleDue(a,fingerprint) && !a.terminal())
        com.sporthub.common.event.ReliableOutbox.enqueue(repo.jdbc(),json,com.sporthub.common.event.RabbitMQConfig.EXCHANGE_BOOKING,
            com.sporthub.common.event.DomainEvent.create("peer.usage.changed",1,"booking-service",id,store.view(id)));
    }
  }

  @Scheduled(
      fixedDelayString = "${booking.peer-expiry-delay-ms:2000}",
      initialDelayString = "${booking.peer-expiry-delay-ms:2000}")
  public void tick() {
    var now = Timestamp.from(clock.instant());
    var ids =
        repo.jdbc()
            .queryForList(
                """
SELECT a.acquisition_id FROM peer_usage_attempts a
WHERE (a.phase='WAITING_PAYMENT' AND a.pay_by<=?)
OR (a.phase IN('PAYMENT_DECLARED','DISPUTED') AND a.starts_at<=?
  AND (a.ends_at<=? OR NOT EXISTS (
    SELECT 1 FROM peer_attendance p
    WHERE p.acquisition_id=a.acquisition_id AND p.observed_at<=a.starts_at)))
ORDER BY a.starts_at,a.acquisition_id LIMIT 100
""",
                UUID.class,
                now,
                now,
                now);
    for (var id : ids) {
      var before = store.attempt(id);
      if (before.terminal()) continue;
      // Presence can postpone the usage decision until endsAt, without extending the schedule.
      if (!before.phase().equals("WAITING_PAYMENT")
          && clock.instant().isBefore(before.endsAt())
          && hasTimelyPresence(before)) continue;
      commands.run(
          id,
          SYSTEM,
          "PEER_USAGE_TIMEOUT",
          "timeout:" + id,
          Map.of("acquisitionId", id),
          (a, fingerprint) -> settleDue(a, fingerprint));
    }
  }

  /** Called inside the same user/court command fence by on-demand resolution too. */
  public boolean settleDue(PeerUsageStore.Attempt a, String fingerprint) {
    var now = clock.instant();
    if (a.terminal()) return true;
    if (a.phase().equals("WAITING_PAYMENT") && !now.isBefore(a.payBy())) {
      handoffs.retain(
          a, "EXPIRED", null, "NONE", "Hết hạn khai thanh toán phiên mua.", fingerprint);
      return true;
    }
    if (Set.of("PAYMENT_DECLARED", "DISPUTED").contains(a.phase()) && !now.isBefore(a.startsAt())) {
      if (!hasTimelyPresence(a)) {
        handoffs.retain(
            a,
            "TIMED_OUT_A_RETAINS",
            null,
            "BUYER_DECLARED",
            "Không bên nào được ghi nhận có mặt đúng giờ; A giữ quyền sử dụng.",
            fingerprint);
        return true;
      }
      if (!now.isBefore(a.endsAt())) {
        handoffs.retain(
            a,
            "ABORTED_A_RETAINS",
            null,
            "BUYER_DECLARED",
            "Đã hết giờ sử dụng, không bàn giao quyền trong quá khứ.",
            fingerprint);
        return true;
      }
    }
    return false;
  }

  public boolean hasTimelyPresence(PeerUsageStore.Attempt a) {
    return repo.jdbc()
            .queryForObject(
                "SELECT count(*) FROM peer_attendance WHERE acquisition_id=? AND observed_at<=?",
                Integer.class,
                a.id(),
                Timestamp.from(a.startsAt()))
        > 0;
  }
}
