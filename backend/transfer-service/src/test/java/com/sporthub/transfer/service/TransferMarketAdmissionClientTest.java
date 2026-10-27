package com.sporthub.transfer.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sporthub.common.exception.ActionPolicyException;
import com.sporthub.common.security.ServiceCalls;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class TransferMarketAdmissionClientTest {
  @Test
  void legacyQuoteChecksCanonicalAdmissionAndSignedTransportPreservesMarketSuspension()
      throws Exception {
    String secret = "synthetic-service-call-secret-more-than-thirty-two-characters";
    UUID user = UUID.randomUUID();
    var payload =
        new AtomicReference<>(
            "{\"status\":403,\"code\":\"TRANSFER_MARKET_SUSPENDED\",\"message\":\"Tạm ngưng"
                + " chợ\",\"details\":{\"permanent\":true}}");
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/api/v1/internal/bookings/market-admission/",
        exchange -> {
          String body =
              new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
          ServiceCalls.verify(
              secret,
              exchange.getRequestMethod(),
              exchange.getRequestURI().getPath(),
              exchange.getRequestHeaders().getFirst("X-Service-Time"),
              body,
              exchange.getRequestHeaders().getFirst("X-Service-Signature"));
          byte[] response = payload.get().getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().set("Content-Type", "application/json");
          exchange.sendResponseHeaders(403, response.length);
          try (var stream = exchange.getResponseBody()) {
            stream.write(response);
          }
        });
    server.start();
    try {
      String url = "http://127.0.0.1:" + server.getAddress().getPort();
      var dependencies = new TransferDependencies(url, url, url, secret, new ObjectMapper());
      assertThatThrownBy(() -> dependencies.marketAllowed(user))
          .isInstanceOfSatisfying(
              ActionPolicyException.class,
              error -> {
                assertThat(error.code()).isEqualTo("TRANSFER_MARKET_SUSPENDED");
                assertThat(error.details().get("permanent")).isEqualTo(true);
              });
      payload.set("{\"code\":\"UNKNOWN_ERROR\",\"details\":{}}");
      assertThatThrownBy(() -> dependencies.marketAllowed(user))
          .isInstanceOf(TransferDependencies.Unavailable.class);
      var mocked = mock(TransferDependencies.class);
      doThrow(
              new ActionPolicyException(
                  403, "TRANSFER_MARKET_SUSPENDED", "Tạm ngưng chợ", Map.of()))
          .when(mocked)
          .marketAllowed(user);
      var quotes = mock(FacilityTransferQuotes.class);
      var reads =
          new TransferListingReads(
              mock(com.sporthub.transfer.repository.TransferRepository.class),
              mocked,
              Clock.systemUTC(),
              new ObjectMapper(),
              quotes);
      var actor =
          new com.sporthub.common.security.RemoteIdentity.Caller(
              user, "Khách", Set.of("CUSTOMER"), Map.of(), "ACTIVE", true, false);
      assertThatThrownBy(() -> reads.quote(null, actor, true))
          .isInstanceOf(ActionPolicyException.class);
      verify(mocked, never()).booking(any());
      verifyNoInteractions(quotes);
    } finally {
      server.stop(0);
    }
  }
}
