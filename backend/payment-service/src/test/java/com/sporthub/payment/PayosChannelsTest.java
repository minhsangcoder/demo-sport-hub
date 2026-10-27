package com.sporthub.payment;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sporthub.common.exception.ForbiddenException;
import com.sporthub.common.security.RemoteIdentity.Caller;
import com.sporthub.payment.provider.*;
import com.sporthub.payment.repository.PaymentRepository;
import com.sporthub.payment.security.PaymentSecretVault;
import com.sporthub.payment.service.*;
import java.util.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.testcontainers.containers.PostgreSQLContainer;

class PayosChannelsTest {
  @Test
  void scopesWriteOnlySecretsAndPreservesOldReceiverWhenRotatingOrDisabling() throws Exception {
    try (var pg = new PostgreSQLContainer<>("postgres:16-alpine")) {
      pg.start();
      var ds = new DriverManagerDataSource(pg.getJdbcUrl(), pg.getUsername(), pg.getPassword());
      Flyway.configure().dataSource(ds).load().migrate();
      var repo = new PaymentRepository(new JdbcTemplate(ds));
      var access = mock(PaymentBankAccess.class);
      var provider = mock(PayosClient.class);
      var catalog = mock(BankCatalog.class);
      when(catalog.require("970436")).thenReturn(new BankCatalog.Bank("970436", "VCB", "VCB"));
      when(provider.webhookUrl(any()))
          .thenAnswer(
              invocation ->
                  "https://gateway.example/api/v1/payments/providers/payos/"
                      + invocation.getArgument(0)
                      + "/webhook");
      when(provider.confirmWebhook(any(), any()))
          .thenReturn(new PayosClient.Receiver(null, "000123456789", "SYNTHETIC OWNER"));
      var vault =
          new PaymentSecretVault("test:" + Base64.getEncoder().encodeToString(new byte[32]));
      var json = new ObjectMapper().findAndRegisterModules();
      var service =
          new PayosChannels(
              repo, access, vault, provider, catalog, json, new DataSourceTransactionManager(ds));
      UUID facility = UUID.randomUUID();
      Caller owner = new Caller(UUID.randomUUID(), "Owner", Set.of("OWNER"), Map.of()),
          staff =
              new Caller(
                  UUID.randomUUID(),
                  "Staff",
                  Set.of("STAFF"),
                  Map.of(facility, Set.of("BOOKING_REFUND", "TRANSFER_PAYOUT")));
      var input =
          new PayosChannels.Input(
              0, "970436", "synthetic-client-id", "synthetic-api-key", "synthetic-checksum-key");
      var configured = service.configure(facility, input, "configure", owner, "token");
      assertThat(configured.status()).isEqualTo("PREPARING");
      assertThat(service.configure(facility, input, "configure", owner, "token"))
          .isEqualTo(configured);
      var ready = service.verify(facility, configured.version(), "verify", owner, "token");
      assertThat(ready.status()).isEqualTo("READY");
      assertThat(ready.maskedAccountNumber()).isEqualTo("***6789");
      String response = json.writeValueAsString(ready);
      assertThat(response)
          .doesNotContain(
              "apiKey",
              "checksumKey",
              "clientId",
              "synthetic-api-key",
              "synthetic-checksum-key",
              "000123456789",
              "ciphertext");
      assertThat(input.toString()).doesNotContain(input.apiKey(), input.checksumKey());
      var rotated =
          service.configure(
              facility,
              new PayosChannels.Input(
                  ready.version(), "970436", input.clientId(), "rotated-api", "rotated-checksum"),
              "rotate",
              owner,
              "token");
      assertThat(rotated.channelId()).isEqualTo(ready.channelId());
      assertThat(rotated.webhookUrl()).isEqualTo(ready.webhookUrl());
      assertThat(service.context(ready.receiverRevisionId()).accountNumber())
          .isEqualTo("000123456789");
      var verified = service.verify(facility, rotated.version(), "verify2", owner, "token");
      var disabled = service.disable(facility, verified.version(), "disable", owner, "token");
      assertThat(disabled.status()).isEqualTo("DISABLED");
      assertThat(service.reconciliationCredentials(ready.channelId()).apiKey())
          .isEqualTo("rotated-api");
      assertThat(service.webhookCredentials(ready.channelId())).hasSize(2);
      assertThatThrownBy(() -> service.ready(facility))
          .isInstanceOf(com.sporthub.common.exception.ConflictException.class);
      assertThatThrownBy(() -> service.configure(facility, input, "staff", staff, "token"))
          .isInstanceOf(ForbiddenException.class);
      UUID other = UUID.randomUUID();
      doThrow(new ForbiddenException("outside facility"))
          .when(access)
          .owner(eq(other), any(), any());
      assertThatThrownBy(() -> service.own(other, owner, "token"))
          .isInstanceOf(ForbiddenException.class);
      verify(provider, times(2)).confirmWebhook(eq(ready.channelId()), any());
      assertThat(
              repo.jdbc().queryForObject("SELECT count(*) FROM payos_bank_credits", Integer.class))
          .isZero();
      assertThat(repo.jdbc().queryForObject("SELECT count(*) FROM payment_orders", Integer.class))
          .isZero();
    }
  }
}
