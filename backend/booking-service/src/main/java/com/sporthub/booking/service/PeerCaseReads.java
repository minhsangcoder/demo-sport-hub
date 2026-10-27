package com.sporthub.booking.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.sporthub.booking.repository.BookingRepository;
import com.sporthub.common.exception.ForbiddenException;
import com.sporthub.common.security.RemoteIdentity.Caller;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Current facility ownership and an actual case are both required to inspect private evidence. */
@Service
public class PeerCaseReads {
  private final BookingRepository repo;
  private final PeerUsageStore attempts;
  private final BookingDependencies dependencies;
  private final PeerUsageTimeouts timeouts;
  private final TransactionTemplate tx;
  private final PeerMoneyReads money;

  public PeerCaseReads(
      BookingRepository repo,
      PeerUsageStore attempts,
      BookingDependencies dependencies,
      PeerUsageTimeouts timeouts,
      PlatformTransactionManager manager,
      PeerMoneyReads money) {
    this.repo = repo;
    this.attempts = attempts;
    this.dependencies = dependencies;
    this.timeouts = timeouts;
    this.tx = new TransactionTemplate(manager);
    this.money = money;
  }

  public void requireFacility(UUID id, UUID facility) {
    if (!attempts.attempt(id).facility().equals(facility))
      throw new ForbiddenException("Vụ việc không thuộc cơ sở đang chọn.");
  }

  public List<JsonNode> list(UUID facility, UUID after, int size, Caller actor, String token) {
    actor.requireRole("OWNER");
    dependencies.ownerFacility(facility, actor, token);
    if (size < 1 || size > 100)
      throw new IllegalArgumentException("Chỉ tải tối đa 100 vụ việc mỗi lần.");
    var ids =
        repo.jdbc()
            .queryForList(
                "SELECT acquisition_id FROM peer_money_cases WHERE facility_id=? AND (?::uuid IS"
                    + " NULL OR acquisition_id>?::uuid) ORDER BY acquisition_id LIMIT ?",
                UUID.class,
                facility,
                after,
                after,
                size);
    return attempts.views(ids);
  }

  public List<Map<String, Object>> presenceContext(UUID facility, Caller actor, String token) {
    dependencies.facilityPermission(facility, actor, token, "BOOKING_CHECK_IN");
    return repo.jdbc()
        .query(
            "SELECT acquisition_id,booking_id,court_id,seller_id,buyer_id,phase,starts_at,ends_at"
                + " FROM peer_usage_attempts WHERE facility_id=? AND phase"
                + " IN('PAYMENT_DECLARED','DISPUTED') ORDER BY starts_at,acquisition_id LIMIT 100",
            (r, n) ->
                Map.<String, Object>of(
                    "id",
                    r.getObject("acquisition_id", UUID.class),
                    "bookingId",
                    r.getObject("booking_id", UUID.class),
                    "courtId",
                    r.getObject("court_id", UUID.class),
                    "sellerId",
                    r.getObject("seller_id", UUID.class),
                    "buyerId",
                    r.getObject("buyer_id", UUID.class),
                    "phase",
                    r.getString("phase"),
                    "startsAt",
                    r.getTimestamp("starts_at").toInstant(),
                    "endsAt",
                    r.getTimestamp("ends_at").toInstant()),
            facility);
  }

  public JsonNode detail(UUID id, UUID facility, Caller actor, String token) {
    requireFacility(id, facility);
    evidenceAccess(id, actor, token);
    return detailUnderCourt(id);
  }

  public JsonNode partyDetail(UUID id, UUID actor) {
    var a = attempts.attempt(id);
    if (!a.participant(actor)) throw new ForbiddenException("Bạn không thuộc vụ việc này.");
    if (!Boolean.TRUE.equals(
        repo.jdbc()
            .queryForObject(
                "SELECT EXISTS(SELECT 1 FROM peer_money_cases WHERE acquisition_id=?)",
                Boolean.class,
                id))) throw new ForbiddenException("Phiên chưa mở hồ sơ tranh chấp.");
    return detailUnderCourt(id);
  }

  private JsonNode detailUnderCourt(UUID id) {
    var before = attempts.attempt(id);
    return tx.execute(
        status -> {
          repo.lock(before.court());
          timeouts.settleBookingUnderCourt(before.booking());
          var a = attempts.attempt(id);
          var booking = repo.find(a.booking());
          var result = new LinkedHashMap<String, Object>();
          result.put("attempt", attempts.view(id));
          result.put(
              "booking",
              Map.of(
                  "id",
                  booking.id(),
                  "status",
                  booking.status(),
                  "currentHolderId",
                  booking.currentHolderId(),
                  "nativeAmount",
                  booking.amount(),
                  "currency",
                  booking.currency(),
                  "startsAt",
                  booking.startsAt(),
                  "endsAt",
                  booking.endsAt()));
          result.put("recipientRevisionId", a.bankRevision());
          result.put("destinationHash", a.destinationHash());
          result.put(
              "moneyCase",
              repo.jdbc()
                  .query(
                      "SELECT * FROM peer_money_cases WHERE acquisition_id=?",
                      (r, n) ->
                          Map.of(
                              "state",
                              r.getString("state"),
                              "reason",
                              Objects.toString(r.getString("reason"), ""),
                              "version",
                              r.getLong("version"),
                              "openedAt",
                              r.getTimestamp("created_at").toInstant(),
                              "updatedAt",
                              r.getTimestamp("updated_at").toInstant()),
                      id)
                  .getFirst());
          var claims =
              repo.jdbc()
                  .query(
                      "SELECT * FROM peer_usage_evidence_receipts WHERE acquisition_id=?",
                      (r, n) ->
                          Map.of(
                              "reference",
                              r.getString("bank_reference"),
                              "evidenceIds",
                              Arrays.asList((UUID[]) r.getArray("evidence_ids").getArray()),
                              "claimedAt",
                              r.getTimestamp("claimed_at").toInstant(),
                              "confirmationMethod",
                              "BUYER_DECLARED"),
                      id);
          result.put("declaration", claims.isEmpty() ? null : claims.getFirst());
          result.put(
              "presence",
              repo.jdbc()
                  .query(
                      "SELECT * FROM peer_attendance WHERE acquisition_id=? ORDER BY"
                          + " observed_at,party",
                      (r, n) ->
                          Map.of(
                              "party",
                              r.getString("party"),
                              "actorId",
                              r.getObject("actor_id", UUID.class),
                              "observedAt",
                              r.getTimestamp("observed_at").toInstant(),
                              "reason",
                              r.getString("reason")),
                      id));
          result.put(
              "history",
              repo.jdbc()
                  .query(
                      "SELECT id,action,actor_id,details,created_at FROM booking_history WHERE"
                          + " booking_id=? AND details->>'acquisitionId'=? ORDER BY created_at"
                          + " DESC,id DESC LIMIT 100",
                      (r, n) -> {
                        var row = new LinkedHashMap<String, Object>();
                        row.put("id", r.getObject("id", UUID.class));
                        row.put("action", r.getString("action"));
                        row.put("actorId", r.getObject("actor_id", UUID.class));
                        row.put("details", repo.read(r.getString("details")));
                        row.put("createdAt", r.getTimestamp("created_at").toInstant());
                        return row;
                      },
                      a.booking(),
                      id.toString()));
          result.put("authority", "USAGE_ONLY");
          result.put("money", money.forCase(id));
          return repo.read(repo.write(result));
        });
  }

  public Map<String, Object> evidenceAccess(UUID id, Caller actor, String token) {
    actor.requireRole("OWNER");
    var attempt = attempts.attempt(id);
    dependencies.ownerFacility(attempt.facility(), actor, token);
    if (!Boolean.TRUE.equals(
        repo.jdbc()
            .queryForObject(
                "SELECT EXISTS(SELECT 1 FROM peer_money_cases WHERE acquisition_id=?)",
                Boolean.class,
                id)))
      throw new ForbiddenException("Chỉ xem chứng cứ khi có vụ việc thuộc cơ sở của bạn.");
    repo.history(
        attempt.booking(),
        actor.id(),
        "PEER_CASE_EVIDENCE_ACCESS",
        Map.of("acquisitionId", id, "facilityId", attempt.facility()));
    return Map.of(
        "acquisitionId",
        id,
        "facilityId",
        attempt.facility(),
        "actorId",
        actor.id(),
        "sellerId",
        attempt.seller(),
        "recipientRevisionId",
        attempt.bankRevision(),
        "destinationHash",
        attempt.destinationHash(),
        "canReadEvidence",
        true);
  }

  public Map<String, Object> retention(UUID id) {
    var attempt = attempts.attempt(id);
    boolean hasResponsibilities =
        Boolean.TRUE.equals(
            repo.jdbc()
                .queryForObject(
                    "SELECT EXISTS(SELECT 1 FROM peer_money_cases WHERE acquisition_id=? AND state"
                        + " NOT IN('CLOSED_UNPROVEN','CLOSED_NO_RETURN','CLOSED_RETURNED','CLOSED_MUTUAL'))"
                        + " OR EXISTS(SELECT 1 FROM peer_return_obligations WHERE acquisition_id=?"
                        + " AND state='AWAITING_BUYER')",
                    Boolean.class,
                    id,
                    id));
    return Map.of(
        "acquisitionId", id, "canDiscardStaged", attempt.terminal() && !hasResponsibilities);
  }
}
