package com.sporthub.transfer.service;

import com.sporthub.common.exception.*;
import com.sporthub.common.money.PeerResaleContract;
import com.sporthub.common.security.RemoteIdentity.Caller;
import com.sporthub.transfer.repository.TransferRepository;
import com.sporthub.transfer.web.TransferDtos.Acquisition;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class PeerEvidenceAccess {
  public record Context(Acquisition acquisition, TransferRepository.Row listing) {}

  private final TransferRepository repo;
  private final PeerBookingClient booking;

  public PeerEvidenceAccess(TransferRepository repo, PeerBookingClient booking) {
    this.repo = repo;
    this.booking = booking;
  }

  public Context context(UUID id) {
    var acquisition = repo.acquisition(id);
    if (!PeerResaleContract.isPeer(acquisition.financeTerms()))
      throw new ConflictException("Phiên này không dùng chứng cứ chuyển nhượng P2P.");
    if (!Boolean.TRUE.equals(
        repo.jdbc()
            .queryForObject(
                "SELECT peer_projection IS NOT NULL FROM transfer_acquisitions WHERE id=?",
                Boolean.class,
                id)))
      throw new ActionPolicyException(
          503,
          "PEER_PENDING_SYNCHRONIZATION",
          "Phiên mua đang chờ xác nhận. Vui lòng thử lại sau.",
          Map.of());
    return new Context(acquisition, repo.find(acquisition.listingId()));
  }

  public Context participant(UUID id, Caller actor) {
    actor.requireRole("CUSTOMER");
    var context = context(id);
    if (!party(context, actor.id())) throw new ForbiddenException("Bạn không thuộc phiên mua này.");
    return context;
  }

  public Context view(UUID id, Caller actor, String token) {
    var context = context(id);
    if (actor.hasRole("CUSTOMER") && party(context, actor.id())) return context;
    actor.requireRole("OWNER");
    var grant = booking.evidenceAccess(id, token);
    if (!id.toString().equals(grant.path("acquisitionId").asText())
        || !actor.id().toString().equals(grant.path("actorId").asText())
        || !context.listing().facilityId().toString().equals(grant.path("facilityId").asText())
        || !grant.path("canReadEvidence").isBoolean()
        || !grant.path("canReadEvidence").booleanValue())
      throw new ForbiddenException("Vụ việc không thuộc cơ sở do bạn sở hữu.");
    return context;
  }

  private static boolean party(Context context, UUID actor) {
    return actor.equals(context.acquisition().buyerId())
        || actor.equals(context.listing().sellerId());
  }
}
