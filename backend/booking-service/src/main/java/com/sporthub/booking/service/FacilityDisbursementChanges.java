package com.sporthub.booking.service;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.databind.JsonNode;
import com.sporthub.booking.repository.BookingRepository;
import com.sporthub.common.exception.*;
import com.sporthub.common.money.FacilityPaymentClient;
import com.sporthub.common.security.RemoteIdentity.Caller;
import com.sporthub.common.security.Signatures;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class FacilityDisbursementChanges {
  public record Destination(
      long version, UUID destinationRevisionId, String consentReference, String reason) {
    @JsonAnySetter
    public void unknown(String key, JsonNode value) {
      throw new IllegalArgumentException("Trường không được phép: " + key);
    }
  }

  public record Review(
      long version, String action, String reason, boolean confirmedNotTransferred) {
    @JsonAnySetter
    public void unknown(String key, JsonNode value) {
      throw new IllegalArgumentException("Trường không được phép: " + key);
    }
  }

  private final BookingRepository repo;
  private final FacilityFinanceAccess access;
  private final FacilityPaymentClient payment;
  private final TransactionTemplate tx;
  private final BookingBankProfiles banks;

  public FacilityDisbursementChanges(
      BookingRepository repo,
      FacilityFinanceAccess access,
      FacilityPaymentClient payment,
      PlatformTransactionManager manager,
      BookingBankProfiles banks) {
    this.repo = repo;
    this.access = access;
    this.payment = payment;
    this.banks = banks;
    tx = new TransactionTemplate(manager);
  }

  public Map<String, Object> destination(
      UUID id, Destination input, String key, Caller actor, String token) {
    FacilityFinanceAccess.key(key);
    if (input == null
        || input.destinationRevisionId() == null
        || input.consentReference() == null
        || input.consentReference().isBlank()
        || input.consentReference().length() > 200)
      throw new IllegalArgumentException("Cần tài khoản nhận và xác nhận đồng ý.");
    reason(input.reason());
    var before = access.obligation(id, false);
    access.recipient(before, actor, token);
    var bank =
        payment.destination(
            input.destinationRevisionId(),
            (UUID) before.get("payee_id"),
            (UUID) before.get("guest_booking_id"),
            false);
    String fingerprint = Signatures.sha256(id + "|" + repo.write(input));
    return tx.execute(
        status -> {
          repo.lock(actor.id());
          var obligation = access.obligation(id, true);
          var old = replay(actor, "DESTINATION", key, fingerprint);
          if (old != null) return old;
          FacilityFinanceAccess.version(obligation, input.version());
          if (!Set.of("AWAITING_BANK_DETAILS", "UNPAID").contains(obligation.get("state"))
              || repo.jdbc()
                      .queryForObject(
                          "SELECT count(*) FROM facility_disbursement_operations WHERE"
                              + " obligation_id=? AND state<>'RELEASED'",
                          Integer.class,
                          id)
                  > 0)
            throw new ConflictException(
                "Đã có lệnh chuyển khoản. Owner phải đối soát và xác nhận chưa chuyển trước khi đổi"
                    + " tài khoản.");
          if (!input.destinationRevisionId().toString().equals(bank.path("id").asText())
              || !bank.path("destinationHash").asText().matches("[a-f0-9]{64}"))
            throw new ConflictException("Không xác minh được phiên bản tài khoản nhận.");
          if (obligation.get("payee_id") != null)
            banks.bindCurrent(
                (UUID) obligation.get("payee_id"),
                input.destinationRevisionId(),
                bank.path("destinationHash").asText());
          repo.jdbc()
              .update(
                  "UPDATE facility_disbursement_obligations SET"
                      + " destination_revision_id=?,destination_hash=?,state='UNPAID',version=version+1"
                      + " WHERE id=?",
                  input.destinationRevisionId(),
                  bank.path("destinationHash").asText(),
                  id);
          access.audit(
              id,
              null,
              actor,
              "DESTINATION_CHANGED",
              input.reason(),
              Map.of(
                  "previousDestinationRevisionId",
                  Objects.toString(obligation.get("destination_revision_id"), ""),
                  "destinationRevisionId",
                  input.destinationRevisionId(),
                  "consentReference",
                  input.consentReference()));
          var result =
              Map.<String, Object>of(
                  "id",
                  id,
                  "state",
                  "UNPAID",
                  "version",
                  input.version() + 1,
                  "destinationRevisionId",
                  input.destinationRevisionId());
          remember(actor, "DESTINATION", key, fingerprint, result);
          return result;
        });
  }

  public Map<String, Object> review(UUID id, Review input, String key, Caller actor, String token) {
    FacilityFinanceAccess.key(key);
    if (input == null
        || !Set.of("REVIEW", "TAKE_OVER", "RELEASE").contains(Objects.toString(input.action(), "")))
      throw new IllegalArgumentException("Chọn thao tác đối soát hợp lệ.");
    reason(input.reason());
    var before = access.operation(id, false);
    var source = access.obligation((UUID) before.get("obligation_id"), false);
    access.operator(source, actor, token);
    if (!input.action().equals("REVIEW")) access.owner(source, actor, token);
    String fingerprint = Signatures.sha256(id + "|" + repo.write(input));
    return tx.execute(
        status -> {
          repo.lock(actor.id());
          var obligation = access.obligation((UUID) before.get("obligation_id"), true);
          var operation = access.operation(id, true);
          var old = replay(actor, "REVIEW", key, fingerprint);
          if (old != null) return old;
          FacilityFinanceAccess.version(operation, input.version());
          if (!Set.of("PREPARED", "REVIEW").contains(operation.get("state")))
            throw new ConflictException("Lệnh đã kết thúc và không được sửa.");
          if (input.action().equals("REVIEW") && !actor.id().equals(operation.get("claimed_by")))
            throw new ForbiddenException("Chỉ người đang nhận xử lý được báo chưa rõ giao dịch.");
          boolean release = input.action().equals("RELEASE");
          if (release && !input.confirmedNotTransferred())
            throw new ConflictException(
                "Chỉ giải phóng khi đã kiểm tra chắc chắn chưa chuyển tiền. QR cũ ngoài hệ thống"
                    + " không thể thu hồi.");
          repo.jdbc()
              .update(
                  "UPDATE facility_disbursement_operations SET"
                      + " state=?,claimed_by=?,version=version+1 WHERE id=?",
                  release ? "RELEASED" : "REVIEW",
                  input.action().equals("TAKE_OVER") ? actor.id() : operation.get("claimed_by"),
                  id);
          repo.jdbc()
              .update(
                  "UPDATE facility_disbursement_obligations SET state=?,version=version+1 WHERE"
                      + " id=?",
                  release ? "UNPAID" : "REVIEW",
                  obligation.get("id"));
          access.audit(
              (UUID) obligation.get("id"),
              id,
              actor,
              input.action(),
              input.reason(),
              Map.of(
                  "confirmedNotTransferred",
                  input.confirmedNotTransferred(),
                  "previousClaimedBy",
                  operation.get("claimed_by")));
          var result =
              Map.<String, Object>of(
                  "operationId",
                  id,
                  "obligationId",
                  obligation.get("id"),
                  "state",
                  release ? "RELEASED" : "REVIEW",
                  "version",
                  input.version() + 1,
                  "warning",
                  release
                      ? "Không dùng lại QR cũ; lệnh mới cần được mở lại."
                      : "Kiểm tra giao dịch đã chuyển và nhập mã cũ; không chuyển lần nữa.");
          remember(actor, "REVIEW", key, fingerprint, result);
          return result;
        });
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> replay(
      Caller actor, String operation, String key, String fingerprint) {
    var rows =
        repo.jdbc()
            .queryForList(
                "SELECT fingerprint,result FROM facility_finance_commands WHERE actor_id=? AND"
                    + " operation=? AND idempotency_key=?",
                actor.id(),
                operation,
                key);
    if (rows.isEmpty()) return null;
    if (!fingerprint.equals(rows.getFirst().get("fingerprint")))
      throw new ConflictException("Mã yêu cầu đã dùng cho nội dung khác.");
    return new com.fasterxml.jackson.databind.ObjectMapper()
        .convertValue(repo.read(rows.getFirst().get("result").toString()), Map.class);
  }

  private void remember(
      Caller actor, String operation, String key, String fingerprint, Object result) {
    repo.jdbc()
        .update(
            "INSERT INTO"
                + " facility_finance_commands(actor_id,operation,idempotency_key,fingerprint,result)"
                + " VALUES(?,?,?,?,?::jsonb)",
            actor.id(),
            operation,
            key,
            fingerprint,
            repo.write(result));
  }

  private void reason(String value) {
    if (value == null || value.isBlank() || value.length() > 500)
      throw new IllegalArgumentException("Cần lý do từ 1–500 ký tự.");
  }
}
