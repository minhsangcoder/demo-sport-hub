package com.sporthub.booking.service;

import com.sporthub.booking.repository.BookingRepository;
import com.sporthub.common.exception.*;
import com.sporthub.common.money.BankProfileContract.*;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The active pointer and the decision are committed in the responsibility authority's database. */
@Service
public class BookingBankProfiles {
  private final BookingRepository repo;
  private final BookingUserFence fence;
  private final BankResponsibilities responsibilities;
  private final Clock clock;
  private final boolean enforced;
  private final PeerUsageTimeouts timeouts;

  public BookingBankProfiles(
      BookingRepository repo,
      BookingUserFence fence,
      BankResponsibilities responsibilities,
      Clock clock,
      @org.springframework.beans.factory.annotation.Value(
              "${SPORTHUB_BANK_PROFILE_ENFORCEMENT:false}")
          boolean enforced,
      PeerUsageTimeouts timeouts) {
    this.repo = repo;
    this.fence = fence;
    this.responsibilities = responsibilities;
    this.clock = clock;
    this.enforced = enforced;
    this.timeouts = timeouts;
  }

  @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
  public void bindCurrent(UUID principal, UUID revision, String destinationHash) {
    if (!enforced
        && !Boolean.TRUE.equals(
            repo.jdbc()
                .queryForObject(
                    "SELECT EXISTS(SELECT 1 FROM user_bank_profiles WHERE principal_id=? AND"
                        + " initialized)",
                    Boolean.class,
                    principal))) return;
    var current = active(principal, revision);
    if (!current.path("destinationHash").asText().equals(destinationHash))
      throw new ConflictException("Thông tin ngân hàng chưa khớp phiên bản đang kích hoạt.");
  }

  @Transactional
  public Status status(UUID principal, boolean existingHistory) {
    Objects.requireNonNull(principal);
    fence.lock(principal);
    settleDue(principal);
    return read(principal, existingHistory);
  }

  @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
  public com.fasterxml.jackson.databind.JsonNode active(UUID principal, UUID requestedRevision) {
    var rows =
        repo.jdbc()
            .queryForList("SELECT * FROM user_bank_profiles WHERE principal_id=?", principal);
    if (rows.isEmpty() || rows.getFirst().get("active_revision_id") == null)
      throw new ActionPolicyException(
          409,
          "BANK_ACCOUNT_REQUIRED",
          "Bạn cần khai và kích hoạt tài khoản ngân hàng cá nhân trước khi đăng chuyển nhượng.",
          Map.of());
    var row = rows.getFirst();
    if (Boolean.TRUE.equals(row.get("reconciliation_required")))
      throw new ActionPolicyException(
          503,
          "BANK_PROFILE_RECONCILIATION_REQUIRED",
          "Hồ sơ ngân hàng cần được đối chiếu trước khi dùng cho giao dịch mới.",
          Map.of());
    if (requestedRevision != null && !requestedRevision.equals(row.get("active_revision_id")))
      throw new ActionPolicyException(
          409,
          "BANK_PROFILE_VERSION_CHANGED",
          "Chỉ sử dụng tài khoản ngân hàng đang được kích hoạt của bạn.",
          Map.of("version", row.get("version")));
    return repo.read(
        repo.write(
            Map.of(
                "id",
                row.get("active_revision_id"),
                "principalId",
                principal,
                "destinationHash",
                row.get("destination_hash"),
                "profileVersion",
                row.get("version"))));
  }

  @Transactional
  public Decision activate(Activation command) {
    validate(command);
    fence.lock(command.principalId());
    settleDue(command.principalId());
    var previous =
        repo.jdbc()
            .queryForList(
                "SELECT * FROM bank_profile_activation_decisions WHERE principal_id=? AND"
                    + " idempotency_key=?",
                command.principalId(),
                command.idempotencyKey());
    if (!previous.isEmpty()) {
      var row = previous.getFirst();
      if (!Objects.equals(row.get("fingerprint"), command.fingerprint())
          || !Objects.equals(row.get("revision_id"), command.revisionId())
          || !Objects.equals(row.get("destination_hash"), command.destinationHash()))
        throw new ConflictException("Mã yêu cầu đã dùng cho thông tin ngân hàng khác.");
      return decision(row);
    }
    Status current = read(command.principalId(), command.existingHistory());
    if (current.version() != command.expectedVersion())
      throw new ActionPolicyException(
          409,
          "BANK_PROFILE_VERSION_CHANGED",
          "Tài khoản ngân hàng đã thay đổi. Vui lòng tải lại.",
          Map.of("version", current.version()));
    if (!current.canRegisterFirst() && !current.canChange())
      throw new ActionPolicyException(
          409,
          "BANK_PROFILE_LOCKED",
          "Bạn còn lịch sân hoặc nghĩa vụ chưa hoàn tất nên chưa thể đổi tài khoản ngân hàng.",
          Map.of("responsibilities", current.responsibilities()));
    long version = current.version() + 1;
    var now = clock.instant().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
    repo.jdbc()
        .update(
            """
INSERT INTO user_bank_profiles(principal_id,active_revision_id,destination_hash,initialized,version,updated_at)
VALUES(?,?,?,TRUE,?,?) ON CONFLICT(principal_id) DO UPDATE SET
active_revision_id=EXCLUDED.active_revision_id,destination_hash=EXCLUDED.destination_hash,
initialized=TRUE,version=EXCLUDED.version,updated_at=EXCLUDED.updated_at
""",
            command.principalId(),
            command.revisionId(),
            command.destinationHash(),
            version,
            Timestamp.from(now));
    repo.jdbc()
        .update(
            """
INSERT INTO bank_profile_activation_decisions(principal_id,idempotency_key,fingerprint,revision_id,
  destination_hash,version,decision,decided_at) VALUES(?,?,?,?,?,?,'ACTIVATED',?)
""",
            command.principalId(),
            command.idempotencyKey(),
            command.fingerprint(),
            command.revisionId(),
            command.destinationHash(),
            version,
            Timestamp.from(now));
    return new Decision(
        command.principalId(),
        command.revisionId(),
        command.destinationHash(),
        version,
        "ACTIVATED",
        now);
  }

  private void settleDue(UUID principal) {
    try {
      var bookings =
          repo.jdbc()
              .queryForList(
                  "SELECT booking_id,court_id FROM peer_usage_attempts WHERE (seller_id=? OR"
                      + " buyer_id=?) AND ((phase='WAITING_PAYMENT' AND pay_by<=?) OR (phase"
                      + " IN('PAYMENT_DECLARED','DISPUTED') AND starts_at<=?))",
                  principal,
                  principal,
                  Timestamp.from(clock.instant()),
                  Timestamp.from(clock.instant()));
      bookings.stream()
          .map(row -> (UUID) row.get("court_id"))
          .distinct()
          .sorted(Comparator.comparing(UUID::toString))
          .forEach(repo::lock);
      // These operations only close due responsibilities; they acquire no other user fence.
      bookings.stream()
          .map(row -> (UUID) row.get("booking_id"))
          .distinct()
          .forEach(timeouts::settleBookingUnderCourt);
    } catch (org.springframework.dao.DataAccessException ex) {
      throw new ActionPolicyException(
          503,
          "BANK_PROFILE_COVERAGE_UNAVAILABLE",
          "Chưa kiểm tra được đầy đủ hồ sơ quyền sân. Vui lòng thử lại.",
          Map.of());
    }
  }

  private Status read(UUID principal, boolean existingHistory) {
    var rows =
        repo.jdbc()
            .queryForList("SELECT * FROM user_bank_profiles WHERE principal_id=?", principal);
    var row = rows.isEmpty() ? Map.<String, Object>of() : rows.getFirst();
    boolean initialized = Boolean.TRUE.equals(row.get("initialized"));
    if (Boolean.TRUE.equals(row.get("reconciliation_required")) || existingHistory && !initialized)
      throw new ActionPolicyException(
          503,
          "BANK_PROFILE_RECONCILIATION_REQUIRED",
          "Hồ sơ ngân hàng cũ cần được đối chiếu trước khi thay đổi. Thông tin giao dịch cũ vẫn"
              + " được giữ nguyên.",
          Map.of());
    var sources = responsibilities.current(principal, clock.instant());
    return new Status(
        principal,
        (UUID) row.get("active_revision_id"),
        (String) row.get("destination_hash"),
        row.get("version") instanceof Number n ? n.longValue() : 0,
        initialized,
        !initialized,
        initialized && sources.isEmpty(),
        sources);
  }

  private Decision decision(Map<String, Object> row) {
    return new Decision(
        (UUID) row.get("principal_id"),
        (UUID) row.get("revision_id"),
        row.get("destination_hash").toString(),
        ((Number) row.get("version")).longValue(),
        row.get("decision").toString(),
        ((Timestamp) row.get("decided_at")).toInstant());
  }

  private void validate(Activation c) {
    if (c == null
        || c.principalId() == null
        || c.revisionId() == null
        || c.expectedVersion() < 0
        || c.destinationHash() == null
        || !c.destinationHash().matches("[a-f0-9]{64}")
        || c.fingerprint() == null
        || !c.fingerprint().matches("[a-f0-9]{64}")
        || c.idempotencyKey() == null
        || !c.idempotencyKey().matches("[A-Za-z0-9_.:-]{1,100}"))
      throw new IllegalArgumentException("Yêu cầu kích hoạt tài khoản ngân hàng không hợp lệ.");
  }
}
