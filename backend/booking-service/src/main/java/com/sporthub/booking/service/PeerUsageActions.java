package com.sporthub.booking.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.sporthub.booking.repository.BookingRepository;
import com.sporthub.common.exception.*;
import com.sporthub.common.money.*;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.*;
import org.springframework.stereotype.Service;

/** Party actions are called by Transfer after its live actor/evidence checks and signed RPC. */
@Service
public class PeerUsageActions {
  private final BookingRepository repo;
  private final PeerUsageStore store;
  private final PeerUsageCommands commands;
  private final PeerHandoffs handoffs;
  private final Clock clock;

  public PeerUsageActions(
      BookingRepository repo,
      PeerUsageStore store,
      PeerUsageCommands commands,
      PeerHandoffs handoffs,
      Clock clock) {
    this.repo = repo;
    this.store = store;
    this.commands = commands;
    this.handoffs = handoffs;
    this.clock = clock;
  }

  public JsonNode read(UUID id, UUID actor) {
    participant(store.attempt(id), actor);
    return store.view(id);
  }

  public JsonNode declaration(UUID id, UUID actor) {
    participant(store.attempt(id), actor);
    var rows =
        repo.jdbc()
            .query(
                "SELECT bank_reference,evidence_ids,claimed_at FROM peer_usage_evidence_receipts"
                    + " WHERE acquisition_id=?",
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
    if (rows.isEmpty())
      throw new ResourceNotFoundException("Người mua chưa có lời khai thanh toán được tiếp nhận.");
    return repo.read(repo.write(rows.getFirst()));
  }

  public JsonNode declare(
      UUID id,
      PeerResaleContract.Declaration input,
      PeerEvidenceReceipt receipt,
      String key,
      UUID actor) {
    if (receipt == null)
      throw new IllegalArgumentException(
          "Cần biên nhận chứng từ đã sẵn sàng từ dịch vụ chuyển nhượng.");
    receipt.validate();
    var before = store.attempt(id);
    buyer(before, actor);
    return commands.run(
        id,
        actor,
        "PEER_DECLARE",
        key,
        Map.of("input", input, "receipt", receipt),
        (a, fingerprint) -> {
          buyer(a, actor);
          store.expected(a, new PeerResaleContract.Command(input.version(), input.termsHash()));
          var now = clock.instant();
          var ids = input.evidenceIds() == null ? Set.of() : new HashSet<>(input.evidenceIds());
          if (!a.phase().equals("WAITING_PAYMENT")
              || !now.isBefore(a.payBy())
              || !id.equals(receipt.acquisitionId())
              || !actor.equals(receipt.buyerId())
              || !a.termsHash().equals(receipt.termsHash())
              || !receipt.createdAt().isBefore(a.payBy())
              || receipt.createdAt().isAfter(now.plusSeconds(5))
              || !receipt.bankTransactionReference().equals(input.bankTransactionReference().trim())
              || !ids.equals(new HashSet<>(receipt.evidenceIds()))
              || ids.size() != input.evidenceIds().size())
            throw new ConflictException(
                "Chứng từ không khớp phiên mua còn hạn. Không chuyển thêm tiền từ mã QR đã hết"
                    + " hạn.");
          repo.jdbc()
              .update(
                  """
INSERT INTO peer_usage_evidence_receipts(acquisition_id,buyer_id,terms_hash,bank_reference,evidence_ids,fingerprint,claimed_at)
VALUES(?,?,?,?,?,?,?)
""",
                  id,
                  actor,
                  a.termsHash(),
                  receipt.bankTransactionReference(),
                  receipt.evidenceIds().toArray(UUID[]::new),
                  receipt.fingerprint(),
                  Timestamp.from(receipt.createdAt()));
          store.phase(a, "PAYMENT_DECLARED", "BUYER_DECLARED", now);
          repo.jdbc()
              .update(
                  "UPDATE user_hold_claims SET phase='PAYMENT_DECLARED',expires_at=?,updated_at=?"
                      + " WHERE source_id=? AND state='ACTIVE'",
                  Timestamp.from(a.endsAt()),
                  Timestamp.from(now),
                  id);
          repo.history(
              a.booking(),
              actor,
              "PEER_PAYMENT_DECLARED",
              Map.of(
                  "acquisitionId",
                  id,
                  "confirmationMethod",
                  "BUYER_DECLARED",
                  "evidenceReceiptHash",
                  receipt.fingerprint()));
        });
  }

  public JsonNode confirm(UUID id, PeerResaleContract.Command input, String key, UUID actor) {
    seller(store.attempt(id), actor);
    return commands.run(
        id,
        actor,
        "PEER_CONFIRM",
        key,
        input,
        (a, fingerprint) -> {
          seller(a, actor);
          store.expected(a, input);
          if (!a.phase().equals("PAYMENT_DECLARED") || !clock.instant().isBefore(a.startsAt()))
            throw new ConflictException(
                "Cần Owner phân xử phiên đang tranh chấp hoặc đã đến giờ chơi.");
          handoffs.complete(
              a,
              actor,
              "COUNTERPARTY_ACKNOWLEDGED",
              "Người bán xác nhận đã nhận đủ tiền chuyển nhượng.",
              fingerprint);
        });
  }

  public JsonNode cancel(UUID id, PeerResaleContract.Command input, String key, UUID actor) {
    buyer(store.attempt(id), actor);
    return commands.run(
        id,
        actor,
        "PEER_CANCEL",
        key,
        input,
        (a, fingerprint) -> {
          buyer(a, actor);
          store.expected(a, input);
          if (!a.phase().equals("WAITING_PAYMENT"))
            throw new ConflictException(
                "Không hủy giữ chỗ sau khi đã khai chuyển tiền. Hãy xử lý hồ sơ quyền sân.");
          handoffs.retain(
              a,
              "EXPIRED",
              actor,
              "NONE",
              "Người mua hủy trước khi khai chuyển tiền.",
              fingerprint);
        });
  }

  public JsonNode dispute(
      UUID id, PeerResaleContract.Command input, String reason, String key, UUID actor) {
    participant(store.attempt(id), actor);
    if (reason == null || reason.isBlank() || reason.length() > 1000)
      throw new IllegalArgumentException("Cần lý do khiếu nại tối đa 1000 ký tự.");
    return commands.run(
        id,
        actor,
        "PEER_OPEN_DISPUTE",
        key,
        Map.of("input", input, "reason", reason),
        (a, fingerprint) -> {
          participant(a, actor);
          store.expected(a, input);
          if (!a.phase().equals("PAYMENT_DECLARED") || !clock.instant().isBefore(a.endsAt()))
            throw new ConflictException("Phiên này không thể mở thêm tranh chấp quyền sử dụng.");
          store.phase(a, "DISPUTED", "BUYER_DECLARED", clock.instant());
          repo.jdbc()
              .update(
                  """
INSERT INTO peer_money_cases(acquisition_id,facility_id,seller_id,buyer_id,state,reason,created_at,updated_at)
VALUES(?,?,?,?,'OPEN',?,?,?) ON CONFLICT(acquisition_id) DO NOTHING
""",
                  id,
                  a.facility(),
                  a.seller(),
                  a.buyer(),
                  reason.trim(),
                  Timestamp.from(clock.instant()),
                  Timestamp.from(clock.instant()));
          repo.jdbc()
              .update(
                  "UPDATE user_hold_claims SET phase='DISPUTED',expires_at=?,updated_at=? WHERE"
                      + " source_id=? AND state='ACTIVE'",
                  Timestamp.from(a.endsAt()),
                  Timestamp.from(clock.instant()),
                  id);
          repo.history(
              a.booking(),
              actor,
              "PEER_DISPUTE_OPENED",
              Map.of("acquisitionId", id, "reason", reason.trim()));
        });
  }

  private static void participant(PeerUsageStore.Attempt a, UUID actor) {
    if (!a.participant(actor))
      throw new ForbiddenException("Bạn không thuộc phiên chuyển nhượng này.");
  }

  private static void buyer(PeerUsageStore.Attempt a, UUID actor) {
    if (!a.buyer().equals(actor))
      throw new ForbiddenException("Chỉ người mua được khai thanh toán phiên này.");
  }

  private static void seller(PeerUsageStore.Attempt a, UUID actor) {
    if (!a.seller().equals(actor))
      throw new ForbiddenException("Chỉ người bán được xác nhận nhận tiền.");
  }
}
