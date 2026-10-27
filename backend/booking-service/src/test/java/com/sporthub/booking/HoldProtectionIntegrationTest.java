package com.sporthub.booking;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.sporthub.booking.service.HoldProtection;
import com.sporthub.common.exception.ActionPolicyException;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.support.TransactionTemplate;

class HoldProtectionIntegrationTest extends HoldProtectionTestSupport {
  @Autowired HoldProtection protection;
  @Autowired StringRedisTemplate redis;

  @BeforeEach
  void futureInterval() {
    start = now.plusSeconds(172800);
    end = start.plusSeconds(3600);
    var quote = dependencies.quote(input());
    ((com.fasterxml.jackson.databind.node.ObjectNode) quote)
        .put("startsAt", start.toString())
        .put("endsAt", end.toString());
  }

  void cancelAt(long seconds) {
    when(clock.instant()).thenReturn(now.plusSeconds(seconds));
    var hold = service.hold(input(), key(), customer, "token", false);
    service.releaseHold(hold.id(), customer, "token");
  }

  @Test
  void policyThirdAndFifthAbandonmentUseServerDeadlinesAndRebuildTheRedisWindow() {
    cancelAt(0);
    cancelAt(901);
    cancelAt(1802);
    when(clock.instant()).thenReturn(now.plusSeconds(1803));
    assertThatThrownBy(() -> service.hold(input(), key(), customer, "token", false))
        .isInstanceOfSatisfying(
            ActionPolicyException.class,
            error -> {
              assertThat(error.getMessage()).contains("ba lượt");
              assertThat(error.details().get("retryAt")).isEqualTo(now.plusSeconds(3602));
            });
    assertThat(redis.opsForZSet().zCard("booking:hold-policy:{" + customer.id() + "}:window"))
        .isEqualTo(3);
    var status=admission.status(customer.id());
    assertThat(status.get("policyAvailability")).isEqualTo("COMPLETE");
    assertThat(status.get("holdBlockedUntil")).isEqualTo(now.plusSeconds(3602));
    cancelAt(3603);
    cancelAt(5404);
    when(clock.instant()).thenReturn(now.plusSeconds(5405));
    assertThatThrownBy(() -> service.hold(input(), key(), customer, "token", false))
        .isInstanceOfSatisfying(
            ActionPolicyException.class,
            error -> assertThat(error.details().get("retryAt")).isEqualTo(now.plusSeconds(91804)));
    redis.delete("booking:hold-policy:{" + customer.id() + "}:revision");
    assertThatThrownBy(() -> service.hold(input(), key(), customer, "token", false))
        .isInstanceOf(ActionPolicyException.class);
    assertThat(redis.opsForZSet().zCard("booking:hold-policy:{" + customer.id() + "}:window"))
        .isEqualTo(5);
    assertThat(
            repo.jdbc()
                .queryForObject(
                    "SELECT count(*) FROM booking_abandonment_events WHERE principal_id=?",
                    Integer.class,
                    customer.id()))
        .isEqualTo(5);
  }

  @Test
  void policyLateWorkerExpiryIsCommittedEvenWhenTheNextAdmissionIsRejected() {
    cancelAt(0);
    cancelAt(901);
    when(clock.instant()).thenReturn(now.plusSeconds(1802));
    var held = service.hold(input(), key(), customer, "token", false);
    when(clock.instant()).thenReturn(held.expiresAt().plusSeconds(1));
    assertThatThrownBy(() -> service.hold(input(), key(), customer, "token", false))
        .isInstanceOf(ActionPolicyException.class);
    assertThat(repo.hold(held.id()).state()).isEqualTo("RELEASED");
    assertThat(
            repo.jdbc()
                .queryForObject(
                    "SELECT count(*) FROM booking_abandonment_events WHERE source_id=?",
                    Integer.class,
                    held.id()))
        .isEqualTo(1);
    assertThat(
            repo.jdbc()
                .queryForObject(
                    "SELECT hold_blocked_until FROM user_admission_restrictions WHERE"
                        + " principal_id=?",
                    java.sql.Timestamp.class,
                    customer.id())
                .toInstant())
        .isEqualTo(held.expiresAt().plusSeconds(1800));
  }

  @Test
  void policyMissingOrStaleCacheFailsClosedButCommittedReplayAndExistingPaymentStayAvailable() {
    var held = service.hold(input(), "same-key", customer, "token", false);
    var booking =
        service.create(
            new com.sporthub.booking.web.BookingDtos.CreateInput(held.id(), null, null),
            key(),
            customer,
            "token",
            false);
    redis.delete("booking:hold-policy:{" + customer.id() + "}:revision");
    assertThat(service.hold(input(), "same-key", customer, "token", false).id())
        .isEqualTo(held.id());
    assertThatThrownBy(
            () ->
                new TransactionTemplate(manager)
                    .execute(
                        status -> {
                          repo.lock(customer.id());
                          protection.requireAllowed(customer.id());
                          return null;
                        }))
        .isInstanceOfSatisfying(
            ActionPolicyException.class, error -> assertThat(error.status()).isEqualTo(503));
    consumer.apply(
        event(booking, UUID.randomUUID(), customer.id(), "BOOKING", null, booking.amount()));
    assertThat(repo.find(booking.id()).status()).isEqualTo("CONFIRMED");
    protection.prepare(customer.id());
    new TransactionTemplate(manager)
        .execute(
            status -> {
              repo.lock(customer.id());
              protection.requireAllowed(customer.id());
              return null;
            });
  }

  @Test
  void policyOnlineHistoryAlsoBlocksNewPeerAcquisitionWithoutFreezingTheSellerListing() {
    var sale = listing();
    var buyer = buyer();
    // Facts are tied to the buyer; the seller's paid source remains a success, not an abandonment.
    for (int n = 0; n < 3; n++) {
      var source = UUID.randomUUID();
      var at = now.minusSeconds((2 - n) * 60L);
      repo.jdbc()
          .update(
              "INSERT INTO"
                  + " booking_abandonment_events(source_id,principal_id,court_id,starts_at,ends_at,kind,reason,effective_at)"
                  + " VALUES(?,?,?,?,?,'ONLINE_BOOKING','EXPIRED',?)",
              source,
              buyer.id(),
              court,
              java.sql.Timestamp.from(start),
              java.sql.Timestamp.from(end),
              java.sql.Timestamp.from(at));
    }
    assertThatThrownBy(() -> acquisitions.acquire(input(sale), key(), buyer))
        .isInstanceOf(ActionPolicyException.class);
    assertThat(attemptsCount(buyer.id())).isZero();
    assertThat(
            repo.jdbc()
                .queryForObject(
                    "SELECT state FROM transfer_leases WHERE listing_id=?",
                    String.class,
                    sale.listing()))
        .isEqualTo("ACTIVE");
  }

  int attemptsCount(UUID user) {
    return repo.jdbc()
        .queryForObject(
            "SELECT count(*) FROM peer_usage_attempts WHERE buyer_id=?", Integer.class, user);
  }

  @Test
  void policyStatusHttpIgnoresUserOverrideAndExposesOnlyTheCurrentCustomer() throws Exception {
    var held=service.hold(input(),key(),customer,"token",false);var other=buyer();
    when(identity.current(any())).thenReturn(other);
    http.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/bookings/admission").param("principalId",customer.id().toString()).header("Authorization","Bearer token"))
        .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
        .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.data.resources").isEmpty());
    when(identity.current(any())).thenReturn(customer);
    http.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/bookings/admission").param("principalId",other.id().toString()).header("Authorization","Bearer token"))
        .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
        .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.data.resources[0].resourceId").value(held.id().toString()))
        .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.data.policyAvailability").value("COMPLETE"));
    when(identity.current(any())).thenReturn(new com.sporthub.common.security.RemoteIdentity.Caller(UUID.randomUUID(),"Staff",Set.of("STAFF"),Map.of()));
    http.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/bookings/admission").header("Authorization","Bearer token"))
        .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isForbidden());
  }
}
