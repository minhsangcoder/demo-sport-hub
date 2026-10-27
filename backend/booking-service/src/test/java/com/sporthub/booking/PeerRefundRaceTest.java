package com.sporthub.booking;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.sporthub.booking.service.BookingPolicyCancellation;
import com.sporthub.common.exception.ConflictException;
import com.sporthub.common.money.PeerResaleContract;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;

class PeerRefundRaceTest extends PeerUsageIntegrationTest {
  @Test
  void peerConcurrentCancellationRequestsCannotCreateRefunds() throws Exception {
    courtAmount = new BigDecimal("120000");
    var priced =
        ((com.fasterxml.jackson.databind.node.ObjectNode) dependencies.quote(input()).deepCopy())
            .put("amount", 120000);
    when(dependencies.quote(any())).thenReturn(priced);
    var sale = sale(new BigDecimal("100000"));
    declare(sale);
    peer.confirm(
        sale.acquisition(), new PeerResaleContract.Command(1, sale.hash()), key(), customer.id());
    noFacilityResaleMoney();
    var request = new BookingPolicyCancellation.Input(UUID.randomUUID(), "Concurrent buyer cancellation");
    var gate = new CountDownLatch(1);
    var pool = Executors.newFixedThreadPool(2);
    try {
      Callable<Boolean> cancel =
          () -> {
            if (!gate.await(10, TimeUnit.SECONDS))
              throw new IllegalStateException("Test gate timed out");
            try {
              cancellation.cancel(sale.original().id(), request, key(), sale.buyer(), "token");
              return true;
            } catch (ConflictException expected) {
              return false;
            }
          };
      var first = pool.submit(cancel);
      var second = pool.submit(cancel);
      gate.countDown();
      assertThat(List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
          .containsExactly(false, false);
    } finally {
      gate.countDown();
      pool.shutdownNow();
      assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
    }
    noFacilityResaleMoney();
    assertThat(repo.find(sale.original().id()).status()).isEqualTo("CONFIRMED");
  }
}
