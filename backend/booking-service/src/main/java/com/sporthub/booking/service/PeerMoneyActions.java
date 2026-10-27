package com.sporthub.booking.service;

import static com.sporthub.common.money.PeerMoneyContract.proof;

import com.fasterxml.jackson.databind.*;
import com.sporthub.booking.repository.BookingRepository;
import com.sporthub.common.event.*;
import com.sporthub.common.exception.*;
import com.sporthub.common.money.PeerMoneyContract.*;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.*;
import org.springframework.stereotype.Service;

/** A/B money history never changes usage, native court receipts or facility disbursements. */
@Service
public class PeerMoneyActions {
  private final BookingRepository repo;
  private final PeerUsageStore store;
  private final PeerUsageCommands commands;
  private final PeerUsageTimeouts timeouts;
  private final ObjectMapper json;
  private final Clock clock;
  private final PeerMoneyReads reads;

  public PeerMoneyActions(
      BookingRepository repo,
      PeerUsageStore store,
      PeerUsageCommands commands,
      PeerUsageTimeouts timeouts,
      ObjectMapper json,
      Clock clock,
      PeerMoneyReads reads) {
    this.repo = repo;
    this.store = store;
    this.commands = commands;
    this.timeouts = timeouts;
    this.json = json;
    this.clock = clock;
    this.reads = reads;
  }

  public JsonNode report(UUID id, Report input, String key, UUID actor) {
    participant(store.attempt(id), actor);
    proof(input.termsHash(), input.bankTransactionReference(), input.evidenceIds());
    return commands.run(
        id,
        actor,
        "PEER_MONEY_REPORT",
        key,
        input,
        (a, fingerprint) -> {
          participant(a, actor);
          terms(a, input.termsHash());
          if (a.phase().equals("WAITING_PAYMENT") && clock.instant().isBefore(a.payBy()))
            throw new ConflictException(
                "Phiên còn hạn; người mua cần dùng thao tác khai đã chuyển tiền.");
          timeouts.settleDue(a, fingerprint);
          openCase(
              a, "Báo vấn đề tiền A–B; không thay đổi quyền sử dụng hoặc yêu cầu cơ sở chi tiền.");
          repo.jdbc()
              .update(
                  "INSERT INTO"
                      + " peer_late_payment_reports(id,acquisition_id,reporter_id,terms_hash,bank_reference,evidence_ids,fingerprint,reported_at)"
                      + " VALUES(?,?,?,?,?,?,?,?)",
                  UUID.randomUUID(),
                  id,
                  actor,
                  a.termsHash(),
                  input.bankTransactionReference().trim(),
                  input.evidenceIds().toArray(UUID[]::new),
                  fingerprint,
                  Timestamp.from(clock.instant()));
          changed(a, actor, "PEER_MONEY_REPORTED");
        });
  }

  public JsonNode declareReturn(UUID id, ReturnCommand command, String key, UUID actor) {
    var before = store.attempt(id);
    if (!before.seller().equals(actor))
      throw new ForbiddenException("Chỉ người bán được khai đã trả lại tiền cho người mua.");
    if (command == null || command.returnId() == null || command.input() == null)
      throw new IllegalArgumentException("Thiếu khoản trả lại.");
    var input = command.input();
    proof(input.termsHash(), input.bankTransactionReference(), input.evidenceIds());
    if (input.amount() == null
        || input.amount().signum() <= 0
        || input.amount().stripTrailingZeros().scale() > 0)
      throw new IllegalArgumentException("Số tiền trả lại phải là số đồng nguyên dương.");
    return commands.run(
        id,
        actor,
        "PEER_RETURN_DECLARE",
        key,
        command,
        (a, fingerprint) -> {
          terms(a, input.termsHash());
          requireCase(id);
          var previous =
              repo.jdbc()
                  .queryForList(
                      "SELECT return_id FROM peer_return_obligations WHERE acquisition_id=? AND"
                          + " upper(trim(bank_reference))=upper(trim(?))",
                      UUID.class,
                      id,
                      input.bankTransactionReference());
          if (!previous.isEmpty())
            throw new ActionPolicyException(
                409,
                "PEER_RETURN_ALREADY_REPORTED",
                "Mã giao dịch trả lại đã được khai trong phiên này. Vui lòng tải lại để xem khoản"
                    + " cũ.",
                Map.of("returnId", previous.getFirst()));
          if (Boolean.TRUE.equals(
              repo.jdbc()
                  .queryForObject(
                      "SELECT count(*)=2 FROM peer_money_no_claims WHERE acquisition_id=?",
                      Boolean.class,
                      id)))
            throw new ConflictException("Hai bên đã chốt hồ sơ tiền của phiên này.");
          BigDecimal declared =
              repo.jdbc()
                  .queryForObject(
                      "SELECT COALESCE(sum(amount),0) FROM peer_return_obligations WHERE"
                          + " acquisition_id=?",
                      BigDecimal.class,
                      id);
          if (input.amount().add(declared).compareTo(a.amount()) > 0)
            throw new ConflictException("Tổng khoản trả lại không được vượt giá mua đã chấp nhận.");
          repo.jdbc()
              .update(
                  "INSERT INTO"
                      + " peer_return_obligations(return_id,acquisition_id,seller_id,buyer_id,amount,terms_hash,bank_reference,evidence_ids,state,created_at,updated_at)"
                      + " VALUES(?,?,?,?,?,?,?,?,'AWAITING_BUYER',?,?)",
                  command.returnId(),
                  id,
                  a.seller(),
                  a.buyer(),
                  input.amount(),
                  a.termsHash(),
                  input.bankTransactionReference().trim(),
                  input.evidenceIds().toArray(UUID[]::new),
                  Timestamp.from(clock.instant()),
                  Timestamp.from(clock.instant()));
          changed(a, actor, "PEER_RETURN_DECLARED");
        });
  }

  public JsonNode acknowledge(UUID id, Acknowledge input, String key, UUID actor) {
    var before = store.attempt(id);
    if (!before.buyer().equals(actor))
      throw new ForbiddenException("Chỉ người mua được xác nhận đã nhận lại tiền.");
    if (input == null || input.returnId() == null || input.version() < 0)
      throw new IllegalArgumentException("Thiếu khoản trả lại hoặc phiên bản hợp lệ.");
    return commands.run(
        id,
        actor,
        "PEER_RETURN_ACK",
        key,
        input,
        (a, fingerprint) -> {
          terms(a, input.termsHash());
          int updated =
              repo.jdbc()
                  .update(
                      "UPDATE peer_return_obligations SET"
                          + " state='BUYER_ACKNOWLEDGED',acknowledged_by=?,finalized_at=?,version=version+1,updated_at=?"
                          + " WHERE return_id=? AND acquisition_id=? AND state='AWAITING_BUYER' AND"
                          + " version=?",
                      actor,
                      Timestamp.from(clock.instant()),
                      Timestamp.from(clock.instant()),
                      input.returnId(),
                      id,
                      input.version());
          if (updated != 1)
            throw new ConflictException(
                "Khoản trả lại đã thay đổi hoặc không thuộc phiên mua này.");
          BigDecimal received =
              repo.jdbc()
                  .queryForObject(
                      "SELECT COALESCE(sum(amount),0) FROM peer_return_obligations WHERE"
                          + " acquisition_id=? AND state='BUYER_ACKNOWLEDGED'",
                      BigDecimal.class,
                      id);
          if (received.compareTo(a.amount()) >= 0) close(id, "CLOSED_RETURNED");
          changed(a, actor, "PEER_RETURN_ACKNOWLEDGED");
        });
  }

  public JsonNode noClaim(UUID id, NoClaim input, String key, UUID actor) {
    participant(store.attempt(id), actor);
    if (input == null) throw new IllegalArgumentException("Cần xác nhận điều khoản phiên mua.");
    return commands.run(
        id,
        actor,
        "PEER_MONEY_NO_CLAIM",
        key,
        input,
        (a, fingerprint) -> {
          participant(a, actor);
          terms(a, input.termsHash());
          requireCase(id);
          repo.jdbc()
              .update(
                  "INSERT INTO peer_money_no_claims(acquisition_id,actor_id,fingerprint,created_at)"
                      + " VALUES(?,?,?,?) ON CONFLICT DO NOTHING",
                  id,
                  actor,
                  fingerprint,
                  Timestamp.from(clock.instant()));
          if (Boolean.TRUE.equals(
              repo.jdbc()
                  .queryForObject(
                      "SELECT count(*)=2 FROM peer_money_no_claims WHERE acquisition_id=?",
                      Boolean.class,
                      id))) {
            repo.jdbc()
                .update(
                    "UPDATE peer_return_obligations SET"
                        + " state='WAIVED_BY_BOTH',finalized_at=?,version=version+1,updated_at=?"
                        + " WHERE acquisition_id=? AND state='AWAITING_BUYER'",
                    Timestamp.from(clock.instant()),
                    Timestamp.from(clock.instant()),
                    id);
            close(id, "CLOSED_MUTUAL");
          }
          changed(a, actor, "PEER_MONEY_NO_CLAIM");
        });
  }

  public JsonNode read(UUID id, UUID actor) {
    return reads.read(id, actor);
  }

  public JsonNode read(UUID id, UUID actor, UUID afterReturn, int size) {
    return reads.read(id, actor, afterReturn, size);
  }

  private void requireCase(UUID id) {
    if (!Boolean.TRUE.equals(
        repo.jdbc()
            .queryForObject(
                "SELECT EXISTS(SELECT 1 FROM peer_money_cases WHERE acquisition_id=?)",
                Boolean.class,
                id)))
      throw new ConflictException(
          "Cần có hồ sơ vấn đề tiền của hai bên trước khi ghi trả lại hoặc chốt yêu cầu.");
  }

  private void openCase(PeerUsageStore.Attempt a, String reason) {
    repo.jdbc()
        .update(
            "INSERT INTO"
                + " peer_money_cases(acquisition_id,facility_id,seller_id,buyer_id,state,reason,created_at,updated_at)"
                + " VALUES(?,?,?,?,'OPEN',?,?,?) ON CONFLICT DO NOTHING",
            a.id(),
            a.facility(),
            a.seller(),
            a.buyer(),
            reason,
            Timestamp.from(clock.instant()),
            Timestamp.from(clock.instant()));
  }

  private void close(UUID id, String state) {
    repo.jdbc()
        .update(
            "UPDATE peer_money_cases SET state=?,version=version+1,updated_at=? WHERE"
                + " acquisition_id=?",
            state,
            Timestamp.from(clock.instant()),
            id);
  }

  private void changed(PeerUsageStore.Attempt a, UUID actor, String action) {
    repo.history(
        a.booking(),
        actor,
        action,
        Map.of("acquisitionId", a.id(), "moneyAuthority", "COUNTERPARTIES_ONLY"));
    ReliableOutbox.enqueue(
        repo.jdbc(),
        json,
        RabbitMQConfig.EXCHANGE_BOOKING,
        DomainEvent.create(
            "peer.money.changed", 1, "booking-service", a.id(), read(a.id(), actor)));
  }

  private static void participant(PeerUsageStore.Attempt a, UUID actor) {
    if (!a.participant(actor))
      throw new ForbiddenException("Chỉ A/B của phiên mua được xác nhận tiền của mình.");
  }

  private static void terms(PeerUsageStore.Attempt a, String hash) {
    if (hash == null || !a.termsHash().equals(hash))
      throw new ConflictException("Điều khoản phiên mua không khớp.");
  }
}
