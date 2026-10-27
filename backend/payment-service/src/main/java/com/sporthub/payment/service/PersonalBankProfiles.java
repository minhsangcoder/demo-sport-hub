package com.sporthub.payment.service;

import com.fasterxml.jackson.databind.*;
import com.sporthub.common.exception.*;
import com.sporthub.common.money.BankProfileContract.*;
import com.sporthub.common.security.RemoteIdentity.Caller;
import com.sporthub.payment.repository.PaymentRepository;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Stage locally, decide in Booking, then project. Unknown outcomes remain staged and replayable.
 */
@Service
public class PersonalBankProfiles {
  private final PaymentRepository repo;
  private final BankDestinations destinations;
  private final BankProfileClient authority;
  private final ObjectMapper json;
  private final TransactionTemplate tx;
  private final boolean enabled;

  public PersonalBankProfiles(
      PaymentRepository repo,
      BankDestinations destinations,
      BankProfileClient authority,
      ObjectMapper json,
      PlatformTransactionManager manager,
      @Value("${SPORTHUB_BANK_PROFILE_ENFORCEMENT:false}") boolean enabled) {
    this.repo = repo;
    this.destinations = destinations;
    this.authority = authority;
    this.json = json;
    tx = new TransactionTemplate(manager);
    this.enabled = enabled;
  }

  public List<BankDestinations.Destination> mine(Caller caller) {
    if (!enabled) return destinations.mine(caller);
    var status = current(caller);
    return status.activeRevisionId() == null
        ? List.of()
        : List.of(destinations.owned(status.activeRevisionId(), caller.id(), null));
  }

  public JsonNode status(Caller caller) {
    var state = current(caller);
    var result = json.valueToTree(state);
    ((com.fasterxml.jackson.databind.node.ObjectNode) result)
        .set(
            "destination",
            state.activeRevisionId() == null
                ? json.nullNode()
                : json.valueToTree(
                    destinations.owned(state.activeRevisionId(), caller.id(), null)));
    return result;
  }

  public BankDestinations.Destination register(
      BankDestinations.Input input, String key, Caller caller, String token) {
    caller.requireRole("CUSTOMER");
    if (!enabled) return destinations.register(input, key, caller, token, null);
    BankDestinations.checkKey(key);
    if (input == null) throw new IllegalArgumentException("Cần thông tin tài khoản ngân hàng.");
    String fingerprint = BankDestinations.fingerprint(input, caller.id(), null);
    var previous = request(caller.id(), key);
    if (previous != null) {
      if (!fingerprint.equals(previous.get("fingerprint")))
        throw new ConflictException("Mã yêu cầu đã dùng cho thông tin ngân hàng khác.");
      return finish(previous, caller.id(), key);
    }
    var status = current(caller);
    if (input.expectedProfileVersion() == null || input.expectedProfileVersion() < 0)
      throw new IllegalArgumentException("Cần phiên bản hồ sơ ngân hàng đang hiển thị.");
    if (input.expectedProfileVersion() != status.version())
      throw new ActionPolicyException(
          409,
          "BANK_PROFILE_VERSION_CHANGED",
          "Tài khoản ngân hàng đã thay đổi. Vui lòng tải lại.",
          Map.of("version", status.version()));
    if (!status.canRegisterFirst() && !status.canChange())
      throw new ActionPolicyException(
          409,
          "BANK_PROFILE_LOCKED",
          "Bạn còn lịch sân hoặc nghĩa vụ chưa hoàn tất nên chưa thể đổi tài khoản ngân hàng.",
          Map.of("responsibilities", status.responsibilities()));
    destinations.stage(input, key, caller, input.expectedProfileVersion(), status.initialized());
    return finish(Objects.requireNonNull(request(caller.id(), key)), caller.id(), key);
  }

  private Status current(Caller caller) {
    caller.requireRole("CUSTOMER");
    if (!enabled)
      throw unavailable("Chính sách tài khoản ngân hàng chưa được bật tại môi trường này.");
    var pending =
        repo.jdbc()
            .queryForList(
                "SELECT * FROM bank_destination_requests WHERE actor_id=? AND state='STAGED' ORDER"
                    + " BY idempotency_key LIMIT 21",
                caller.id());
    if (pending.size() > 20)
      throw unavailable("Có nhiều yêu cầu ngân hàng cần đối chiếu trước khi thay đổi.");
    for (var row : pending) {
      try {
        finish(row, caller.id(), row.get("idempotency_key").toString());
      } catch (ActionPolicyException ex) {
        if (ex.status() != 409
            && !(ex.status() == 503
                && ex.code().equals("BANK_PROFILE_RECONCILIATION_REQUIRED")
                && Boolean.TRUE.equals(row.get("history_present")))) throw ex;
      }
    }
    boolean history =
        Boolean.TRUE.equals(
            repo.jdbc()
                .queryForObject(
                    """
SELECT EXISTS(SELECT 1 FROM customer_bank_destination_revisions d
  WHERE d.principal_id=? AND NOT EXISTS(SELECT 1 FROM bank_destination_requests r
    WHERE r.revision_id=d.id AND r.state IN('STAGED','REJECTED')))
""",
                    Boolean.class,
                    caller.id()));
    Status state;
    try {
      state = authority.status(caller.id(), history);
    } catch (ActionPolicyException failure) {
      if (!history
          || failure.status() != 503
          || !failure.code().equals("BANK_PROFILE_RECONCILIATION_REQUIRED")) throw failure;
      var candidates = destinations.mine(caller);
      if (candidates.isEmpty()) throw failure;
      var sole = candidates.size() == 1 ? candidates.getFirst() : null;
      state =
          authority.reconcile(
              new LegacyHistory(
                  caller.id(),
                  sole == null ? null : sole.id(),
                  sole == null ? null : sole.destinationHash()));
    }
    if (state == null || !caller.id().equals(state.principalId()))
      throw unavailable("Chưa xác định được chủ hồ sơ ngân hàng.");
    if (history && !state.initialized())
      throw unavailable("Hồ sơ ngân hàng cũ cần được đối chiếu, không thể khai lại lần đầu.");
    if (state.activeRevisionId() != null) {
      var destination = destinations.owned(state.activeRevisionId(), caller.id(), null);
      if (!Objects.equals(destination.destinationHash(), state.destinationHash()))
        throw unavailable("Phiên bản tài khoản ngân hàng chưa đồng bộ. Vui lòng thử lại.");
    }
    return state;
  }

  private BankDestinations.Destination finish(
      Map<String, Object> request, UUID principal, String key) {
    UUID revision = (UUID) request.get("revision_id");
    var destination = destinations.staged(revision, principal);
    String state = request.get("state").toString();
    if (state.equals("REJECTED")) {
      JsonNode error = read(request.get("decision"));
      throw new ActionPolicyException(
          409,
          error.path("code").asText("BANK_PROFILE_LOCKED"),
          "Yêu cầu đổi ngân hàng đã bị từ chối. Vui lòng tải lại trước khi tạo yêu cầu mới.",
          Map.of());
    }
    if (state.equals("ACTIVATED") || state.equals("LEGACY")) return destination;
    if (!(request.get("expected_profile_version") instanceof Number expected))
      throw unavailable("Thiếu phiên bản yêu cầu ngân hàng.");
    Decision decision;
    try {
      decision =
          authority.activate(
              new Activation(
                  principal,
                  revision,
                  destination.destinationHash(),
                  expected.longValue(),
                  Boolean.TRUE.equals(request.get("history_present")),
                  key,
                  request.get("fingerprint").toString()));
    } catch (ActionPolicyException ex) {
      if (ex.status() == 409)
        project(principal, key, "REJECTED", null, json.valueToTree(Map.of("code", ex.code())));
      throw ex;
    }
    if (decision == null
        || !principal.equals(decision.principalId())
        || !revision.equals(decision.revisionId())
        || !destination.destinationHash().equals(decision.destinationHash())
        || decision.version() != expected.longValue() + 1
        || !"ACTIVATED".equals(decision.decision()))
      throw unavailable("Kết quả kích hoạt tài khoản chưa đồng bộ.");
    project(principal, key, "ACTIVATED", decision.version(), json.valueToTree(decision));
    return destinations.owned(revision, principal, null);
  }

  private Map<String, Object> request(UUID principal, String key) {
    var rows =
        repo.jdbc()
            .queryForList(
                "SELECT * FROM bank_destination_requests WHERE actor_id=? AND idempotency_key=?",
                principal,
                key);
    return rows.isEmpty() ? null : rows.getFirst();
  }

  private void project(UUID principal, String key, String state, Long version, JsonNode result) {
    tx.executeWithoutResult(
        status -> {
          repo.lock(principal);
          var current = Objects.requireNonNull(request(principal, key));
          if (!"STAGED".equals(current.get("state"))) {
            if (!state.equals(current.get("state")))
              throw unavailable("Quyết định ngân hàng chưa đồng bộ.");
            return;
          }
          repo.jdbc()
              .update(
                  "UPDATE bank_destination_requests SET"
                      + " state=?,activation_version=?,decision=?::jsonb WHERE actor_id=? AND"
                      + " idempotency_key=? AND state='STAGED'",
                  state,
                  version,
                  result.toString(),
                  principal,
                  key);
        });
  }

  private JsonNode read(Object value) {
    try {
      return json.readTree(String.valueOf(value));
    } catch (Exception ex) {
      throw unavailable("Chưa đọc được kết quả yêu cầu ngân hàng.");
    }
  }

  private ActionPolicyException unavailable(String message) {
    return new ActionPolicyException(
        503, "BANK_PROFILE_RECONCILIATION_REQUIRED", message, Map.of());
  }
}
