package com.sporthub.payment.web;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sporthub.common.exception.ForbiddenException;
import com.sporthub.common.security.RemoteIdentity;
import com.sporthub.common.security.RemoteIdentity.Caller;
import com.sporthub.payment.service.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class PeerCaseRecipientTest {
  @Test
  void onlyCurrentCaseOwnerCanResolveTheFrozenMaskedSellerBankRevision() throws Exception {
    var identity = mock(RemoteIdentity.class);
    var authority = mock(PeerQrContextClient.class);
    var banks = mock(BankDestinations.class);
    var controller = new PeerCaseRecipientController(identity, authority, banks);
    var owner = new Caller(UUID.randomUUID(), "Owner", Set.of("OWNER"), Map.of());
    UUID acquisition = UUID.randomUUID(), seller = UUID.randomUUID(), revision = UUID.randomUUID();
    var request = new MockHttpServletRequest();
    request.addHeader("Authorization", "Bearer owner");
    when(identity.current(request)).thenReturn(owner);
    var json = new ObjectMapper().findAndRegisterModules();
    var grant =
        json.createObjectNode()
            .put("actorId", owner.id().toString())
            .put("acquisitionId", acquisition.toString())
            .put("sellerId", seller.toString())
            .put("recipientRevisionId", revision.toString())
            .put("destinationHash", "a".repeat(64))
            .put("canReadEvidence", true);
    when(authority.caseAccess(acquisition, "Bearer owner")).thenReturn(grant);
    var destination =
        new BankDestinations.Destination(
            revision,
            seller,
            null,
            "970436",
            "VCB",
            "***6789",
            "A",
            "a".repeat(64),
            Instant.now(),
            "SELF_DECLARED");
    when(banks.owned(revision, seller, null)).thenReturn(destination);
    assertThat(json.writeValueAsString(controller.recipient(acquisition, request)))
        .contains("***6789", "SELF_DECLARED")
        .doesNotContain("accountNumber", "account_ciphertext");
    grant.put("actorId", UUID.randomUUID().toString());
    assertThatThrownBy(() -> controller.recipient(acquisition, request))
        .isInstanceOf(ForbiddenException.class);
    when(identity.current(request))
        .thenReturn(new Caller(UUID.randomUUID(), "Staff", Set.of("STAFF"), Map.of()));
    assertThatThrownBy(() -> controller.recipient(acquisition, request))
        .isInstanceOf(ForbiddenException.class);
    verify(banks, times(1)).owned(revision, seller, null);
    verify(banks, never()).privateDestination(any(), any(), any());
  }
}
