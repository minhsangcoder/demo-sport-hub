package com.sporthub.payment;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sporthub.common.security.RemoteIdentity.Caller;
import com.sporthub.payment.repository.PaymentRepository;
import com.sporthub.payment.security.PaymentSecretVault;
import com.sporthub.payment.service.*;
import java.util.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.testcontainers.containers.PostgreSQLContainer;

class BankDestinationsTest {
  @Test
  void exactLeadingZerosAreEncryptedAndNeverBecomeSomeoneElsesRevision() throws Exception {
    try (var pg = new PostgreSQLContainer<>("postgres:16-alpine")) {
      pg.start();
      var ds = new DriverManagerDataSource(pg.getJdbcUrl(), pg.getUsername(), pg.getPassword());
      Flyway.configure().dataSource(ds).load().migrate();
      var repo = new PaymentRepository(new JdbcTemplate(ds));
      var catalog = mock(BankCatalog.class);
      var access = mock(PaymentBankAccess.class);
      when(catalog.require("970436")).thenReturn(new BankCatalog.Bank("970436", "VCB", "VCB"));
      var vault =
          new PaymentSecretVault("test:" + Base64.getEncoder().encodeToString(new byte[32]));
      var service =
          new BankDestinations(repo, vault, catalog, access, new DataSourceTransactionManager(ds));
      Caller alice = new Caller(UUID.randomUUID(), "Alice", Set.of("CUSTOMER"), Map.of()),
          bob = new Caller(UUID.randomUUID(), "Bob", Set.of("CUSTOMER"), Map.of());
      var input =
          new BankDestinations.Input(
              "970436", "000123456789", "ALICE", "I accept this destination");
      var first = service.register(input, "first", alice, "token", null);
      assertThat(service.register(input, "first", alice, "token", null)).isEqualTo(first);
      assertThat(service.privateDestination(first.id(), alice.id(), null).accountNumber())
          .isEqualTo("000123456789");
      var json = new ObjectMapper().findAndRegisterModules();
      assertThat(json.writeValueAsString(first))
          .doesNotContain("000123456789", "account_ciphertext");
      assertThat(
              repo.jdbc()
                  .queryForObject(
                      "SELECT account_ciphertext FROM customer_bank_destination_revisions WHERE"
                          + " id=?",
                      String.class,
                      first.id()))
          .doesNotContain("000123456789");
      assertThatThrownBy(() -> service.owned(first.id(), bob.id(), null))
          .isInstanceOf(com.sporthub.common.exception.ForbiddenException.class);
      assertThatThrownBy(
              () ->
                  service.register(
                      new BankDestinations.Input("970436", "123", "", input.consentReference()),
                      "first",
                      alice,
                      "token",
                      null))
          .isInstanceOf(com.sporthub.common.exception.ConflictException.class);
      var second =
          service.register(
              new BankDestinations.Input(
                  "970436", "00000009999", "ALICE", input.consentReference()),
              "second",
              alice,
              "token",
              null);
      assertThat(second.id()).isNotEqualTo(first.id());
      UUID staged=UUID.randomUUID();
      repo.jdbc().update("INSERT INTO customer_bank_destination_revisions(id,principal_id,bank_bin,bank_name,account_ciphertext,masked_account,destination_hash,consent_reference,created_by) VALUES(?,?,'970436','VCB',?,'***0777',?,'consent',?)",
          staged,alice.id(),vault.seal("000777","bank:"+staged),"d".repeat(64),alice.id());
      repo.jdbc().update("INSERT INTO bank_destination_requests(actor_id,idempotency_key,fingerprint,revision_id,state,expected_profile_version) VALUES(?,'staged-command',?,?,'STAGED',0)",alice.id(),"e".repeat(64),staged);
      assertThat(service.mine(alice)).noneMatch(destination->destination.id().equals(staged));
      assertThatThrownBy(()->service.owned(staged,alice.id(),null)).isInstanceOf(com.sporthub.common.exception.ConflictException.class);
      assertThat(service.privateDestination(first.id(), alice.id(), null).accountNumber())
          .isEqualTo("000123456789");
      for (String invalid : List.of("", "12 34", "+123", "12345678901234567890"))
        assertThatThrownBy(
                () ->
                    service.register(
                        new BankDestinations.Input("970436", invalid, "", "consent"),
                        UUID.randomUUID().toString(),
                        alice,
                        "token",
                        null))
            .isInstanceOf(IllegalArgumentException.class);
      UUID guest = UUID.randomUUID();
      service.register(input, "guest", alice, "token", guest);
      verify(access).guest(guest, alice, "token");
      var guestDestination =
          repo.jdbc()
              .queryForMap(
                  "SELECT * FROM customer_bank_destination_revisions WHERE guest_booking_id=?",
                  guest);
      assertThat(guestDestination.get("principal_id")).isNull();
      assertThatThrownBy(
              () -> service.privateDestination((UUID) guestDestination.get("id"), alice.id(), null))
          .isInstanceOf(com.sporthub.common.exception.ForbiddenException.class);
    }
  }
}
