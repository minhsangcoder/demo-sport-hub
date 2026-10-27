package com.sporthub.booking;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.sporthub.booking.web.BookingDtos.CodeInput;
import com.sporthub.common.exception.*;
import com.sporthub.common.money.PeerResaleContract;
import com.sporthub.common.security.RemoteIdentity.Caller;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;

class PeerUsageRaceTest extends PeerUsageIntegrationTest {
  @org.springframework.beans.factory.annotation.Autowired
  com.sporthub.booking.service.BookingUsage usage;

  @Test
  void peerRaceCodeResolvedBeforeHandoffIsRevalidatedAfterTakingTheCourtLock() {
    var sale = sale();
    declare(sale);
    String resolvedCode = sale.original().bookingCode();
    peer.confirm(
        sale.acquisition(), new PeerResaleContract.Command(1, sale.hash()), key(), customer.id());
    when(clock.instant()).thenReturn(start.minusSeconds(60));
    var staff =
        new Caller(
            UUID.randomUUID(),
            "Staff",
            Set.of("STAFF"),
            Map.of(facility, Set.of("BOOKING_CHECK_IN")));
    var tx = new org.springframework.transaction.support.TransactionTemplate(manager);
    assertThatThrownBy(
            () ->
                tx.execute(
                    status ->
                        usage.checkin(sale.original().id(), resolvedCode, staff, "token", true)))
        .isInstanceOf(ResourceNotFoundException.class);
    assertThat(repo.find(sale.original().id()).currentHolderId()).isEqualTo(sale.buyer().id());
    assertThat(repo.find(sale.original().id()).status()).isEqualTo("CONFIRMED");
    assertThat(
            service
                .checkinByCode(
                    facility,
                    new CodeInput(repo.find(sale.original().id()).bookingCode()),
                    staff,
                    "token")
                .status())
        .isEqualTo("CHECKED_IN");
  }

  private List<Boolean> race(Callable<?>... actions) throws Exception {
    var gate = new CountDownLatch(1);
    try (var pool = Executors.newFixedThreadPool(actions.length)) {
      var jobs = new ArrayList<Future<Boolean>>();
      for (var action : actions)
        jobs.add(
            pool.submit(
                () -> {
                  gate.await();
                  try {
                    action.call();
                    return true;
                  } catch (ConflictException
                      | ForbiddenException
                      | ResourceNotFoundException expected) {
                    return false;
                  }
                }));
      gate.countDown();
      var results = new ArrayList<Boolean>();
      for (var job : jobs) results.add(job.get(10, TimeUnit.SECONDS));
      return results;
    }
  }

  @Test
  void peerRaceThirdCanonicalFraudFindingCreatesAPermanentMarketBan() {
    var sale = sale();
    dispute(sale);
    for (int n = 0; n < 2; n++) {
      UUID past = UUID.randomUUID();
      repo.jdbc()
          .update(
              "INSERT INTO"
                  + " peer_usage_attempts(acquisition_id,listing_id,booking_id,facility_id,court_id,seller_id,buyer_id,amount,currency,starts_at,ends_at,bank_revision_id,destination_hash,refund_policy_hash,terms_hash,pay_by,phase,confirmation_method,created_at,updated_at)"
                  + " SELECT"
                  + " ?,listing_id,booking_id,facility_id,court_id,seller_id,buyer_id,amount,currency,starts_at,ends_at,bank_revision_id,destination_hash,refund_policy_hash,terms_hash,pay_by,'ABORTED_A_RETAINS','OWNER_ADJUDICATED',created_at,updated_at"
                  + " FROM peer_usage_attempts WHERE acquisition_id=?",
              past,
              sale.acquisition());
      repo.jdbc()
          .update(
              "INSERT INTO"
                  + " peer_usage_decisions(acquisition_id,booking_id,listing_id,seller_id,buyer_id,amount,currency,terms_hash,decision,confirmation_method,actor_id,reason,command_fingerprint,booking_version,decided_at)"
                  + " SELECT"
                  + " acquisition_id,booking_id,listing_id,seller_id,buyer_id,amount,currency,terms_hash,'ABORTED_A_RETAINS','OWNER_ADJUDICATED',?,'Previous"
                  + " intentional fraud',?,0,? FROM peer_usage_attempts WHERE acquisition_id=?",
              owner,
              "c".repeat(64),
              java.sql.Timestamp.from(now.minusSeconds((n + 1) * 40L * 86400)),
              past);
      repo.jdbc()
          .update(
              "INSERT INTO"
                  + " market_fraud_strikes(acquisition_id,principal_id,actor_id,reason,evidence,recorded_at)"
                  + " VALUES(?,?,?,'Previous intentional fraud','{}'::jsonb,?)",
              past,
              sale.buyer().id(),
              owner,
              java.sql.Timestamp.from(now.minusSeconds((n + 1) * 40L * 86400)));
    }
    var input =
        new PeerResaleContract.Resolution(
            2, "BUYER_FRAUD", "Đã xác định khai giả lần thứ ba", List.of(sale.bill()));
    String request = key();
    var finalResult =
        adjudication.resolve(sale.acquisition(), input, request, venueOwner(), "token");
    assertThat(adjudication.resolve(sale.acquisition(), input, request, venueOwner(), "token"))
        .isEqualTo(finalResult);
    var restriction =
        repo.jdbc()
            .queryForMap(
                "SELECT strike_count,market_permanent FROM user_admission_restrictions WHERE"
                    + " principal_id=?",
                sale.buyer().id());
    assertThat(restriction)
        .containsEntry("strike_count", 3)
        .containsEntry("market_permanent", true);
    noFacilityResaleMoney();
  }

  @Test
  void peerRaceAtEndsAtPresenceCannotAuthorizePastUsageOrReopenTheFinalDecision() {
    var sale = sale();
    dispute(sale);
    adjudication.presence(
        sale.acquisition(),
        new PeerResaleContract.Presence("BUYER", "Đã có mặt trước giờ"),
        key(),
        venueOwner(),
        "token");
    when(clock.instant()).thenReturn(end);
    var result =
        adjudication.resolve(
            sale.acquisition(),
            new PeerResaleContract.Resolution(
                3, "AWARD_BUYER", "Không cộng thời gian", List.of(sale.bill())),
            key(),
            venueOwner(),
            "token");
    assertThat(result.path("phase").asText()).isEqualTo("ABORTED_A_RETAINS");
    assertThat(repo.find(sale.original().id()).currentHolderId()).isEqualTo(customer.id());
    assertThat(repo.find(sale.original().id()).endsAt()).isEqualTo(end);
    assertThat(repo.find(sale.original().id()).checkinTokenVersion())
        .isEqualTo(sale.original().checkinTokenVersion());
    assertThatThrownBy(
            () ->
                adjudication.resolve(
                    sale.acquisition(),
                    new PeerResaleContract.Resolution(
                        4, "AWARD_BUYER", "Đã chốt", List.of(sale.bill())),
                    key(),
                    venueOwner(),
                    "token"))
        .isInstanceOf(ConflictException.class);
    noFacilityResaleMoney();
  }

  @Test
  void peerRaceExactStartPresenceAndDefaultHaveOneOrderedOutcome() throws Exception {
    var sale = sale();
    dispute(sale);
    when(clock.instant()).thenReturn(start);
    race(
        () ->
            adjudication.presence(
                sale.acquisition(),
                new PeerResaleContract.Presence("BUYER", "Có mặt đúng giờ"),
                key(),
                venueOwner(),
                "token"),
        () -> {
          timeouts.tick();
          return null;
        });
    var phase = peerStore.attempt(sale.acquisition()).phase();
    int presence =
        repo.jdbc()
            .queryForObject(
                "SELECT count(*) FROM peer_attendance WHERE acquisition_id=?",
                Integer.class,
                sale.acquisition());
    assertThat(phase).isEqualTo(presence == 1 ? "DISPUTED" : "TIMED_OUT_A_RETAINS");
    assertThat(repo.find(sale.original().id()).currentHolderId()).isEqualTo(customer.id());
    assertThat(repo.find(sale.original().id()).status()).isEqualTo("CONFIRMED");
    noFacilityResaleMoney();
  }

  @Test
  void peerRaceReceiptAckComplaintAndOldCodeCannotCheckInThePreviousHolder() throws Exception {
    var sale = sale();
    declare(sale);
    when(clock.instant()).thenReturn(start.minusSeconds(60));
    var staff =
        new Caller(
            UUID.randomUUID(),
            "Staff",
            Set.of("STAFF"),
            Map.of(facility, Set.of("BOOKING_CHECK_IN")));
    var input = new PeerResaleContract.Command(1, sale.hash());
    var results =
        race(
            () -> peer.confirm(sale.acquisition(), input, key(), customer.id()),
            () ->
                peer.dispute(
                    sale.acquisition(),
                    input,
                    "Người mua yêu cầu phân xử",
                    key(),
                    sale.buyer().id()),
            () ->
                service.checkinByCode(
                    facility, new CodeInput(sale.original().bookingCode()), staff, "token"));
    assertThat(results.get(2)).isFalse();
    assertThat(results.subList(0, 2)).containsExactlyInAnyOrder(true, false);
    var booking = repo.find(sale.original().id());
    assertThat(booking.status()).isEqualTo("CONFIRMED");
    assertThat(booking.currentHolderId())
        .isEqualTo(results.getFirst() ? sale.buyer().id() : customer.id());
    assertThat(peerStore.attempt(sale.acquisition()).phase())
        .isEqualTo(results.getFirst() ? "COMPLETED" : "DISPUTED");
    noFacilityResaleMoney();
  }

  @Test
  void peerRaceOppositeOwnerDecisionsConsumeAtMostOneRightAndNeverCreateMoney() throws Exception {
    var sale = sale();
    dispute(sale);
    var grant =
        new PeerResaleContract.Resolution(
            2, "AWARD_BUYER", "B được quyền sân", List.of(sale.bill()));
    var retain =
        new PeerResaleContract.Resolution(
            2, "SELLER_RETAINS", "A giữ quyền sân", List.of(sale.bill()));
    var results =
        race(
            () -> adjudication.resolve(sale.acquisition(), grant, key(), venueOwner(), "token"),
            () -> adjudication.resolve(sale.acquisition(), retain, key(), venueOwner(), "token"));
    assertThat(results).containsExactlyInAnyOrder(true, false);
    assertThat(
            repo.jdbc()
                .queryForObject(
                    "SELECT count(*) FROM peer_usage_decisions WHERE acquisition_id=?",
                    Integer.class,
                    sale.acquisition()))
        .isEqualTo(1);
    var booking = repo.find(sale.original().id());
    assertThat(booking.currentHolderId())
        .isEqualTo(results.getFirst() ? sale.buyer().id() : customer.id());
    assertThat(booking.checkinTokenVersion())
        .isEqualTo(sale.original().checkinTokenVersion() + (results.getFirst() ? 1 : 0));
    noFacilityResaleMoney();
  }
}
