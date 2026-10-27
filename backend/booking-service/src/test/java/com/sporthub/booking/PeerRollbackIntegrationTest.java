package com.sporthub.booking;

import static org.assertj.core.api.Assertions.*;

import com.sporthub.booking.service.PeerListingAuthority;
import com.sporthub.common.exception.ActionPolicyException;
import com.sporthub.common.money.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = "SPORTHUB_PEER_RESALE_FACILITIES=")
class PeerRollbackIntegrationTest extends PeerUsageIntegrationTest {
  @Autowired PeerListingAuthority listings;

  @Test
  void rollbackDisablesNewTermsButAnAcceptedAttemptCanStillDeclareAndComplete() {
    var sale = sale();
    assertThatThrownBy(
            () ->
                listings.quote(
                    new PeerMarketContract.Quote(
                        sale.original().id(),
                        sale.original().amount(),
                        start.minusSeconds(60),
                        UUID.randomUUID()),
                    customer))
        .isInstanceOfSatisfying(
            ActionPolicyException.class,
            error -> assertThat(error.code()).isEqualTo("PEER_RESALE_NOT_ENABLED"));
    declare(sale);
    peer.confirm(
        sale.acquisition(), new PeerResaleContract.Command(1, sale.hash()), key(), customer.id());
    assertThat(repo.find(sale.original().id()).currentHolderId()).isEqualTo(sale.buyer().id());
    noFacilityResaleMoney();
  }
}
