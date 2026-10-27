package com.sporthub.booking;

import static org.assertj.core.api.Assertions.*;

import com.sporthub.booking.repository.BookingRepository;
import com.sporthub.booking.service.BookingBankProfiles;
import com.sporthub.common.exception.ActionPolicyException;
import com.sporthub.common.money.BankProfileContract.Activation;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

final class BankFenceProbe {
  static void ordered(
      BookingRepository repo,
      PlatformTransactionManager manager,
      BookingBankProfiles banks,
      UUID court,
      UUID principal,
      Callable<?> source)
      throws Exception {
    var pool = Executors.newFixedThreadPool(2);
    var creating = new AtomicReference<Future<?>>();
    var changing = new AtomicReference<Future<?>>();
    try {
      new TransactionTemplate(manager)
          .executeWithoutResult(
              status -> {
                repo.lock(court);
                creating.set(pool.submit(source));
                waits(repo, 1);
                changing.set(
                    pool.submit(
                        () ->
                            banks.activate(
                                new Activation(
                                    principal,
                                    UUID.randomUUID(),
                                    "a".repeat(64),
                                    1,
                                    true,
                                    UUID.randomUUID().toString(),
                                    "b".repeat(64)))));
                waits(repo, 2);
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

  private static void waits(BookingRepository repo, int count) {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
    while (System.nanoTime() < deadline) {
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
}
