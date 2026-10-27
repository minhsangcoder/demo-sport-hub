package com.sporthub.transfer.service;

import com.fasterxml.jackson.databind.*;
import com.sporthub.common.exception.*;
import com.sporthub.common.money.*;
import com.sporthub.common.security.RemoteIdentity.Caller;
import com.sporthub.common.security.Signatures;
import com.sporthub.transfer.repository.TransferRepository;
import java.sql.Timestamp;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** A staged purchase is not a reservation or payment; Booking alone admits and grants usage. */
@Service
public class PeerTransferAcquisitions {
  private final TransferRepository repo;
  private final PeerBookingClient booking;
  private final PeerTransferProjection projection;
  private final Clock clock;
  private final TransactionTemplate tx;

  public PeerTransferAcquisitions(
      TransferRepository repo,
      PeerBookingClient booking,
      PeerTransferProjection projection,
      Clock clock,
      PlatformTransactionManager manager) {
    this.repo = repo;
    this.booking = booking;
    this.projection = projection;
    this.clock = clock;
    tx = new TransactionTemplate(manager);
  }

  public UUID acquire(UUID listing, String key, Caller actor, String acceptedHash) {
    actor.requireRole("CUSTOMER");
    actor.requireActiveVerified();
    PeerTransferListings.checkKey(key);
    String fingerprint =
        Signatures.sha256(acceptedHash == null ? listing.toString() : listing + "|" + acceptedHash);
    UUID previous = repo.previous(actor.id(), "ACQUIRE", key, fingerprint);
    if (previous != null) {
      finish(previous, actor);
      return previous;
    }
    projection.listing(listing);
    UUID id;
    try {
      id =
          tx.execute(
              status -> {
                repo.lock(actor.id());
                repo.lock(listing);
                UUID prior = repo.previous(actor.id(), "ACQUIRE", key, fingerprint);
                if (prior != null) return prior;
                var row = repo.find(listing);
                if (!PeerResaleContract.isPeer(row.financeTerms()))
                  throw new ConflictException(
                      "Tin này cần dùng luồng chuyển nhượng đã chấp nhận trước đây.");
                if (row.sellerId().equals(actor.id()))
                  throw new ConflictException(
                      "Bạn không thể mua tin chuyển nhượng của chính mình.");
                if (!row.state().equals("ACTIVE")
                    || !row.workflow().equals("READY")
                    || !row.deadline().isAfter(clock.instant()))
                  throw new ConflictException("Tin đang có người mua hoặc đã hết hạn.");
                if (acceptedHash == null
                    || !acceptedHash.equals(row.financeTerms().path("termsHash").asText()))
                  throw new ConflictException("Vui lòng xem và chấp nhận điều khoản mua hiện tại.");
                Instant acceptedAt = clock.instant().truncatedTo(ChronoUnit.MICROS),
                    expires = acceptedAt.plusSeconds(300);
                if (expires.isAfter(row.deadline())) expires = row.deadline();
                UUID acquisition = UUID.randomUUID();
                var terms =
                    PeerResaleTerms.acquisition(
                        row.financeTerms(),
                        listing,
                        row.version(),
                        acquisition,
                        actor.id(),
                        expires,
                        acceptedAt);
                UUID conversation = UUID.randomUUID();
                repo.jdbc()
                    .update(
                        "INSERT INTO"
                            + " transfer_conversations(id,listing_id,seller_id,buyer_id,created_at)"
                            + " VALUES(?,?,?,?,?) ON CONFLICT(listing_id,seller_id,buyer_id) DO"
                            + " NOTHING",
                        conversation,
                        listing,
                        row.sellerId(),
                        actor.id(),
                        Timestamp.from(acceptedAt));
                conversation =
                    repo.jdbc()
                        .queryForObject(
                            "SELECT id FROM transfer_conversations WHERE listing_id=? AND"
                                + " seller_id=? AND buyer_id=?",
                            UUID.class,
                            listing,
                            row.sellerId(),
                            actor.id());
                repo.jdbc()
                    .update(
                        "INSERT INTO"
                            + " transfer_acquisitions(id,listing_id,buyer_id,amount,currency,expires_at,state,finance_terms,protocol,conversation_id,created_at,updated_at)"
                            + " VALUES(?,?,?,?,'VND',?,'PENDING',?::jsonb,'PEER_RESALE_V1',?,?,?)",
                        acquisition,
                        listing,
                        actor.id(),
                        row.price(),
                        Timestamp.from(expires),
                        terms.toString(),
                        conversation,
                        Timestamp.from(acceptedAt),
                        Timestamp.from(acceptedAt));
                repo.remember(actor.id(), "ACQUIRE", key, fingerprint, acquisition);
                repo.audit(
                    row,
                    actor.id(),
                    "PEER_PURCHASE_STAGED",
                    Map.of(
                        "acquisitionId",
                        acquisition,
                        "termsHash",
                        terms.path("termsHash").asText()));
                return acquisition;
              });
    } catch (org.springframework.dao.DataIntegrityViolationException ex) {
      throw new ConflictException(
          "Tin đang xử lý một yêu cầu mua khác. Vui lòng tải lại trạng thái.");
    }
    finish(id, actor);
    return id;
  }

  private void finish(UUID id, Caller actor) {
    var local = repo.acquisition(id);
    if (!PeerResaleContract.isPeer(local.financeTerms()) || !local.state().equals("PENDING"))
      return;
    if (!local.buyerId().equals(actor.id()))
      throw new ForbiddenException("Phiên mua không thuộc bạn.");
    if (!local.expiresAt().isAfter(clock.instant())) {
      projection.acquisition(id);
      return;
    }
    var terms = local.financeTerms();
    var input =
        new PeerMarketContract.Acquire(
            local.listingId(),
            id,
            terms.path("listingVersion").asLong(),
            terms.path("sellerTermsHash").asText(),
            Instant.parse(terms.path("acceptedAt").asText()));
    try {
      booking.market("acquire", input, "acquire:" + id, actor);
      projection.acquisition(id);
    } catch (ActionPolicyException ex) {
      if (ex.status() < 500)
        tx.executeWithoutResult(
            status -> {
              repo.lock(local.listingId());
              repo.jdbc()
                  .update(
                      "UPDATE transfer_acquisitions SET state='FAILED',updated_at=? WHERE id=? AND"
                          + " state='PENDING' AND peer_projection IS NULL",
                      Timestamp.from(clock.instant()),
                      id);
            });
      throw ex;
    }
  }

  public boolean isPeer(UUID id) {
    return PeerResaleContract.isPeer(repo.acquisition(id).financeTerms());
  }

  public JsonNode read(UUID id, Caller actor) {
    actor.requireRole("CUSTOMER");
    var local = repo.acquisition(id);
    var listing = repo.find(local.listingId());
    if (!local.buyerId().equals(actor.id()) && !listing.sellerId().equals(actor.id()))
      throw new ForbiddenException("Bạn không thuộc cuộc trao đổi chuyển nhượng này.");
    if (!PeerResaleContract.isPeer(local.financeTerms()))
      throw new ConflictException("Phiên mua thuộc luồng chuyển nhượng trước đây.");
    var canonical = projection.acquisition(id);
    if (!canonical.path("attempt").isObject()) {
      boolean pending = repo.acquisition(id).state().equals("PENDING");
      throw new ActionPolicyException(
          pending ? 503 : 409,
          pending ? "PEER_PENDING_SYNCHRONIZATION" : "PEER_ATTEMPT_NOT_ADMITTED",
          pending
              ? "Phiên mua đang chờ xác nhận giữ chỗ. Chưa chuyển tiền từ mã QR."
              : "Phiên mua chưa được tiếp nhận và đã kết thúc. Chưa có quyền dùng sân được bàn"
                    + " giao.",
          Map.of("acquisitionId", id));
    }
    var result = canonical.path("attempt").deepCopy();
    UUID conversation =
        repo.jdbc()
            .queryForObject(
                "SELECT conversation_id FROM transfer_acquisitions WHERE id=?", UUID.class, id);
    ((com.fasterxml.jackson.databind.node.ObjectNode) result)
        .put("conversationId", conversation.toString());
    return result;
  }
}
