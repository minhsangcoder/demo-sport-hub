package com.sporthub.booking.service;

import com.sporthub.booking.repository.BookingRepository;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

/** Commits due source facts before any Redis projection can observe them. */
@Service
public class HoldPolicyLedger {
  record Snapshot(UUID revision, List<HoldPenaltyPolicy.Fact> facts) {}

  private final BookingRepository repo;
  private final Clock clock;
  private final ObjectProvider<BookingHolds> holds;
  private final PeerUsageTimeouts peers;

  public HoldPolicyLedger(
      BookingRepository repo,
      Clock clock,
      ObjectProvider<BookingHolds> holds,
      PeerUsageTimeouts peers) {
    this.repo = repo;
    this.clock = clock;
    this.holds = holds;
    this.peers = peers;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public Snapshot prepare(UUID user) {
    repo.lock(user);
    var courts =
        repo.jdbc()
            .queryForList(
                """
SELECT court_id FROM user_hold_claims WHERE principal_id=? AND state='ACTIVE'
UNION SELECT court_id FROM slot_reservations WHERE holder_id=? AND state='HOLD'
""",
                UUID.class,
                user,
                user);
    var ordered = courts.stream().sorted(Comparator.comparing(UUID::toString)).toList();
    ordered.forEach(repo::lock);
    for (var court : ordered) holds.getObject().expireCourt(court);
    var bookings =
        repo.jdbc()
            .queryForList(
                """
SELECT DISTINCT a.booking_id FROM peer_usage_attempts a JOIN user_hold_claims c ON c.source_id=a.acquisition_id
WHERE c.principal_id=? AND c.state='ACTIVE'
""",
                UUID.class,
                user);
    bookings.forEach(peers::settleBookingUnderCourt);
    var now = clock.instant();
    repo.jdbc()
        .update(
            """
INSERT INTO booking_admission_versions(principal_id,revision,updated_at) VALUES(?,?,?)
ON CONFLICT(principal_id) DO NOTHING
""",
            user,
            UUID.randomUUID(),
            Timestamp.from(now));
    var revision =
        repo.jdbc()
            .queryForObject(
                "SELECT revision FROM booking_admission_versions WHERE principal_id=?",
                UUID.class,
                user);
    var facts =
        repo.jdbc()
            .query(
                """
WITH facts AS (
 SELECT source_id,'ABANDONED' outcome,effective_at,position FROM booking_abandonment_events WHERE principal_id=? AND effective_at>=?
 UNION ALL SELECT source_id,'SUCCEEDED',effective_at,position FROM booking_admission_successes WHERE principal_id=? AND effective_at>=?
) SELECT * FROM facts f WHERE NOT EXISTS(SELECT 1 FROM booking_admission_exemptions x WHERE x.source_id=f.source_id)
ORDER BY effective_at,position LIMIT 10001
""",
                (r, n) ->
                    new HoldPenaltyPolicy.Fact(
                        r.getObject("source_id", UUID.class),
                        HoldPenaltyPolicy.Outcome.valueOf(r.getString("outcome")),
                        r.getTimestamp("effective_at").toInstant(),
                        r.getLong("position")),
                user,
                Timestamp.from(now.minusSeconds(172800)),
                user,
                Timestamp.from(now.minusSeconds(172800)));
    if (facts.size() > 10000)
      throw new IllegalStateException("Admission history needs reconciliation");
    var policy = HoldPenaltyPolicy.calculate(facts, now);
    repo.jdbc()
        .update(
            """
INSERT INTO user_admission_restrictions(principal_id,hold_blocked_until,online_blocked_until,policy_version,updated_at)
VALUES(?,?,?,1,?) ON CONFLICT(principal_id) DO UPDATE SET hold_blocked_until=excluded.hold_blocked_until,
online_blocked_until=excluded.online_blocked_until,policy_version=GREATEST(user_admission_restrictions.policy_version,1),updated_at=excluded.updated_at
""",
            user,
            stamp(policy.holdBlockedUntil()),
            stamp(policy.onlineBlockedUntil()),
            Timestamp.from(now));
    return new Snapshot(revision, List.copyOf(facts));
  }

  private static Timestamp stamp(Instant at) {
    return at == null ? null : Timestamp.from(at);
  }
}
