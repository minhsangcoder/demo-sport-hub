package com.sporthub.booking.service;

import com.sporthub.booking.repository.BookingRepository;
import com.sporthub.common.exception.ActionPolicyException;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class HoldProtection {
  private final boolean enabled;
  private final HoldPolicyLedger ledger;
  private final HoldPolicyIndex index;
  private final BookingRepository repo;
  private final Clock clock;

  public HoldProtection(
      @Value("${SPORTHUB_HOLD_PROTECTION_ENABLED:false}") boolean enabled,
      HoldPolicyLedger ledger,
      HoldPolicyIndex index,
      BookingRepository repo,
      Clock clock) {
    this.enabled = enabled;
    this.ledger = ledger;
    this.index = index;
    this.repo = repo;
    this.clock = clock;
  }

  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  public void prepare(UUID user) {
    if (!enabled) return;
    try {
      index.publish(user, ledger.prepare(user));
    } catch (RuntimeException failure) {
      throw unavailable();
    }
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public void requireAllowed(UUID user) {
    if (!enabled) return;
    Instant at = clock.instant();
    Map<String, Object> row;
    try {
      row =
          repo.jdbc()
              .queryForMap(
                  """
SELECT v.revision,r.hold_blocked_until,r.online_blocked_until FROM booking_admission_versions v
JOIN user_admission_restrictions r ON r.principal_id=v.principal_id WHERE v.principal_id=?
""",
                  user);
      if (!index.current(user, (UUID) row.get("revision"))) throw unavailable();
      boolean due =
          Boolean.TRUE.equals(
              repo.jdbc()
                  .queryForObject(
                      """
SELECT EXISTS(SELECT 1 FROM user_hold_claims c WHERE c.principal_id=? AND c.protection_eligible
 AND NOT c.grandfather AND c.state='ACTIVE' AND c.expires_at<=? AND
 (c.kind='ONLINE_BOOKING' OR (c.kind='PEER_ACQUISITION' AND c.phase='WAITING_PAYMENT')))
""",
                      Boolean.class,
                      user,
                      Timestamp.from(at)));
      if (due) throw unavailable();
    } catch (ActionPolicyException failure) {
      throw failure;
    } catch (RuntimeException failure) {
      throw unavailable();
    }
    Instant online = instant(row.get("online_blocked_until")),
        hold = instant(row.get("hold_blocked_until"));
    if (online != null && online.isAfter(at))
      reject(
          "ONLINE_BOOKING_SUSPENDED",
          online,
          "Bạn đã bỏ hoặc để hết hạn năm lượt giữ trong ngày. Vui lòng quay lại sau thời gian"
              + " nghỉ.");
    if (hold != null && hold.isAfter(at))
      reject(
          "HOLD_COOLDOWN",
          hold,
          "Bạn đã bỏ hoặc để hết hạn ba lượt giữ liên tiếp trong một giờ. Vui lòng nghỉ trước khi"
              + " giữ sân mới.");
  }

  private static Instant instant(Object value) {
    return value == null ? null : ((Timestamp) value).toInstant();
  }

  private static void reject(String code, Instant until, String message) {
    throw new ActionPolicyException(
        409, code, message, Map.of("policyVersion", 1, "retryAt", until));
  }

  private ActionPolicyException unavailable() {
    return new ActionPolicyException(
        503,
        "HOLD_POLICY_UNAVAILABLE",
        "Chưa thể đối chiếu lịch sử giữ chỗ. Vui lòng thử lại; giao dịch và lịch đã có vẫn được"
            + " phục vụ.",
        Map.of("policyVersion", 1, "retryAt", clock.instant().plusSeconds(5)));
  }
}
