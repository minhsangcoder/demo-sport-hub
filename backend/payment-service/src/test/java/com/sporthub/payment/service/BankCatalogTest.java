package com.sporthub.payment.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

import java.time.*;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class BankCatalogTest {
  @Test
  void cachesSupportedBinsAndRetainsTimestampDuringTemporaryOutage() {
    Clock clock = mock(Clock.class);
    Instant start = Instant.parse("2026-10-23T00:00:00Z");
    when(clock.instant()).thenReturn(start);
    var builder = RestClient.builder().baseUrl("https://api.vietqr.io/v2/banks");
    var server = MockRestServiceServer.bindTo(builder).build();
    var catalog = new BankCatalog(builder.build(), clock);
    server
        .expect(requestTo("https://api.vietqr.io/v2/banks"))
        .andRespond(
            withSuccess(
                """
{"code":"00","data":[{"bin":"970436","name":"Vietcombank","shortName":"VCB","transferSupported":1},
{"bin":"970000","name":"Unsupported","shortName":"NO","transferSupported":0}]}
""",
                MediaType.APPLICATION_JSON));
    server.expect(requestTo("https://api.vietqr.io/v2/banks")).andRespond(withServerError());
    server.expect(requestTo("https://api.vietqr.io/v2/banks")).andRespond(withServerError());
    assertThat(catalog.require("970436").shortName()).isEqualTo("VCB");
    for (int n = 0; n < 10; n++) assertThat(catalog.read().updatedAt()).isEqualTo(start);
    assertThatThrownBy(() -> catalog.require("970000"))
        .isInstanceOf(IllegalArgumentException.class);
    when(clock.instant()).thenReturn(start.plus(Duration.ofDays(2)));
    assertThat(catalog.read().stale()).isTrue();
    assertThat(catalog.read().updatedAt()).isEqualTo(start);
    when(clock.instant()).thenReturn(start.plus(Duration.ofDays(8)));
    assertThatThrownBy(catalog::read)
        .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    server.verify();
  }

  @Test
  void invalidProviderDataDoesNotProduceAnEmptyUsableCatalog() {
    var builder = RestClient.builder().baseUrl("https://api.vietqr.io/v2/banks");
    var server = MockRestServiceServer.bindTo(builder).build();
    server
        .expect(requestTo("https://api.vietqr.io/v2/banks"))
        .andRespond(
            withSuccess(
                "{\"code\":\"00\",\"data\":[{\"bin\":\"invalid\",\"transferSupported\":1}]}",
                MediaType.APPLICATION_JSON));
    assertThatThrownBy(() -> new BankCatalog(builder.build(), Clock.systemUTC()).read())
        .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    server.verify();
  }
}
