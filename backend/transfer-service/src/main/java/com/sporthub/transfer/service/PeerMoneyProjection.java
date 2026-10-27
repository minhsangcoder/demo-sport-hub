package com.sporthub.transfer.service;

import com.sporthub.common.exception.ActionPolicyException;
import com.sporthub.transfer.repository.TransferRepository;
import java.sql.Timestamp;
import java.util.*;

/** Append facts only after their canonical Booking command is known to have committed. */
public class PeerMoneyProjection {
  private final TransferRepository repo;

  public PeerMoneyProjection(TransferRepository repo) {
    this.repo = repo;
  }

  public void apply(PeerPartyCommandStore.Command command) {
    if (!org.springframework.transaction.support.TransactionSynchronizationManager
        .isActualTransactionActive())
      throw new IllegalStateException("Money projection requires the command transaction");
    var input = command.payload().path("input");
    switch (command.action()) {
      case "PEER_RETURN_DECLARE" -> {
        var body = input.path("input");
        var ids = new ArrayList<UUID>();
        body.path("evidenceIds").forEach(value -> ids.add(UUID.fromString(value.asText())));
        repo.jdbc()
            .update(
                "INSERT INTO"
                    + " peer_return_declarations(id,acquisition_id,seller_id,amount,bank_reference,evidence_ids,terms_hash,fingerprint,created_at)"
                    + " VALUES(?,?,?,?,?,?,?,?,?) ON CONFLICT(id) DO NOTHING",
                UUID.fromString(input.path("returnId").asText()),
                command.acquisition(),
                command.actor(),
                new java.math.BigDecimal(body.path("amount").asText()),
                body.path("bankTransactionReference").asText().trim(),
                ids.toArray(UUID[]::new),
                body.path("termsHash").asText(),
                command.fingerprint(),
                Timestamp.from(command.createdAt()));
      }
      case "PEER_RETURN_ACK" -> {
        UUID returned = UUID.fromString(input.path("returnId").asText());
        if (!Boolean.TRUE.equals(
            repo.jdbc()
                .queryForObject(
                    "SELECT EXISTS(SELECT 1 FROM peer_return_declarations WHERE id=? AND"
                        + " acquisition_id=?)",
                    Boolean.class,
                    returned,
                    command.acquisition())))
          throw new ActionPolicyException(
              503,
              "PEER_RETURN_PENDING_SYNCHRONIZATION",
              "Khoản trả đã có kết quả, đang chờ đồng bộ lịch sử. Vui lòng kiểm tra lại cùng"
                  + " phiên.",
              Map.of());
        repo.jdbc()
            .update(
                "INSERT INTO peer_return_acknowledgments(return_id,buyer_id,fingerprint,created_at)"
                    + " VALUES(?,?,?,?) ON CONFLICT(return_id) DO NOTHING",
                returned,
                command.actor(),
                command.fingerprint(),
                Timestamp.from(command.createdAt()));
      }
      case "PEER_MONEY_NO_CLAIM" ->
          repo.jdbc()
              .update(
                  "INSERT INTO peer_money_no_claims(acquisition_id,actor_id,fingerprint,created_at)"
                      + " VALUES(?,?,?,?) ON CONFLICT DO NOTHING",
                  command.acquisition(),
                  command.actor(),
                  command.fingerprint(),
                  Timestamp.from(command.createdAt()));
      default -> {}
    }
  }
}
