package com.sporthub.booking;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.sporthub.booking.service.PeerMoneyActions;
import com.sporthub.common.exception.*;
import com.sporthub.common.money.*;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class PeerMoneyIntegrationTest extends PeerUsageIntegrationTest {
  @Autowired PeerMoneyActions money;

  private void retain(Sale sale) {
    adjudication.resolve(
        sale.acquisition(),
        new PeerResaleContract.Resolution(
            2,
            "SELLER_RETAINS",
            "Chốt quyền sân cho A, không quyết định tiền",
            List.of(sale.bill())),
        key(),
        venueOwner(),
        "token");
  }

  private PeerMoneyContract.ReturnCommand returned(Sale sale, String amount) {
    return new PeerMoneyContract.ReturnCommand(
        UUID.randomUUID(),
        new PeerMoneyContract.ReturnInput(
            sale.hash(),
            new BigDecimal(amount),
            "A RETURN " + UUID.randomUUID(),
            List.of(UUID.randomUUID())));
  }

  private void bankProfiles(Sale sale) {
    for (UUID user : List.of(customer.id(), sale.buyer().id()))
      bankProfiles.activate(
          new BankProfileContract.Activation(
              user, UUID.randomUUID(), "b".repeat(64), 0, false, key(), "c".repeat(64)));
  }

  @Test
  void peerMoneyLateReportExpiresOldUsageAndDoesNotInventPaymentOrBankLockFromFinalCase() {
    var sale = sale();
    bankProfiles(sale);
    var before = repo.find(sale.original().id());
    when(clock.instant()).thenReturn(now.plusSeconds(301));
    var report = new PeerMoneyContract.Report(sale.hash(), "LATE BANK", List.of(sale.bill()));
    String key = key();
    var result = money.report(sale.acquisition(), report, key, sale.buyer().id());
    assertThat(result.path("phase").asText()).isEqualTo("EXPIRED");
    assertThat(money.report(sale.acquisition(), report, key, sale.buyer().id())).isEqualTo(result);
    assertThat(money.read(sale.acquisition(), sale.buyer().id()).path("reports")).hasSize(1);
    assertThat(repo.find(before.id()).currentHolderId()).isEqualTo(customer.id());
    assertThat(repo.find(before.id()).checkinTokenVersion())
        .isEqualTo(before.checkinTokenVersion());
    assertThat(bankProfiles.status(sale.buyer().id(), true).responsibilities()).isEmpty();
    assertThat(bankProfiles.status(customer.id(), true).responsibilities())
        .extracting(BankProfileContract.Responsibility::kind)
        .doesNotContain("PEER_DISPUTE");
    assertThatThrownBy(() -> money.report(sale.acquisition(), report, key(), owner))
        .isInstanceOf(ForbiddenException.class);
    noFacilityResaleMoney();
  }

  @Test
  void peerMoneyOnlyBuyerAckClosesItsReturnAndPartialAmountDoesNotCloseTheWholeCase() {
    var sale = sale();
    bankProfiles(sale);
    dispute(sale);
    retain(sale);
    when(clock.instant()).thenReturn(end);
    var first = returned(sale, "40000");
    String declaredKey = key();
    money.declareReturn(sale.acquisition(), first, declaredKey, customer.id());
    money.declareReturn(sale.acquisition(), first, declaredKey, customer.id());
    assertThatThrownBy(
            () ->
                money.declareReturn(
                    sale.acquisition(),
                    new PeerMoneyContract.ReturnCommand(UUID.randomUUID(), first.input()),
                    key(),
                    customer.id()))
        .isInstanceOfSatisfying(
            ActionPolicyException.class,
            error -> assertThat(error.code()).isEqualTo("PEER_RETURN_ALREADY_REPORTED"));
    assertThat(bankProfiles.status(customer.id(), true).canChange()).isFalse();
    assertThat(bankProfiles.status(sale.buyer().id(), true).canChange()).isFalse();
    assertThatThrownBy(
            () ->
                money.acknowledge(
                    sale.acquisition(),
                    new PeerMoneyContract.Acknowledge(first.returnId(), 0, sale.hash()),
                    key(),
                    customer.id()))
        .isInstanceOf(ForbiddenException.class);
    String ackKey = key();
    var ack = new PeerMoneyContract.Acknowledge(first.returnId(), 0, sale.hash());
    money.acknowledge(sale.acquisition(), ack, ackKey, sale.buyer().id());
    money.acknowledge(sale.acquisition(), ack, ackKey, sale.buyer().id());
    assertThat(money.read(sale.acquisition(), customer.id()).path("state").asText())
        .isEqualTo("OPEN");
    assertThat(bankProfiles.status(customer.id(), true).canChange()).isTrue();
    assertThatThrownBy(
            () ->
                money.declareReturn(
                    sale.acquisition(), returned(sale, "40001"), key(), customer.id()))
        .isInstanceOf(ConflictException.class);
    var second = returned(sale, "40000");
    money.declareReturn(sale.acquisition(), second, key(), customer.id());
    money.acknowledge(
        sale.acquisition(),
        new PeerMoneyContract.Acknowledge(second.returnId(), 0, sale.hash()),
        key(),
        sale.buyer().id());
    var closed = money.read(sale.acquisition(), customer.id());
    assertThat(closed.path("state").asText()).isEqualTo("CLOSED_RETURNED");
    assertThat(closed.path("returns")).hasSize(2);
    var page = money.read(sale.acquisition(), customer.id(), null, 1);
    assertThat(page.path("returns")).hasSize(1);
    assertThat(page.path("returnPage").path("more").asBoolean()).isTrue();
    assertThat(page.path("totals").path("received").asText()).isEqualTo("80000");
    var next =
        money.read(
            sale.acquisition(),
            customer.id(),
            UUID.fromString(page.path("returnPage").path("nextAfter").asText()),
            1);
    assertThat(next.path("returns")).hasSize(1);
    assertThat(next.path("returns").get(0).path("id").asText())
        .isNotEqualTo(page.path("returns").get(0).path("id").asText());
    assertThat(next.path("returnPage").path("more").asBoolean()).isFalse();
    assertThatThrownBy(() -> money.read(sale.acquisition(), customer.id(), null, 101))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(closed.toString()).doesNotContain("BANK_VERIFIED", "feeBps", "refundRate");
    assertThat(bankProfiles.status(sale.buyer().id(), true).canChange()).isTrue();
    assertThatThrownBy(
            () -> money.declareReturn(sale.acquisition(), returned(sale, "1"), key(), owner))
        .isInstanceOf(ForbiddenException.class);
    noFacilityResaleMoney();
  }

  @Test
  void peerMoneyMutualNoClaimWaivesPendingReturnOnlyAfterBothPartiesAndPreservesUsage() {
    var sale = sale();
    bankProfiles(sale);
    dispute(sale);
    retain(sale);
    when(clock.instant()).thenReturn(end);
    var returned = returned(sale, "80000");
    money.declareReturn(sale.acquisition(), returned, key(), customer.id());
    var noClaim = new PeerMoneyContract.NoClaim(sale.hash());
    money.noClaim(sale.acquisition(), noClaim, key(), customer.id());
    assertThat(bankProfiles.status(customer.id(), true).canChange()).isFalse();
    assertThat(
            money
                .read(sale.acquisition(), customer.id())
                .path("returns")
                .get(0)
                .path("state")
                .asText())
        .isEqualTo("AWAITING_BUYER");
    assertThatThrownBy(() -> money.noClaim(sale.acquisition(), noClaim, key(), owner))
        .isInstanceOf(ForbiddenException.class);
    money.noClaim(sale.acquisition(), noClaim, key(), sale.buyer().id());
    var closed = money.read(sale.acquisition(), customer.id());
    assertThat(closed.path("state").asText()).isEqualTo("CLOSED_MUTUAL");
    assertThat(closed.path("returns").get(0).path("state").asText()).isEqualTo("WAIVED_BY_BOTH");
    assertThat(closed.path("returns").get(0).path("confirmationMethod").asText()).isEqualTo("NONE");
    assertThat(bankProfiles.status(customer.id(), true).canChange()).isTrue();
    assertThat(peerStore.attempt(sale.acquisition()).phase()).isEqualTo("ABORTED_A_RETAINS");
    assertThat(repo.find(sale.original().id()).currentHolderId()).isEqualTo(customer.id());
    noFacilityResaleMoney();
  }

  @Test
  void peerMoneyReportAfterSuccessfulHandoffCannotFlipHolderCodeOrResaleRight() {
    var sale = sale();
    declare(sale);
    peer.confirm(
        sale.acquisition(), new PeerResaleContract.Command(1, sale.hash()), key(), customer.id());
    var handed = repo.find(sale.original().id());
    money.report(
        sale.acquisition(),
        new PeerMoneyContract.Report(sale.hash(), "Late evidence", List.of(sale.bill())),
        key(),
        sale.buyer().id());
    assertThat(peerStore.attempt(sale.acquisition()).phase()).isEqualTo("COMPLETED");
    assertThat(repo.find(handed.id()).currentHolderId()).isEqualTo(sale.buyer().id());
    assertThat(repo.find(handed.id()).checkinTokenVersion())
        .isEqualTo(handed.checkinTokenVersion());
    assertThat(repo.find(handed.id()).bookingCode()).isEqualTo(handed.bookingCode());
    assertThatThrownBy(() -> peerHandoffs.requireUnused(handed.id()))
        .isInstanceOf(ConflictException.class);
    noFacilityResaleMoney();
  }
}
