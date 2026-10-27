package com.sporthub.payment.web;

import com.sporthub.common.dto.ApiResponse;
import com.sporthub.common.exception.ForbiddenException;
import com.sporthub.common.security.RemoteIdentity;
import com.sporthub.payment.service.*;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;

@RestController
public class PeerCaseRecipientController {
  private final RemoteIdentity identity;
  private final PeerQrContextClient authority;
  private final BankDestinations banks;

  public PeerCaseRecipientController(
      RemoteIdentity identity, PeerQrContextClient authority, BankDestinations banks) {
    this.identity = identity;
    this.authority = authority;
    this.banks = banks;
  }

  @GetMapping("/api/v1/payments/peer-acquisitions/{id}/case-recipient")
  public ApiResponse<BankDestinations.Destination> recipient(
      @PathVariable UUID id, HttpServletRequest request) {
    var actor = identity.current(request);
    actor.requireRole("OWNER");
    var grant = authority.caseAccess(id, request.getHeader("Authorization"));
    if (!id.toString().equals(grant.path("acquisitionId").asText())
        || !actor.id().toString().equals(grant.path("actorId").asText())
        || !grant.path("canReadEvidence").isBoolean()
        || !grant.path("canReadEvidence").asBoolean())
      throw new ForbiddenException("Bạn không có quyền xem bên nhận tiền của vụ việc này.");
    var recipient =
        banks.owned(
            UUID.fromString(grant.path("recipientRevisionId").asText()),
            UUID.fromString(grant.path("sellerId").asText()),
            null);
    if (!recipient.destinationHash().equals(grant.path("destinationHash").asText()))
      throw new ForbiddenException("Thông tin nhận tiền không khớp điều khoản vụ việc.");
    return ApiResponse.success(recipient);
  }
}
