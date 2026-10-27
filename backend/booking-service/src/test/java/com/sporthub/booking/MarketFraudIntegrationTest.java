package com.sporthub.booking;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.sporthub.booking.service.*;
import com.sporthub.common.exception.ActionPolicyException;
import com.sporthub.common.money.*;
import com.sporthub.common.security.RemoteIdentity.Caller;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class MarketFraudIntegrationTest extends PeerUsageIntegrationTest {
  @Autowired PeerMarketAccess market;
  @Autowired PeerMoneyActions money;

  void nextCourt() {
    court = UUID.randomUUID();
    start = now.plusSeconds(10800);
    end = start.plusSeconds(3600);
    var quote =
        ((com.fasterxml.jackson.databind.node.ObjectNode) dependencies.quote(input()).deepCopy())
            .put("courtId", court.toString())
            .put("startsAt", start.toString())
            .put("endsAt", end.toString());
    when(dependencies.quote(any())).thenReturn(quote);
    when(clock.instant()).thenReturn(now);
  }

  @Test
  void marketFraudCorrectionPreservesFindingAndUsageAndOnlyRemovesTheCorrectedRestriction() {
    var sale = sale(new BigDecimal("80000"));
    dispute(sale);
    var owner = venueOwner();
    adjudication.resolve(
        sale.acquisition(),
        new PeerResaleContract.Resolution(
            2, "BUYER_FRAUD", "Kết luận ban đầu dựa trên chứng cứ chưa đủ", List.of(sale.bill())),
        key(),
        owner,
        "token");
    var decision =
        repo.jdbc()
            .queryForMap(
                "SELECT * FROM peer_usage_decisions WHERE acquisition_id=?", sale.acquisition());
    var before = repo.find(sale.original().id());
    var correction =
        new PeerResaleContract.FraudCorrection(
            "Đã kiểm tra chứng cứ bổ sung, rút kết luận cố ý khai giả", List.of(sale.bill()));
    var request = key();
    assertThatThrownBy(
            () ->
                adjudication.correctFraud(
                    sale.acquisition(), correction, key(), sale.buyer(), "token"))
        .isInstanceOf(com.sporthub.common.exception.ForbiddenException.class);
    adjudication.correctFraud(sale.acquisition(), correction, request, owner, "token");
    adjudication.correctFraud(sale.acquisition(), correction, request, owner, "token");
    market.allowed(sale.buyer().id());
    assertThat(
            repo.jdbc()
                .queryForObject(
                    "SELECT strike_count FROM user_admission_restrictions WHERE principal_id=?",
                    Integer.class,
                    sale.buyer().id()))
        .isZero();
    assertThat(
            repo.jdbc()
                .queryForObject(
                    "SELECT count(*) FROM market_fraud_strikes WHERE acquisition_id=?",
                    Integer.class,
                    sale.acquisition()))
        .isEqualTo(1);
    assertThat(
            repo.jdbc()
                .queryForObject(
                    "SELECT count(*) FROM market_strike_corrections WHERE acquisition_id=?",
                    Integer.class,
                    sale.acquisition()))
        .isEqualTo(1);
    assertThat(
            repo.jdbc()
                .queryForMap(
                    "SELECT * FROM peer_usage_decisions WHERE acquisition_id=?",
                    sale.acquisition()))
        .isEqualTo(decision);
    assertThat(repo.find(sale.original().id())).isEqualTo(before);
    assertThatThrownBy(
            () ->
                repo.jdbc()
                    .update(
                        "DELETE FROM market_strike_corrections WHERE acquisition_id=?",
                        sale.acquisition()))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    assertThatThrownBy(
            () -> adjudication.correctFraud(sale.acquisition(), correction, key(), owner, "token"))
        .isInstanceOf(com.sporthub.common.exception.ConflictException.class);
  }

  @Test
  void marketFraudSecondSuspensionAndThirdPermanentBanPreservePaidUseAndOldMoneyCases() {
    var buyer =
        new Caller(UUID.randomUUID(), "B", Set.of("CUSTOMER"), Map.of(), "ACTIVE", true, false);
    var seller = customer;
    customer = buyer;
    var paid = pending();
    consumer.apply(event(paid, UUID.randomUUID(), buyer.id(), "BOOKING", null, paid.amount()));
    customer = seller;
    for (int strike = 1; strike <= 3; strike++) {
      nextCourt();
      var sale = sale(new BigDecimal("80000"), buyer);
      dispute(sale);
      var input =
          new PeerResaleContract.Resolution(
              2,
              "BUYER_FRAUD",
              "Đã đối chiếu chứng cứ, kết luận cố ý khai chuyển giả",
              List.of(sale.bill()));
      String request = key();
      adjudication.resolve(sale.acquisition(), input, request, venueOwner(), "token");
      adjudication.resolve(sale.acquisition(), input, request, venueOwner(), "token");
      var restriction =
          repo.jdbc()
              .queryForMap(
                  "SELECT * FROM user_admission_restrictions WHERE principal_id=?", buyer.id());
      assertThat(restriction.get("strike_count")).isEqualTo(strike);
      assertThat(restriction.get("market_permanent")).isEqualTo(strike == 3);
      assertThat(((Timestamp) restriction.get("market_blocked_until")).toInstant())
          .isEqualTo(now.plusSeconds(30L * 86400));
      assertThatThrownBy(() -> market.allowed(buyer.id()))
          .isInstanceOfSatisfying(
              ActionPolicyException.class,
              error -> assertThat(error.code()).isEqualTo("TRANSFER_MARKET_SUSPENDED"));
      money.report(
          sale.acquisition(),
          new PeerMoneyContract.Report(sale.hash(), "B-CASE-" + strike, List.of(sale.bill())),
          key(),
          buyer.id());
      assertThat(money.read(sale.acquisition(), buyer.id()).path("state").asText())
          .isEqualTo("OPEN");
      assertThat(service.detail(paid.id(), buyer, "token").booking().id()).isEqualTo(paid.id());
      if (strike == 1) {
        when(clock.instant()).thenReturn(paid.startsAt().minusSeconds(1));
        assertThat(
                service
                    .checkinByCode(
                        facility,
                        new com.sporthub.booking.web.BookingDtos.CodeInput(
                            repo.find(paid.id()).bookingCode()),
                        venueOwner(),
                        "token")
                    .status())
            .isEqualTo("CHECKED_IN");
      }
      now = now.plusSeconds(31L * 86400);
      when(clock.instant()).thenReturn(now);
      if (strike < 3) market.allowed(buyer.id());
      else
        assertThatThrownBy(() -> market.allowed(buyer.id()))
            .isInstanceOf(ActionPolicyException.class);
    }
    assertThat(
            repo.jdbc()
                .queryForObject(
                    "SELECT count(*) FROM market_fraud_strikes WHERE principal_id=?",
                    Integer.class,
                    buyer.id()))
        .isEqualTo(3);
  }
}
