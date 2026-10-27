package com.sporthub.booking;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.sporthub.booking.service.PeerMoneyActions;
import com.sporthub.common.money.PeerMoneyContract;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class PeerLateMoneyLeaseTest extends PeerMarketIntegrationTest {
  @Autowired PeerMoneyActions money;

  @Test
  void peerLateMoneyReportLeavesTheNextBuyersLeaseAndQuotaUntouched() {
    var sale = listing();
    var firstBuyer = buyer();
    var first = input(sale);
    var acquired = acquisitions.acquire(first, key(), firstBuyer);
    when(clock.instant()).thenReturn(now.plusSeconds(301));
    acquisitions.status(first.acquisitionId(), sale.listing());
    var nextBuyer = buyer();
    var next = input(sale);
    acquisitions.acquire(next, key(), nextBuyer);
    money.report(
        first.acquisitionId(),
        new PeerMoneyContract.Report(
            acquired.path("terms").path("termsHash").asText(),
            "LATE BILL",
            List.of(UUID.randomUUID())),
        key(),
        firstBuyer.id());
    var lease =
        repo.jdbc()
            .queryForMap(
                "SELECT state,acquisition_id,buyer_id FROM transfer_leases WHERE listing_id=?",
                sale.listing());
    assertThat(lease)
        .containsEntry("state", "LOCKED")
        .containsEntry("acquisition_id", next.acquisitionId())
        .containsEntry("buyer_id", nextBuyer.id());
    assertThat(attempts.attempt(first.acquisitionId()).phase()).isEqualTo("EXPIRED");
    assertThat(attempts.attempt(next.acquisitionId()).phase()).isEqualTo("WAITING_PAYMENT");
    assertThat((List<?>) admission.status(firstBuyer.id()).get("resources")).isEmpty();
    assertThat((List<?>) admission.status(nextBuyer.id()).get("resources")).hasSize(1);
    assertThat(repo.find(sale.booking().id()).currentHolderId()).isEqualTo(customer.id());
    assertThat(
            repo.jdbc()
                .queryForObject(
                    "SELECT count(*) FROM facility_disbursement_obligations WHERE facility_id=?",
                    Integer.class,
                    facility))
        .isZero();
  }
}
