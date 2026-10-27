package com.sporthub.booking.service;

import com.sporthub.booking.repository.BookingRepository;
import com.sporthub.common.exception.ActionPolicyException;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

/** One online reservation budget shared by native booking and peer resale. */
@Service
public class BookingAdmission {
  public record Resource(String kind, UUID resourceId, Instant expiresAt) {}

  private final BookingRepository repo;
  private final Clock clock;
  private final boolean protectionEnabled;
  private final HoldProtection protection;

  public BookingAdmission(BookingRepository repo, Clock clock) {
    this(repo, clock, false, null);
  }

  @org.springframework.beans.factory.annotation.Autowired
  public BookingAdmission(
      BookingRepository repo,
      Clock clock,
      @org.springframework.beans.factory.annotation.Value(
              "${SPORTHUB_HOLD_PROTECTION_ENABLED:false}")
          boolean protectionEnabled,
      HoldProtection protection) {
    this.repo = repo;
    this.clock = clock;
    this.protectionEnabled = protectionEnabled;
    this.protection = protection;
  }

  /** Caller owns all user fences first; acquire all affected courts before touching claim rows. */
  @Transactional(propagation = Propagation.MANDATORY)
  public void lockCourts(UUID user, UUID target) {
    var courts =
        new HashSet<>(
            repo.jdbc()
                .queryForList(
                    """
SELECT court_id FROM user_hold_claims WHERE principal_id=? AND state='ACTIVE'
UNION SELECT court_id FROM slot_reservations WHERE holder_id=? AND state='HOLD' AND expires_at>?
UNION SELECT b.court_id FROM transfer_leases l JOIN bookings b ON b.id=l.booking_id
  WHERE l.buyer_id=? AND l.state='LOCKED'
""",
                    UUID.class,
                    user,
                    user,
                    Timestamp.from(clock.instant()),
                    user));
    courts.add(target);
    courts.stream().sorted(Comparator.comparing(UUID::toString)).forEach(repo::lock);
  }

  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  public Map<String, Object> status(UUID user) {
    String availability = protectionEnabled ? "COMPLETE" : "NOT_ENABLED";
    if (protectionEnabled) {
      try {
        protection.prepare(user);
      } catch (ActionPolicyException failure) {
        if (failure.status() != 503) throw failure;
        availability = "UNAVAILABLE";
      }
    }
    var rows =
        repo.jdbc()
            .queryForList("SELECT * FROM user_admission_restrictions WHERE principal_id=?", user);
    var restriction = rows.isEmpty() ? Map.<String, Object>of() : rows.getFirst();
    var result = new LinkedHashMap<String, Object>();
    result.put("policyVersion", 1);
    result.put("maxConcurrent", 1);
    result.put("policyAvailability", availability);
    result.put("resources", resources(user));
    for (var entry :
        Map.of(
                "holdBlockedUntil",
                "hold_blocked_until",
                "onlineBlockedUntil",
                "online_blocked_until",
                "marketBlockedUntil",
                "market_blocked_until")
            .entrySet()) {
      var value = (Timestamp) restriction.get(entry.getValue());
      result.put(entry.getKey(), value == null ? null : value.toInstant());
    }
    result.put("marketPermanent", Boolean.TRUE.equals(restriction.get("market_permanent")));
    result.put("strikeCount", restriction.getOrDefault("strike_count", 0));
    return result;
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public void reserve(
      UUID user,
      String kind,
      UUID source,
      UUID court,
      UUID facility,
      Instant start,
      Instant end,
      Instant expires,
      String phase) {
    if (!Set.of("ONLINE_BOOKING", "PEER_ACQUISITION").contains(kind))
      throw new IllegalArgumentException("Loại giữ chỗ không hợp lệ.");
    if (protectionEnabled) protection.requireAllowed(user);
    requireAvailable(user);
    if (protectionEnabled) antiRegrab(user, court, start, end);
    repo.jdbc()
        .update(
            """
INSERT INTO user_hold_claims(id,principal_id,kind,source_id,court_id,facility_id,starts_at,ends_at,
  expires_at,phase,state,created_at,updated_at,protection_eligible) VALUES(?,?,?,?,?,?,?,?,?,?,'ACTIVE',?,?,?)
""",
            UUID.randomUUID(),
            user,
            kind,
            source,
            court,
            facility,
            Timestamp.from(start),
            Timestamp.from(end),
            Timestamp.from(expires),
            phase,
            Timestamp.from(clock.instant()),
            Timestamp.from(clock.instant()),
            protectionEnabled);
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public void requireAvailable(UUID user) {
    reconcile(user);
    var occupied = resources(user);
    if (occupied.isEmpty()) return;
    var details = new LinkedHashMap<String, Object>();
    details.put("policyVersion", 1);
    details.put("maxConcurrent", 1);
    details.put("resources", occupied);
    var first = occupied.getFirst();
    if (first.expiresAt() != null && first.expiresAt().isAfter(clock.instant()))
      details.put("retryAt", first.expiresAt());
    throw new ActionPolicyException(
        409,
        "HOLD_QUOTA_EXCEEDED",
        "Bạn đang có một lượt giữ sân hoặc mua chuyển nhượng chưa hoàn tất. Vui lòng hoàn tất hoặc"
            + " hủy trước khi đặt mới.",
        details);
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public void release(UUID hold, String phase) {
    repo.jdbc()
        .update(
            "UPDATE user_hold_claims SET state=?,phase=?,updated_at=? WHERE source_id=? AND"
                + " state='ACTIVE'",
            phase.equals("CONFIRMED") ? "COMPLETED" : "RELEASED",
            phase,
            Timestamp.from(clock.instant()),
            hold);
  }

  private void reconcile(UUID user) {
    repo.jdbc()
        .update(
            """
            UPDATE user_hold_claims c SET state=CASE WHEN l.state='COMPLETED' THEN 'COMPLETED'
              WHEN l.state<>'LOCKED' OR l.expires_at<=? THEN 'RELEASED' ELSE c.state END,
              phase='LEGACY_'||l.state,updated_at=?
            FROM transfer_leases l WHERE c.source_id=l.acquisition_id AND c.principal_id=?
              AND c.kind='LEGACY_ACQUISITION' AND c.state='ACTIVE' AND l.protocol='LEGACY_OWNER'
            """,
            Timestamp.from(clock.instant()),
            Timestamp.from(clock.instant()),
            user);
    repo.jdbc()
        .update(
            """
            UPDATE user_hold_claims c SET state=CASE WHEN r.state='BOOKED' THEN 'COMPLETED'
                WHEN r.state='RELEASED' OR r.expires_at<=? THEN 'RELEASED' ELSE c.state END,
              expires_at=r.expires_at,updated_at=?
            FROM slot_reservations r WHERE c.source_id=r.id AND c.principal_id=?
              AND c.kind='ONLINE_BOOKING' AND c.state='ACTIVE'
            """,
            Timestamp.from(clock.instant()),
            Timestamp.from(clock.instant()),
            user);
    repo.jdbc()
        .update(
            """
UPDATE user_hold_claims c SET state=CASE WHEN a.phase='COMPLETED' THEN 'COMPLETED' ELSE 'RELEASED' END,
  phase=a.phase,updated_at=? FROM peer_usage_attempts a
WHERE c.source_id=a.acquisition_id AND c.principal_id=? AND c.kind='PEER_ACQUISITION'
  AND c.state='ACTIVE' AND (a.phase IN('COMPLETED','EXPIRED','ABORTED_A_RETAINS','TIMED_OUT_A_RETAINS')
    OR (a.phase='WAITING_PAYMENT' AND a.pay_by<=?))
""",
            Timestamp.from(clock.instant()),
            user,
            Timestamp.from(clock.instant()));
  }

  private void antiRegrab(UUID user, UUID court, Instant start, Instant end) {
    var rows =
        repo.jdbc()
            .queryForList(
                """
SELECT e.source_id,e.effective_at+INTERVAL '15 minutes' AS retry_at FROM booking_abandonment_events e
WHERE e.principal_id=? AND e.court_id=? AND e.effective_at>?
 AND tstzrange(e.starts_at,e.ends_at,'[)') && tstzrange(?,?,'[)')
 AND NOT EXISTS(SELECT 1 FROM booking_admission_exemptions x WHERE x.source_id=e.source_id)
ORDER BY e.effective_at DESC,e.source_id LIMIT 1
""",
                user,
                court,
                Timestamp.from(clock.instant().minusSeconds(900)),
                Timestamp.from(start),
                Timestamp.from(end));
    if (rows.isEmpty()) return;
    var row = rows.getFirst();
    throw new ActionPolicyException(
        409,
        "HOLD_REGRAB_COOLDOWN",
        "Bạn vừa bỏ lượt giữ có thời gian giao nhau trên sân này. Vui lòng chọn khoảng khác hoặc"
            + " chờ hết thời gian nghỉ.",
        Map.of(
            "policyVersion",
            1,
            "retryAt",
            ((Timestamp) row.get("retry_at")).toInstant(),
            "sourceId",
            row.get("source_id"),
            "courtId",
            court));
  }

  private List<Resource> resources(UUID user) {
    return repo.jdbc()
        .query(
            """
WITH actor AS (SELECT ?::uuid id,?::timestamptz at), sources AS (
  SELECT 'ONLINE_BOOKING' kind,r.id,r.expires_at FROM slot_reservations r,actor u
  WHERE r.holder_id=u.id AND r.state='HOLD' AND r.expires_at>u.at
    AND COALESCE(r.quote->>'admissionChannel','ONLINE')<>'COUNTER'
    AND NOT EXISTS(SELECT 1 FROM bookings b WHERE b.reservation_id=r.id AND b.source='OFFLINE_COUNTER')
  UNION
  SELECT 'PEER_ACQUISITION',a.acquisition_id,
    CASE WHEN a.phase='WAITING_PAYMENT' THEN a.pay_by ELSE NULL::timestamptz END
  FROM peer_usage_attempts a,actor u WHERE a.buyer_id=u.id
    AND NOT EXISTS(SELECT 1 FROM peer_usage_decisions d WHERE d.acquisition_id=a.acquisition_id)
    AND (a.phase IN('PAYMENT_DECLARED','DISPUTED','HANDOFF_PENDING') OR (a.phase='WAITING_PAYMENT' AND a.pay_by>u.at))
  UNION
  SELECT 'LEGACY_ACQUISITION',l.acquisition_id,l.expires_at FROM transfer_leases l,actor u
  WHERE l.buyer_id=u.id AND l.protocol='LEGACY_OWNER' AND l.state='LOCKED' AND l.expires_at>u.at
  UNION
  SELECT 'CLAIM_REQUIRES_RECONCILIATION',c.source_id,NULL::timestamptz FROM user_hold_claims c,actor u
  WHERE c.principal_id=u.id AND c.state='ACTIVE'
    AND ((c.kind='ONLINE_BOOKING' AND NOT EXISTS(SELECT 1 FROM slot_reservations r WHERE r.id=c.source_id))
      OR (c.kind='PEER_ACQUISITION' AND NOT EXISTS(SELECT 1 FROM peer_usage_attempts a WHERE a.acquisition_id=c.source_id))
      OR (c.kind='LEGACY_ACQUISITION' AND NOT EXISTS(SELECT 1 FROM transfer_leases l WHERE l.acquisition_id=c.source_id AND l.protocol='LEGACY_OWNER')))
) SELECT kind,id,expires_at FROM sources ORDER BY kind,id LIMIT 100
""",
            (r, n) ->
                new Resource(
                    r.getString("kind"),
                    r.getObject("id", UUID.class),
                    r.getTimestamp("expires_at") == null
                        ? null
                        : r.getTimestamp("expires_at").toInstant()),
            user,
            Timestamp.from(clock.instant()));
  }
}
