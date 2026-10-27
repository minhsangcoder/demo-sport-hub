package com.sporthub.transfer.service;

import com.fasterxml.jackson.databind.*;
import com.sporthub.common.exception.*;
import com.sporthub.common.money.*;
import com.sporthub.common.security.*;
import com.sporthub.common.security.RemoteIdentity.Caller;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class PeerMoneyCommands {
  private final PeerEvidenceAccess access;
  private final PeerEvidence evidence;
  private final PeerPartyCommandStore store;
  private final PeerPartyCommands executor;
  private final PeerBookingClient booking;
  private final ObjectMapper json;

  public PeerMoneyCommands(
      PeerEvidenceAccess access,
      PeerEvidence evidence,
      PeerPartyCommandStore store,
      PeerPartyCommands executor,
      PeerBookingClient booking,
      ObjectMapper json) {
    this.access = access;
    this.evidence = evidence;
    this.store = store;
    this.executor = executor;
    this.booking = booking;
    this.json = json;
  }

  public JsonNode read(UUID id, Caller actor) {
    access.participant(id, actor);
    return booking.party(id, "money", actor.id(), null, null, null, null);
  }

  public JsonNode read(UUID id, UUID afterReturn, int size, Caller actor) {
    access.participant(id, actor);
    if (size < 1 || size > 100)
      throw new IllegalArgumentException("Chỉ tải tối đa 100 khoản trả mỗi lần.");
    var page = json.createObjectNode().put("size", size);
    if (afterReturn != null) page.put("afterReturn", afterReturn.toString());
    return booking.party(id, "money", actor.id(), page, null, null, null);
  }

  public JsonNode report(UUID id, PeerMoneyContract.Report input, String key, Caller actor) {
    var context = access.participant(id, actor);
    checkTerms(context, input == null ? null : input.termsHash());
    String fingerprint = fingerprint(id, "PEER_MONEY_REPORT", input, key);
    var command = store.find(id, actor.id(), "PEER_MONEY_REPORT", key, fingerprint);
    if (command == null) {
      PeerMoneyContract.proof(
          input.termsHash(), input.bankTransactionReference(), input.evidenceIds());
      evidence.requireReady(id, actor.id(), input.evidenceIds());
      command = stage(id, actor, "PEER_MONEY_REPORT", "money-report", input, key, fingerprint);
    }
    return executor.execute(command);
  }

  public JsonNode returned(UUID id, PeerMoneyContract.ReturnInput input, String key, Caller actor) {
    var context = access.participant(id, actor);
    if (!context.listing().sellerId().equals(actor.id()))
      throw new ForbiddenException("Chỉ A được khai đã trả lại tiền cho B.");
    checkTerms(context, input == null ? null : input.termsHash());
    String fingerprint = fingerprint(id, "PEER_RETURN_DECLARE", input, key);
    var command = store.find(id, actor.id(), "PEER_RETURN_DECLARE", key, fingerprint);
    if (command == null) {
      PeerMoneyContract.proof(
          input.termsHash(), input.bankTransactionReference(), input.evidenceIds());
      if (input.amount() == null
          || input.amount().signum() <= 0
          || input.amount().stripTrailingZeros().scale() > 0)
        throw new IllegalArgumentException("Số tiền trả lại phải là số đồng nguyên dương.");
      evidence.requireReady(id, actor.id(), input.evidenceIds());
      command =
          stage(
              id,
              actor,
              "PEER_RETURN_DECLARE",
              "return-declare",
              new PeerMoneyContract.ReturnCommand(UUID.randomUUID(), input),
              key,
              fingerprint);
    }
    return executor.execute(command);
  }

  public JsonNode acknowledge(
      UUID id, UUID returned, PeerResaleContract.Command input, String key, Caller actor) {
    var context = access.participant(id, actor);
    if (!context.acquisition().buyerId().equals(actor.id()))
      throw new ForbiddenException("Chỉ B được xác nhận đã nhận lại tiền.");
    checkTerms(context, input == null ? null : input.termsHash());
    var payload = new PeerMoneyContract.Acknowledge(returned, input.version(), input.termsHash());
    String fingerprint = fingerprint(id, "PEER_RETURN_ACK", payload, key);
    var command = store.find(id, actor.id(), "PEER_RETURN_ACK", key, fingerprint);
    if (command == null)
      command = stage(id, actor, "PEER_RETURN_ACK", "return-ack", payload, key, fingerprint);
    return executor.execute(command);
  }

  public JsonNode noClaim(UUID id, PeerMoneyContract.NoClaim input, String key, Caller actor) {
    var context = access.participant(id, actor);
    checkTerms(context, input == null ? null : input.termsHash());
    String fingerprint = fingerprint(id, "PEER_MONEY_NO_CLAIM", input, key);
    var command = store.find(id, actor.id(), "PEER_MONEY_NO_CLAIM", key, fingerprint);
    if (command == null)
      command = stage(id, actor, "PEER_MONEY_NO_CLAIM", "money-no-claim", input, key, fingerprint);
    return executor.execute(command);
  }

  private PeerPartyCommandStore.Command stage(
      UUID id,
      Caller actor,
      String action,
      String target,
      Object input,
      String key,
      String fingerprint) {
    var payload = json.createObjectNode().put("target", target);
    payload.set("input", json.valueToTree(input));
    return store.stage(id, actor.id(), action, key, fingerprint, payload, null);
  }

  private String fingerprint(UUID id, String action, Object input, String key) {
    PeerTransferListings.checkKey(key);
    return Signatures.sha256(id + "|" + action + "|" + CanonicalCommandJson.write(json, input));
  }

  private void checkTerms(PeerEvidenceAccess.Context context, String hash) {
    if (hash == null
        || !context.acquisition().financeTerms().path("termsHash").asText().equals(hash))
      throw new ConflictException("Điều khoản phiên mua không khớp.");
  }
}
