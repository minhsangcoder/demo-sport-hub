package com.sporthub.booking;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.sporthub.booking.web.BookingDtos.*;
import com.sporthub.booking.web.GroupDtos.*;
import com.sporthub.common.exception.ActionPolicyException;
import com.sporthub.common.money.BankProfileContract.Activation;
import com.sporthub.common.security.RemoteIdentity.Caller;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionTemplate;

class BankProducerFenceTest extends BankProfileIntegrationTest {
  private Activation replacement(UUID principal, long version) {
    return new Activation(
        principal, UUID.randomUUID(), "a".repeat(64), version, version > 0, key(), "b".repeat(64));
  }

  private void waits(int count) {
    long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
    while (System.nanoTime() < end) {
      if (repo.jdbc()
              .queryForObject(
                  "SELECT count(*) FROM pg_locks WHERE locktype='advisory' AND NOT granted",
                  Integer.class)
          >= count) return;
      try {
        Thread.sleep(20);
      } catch (InterruptedException interrupted) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException(interrupted);
      }
    }
    throw new AssertionError("Expected ordered court/user fence waiters: " + count);
  }

  private void ordered(UUID principal, Callable<?> source) throws Exception {
    var pool = Executors.newFixedThreadPool(2);
    var creating = new AtomicReference<Future<?>>();
    var changing = new AtomicReference<Future<?>>();
    try {
      new TransactionTemplate(manager)
          .executeWithoutResult(
              status -> {
                repo.lock(court);
                creating.set(pool.submit(source));
                waits(1);
                changing.set(pool.submit(() -> banks.activate(replacement(principal, 1))));
                waits(2);
                assertThat(changing.get().isDone()).isFalse();
              });
      assertThat(creating.get().get(10, TimeUnit.SECONDS)).isNotNull();
      assertThatThrownBy(() -> changing.get().get(10, TimeUnit.SECONDS))
          .hasCauseInstanceOf(ActionPolicyException.class);
      assertThat(banks.status(principal, true).version()).isEqualTo(1);
      assertThat(banks.status(principal, true).canChange()).isFalse();
    } finally {
      pool.shutdownNow();
      assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
    }
  }

  @Test
  void fenceCounterLinkedCustomerPrecedesCourtSoReplacementCannotPassThePendingBooking()
      throws Exception {
    banks.activate(replacement(customer.id(), 0));
    var staff =
        new Caller(
            UUID.randomUUID(),
            "Staff",
            Set.of("CUSTOMER", "STAFF"),
            Map.of(facility, Set.of("BOOKING_CREATE_COUNTER")),
            "ACTIVE",
            true,
            false);
    when(dependencies.counterCustomer(facility, "0900000000", "token")).thenReturn(customer.id());
    ordered(
        customer.id(),
        () ->
            service.counter(
                new CounterInput(
                    court,
                    start,
                    end,
                    courtAmount,
                    "Linked customer",
                    "0900000000",
                    "BANK_QR",
                    policy.path("termsHash").asText()),
                key(),
                staff,
                "token"));
    assertThat(banks.status(staff.id(), false).responsibilities()).isEmpty();
  }

  @Test
  void fenceJoiningMemberPrecedesCourtSoReplacementCannotPassNewGroupMembership() throws Exception {
    var held =
        service.hold(
            new HoldInput(court, start, end, courtAmount), key(), customer, "token", false);
    var group =
        groups.create(new CreateGroup(held.id(), "Bank fence group", 3), key(), customer, "token");
    var invite = groups.invite(group.id(), customer).code();
    var member =
        new Caller(
            UUID.randomUUID(), "Member", Set.of("CUSTOMER"), Map.of(), "ACTIVE", true, false);
    banks.activate(replacement(member.id(), 0));
    ordered(member.id(), () -> groups.join(new JoinGroup(invite), member));
  }
}
