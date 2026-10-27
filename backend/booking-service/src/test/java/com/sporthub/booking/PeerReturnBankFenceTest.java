package com.sporthub.booking;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.sporthub.common.money.*;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;

class PeerReturnBankFenceTest extends PeerMoneyIntegrationTest {
  @Test
  void fenceNewAcknowledgedReturnLocksAgainAfterFinalUsageAndCalendarSourcesHaveEnded()
      throws Exception {
    var sale = sale();
    dispute(sale);
    adjudication.resolve(
        sale.acquisition(),
        new PeerResaleContract.Resolution(
            2, "SELLER_RETAINS", "Final usage only", List.of(sale.bill())),
        key(),
        venueOwner(),
        "token");
    when(clock.instant()).thenReturn(end);
    bankProfiles.activate(
        new BankProfileContract.Activation(
            customer.id(), UUID.randomUUID(), "b".repeat(64), 0, false, key(), "c".repeat(64)));
    assertThat(bankProfiles.status(customer.id(), true).canChange()).isTrue();
    var command =
        new PeerMoneyContract.ReturnCommand(
            UUID.randomUUID(),
            new PeerMoneyContract.ReturnInput(
                sale.hash(), new BigDecimal("40000"), "A-RETURN-FENCE", List.of(sale.bill())));
    BankFenceProbe.ordered(
        repo,
        manager,
        bankProfiles,
        court,
        customer.id(),
        () -> money.declareReturn(sale.acquisition(), command, key(), customer.id()));
  }
}
