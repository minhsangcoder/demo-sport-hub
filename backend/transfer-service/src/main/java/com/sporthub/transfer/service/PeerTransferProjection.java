package com.sporthub.transfer.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.sporthub.common.exception.*;
import com.sporthub.common.money.PeerResaleContract;
import com.sporthub.common.money.PeerResaleTerms;
import com.sporthub.transfer.repository.TransferRepository;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Recovery reads canonical results; workers never create a fresh claim from a cached caller. */
@Service
public class PeerTransferProjection {
  private final TransferRepository repo;
  private final PeerBookingClient booking;
  private final Clock clock;
  private final TransactionTemplate tx;

  public PeerTransferProjection(
      TransferRepository repo,
      PeerBookingClient booking,
      Clock clock,
      PlatformTransactionManager manager) {
    this.repo = repo;
    this.booking = booking;
    this.clock = clock;
    tx = new TransactionTemplate(manager);
  }

  public void listing(UUID id) {
    applyListing(id, booking.listing(id));
  }

  public void applyListing(UUID id, JsonNode canonical) {
    tx.executeWithoutResult(
        status -> {
          repo.lock(id);
          var local = repo.find(id);
          if (!PeerResaleContract.isPeer(local.financeTerms())
              || !id.toString().equals(canonical.path("id").asText()))
            throw new ConflictException("Projection không khớp protocol hoặc tin chuyển nhượng.");
          var terms = canonical.path("terms");
          PeerResaleTerms.validateSeller(terms);
          if (!local.bookingId().toString().equals(terms.path("bookingId").asText())
              || !local.sellerId().toString().equals(terms.path("sellerId").asText()))
            throw new ConflictException("Projection không khớp người bán/lượt đặt.");
          String state = canonical.path("state").asText();
          if (state.equals("LOCKED") || state.equals("COMPLETED")) return;
          if (!Set.of("ACTIVE", "RELEASED").contains(state))
            throw new ConflictException("Trạng thái tin chuyển nhượng chưa xác định.");
          if (local.state().equals("COMPLETED")) return;
          // A newer acquisition must be resolved from its own authoritative result first.
          if (local.activeAcquisitionId() != null) return;
          if (canonical.path("version").asLong() < local.version()) return;
          var deadline = java.time.Instant.parse(terms.path("deadline").asText());
          String next =
              state.equals("RELEASED")
                  ? (deadline.isAfter(clock.instant()) ? "WITHDRAWN" : "EXPIRED")
                  : deadline.isAfter(clock.instant()) ? "ACTIVE" : "EXPIRED";
          if (!repo.read(local.financeTerms().toString()).equals(repo.read(terms.toString()))) {
            if (!local.state().equals("ACTIVE")
                || !local.workflow().equals("READY")
                || canonical.path("version").asLong() <= local.version())
              throw new ConflictException(
                  "Điều khoản tin cần được đối chiếu trước khi cập nhật trạng thái.");
            repo.jdbc()
                .update(
                    "UPDATE transfer_listings SET"
                        + " price=?,deadline=?,finance_terms=?::jsonb,version=?,updated_at=? WHERE"
                        + " id=?",
                    new java.math.BigDecimal(terms.path("amount").asText()),
                    Timestamp.from(java.time.Instant.parse(terms.path("deadline").asText())),
                    terms.toString(),
                    canonical.path("version").asLong(),
                    Timestamp.from(clock.instant()),
                    id);
          }
          repo.jdbc()
              .update(
                  "UPDATE transfer_listings SET"
                      + " state=?,workflow='READY',last_error=NULL,updated_at=? WHERE id=?",
                  next,
                  Timestamp.from(clock.instant()),
                  id);
        });
  }

  public JsonNode acquisition(UUID id) {
    var canonical = booking.acquisition(id, repo.acquisition(id).listingId());
    applyAcquisition(id, canonical);
    return canonical;
  }

  public void applyAcquisition(UUID id, JsonNode canonical) {
    var before = repo.acquisition(id);
    tx.executeWithoutResult(
        status -> {
          repo.lock(before.listingId());
          var local = repo.acquisition(id);
          var listing = repo.find(local.listingId());
          if (!PeerResaleContract.isPeer(local.financeTerms()))
            throw new ConflictException("Không dùng projection P2P cho giao dịch cũ.");
          if (canonical.path("phase").asText().equals("UNKNOWN")) {
            if (Boolean.TRUE.equals(
                repo.jdbc()
                    .queryForObject(
                        "SELECT peer_projection IS NOT NULL FROM transfer_acquisitions WHERE id=?",
                        Boolean.class,
                        id)))
              throw new ActionPolicyException(
                  503,
                  "PEER_AUTHORITY_COVERAGE_UNAVAILABLE",
                  "Chưa đối chiếu được phiên mua đã được ghi nhận. Vui lòng thử lại.",
                  Map.of());
            if (!local.expiresAt().isAfter(clock.instant()) && local.state().equals("PENDING"))
              repo.jdbc()
                  .update(
                      "UPDATE transfer_acquisitions SET state='EXPIRED',updated_at=? WHERE id=?",
                      Timestamp.from(clock.instant()),
                      id);
            else
              repo.jdbc()
                  .update(
                      "UPDATE transfer_acquisitions SET updated_at=? WHERE id=?",
                      Timestamp.from(clock.instant()),
                      id);
            return;
          }
          var attempt = canonical.path("attempt");
          PeerResaleTerms.validateAcquisition(canonical.path("terms"));
          if (!id.toString().equals(attempt.path("id").asText())
              || !local.listingId().toString().equals(attempt.path("listingId").asText())
              || !local.buyerId().toString().equals(attempt.path("buyerId").asText())
              || !local
                  .financeTerms()
                  .path("termsHash")
                  .asText()
                  .equals(attempt.path("termsHash").asText())
              || !repo.read(local.financeTerms().toString())
                  .equals(repo.read(canonical.path("terms").toString())))
            throw new ConflictException("Projection phiên mua không khớp điều khoản đã chấp nhận.");
          String phase = attempt.path("phase").asText();
          if (phase.equals("COMPLETED")
              && !Set.of("COUNTERPARTY_ACKNOWLEDGED", "OWNER_ADJUDICATED")
                  .contains(attempt.path("confirmationMethod").asText()))
            throw new ConflictException(
                "Bàn giao P2P cần xác nhận đúng bản chất giữa các bên hoặc quyết định quyền sân của"
                    + " Owner.");
          if (Set.of("EXPIRED", "ABORTED_A_RETAINS", "TIMED_OUT_A_RETAINS").contains(phase)) {
            if (local.state().equals("PENDING"))
              repo.jdbc()
                  .update(
                      "UPDATE transfer_acquisitions SET"
                          + " state='EXPIRED',peer_projection=?::jsonb,updated_at=? WHERE id=?",
                      attempt.toString(),
                      Timestamp.from(clock.instant()),
                      id);
            if (id.equals(listing.activeAcquisitionId()) && listing.state().equals("LOCKED"))
              repo.jdbc()
                  .update(
                      "UPDATE transfer_listings SET"
                          + " state=?,workflow='READY',active_acquisition_id=NULL,updated_at=?"
                          + " WHERE id=? AND active_acquisition_id=?",
                      listing.deadline().isAfter(clock.instant()) ? "ACTIVE" : "EXPIRED",
                      Timestamp.from(clock.instant()),
                      listing.id(),
                      id);
            return;
          }
          if (!Set.of(
                  "WAITING_PAYMENT", "PAYMENT_DECLARED", "DISPUTED", "HANDOFF_PENDING", "COMPLETED")
              .contains(phase))
            throw new ConflictException("Trạng thái quyền sử dụng chưa xác định.");
          if (listing.activeAcquisitionId() != null && !id.equals(listing.activeAcquisitionId()))
            throw new ConflictException("Tin đã gắn với phiên mua khác, cần đối chiếu.");
          if (listing.state().equals("ACTIVE"))
            repo.jdbc()
                .update(
                    "UPDATE transfer_listings SET"
                        + " state='LOCKED',workflow='READY',active_acquisition_id=?,updated_at=?"
                        + " WHERE id=?",
                    id,
                    Timestamp.from(clock.instant()),
                    listing.id());
          repo.jdbc()
              .update(
                  "UPDATE transfer_acquisitions SET peer_projection=?::jsonb,updated_at=? WHERE"
                      + " id=?",
                  attempt.toString(),
                  Timestamp.from(clock.instant()),
                  id);
          if (phase.equals("COMPLETED")) {
            if (local.state().equals("PENDING"))
              repo.jdbc()
                  .update(
                      "UPDATE transfer_acquisitions SET state='PENDING_HANDOFF' WHERE id=?", id);
            if (Set.of("PENDING", "PENDING_HANDOFF").contains(local.state()))
              repo.jdbc().update("UPDATE transfer_acquisitions SET state='SUCCESS' WHERE id=?", id);
            if (!listing.state().equals("COMPLETED"))
              repo.jdbc()
                  .update(
                      "UPDATE transfer_listings SET"
                          + " state='COMPLETED',workflow='READY',active_acquisition_id=?,updated_at=?"
                          + " WHERE id=?",
                      id,
                      Timestamp.from(clock.instant()),
                      listing.id());
          }
        });
  }

  @Scheduled(
      fixedDelayString = "${transfer.peer-recovery-ms:5000}",
      initialDelayString = "${transfer.peer-recovery-ms:5000}")
  public void recover() {
    var pending =
        repo.jdbc()
            .queryForList(
                "SELECT id FROM transfer_acquisitions WHERE protocol='PEER_RESALE_V1' AND state"
                    + " IN('PENDING','PENDING_HANDOFF') ORDER BY updated_at,id LIMIT 50",
                UUID.class);
    for (var id : pending)
      try {
        acquisition(id);
      } catch (RuntimeException ignored) {
        repo.jdbc()
            .update(
                "UPDATE transfer_acquisitions SET updated_at=? WHERE id=? AND"
                    + " protocol='PEER_RESALE_V1'",
                Timestamp.from(clock.instant()),
                id);
      }
    var listings =
        repo.jdbc()
            .queryForList(
                "SELECT id FROM transfer_listings WHERE protocol='PEER_RESALE_V1' AND"
                    + " state='ACTIVE' AND (workflow='REGISTER' OR deadline<=?) ORDER BY"
                    + " updated_at,id LIMIT 50",
                UUID.class,
                Timestamp.from(clock.instant()));
    for (var id : listings)
      try {
        listing(id);
      } catch (RuntimeException ignored) {
        repo.jdbc()
            .update(
                "UPDATE transfer_listings SET last_error='PEER_AUTHORITY_UNAVAILABLE',updated_at=?"
                    + " WHERE id=? AND protocol='PEER_RESALE_V1'",
                Timestamp.from(clock.instant()),
                id);
      }
  }
}
