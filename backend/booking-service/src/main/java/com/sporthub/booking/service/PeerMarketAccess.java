package com.sporthub.booking.service;

import com.sporthub.booking.repository.BookingRepository;
import com.sporthub.common.exception.*;
import com.sporthub.common.security.RemoteIdentity.Caller;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Rollback disables new writes while readers, acknowledgments and cases remain available. */
@Component
public class PeerMarketAccess {
  private final BookingRepository repo;
  private final Clock clock;
  private final Set<String> facilities;

  public PeerMarketAccess(
      BookingRepository repo,
      Clock clock,
      @Value("${SPORTHUB_PEER_RESALE_FACILITIES:}") String configured) {
    this.repo = repo;
    this.clock = clock;
    facilities = new HashSet<>();
    for (String entry : configured.split(",")) {
      if (entry.isBlank()) continue;
      String value = entry.trim();
      facilities.add(value.equals("*") ? value : UUID.fromString(value).toString());
    }
  }

  public void enabled(UUID facility) {
    if (!facilities.contains("*") && !facilities.contains(facility.toString()))
      throw new ActionPolicyException(
          503,
          "PEER_RESALE_NOT_ENABLED",
          "Chuyển nhượng trực tiếp chưa được bật tại cơ sở này.",
          Map.of());
  }

  public void actor(Caller actor) {
    actor.requireRole("CUSTOMER");
    actor.requireActiveVerified();
  }

  /** Invoked inside the same user fence that commits a fraud restriction. */
  public void allowed(UUID principal) {
    allowed(repo, clock, principal);
  }

  static void allowed(BookingRepository repo, Clock clock, UUID principal) {
    var rows =
        repo.jdbc()
            .queryForList(
                "SELECT market_permanent,market_blocked_until FROM user_admission_restrictions"
                    + " WHERE principal_id=?",
                principal);
    if (rows.isEmpty()) return;
    var row = rows.getFirst();
    Timestamp until = (Timestamp) row.get("market_blocked_until");
    if (Boolean.TRUE.equals(row.get("market_permanent"))
        || until != null && until.toInstant().isAfter(clock.instant()))
      throw new ActionPolicyException(
          403,
          "TRANSFER_MARKET_SUSPENDED",
          Boolean.TRUE.equals(row.get("market_permanent"))
              ? "Tài khoản đã bị cấm tham gia chợ chuyển nhượng."
              : "Tài khoản đang bị tạm ngưng tham gia chợ chuyển nhượng.",
          Boolean.TRUE.equals(row.get("market_permanent"))
              ? Map.of("permanent", true)
              : Map.of("retryAt", until.toInstant(), "permanent", false));
  }
}
