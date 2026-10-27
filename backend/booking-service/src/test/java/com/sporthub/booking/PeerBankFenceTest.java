package com.sporthub.booking;

import com.sporthub.common.money.BankProfileContract.Activation;
import java.util.*;
import org.junit.jupiter.api.Test;

class PeerBankFenceTest extends PeerMarketIntegrationTest {
  @Test
  void fencePeerBuyerAdmissionPrecedesCourtAndCannotRaceAPersonalBankReplacement()
      throws Exception {
    var sale = listing();
    var buyer = buyer();
    banks.activate(
        new Activation(
            buyer.id(), UUID.randomUUID(), "b".repeat(64), 0, false, key(), "c".repeat(64)));
    var command = input(sale);
    BankFenceProbe.ordered(
        repo, manager, banks, court, buyer.id(), () -> acquisitions.acquire(command, key(), buyer));
  }
}
