package com.sporthub.transfer.service;

import com.fasterxml.jackson.databind.*;
import com.sporthub.common.exception.*;
import com.sporthub.common.money.*;
import com.sporthub.common.security.*;
import com.sporthub.common.security.RemoteIdentity.Caller;
import com.sporthub.transfer.repository.TransferRepository;
import com.sporthub.transfer.web.TransferDtos.*;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Edit/withdraw retain a frozen intent across an uncertain native response. */
@Service
public class PeerListingCommands {
  private final TransferRepository repo;
  private final PeerTransferListings listings;
  private final PeerBookingClient booking;
  private final PeerTransferProjection projection;
  private final Clock clock;
  private final ObjectMapper json;
  private final TransactionTemplate tx;

  public PeerListingCommands(
      TransferRepository repo,
      PeerTransferListings listings,
      PeerBookingClient booking,
      PeerTransferProjection projection,
      Clock clock,
      ObjectMapper json,
      PlatformTransactionManager manager) {
    this.repo = repo;
    this.listings = listings;
    this.booking = booking;
    this.projection = projection;
    this.clock = clock;
    this.json = json;
    tx = new TransactionTemplate(manager);
  }

  public JsonNode quote(UUID id, Edit input, Caller actor) {
    var row = owner(id, actor);
    if (!row.state().equals("ACTIVE") || !row.workflow().equals("READY"))
      throw new ConflictException("Chỉ sửa tin đang mở và chưa có người mua.");
    return listings.quote(
        new Create(
            row.bookingId(),
            input.price(),
            input.deadline(),
            input.beneficiaryRevisionId(),
            null,
            input.bankDestinationRevisionId()),
        actor);
  }

  public void edit(UUID id, Edit input, String key, Caller actor) {
    owner(id, actor);
    PeerTransferListings.checkKey(key);
    String fingerprint = fingerprint(id, input);
    var previous = previous(id, actor.id(), "publish", key, fingerprint);
    if (previous != null) {
      execute(previous, actor);
      return;
    }
    if (input.expectedVersion() == null || input.expectedVersion() != repo.find(id).version())
      throw new ConflictException("Phiên bản tin đã thay đổi. Vui lòng tải lại.");
    var terms = quote(id, input, actor);
    if (!terms.path("termsHash").asText().equals(input.acceptedTermsHash()))
      throw new ConflictException("Cần xem và đồng ý lại giá, tài khoản và điều khoản chia sẻ.");
    execute(
        stage(
            id,
            actor,
            "publish",
            key,
            fingerprint,
            json.valueToTree(new PeerMarketContract.Publish(id, input.expectedVersion(), terms))),
        actor);
  }

  public void withdraw(UUID id, long version, String key, Caller actor) {
    owner(id, actor);
    PeerTransferListings.checkKey(key);
    var input = new PeerMarketContract.Withdraw(id, version);
    String fingerprint = fingerprint(id, input);
    var previous = previous(id, actor.id(), "withdraw", key, fingerprint);
    execute(
        previous == null
            ? stage(id, actor, "withdraw", key, fingerprint, json.valueToTree(input))
            : previous,
        actor);
  }

  private TransferRepository.Row owner(UUID id, Caller actor) {
    actor.requireRole("CUSTOMER");
    var row = repo.find(id);
    if (!actor.id().equals(row.sellerId())) throw new ForbiddenException("Tin không thuộc bạn.");
    if (!PeerResaleContract.isPeer(row.financeTerms()))
      throw new ConflictException("Tin thuộc luồng cũ.");
    return row;
  }

  private String fingerprint(UUID id, Object input) {
    return Signatures.sha256(id + "|" + CanonicalCommandJson.write(json, input));
  }

  private Map<String, Object> previous(
      UUID id, UUID actor, String action, String key, String fingerprint) {
    var rows =
        repo.jdbc()
            .queryForList(
                "SELECT * FROM peer_listing_operations WHERE actor_id=? AND action=? AND"
                    + " idempotency_key=?",
                actor,
                action,
                key);
    if (rows.isEmpty()) return null;
    var row = rows.getFirst();
    if (!id.equals(row.get("listing_id")) || !fingerprint.equals(row.get("fingerprint")))
      throw new ConflictException("Mã yêu cầu đã dùng cho thao tác khác.");
    return row;
  }

  private Map<String, Object> stage(
      UUID id, Caller actor, String action, String key, String fingerprint, JsonNode command) {
    return tx.execute(
        status -> {
          repo.lock(actor.id());
          repo.lock(id);
          var old = previous(id, actor.id(), action, key, fingerprint);
          if (old != null) return old;
          owner(id, actor);
          repo.jdbc()
              .update(
                  "INSERT INTO"
                      + " peer_listing_operations(actor_id,listing_id,action,idempotency_key,fingerprint,command,created_at,updated_at)"
                      + " VALUES(?,?,?,?,?,?::jsonb,?,?)",
                  actor.id(),
                  id,
                  action,
                  key,
                  fingerprint,
                  command.toString(),
                  Timestamp.from(clock.instant()),
                  Timestamp.from(clock.instant()));
          return previous(id, actor.id(), action, key, fingerprint);
        });
  }

  private void execute(Map<String, Object> row, Caller actor) {
    if (row.get("state").equals("REJECTED"))
      throw new ConflictException("Yêu cầu đã bị từ chối. Tải lại trước khi tạo thao tác mới.");
    if (row.get("state").equals("COMPLETED")) {
      refresh((UUID) row.get("listing_id"));
      return;
    }
    try {
      var result =
          booking.market(
              row.get("action").toString(),
              repo.read(row.get("command").toString()),
              remoteKey(row),
              actor);
      complete(row, result);
    } catch (ActionPolicyException ex) {
      if (ex.status() < 500)
        finish(
            row,
            "REJECTED",
            json.valueToTree(Map.of("code", ex.code(), "message", ex.getMessage())));
      throw ex;
    }
  }

  private String remoteKey(Map<String, Object> row) {
    return "pl:"
        + Signatures.sha256(
            row.get("actor_id") + "|" + row.get("action") + "|" + row.get("idempotency_key"));
  }

  private void complete(Map<String, Object> row, JsonNode result) {
    UUID id = (UUID) row.get("listing_id");
    if (!id.toString().equals(result.path("id").asText()))
      throw new ConflictException("Kết quả không thuộc tin này.");
    refresh(id);
    finish(row, "COMPLETED", result);
  }

  private void refresh(UUID id) {
    var row = repo.find(id);
    if (row.activeAcquisitionId() != null) projection.acquisition(row.activeAcquisitionId());
    projection.listing(id);
  }

  private void finish(Map<String, Object> row, String state, JsonNode result) {
    tx.executeWithoutResult(
        status -> {
          repo.lock((UUID) row.get("actor_id"));
          repo.lock((UUID) row.get("listing_id"));
          repo.jdbc()
              .update(
                  "UPDATE peer_listing_operations SET state=?,result=?::jsonb,updated_at=? WHERE"
                      + " actor_id=? AND action=? AND idempotency_key=? AND state='PENDING'",
                  state,
                  result.toString(),
                  Timestamp.from(clock.instant()),
                  row.get("actor_id"),
                  row.get("action"),
                  row.get("idempotency_key"));
        });
  }

  @Scheduled(
      fixedDelayString = "${transfer.peer-command-recovery-ms:5000}",
      initialDelayString = "${transfer.peer-command-recovery-ms:5000}")
  public void recover() {
    for (var row :
        repo.jdbc()
            .queryForList(
                "SELECT * FROM peer_listing_operations WHERE state='PENDING' ORDER BY"
                    + " updated_at,listing_id LIMIT 50")) {
      try {
        var actor =
            new Caller(
                (UUID) row.get("actor_id"),
                "",
                Set.of("CUSTOMER"),
                Map.of(),
                "ACTIVE",
                true,
                false);
        String action = row.get("action").equals("publish") ? "PEER_PUBLISH" : "PEER_WITHDRAW";
        var result =
            booking.market(
                "result",
                new PeerMarketContract.Result(
                    (UUID) row.get("listing_id"), action, repo.read(row.get("command").toString())),
                remoteKey(row),
                actor);
        if (!"UNKNOWN".equals(result.path("state").asText())) complete(row, result);
      } catch (RuntimeException ignored) {
        /* Keep the intent; a worker never retries a human command. */
      } finally {
        repo.jdbc()
            .update(
                "UPDATE peer_listing_operations SET updated_at=? WHERE actor_id=? AND action=? AND"
                    + " idempotency_key=? AND state='PENDING'",
                Timestamp.from(clock.instant()),
                row.get("actor_id"),
                row.get("action"),
                row.get("idempotency_key"));
      }
    }
  }
}
