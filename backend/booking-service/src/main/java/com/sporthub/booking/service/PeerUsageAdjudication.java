package com.sporthub.booking.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.sporthub.booking.repository.BookingRepository;
import com.sporthub.common.exception.*;
import com.sporthub.common.money.PeerResaleContract;
import com.sporthub.common.security.RemoteIdentity.Caller;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;

/** Facility operators record attendance; only the facility Owner decides disputed usage rights. */
@Service
public class PeerUsageAdjudication {
  private final BookingRepository repo;
  private final BookingDependencies dependencies;
  private final PeerUsageStore store;
  private final PeerUsageCommands commands;
  private final PeerHandoffs handoffs;
  private final PeerUsageTimeouts timeouts;
  private final PeerCaseEvidenceClient evidence;
  private final Clock clock;
  private final MarketFraudRestrictions restrictions;

  public PeerUsageAdjudication(
      BookingRepository repo,
      BookingDependencies dependencies,
      PeerUsageStore store,
      PeerUsageCommands commands,
      PeerHandoffs handoffs,
      PeerUsageTimeouts timeouts,
      PeerCaseEvidenceClient evidence,
      Clock clock,
      MarketFraudRestrictions restrictions) {
    this.repo = repo;
    this.dependencies = dependencies;
    this.store = store;
    this.commands = commands;
    this.handoffs = handoffs;
    this.timeouts = timeouts;
    this.evidence = evidence;
    this.clock = clock;
    this.restrictions = restrictions;
  }

  public JsonNode presence(
      UUID id, PeerResaleContract.Presence input, String key, Caller actor, String token) {
    var before = store.attempt(id);
    dependencies.facilityPermission(before.facility(), actor, token, "BOOKING_CHECK_IN");
    if (!Set.of("SELLER", "BUYER").contains(input.party())
        || input.reason() == null
        || input.reason().isBlank()
        || input.reason().length() > 500)
      throw new IllegalArgumentException("Cần chọn đúng bên có mặt và ghi lý do tối đa 500 ký tự.");
    return commands.run(
        id,
        actor.id(),
        "PEER_PRESENCE",
        key,
        input,
        (a, fingerprint) -> {
          var now = clock.instant();
          if (a.terminal()
              || now.isAfter(a.startsAt())
              || !Set.of("PAYMENT_DECLARED", "DISPUTED").contains(a.phase()))
            throw new ConflictException(
                "Chỉ ghi nhận có mặt trước hoặc đúng giờ bắt đầu khi vụ việc còn mở.");
          int added =
              repo.jdbc()
                  .update(
                      "INSERT INTO"
                          + " peer_attendance(acquisition_id,party,actor_id,observed_at,reason)"
                          + " VALUES(?,?,?,?,?) ON CONFLICT(acquisition_id,party) DO NOTHING",
                      id,
                      input.party(),
                      actor.id(),
                      Timestamp.from(now),
                      input.reason().trim());
          if (added > 0) {
            store.phase(a, a.phase(), a.confirmationMethod(), now);
            repo.history(
                a.booking(),
                actor.id(),
                "PEER_ATTENDANCE_RECORDED",
                Map.of("acquisitionId", id, "party", input.party(), "observedAt", now));
          }
        });
  }

  public JsonNode resolve(
      UUID id, PeerResaleContract.Resolution input, String key, Caller actor, String token) {
    actor.requireRole("OWNER");
    var before = store.attempt(id);
    dependencies.ownerFacility(before.facility(), actor, token);
    if (!Set.of("AWARD_BUYER", "SELLER_RETAINS", "BUYER_FRAUD").contains(input.outcome())
        || input.reason() == null
        || input.reason().isBlank()
        || input.reason().length() > 1000
        || input.evidenceIds() == null
        || input.evidenceIds().size() > 20
        || input.evidenceIds().stream().anyMatch(Objects::isNull))
      throw new IllegalArgumentException("Cần kết luận quyền sử dụng, lý do và chứng cứ hợp lệ.");
    Set<UUID> available = evidence.ready(id, input.evidenceIds());
    if (!available.containsAll(input.evidenceIds()))
      throw new ForbiddenException("Có chứng cứ không thuộc vụ việc này hoặc chưa sẵn sàng.");
    if (input.outcome().equals("BUYER_FRAUD") && input.evidenceIds().isEmpty())
      throw new IllegalArgumentException(
          "Kết luận cố ý khai chuyển tiền giả phải có chứng cứ được ghi nhận.");
    return commands.run(
        id,
        actor.id(),
        "PEER_RESOLVE",
        key,
        input,
        (a, fingerprint) -> {
          store.expected(a, new PeerResaleContract.Command(input.version(), before.termsHash()));
          if (a.terminal())
            throw new ConflictException("Vụ việc đã có quyết định quyền sử dụng cuối.");
          if (timeouts.settleDue(a, fingerprint)) return;
          if (!a.phase().equals("DISPUTED"))
            throw new ConflictException("Chỉ phân xử phiên đã mở tranh chấp.");
          if (input.outcome().equals("AWARD_BUYER"))
            handoffs.complete(
                a, actor.id(), "OWNER_ADJUDICATED", input.reason().trim(), fingerprint);
          else {
            handoffs.retain(
                a,
                "ABORTED_A_RETAINS",
                actor.id(),
                "OWNER_ADJUDICATED",
                input.reason().trim(),
                fingerprint);
            if (input.outcome().equals("BUYER_FRAUD")) strike(a, actor, input);
          }
          repo.history(
              a.booking(),
              actor.id(),
              "PEER_OWNER_ADJUDICATED",
              Map.of(
                  "acquisitionId",
                  id,
                  "outcome",
                  input.outcome(),
                  "reason",
                  input.reason().trim(),
                  "evidenceIds",
                  input.evidenceIds(),
                  "authority",
                  "USAGE_ONLY"));
        });
  }

  private void strike(PeerUsageStore.Attempt a, Caller actor, PeerResaleContract.Resolution input) {
    var now = clock.instant();
    int added =
        repo.jdbc()
            .update(
                "INSERT INTO"
                    + " market_fraud_strikes(acquisition_id,principal_id,actor_id,reason,evidence,recorded_at)"
                    + " VALUES(?,?,?,?,?::jsonb,?) ON CONFLICT(acquisition_id) DO NOTHING",
                a.id(),
                a.buyer(),
                actor.id(),
                input.reason().trim(),
                repo.write(Map.of("evidenceIds", input.evidenceIds())),
                Timestamp.from(now));
    if (added == 0) return;
    restrictions.rebuild(a.buyer());
  }

  public JsonNode correctFraud(
      UUID id, PeerResaleContract.FraudCorrection input, String key, Caller actor, String token) {
    actor.requireRole("OWNER");
    var before = store.attempt(id);
    dependencies.ownerFacility(before.facility(), actor, token);
    if (input == null
        || input.reason() == null
        || input.reason().isBlank()
        || input.reason().length() > 1000
        || input.evidenceIds() == null
        || input.evidenceIds().isEmpty()
        || input.evidenceIds().size() > 20
        || input.evidenceIds().stream().anyMatch(Objects::isNull))
      throw new IllegalArgumentException("Đính chính cần lý do và từ 1 đến 20 chứng cứ.");
    if (!evidence.ready(id, input.evidenceIds()).containsAll(input.evidenceIds()))
      throw new ForbiddenException("Chứng cứ đính chính chưa sẵn sàng hoặc không thuộc vụ việc.");
    return commands.run(
        id,
        actor.id(),
        "PEER_FRAUD_CORRECTION",
        key,
        input,
        (a, fingerprint) -> {
          if (!Boolean.TRUE.equals(
              repo.jdbc()
                  .queryForObject(
                      "SELECT EXISTS(SELECT 1 FROM market_fraud_strikes WHERE acquisition_id=?)",
                      Boolean.class,
                      id)))
            throw new ConflictException("Vụ việc chưa có kết luận gian dối để đính chính.");
          if (repo.jdbc()
                  .update(
                      """
INSERT INTO market_strike_corrections(acquisition_id,actor_id,reason,evidence,recorded_at)
VALUES(?,?,?,?::jsonb,?) ON CONFLICT(acquisition_id) DO NOTHING
""",
                      id,
                      actor.id(),
                      input.reason().trim(),
                      repo.write(input.evidenceIds()),
                      Timestamp.from(clock.instant()))
              == 0) throw new ConflictException("Kết luận gian dối này đã được đính chính.");
          restrictions.rebuild(a.buyer());
          repo.history(
              a.booking(),
              actor.id(),
              "PEER_FRAUD_CORRECTED",
              Map.of(
                  "acquisitionId",
                  id,
                  "reason",
                  input.reason().trim(),
                  "evidenceIds",
                  input.evidenceIds(),
                  "usageDecisionUnchanged",
                  true));
        });
  }
}
