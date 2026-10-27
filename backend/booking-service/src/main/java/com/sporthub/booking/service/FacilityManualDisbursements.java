package com.sporthub.booking.service;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.databind.JsonNode;
import com.sporthub.booking.repository.BookingRepository;
import com.sporthub.common.exception.*;
import com.sporthub.common.money.FacilityPaymentClient;
import com.sporthub.common.security.RemoteIdentity.Caller;
import com.sporthub.common.security.Signatures;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Preparing is a durable human claim. It never expires and never sends a bank transfer. */
@Service
public class FacilityManualDisbursements {
  public record Prepare(long version) {
    @JsonAnySetter
    public void unknown(String key, JsonNode value) {
      throw new IllegalArgumentException("Trường không được phép: " + key);
    }
  }

  public record Complete(long version, String bankTransactionReference) {
    @JsonAnySetter
    public void unknown(String key, JsonNode value) {
      throw new IllegalArgumentException("Trường không được phép: " + key);
    }
  }

  private final BookingRepository repo;
  private final FacilityFinanceAccess access;
  private final FacilityPaymentClient payment;
  private final VietQrTransfer encoder;
  private final CheckinQr images;
  private final Clock clock;
  private final TransactionTemplate tx;

  public FacilityManualDisbursements(
      BookingRepository repo,
      FacilityFinanceAccess access,
      FacilityPaymentClient payment,
      VietQrTransfer encoder,
      CheckinQr images,
      Clock clock,
      PlatformTransactionManager manager) {
    this.repo = repo;
    this.access = access;
    this.payment = payment;
    this.encoder = encoder;
    this.images = images;
    this.clock = clock;
    tx = new TransactionTemplate(manager);
  }

  public Map<String, Object> prepare(
      UUID id, Prepare input, String key, Caller actor, String token) {
    FacilityFinanceAccess.key(key);
    if (input == null) throw new IllegalArgumentException("Cần phiên bản nghĩa vụ.");
    var before = access.obligation(id, false);
    access.operator(before, actor, token);
    String fingerprint = Signatures.sha256(id + "|" + repo.write(input));
    UUID operation =
        tx.execute(
            status -> {
              repo.lock(actor.id());
              var obligation = access.obligation(id, true);
              var replay = replay(actor, "PREPARE", key, fingerprint);
              if (replay != null) return UUID.fromString(replay.path("operationId").asText());
              var active =
                  repo.jdbc()
                      .queryForList(
                          "SELECT * FROM facility_disbursement_operations WHERE obligation_id=? AND"
                              + " state<>'RELEASED'",
                          id);
              if (!active.isEmpty()) {
                var current = active.getFirst();
                if (!actor.id().equals(current.get("claimed_by")))
                  throw new ConflictException(
                      "Khoản này đang được người khác xử lý. Không chuyển lại.");
                UUID existing = (UUID) current.get("id");
                remember(actor, "PREPARE", key, fingerprint, Map.of("operationId", existing));
                return existing;
              }
              FacilityFinanceAccess.version(obligation, input.version());
              if (!"UNPAID".equals(obligation.get("state"))
                  || obligation.get("destination_revision_id") == null)
                throw new ConflictException(
                    "Khoản phải trả chưa có tài khoản hợp lệ hoặc đang cần đối soát.");
              long code =
                  repo.jdbc()
                      .queryForObject("SELECT nextval('facility_disbursement_codes')", Long.class);
              UUID created = UUID.randomUUID();
              String memo =
                  "SH "
                      + ("SELLER_PAYOUT".equals(obligation.get("kind")) ? "PA " : "HO ")
                      + Long.toString(code, 36).toUpperCase(Locale.ROOT);
              repo.jdbc()
                  .update(
                      "INSERT INTO"
                          + " facility_disbursement_operations(id,obligation_id,claimed_by,destination_revision_id,destination_hash,amount,reference_code,description,state)"
                          + " VALUES(?,?,?,?,?,?,?,?,'PREPARED')",
                      created,
                      id,
                      actor.id(),
                      obligation.get("destination_revision_id"),
                      obligation.get("destination_hash"),
                      obligation.get("amount"),
                      code,
                      memo);
              repo.jdbc()
                  .update(
                      "UPDATE facility_disbursement_obligations SET"
                          + " state='PREPARED',version=version+1 WHERE id=?",
                      id);
              access.audit(
                  id,
                  created,
                  actor,
                  "PREPARED",
                  "Mở hướng dẫn chuyển khoản; chưa xác nhận đã chuyển.",
                  Map.of("reference", memo));
              remember(actor, "PREPARE", key, fingerprint, Map.of("operationId", created));
              return created;
            });
    return read(operation, actor, token);
  }

  public Map<String, Object> read(UUID id, Caller actor, String token) {
    var operation = access.operation(id, false);
    var obligation = access.obligation((UUID) operation.get("obligation_id"), false);
    access.operator(obligation, actor, token);
    if (!actor.id().equals(operation.get("claimed_by")))
      throw new ForbiddenException(
          "Chỉ người đang nhận xử lý được xem QR; Owner cần tiếp nhận trước.");
    var result = summary(operation, obligation);
    if (!"PREPARED".equals(operation.get("state"))) return result;
    var bank =
        payment.destination(
            (UUID) operation.get("destination_revision_id"),
            (UUID) obligation.get("payee_id"),
            (UUID) obligation.get("guest_booking_id"),
            true);
    var destination = bank.path("destination");
    if (!operation.get("destination_hash").equals(destination.path("destinationHash").asText()))
      throw new ConflictException("Phiên bản tài khoản nhận không khớp.");
    var current = access.operation(id, false);
    var latest = access.obligation((UUID) obligation.get("id"), false);
    if (!current.get("version").equals(operation.get("version"))
        || !latest.get("version").equals(obligation.get("version"))
        || !actor.id().equals(current.get("claimed_by")))
      throw new ConflictException("Hướng dẫn đã thay đổi. Không dùng QR cũ.");
    String payload =
        encoder.encode(
            destination.path("bankBin").asText(),
            bank.path("accountNumber").asText(),
            (BigDecimal) operation.get("amount"),
            operation.get("description").toString());
    result.put("bankBin", destination.path("bankBin").asText());
    result.put("bankName", destination.path("bankName").asText());
    result.put("accountNumber", bank.path("accountNumber").asText());
    result.put("accountName", destination.path("accountName").asText());
    result.put("qrPayload", payload);
    result.put("qrImageDataUrl", images.pngDataUrl(payload));
    return result;
  }

  public Map<String, Object> complete(
      UUID id, Complete input, String key, Caller actor, String token) {
    FacilityFinanceAccess.key(key);
    if (input == null
        || input.bankTransactionReference() == null
        || !input.bankTransactionReference().matches("[A-Za-z0-9][A-Za-z0-9 ._:/-]{0,119}"))
      throw new IllegalArgumentException("Nhập mã giao dịch ngân hàng hợp lệ trước khi hoàn tất.");
    var before = access.operation(id, false);
    var source = access.obligation((UUID) before.get("obligation_id"), false);
    access.operator(source, actor, token);
    if ("REVIEW".equals(before.get("state"))) access.owner(source, actor, token);
    String reference = input.bankTransactionReference().trim(),
        normalized = reference.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
    String fingerprint = Signatures.sha256(id + "|" + repo.write(input));
    return tx.execute(
        status -> {
          repo.lock(actor.id());
          var obligation = access.obligation((UUID) before.get("obligation_id"), true);
          var operation = access.operation(id, true);
          if (!actor.id().equals(operation.get("claimed_by")))
            throw new ForbiddenException("Bạn không phải người đang xử lý lệnh này.");
          var old = replay(actor, "COMPLETE", key, fingerprint);
          if (old != null) return jsonMap(old);
          if ("MANUALLY_RECORDED".equals(operation.get("state"))) {
            var receipt =
                repo.jdbc()
                    .queryForMap(
                        "SELECT normalized_reference FROM facility_disbursement_receipts WHERE"
                            + " operation_id=?",
                        id);
            if (!normalized.equals(receipt.get("normalized_reference")))
              throw new ConflictException("Lệnh đã được xác nhận bằng mã giao dịch khác.");
            return summary(operation, obligation);
          }
          FacilityFinanceAccess.version(operation, input.version());
          if (!Set.of("PREPARED", "REVIEW").contains(operation.get("state"))
              || !operation
                  .get("destination_revision_id")
                  .equals(obligation.get("destination_revision_id")))
            throw new ConflictException("Lệnh không còn hiệu lực; cần đối soát giao dịch trước.");
          repo.jdbc()
              .queryForObject(
                  "SELECT pg_advisory_xact_lock(hashtextextended(?,7063))",
                  Object.class,
                  obligation.get("receiver_owner_id") + ":" + normalized);
          boolean duplicate =
              repo.jdbc()
                      .queryForObject(
                          "SELECT count(*) FROM facility_disbursement_receipts WHERE source_scope=?"
                              + " AND normalized_reference=?",
                          Integer.class,
                          obligation.get("receiver_owner_id"),
                          normalized)
                  > 0;
          if (duplicate) {
            repo.jdbc()
                .update(
                    "UPDATE facility_disbursement_operations SET state='REVIEW',version=version+1"
                        + " WHERE id=?",
                    id);
            repo.jdbc()
                .update(
                    "UPDATE facility_disbursement_obligations SET state='REVIEW',version=version+1"
                        + " WHERE id=?",
                    obligation.get("id"));
            access.audit(
                (UUID) obligation.get("id"),
                id,
                actor,
                "DUPLICATE_REFERENCE",
                "Mã giao dịch đã dùng ở khoản khác; kiểm tra ngân hàng trước khi chuyển thêm.",
                Map.of("bankTransactionReference", reference));
          } else {
            repo.jdbc()
                .update(
                    "INSERT INTO"
                        + " facility_disbursement_receipts(id,operation_id,obligation_id,source_scope,bank_reference,normalized_reference,amount,destination_revision_id,destination_hash,confirmation_method,confirmed_at,performed_by)"
                        + " VALUES(?,?,?,?,?,?,?,?,?,'MANUALLY_RECORDED',?,?)",
                    UUID.randomUUID(),
                    id,
                    obligation.get("id"),
                    obligation.get("receiver_owner_id"),
                    reference,
                    normalized,
                    operation.get("amount"),
                    operation.get("destination_revision_id"),
                    operation.get("destination_hash"),
                    Timestamp.from(clock.instant()),
                    actor.id());
            repo.jdbc()
                .update(
                    "UPDATE facility_disbursement_operations SET"
                        + " state='MANUALLY_RECORDED',version=version+1 WHERE id=?",
                    id);
            repo.jdbc()
                .update(
                    "UPDATE facility_disbursement_obligations SET"
                        + " state='MANUALLY_RECORDED',version=version+1 WHERE id=?",
                    obligation.get("id"));
            access.audit(
                (UUID) obligation.get("id"),
                id,
                actor,
                "MANUALLY_RECORDED",
                "Cơ sở xác nhận đã chuyển",
                Map.of("bankTransactionReference", reference));
          }
          var result =
              summary(
                  access.operation(id, false),
                  access.obligation((UUID) obligation.get("id"), false));
          remember(actor, "COMPLETE", key, fingerprint, result);
          return result;
        });
  }

  private Map<String, Object> summary(
      Map<String, Object> operation, Map<String, Object> obligation) {
    var result = new LinkedHashMap<String, Object>();
    for (String field :
        List.of(
            "id",
            "version",
            "claimed_by",
            "destination_revision_id",
            "amount",
            "description",
            "state",
            "created_at"))
      result.put(
          switch (field) {
            case "claimed_by" -> "claimedBy";
            case "destination_revision_id" -> "destinationRevisionId";
            case "created_at" -> "createdAt";
            default -> field;
          },
          operation.get(field));
    result.put("operationId", operation.get("id"));
    result.put("obligationId", obligation.get("id"));
    result.put("obligationVersion", obligation.get("version"));
    result.put("kind", obligation.get("kind"));
    result.put("mode", "REAL");
    result.put("amount", ((BigDecimal) operation.get("amount")).toPlainString());
    result.put(
        "warning",
        "Quét QR chưa xác nhận đã chuyển. Nếu đã chuyển nhưng mất phản hồi, dùng lại mã giao dịch"
            + " cũ; không chuyển lần nữa.");
    var receipts =
        repo.jdbc()
            .queryForList(
                "SELECT id,bank_reference,confirmed_at,confirmation_method FROM"
                    + " facility_disbursement_receipts WHERE operation_id=?",
                operation.get("id"));
    if (!receipts.isEmpty()) {
      var receipt = receipts.getFirst();
      result.put("receiptId", receipt.get("id"));
      result.put("bankTransactionReference", receipt.get("bank_reference"));
      result.put("confirmedAt", receipt.get("confirmed_at"));
      result.put("confirmationMethod", "MANUALLY_RECORDED");
      result.put("confirmationLabel", "Cơ sở xác nhận đã chuyển");
    }
    return result;
  }

  private JsonNode replay(Caller actor, String operation, String key, String fingerprint) {
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
      throw new ConflictException("Mã yêu cầu đã dùng với nội dung khác.");
    return repo.read(rows.getFirst().get("result").toString());
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

  @SuppressWarnings("unchecked")
  private Map<String, Object> jsonMap(JsonNode node) {
    return new com.fasterxml.jackson.databind.ObjectMapper().convertValue(node, Map.class);
  }
}
