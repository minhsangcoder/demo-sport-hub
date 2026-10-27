package com.sporthub.transfer.service;

import com.fasterxml.jackson.databind.*;
import com.sporthub.common.exception.*;
import com.sporthub.common.money.*;
import com.sporthub.common.security.*;
import com.sporthub.common.security.RemoteIdentity.Caller;
import java.time.*;
import java.util.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class PeerPartyCommands {
  private final PeerEvidenceAccess access;
  private final PeerEvidence evidence;
  private final PeerPartyCommandStore commands;
  private final PeerBookingClient booking;
  private final PeerTransferProjection projection;
  private final ObjectMapper json;
  private final Clock clock;

  public PeerPartyCommands(
      PeerEvidenceAccess access,
      PeerEvidence evidence,
      PeerPartyCommandStore commands,
      PeerBookingClient booking,
      PeerTransferProjection projection,
      ObjectMapper json,
      Clock clock) {
    this.access = access;
    this.evidence = evidence;
    this.commands = commands;
    this.booking = booking;
    this.projection = projection;
    this.json = json;
    this.clock = clock;
  }

  public JsonNode declare(UUID id, PeerResaleContract.Declaration input, String key, Caller actor) {
    var context = access.participant(id, actor);
    if (!actor.id().equals(context.acquisition().buyerId()))
      throw new ForbiddenException("Chỉ người mua được khai đã chuyển tiền.");
    String fingerprint = fingerprint(id, "PEER_DECLARE", input, key);
    var command = commands.find(id, actor.id(), "PEER_DECLARE", key, fingerprint);
    if (command == null) {
      var current = projection.acquisition(id).path("attempt");
      if (!"WAITING_PAYMENT".equals(current.path("phase").asText())
          || current.path("version").asLong() != input.version()
          || !context
              .acquisition()
              .financeTerms()
              .path("termsHash")
              .asText()
              .equals(input.termsHash()))
        throw new ConflictException(
            "Phiên bản hoặc trạng thái đã thay đổi. Vui lòng tải lại trước khi khai thanh toán.");
      evidence.requireReady(id, actor.id(), input.evidenceIds());
      if (!clock.instant().isBefore(context.acquisition().expiresAt()))
        throw new ConflictException(
            "Phiên đã hết hạn. Hãy dùng mục báo tiền chuyển muộn, không chuyển thêm từ QR cũ.");
      var receipt =
          PeerEvidenceReceipt.create(
              id,
              actor.id(),
              input.termsHash(),
              input.bankTransactionReference(),
              input.evidenceIds(),
              clock.instant().truncatedTo(java.time.temporal.ChronoUnit.MICROS));
      var payload = json.createObjectNode().put("target", "declare");
      payload.set("input", json.valueToTree(input));
      payload.set("receipt", json.valueToTree(receipt));
      command = commands.stage(id, actor.id(), "PEER_DECLARE", key, fingerprint, payload, receipt);
    }
    return execute(command);
  }

  public JsonNode confirm(UUID id, PeerResaleContract.Command input, String key, Caller actor) {
    var context = access.participant(id, actor);
    if (!actor.id().equals(context.listing().sellerId()))
      throw new ForbiddenException("Chỉ người bán được xác nhận đã nhận đủ tiền.");
    return simple(id, "PEER_CONFIRM", "confirm", input, null, key, actor);
  }

  public JsonNode cancel(UUID id, PeerResaleContract.Command input, String key, Caller actor) {
    var context = access.participant(id, actor);
    if (!actor.id().equals(context.acquisition().buyerId()))
      throw new ForbiddenException("Chỉ người mua được hủy lượt giữ của mình.");
    return simple(id, "PEER_CANCEL", "cancel", input, null, key, actor);
  }

  public JsonNode deny(UUID id, PeerResaleContract.Command input, String key, Caller actor) {
    var context = access.participant(id, actor);
    if (!actor.id().equals(context.listing().sellerId()))
      throw new ForbiddenException("Chỉ người bán được khai chưa nhận được tiền.");
    return simple(
        id,
        "PEER_OPEN_DISPUTE",
        "dispute",
        input,
        "Người bán khai chưa nhận đủ tiền, cần Owner xem bằng chứng và phân xử quyền sân.",
        key,
        actor);
  }

  public JsonNode dispute(UUID id, PeerResaleContract.Command input, String key, Caller actor) {
    var context = access.participant(id, actor);
    if (!actor.id().equals(context.acquisition().buyerId()))
      throw new ForbiddenException("Chỉ người mua được gửi khiếu nại chưa được bàn giao.");
    return simple(
        id,
        "PEER_OPEN_DISPUTE",
        "dispute",
        input,
        "Người mua yêu cầu Owner xem bằng chứng và phân xử quyền sân do chưa được xác nhận nhận"
            + " tiền.",
        key,
        actor);
  }

  private JsonNode simple(
      UUID id,
      String action,
      String target,
      PeerResaleContract.Command input,
      String reason,
      String key,
      Caller actor) {
    if (input == null) throw new IllegalArgumentException("Cần phiên bản và điều khoản phiên mua.");
    var payload = json.createObjectNode().put("target", target);
    payload.set("input", json.valueToTree(input));
    if (reason != null) payload.put("reason", reason);
    String fingerprint = fingerprint(id, action, payload, key);
    var command = commands.find(id, actor.id(), action, key, fingerprint);
    if (command == null)
      command = commands.stage(id, actor.id(), action, key, fingerprint, payload, null);
    return execute(command);
  }

  private String fingerprint(UUID id, String action, Object input, String key) {
    PeerTransferListings.checkKey(key);
    if (input == null) throw new IllegalArgumentException("Thiếu nội dung yêu cầu.");
    return Signatures.sha256(id + "|" + action + "|" + CanonicalCommandJson.write(json, input));
  }

  JsonNode execute(PeerPartyCommandStore.Command command) {
    if (command.state().equals("REJECTED"))
      throw new ActionPolicyException(
          command.result().path("status").asInt(),
          command.result().path("code").asText(),
          command.result().path("message").asText(),
          Map.of());
    if (command.state().equals("COMPLETED")) {
      projection.acquisition(command.acquisition());
      return command.result();
    }
    JsonNode result;
    try {
      var payload = command.payload();
      var nativeResult =
          booking.party(
              command.acquisition(),
              payload.path("target").asText(),
              command.actor(),
              payload.path("input"),
              payload.has("receipt") ? payload.path("receipt") : null,
              payload.path("reason").asText(null),
              command.remoteKey());
      result = commands.complete(command, nativeResult);
    } catch (ActionPolicyException ex) {
      if (ex.status() < 500) commands.reject(command, ex);
      throw ex;
    }
    projection.acquisition(command.acquisition());
    return result;
  }

  @Scheduled(
      fixedDelayString = "${transfer.peer-command-recovery-ms:5000}",
      initialDelayString = "${transfer.peer-command-recovery-ms:5000}")
  public void recover() {
    for (var command : commands.pending()) {
      try {
        var result =
            booking.commandResult(
                command.acquisition(), command.actor(), command.action(), command.remoteKey());
        if (!"UNKNOWN".equals(result.path("phase").asText())) {
          commands.complete(command, result);
          projection.acquisition(command.acquisition());
        }
      } catch (RuntimeException ignored) {
        /* An unknown result is not permission to repeat a human action. */
      } finally {
        commands.touched(command);
      }
    }
  }
}
