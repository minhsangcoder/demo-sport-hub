package com.sporthub.payment.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sporthub.common.exception.*;
import com.sporthub.common.money.BankProfileContract.*;
import com.sporthub.common.security.RemoteIdentity.Caller;
import com.sporthub.payment.repository.PaymentRepository;
import com.sporthub.payment.security.PaymentSecretVault;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.testcontainers.containers.PostgreSQLContainer;

class PersonalBankProfilesTest {
  static PostgreSQLContainer<?> pg;
  static PaymentRepository repo;
  static DataSourceTransactionManager manager;
  BankDestinations destinations;
  BankProfileClient authority;
  PersonalBankProfiles profiles;
  Caller user;
  BankDestinations.Input input;

  @BeforeAll
  static void database() {
    pg = new PostgreSQLContainer<>("postgres:16-alpine");
    pg.start();
    var ds = new DriverManagerDataSource(pg.getJdbcUrl(), pg.getUsername(), pg.getPassword());
    Flyway.configure().dataSource(ds).load().migrate();
    repo = new PaymentRepository(new JdbcTemplate(ds));
    manager = new DataSourceTransactionManager(ds);
  }

  @AfterAll
  static void stop() {
    pg.stop();
  }

  @BeforeEach
  void setup() {
    user = new Caller(UUID.randomUUID(), "A", Set.of("CUSTOMER"), Map.of());
    input = new BankDestinations.Input("970436", "000123456789", "A", "consent-v1", 0L);
    var catalog = mock(BankCatalog.class);
    when(catalog.require("970436")).thenReturn(new BankCatalog.Bank("970436", "VCB", "VCB"));
    destinations =
        new BankDestinations(
            repo,
            new PaymentSecretVault("test:" + Base64.getEncoder().encodeToString(new byte[32])),
            catalog,
            mock(PaymentBankAccess.class),
            manager);
    authority = mock(BankProfileClient.class);
    profiles =
        new PersonalBankProfiles(
            repo,
            destinations,
            authority,
            new ObjectMapper().findAndRegisterModules(),
            manager,
            true);
    when(authority.status(user.id(), false))
        .thenReturn(new Status(user.id(), null, null, 0, false, true, false, List.of()));
  }

  @Test
  void lostActivationReplyKeepsUnselectableStageAndSameKeyRecoversOneRevision() {
    var committed = new AtomicReference<Decision>();
    var lost = new AtomicBoolean(true);
    when(authority.activate(any()))
        .thenAnswer(
            call -> {
              assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
              Activation c = call.getArgument(0);
              committed.compareAndSet(
                  null,
                  new Decision(
                      c.principalId(),
                      c.revisionId(),
                      c.destinationHash(),
                      1,
                      "ACTIVATED",
                      Instant.now()));
              if (lost.getAndSet(false))
                throw new ActionPolicyException(
                    503, "BANK_PROFILE_AUTHORITY_UNAVAILABLE", "reply lost", Map.of());
              return committed.get();
            });
    when(authority.status(user.id(), true))
        .thenAnswer(
            call -> {
              var d = committed.get();
              return new Status(
                  user.id(), d.revisionId(), d.destinationHash(), 1, true, false, true, List.of());
            });
    assertThatThrownBy(() -> profiles.register(input, "lost", user, "token"))
        .isInstanceOf(ActionPolicyException.class);
    var request =
        repo.jdbc()
            .queryForMap("SELECT * FROM bank_destination_requests WHERE actor_id=?", user.id());
    assertThat(request.get("state")).isEqualTo("STAGED");
    UUID staged = (UUID) request.get("revision_id");
    assertThat(destinations.mine(user)).isEmpty();
    assertThatThrownBy(() -> destinations.owned(staged, user.id(), null))
        .isInstanceOf(ConflictException.class);
    profiles =
        new PersonalBankProfiles(
            repo,
            destinations,
            authority,
            new ObjectMapper().findAndRegisterModules(),
            manager,
            true);
    var recovered = profiles.status(user);
    assertThat(recovered.path("activeRevisionId").asText()).isEqualTo(staged.toString());
    assertThat(recovered.path("canRegisterFirst").asBoolean()).isFalse();
    assertThat(profiles.register(input, "lost", user, "token").id()).isEqualTo(staged);
    assertThat(profiles.mine(user))
        .extracting(BankDestinations.Destination::id)
        .containsExactly(staged);
    assertThat(
            repo.jdbc()
                .queryForObject(
                    "SELECT count(*) FROM customer_bank_destination_revisions WHERE principal_id=?",
                    Integer.class,
                    user.id()))
        .isEqualTo(1);
    assertThat(recovered.toString()).doesNotContain(input.accountNumber(), "account_ciphertext");
    verify(authority, times(2)).activate(any());
  }

  @Test
  void definitiveLockRejectsStageAndReplayCannotTreatItAsARegisteredAccount() {
    when(authority.activate(any()))
        .thenThrow(new ActionPolicyException(409, "BANK_PROFILE_LOCKED", "locked", Map.of()));
    assertThatThrownBy(() -> profiles.register(input, "locked", user, "token"))
        .isInstanceOf(ActionPolicyException.class);
    assertThat(
            repo.jdbc()
                .queryForObject(
                    "SELECT state FROM bank_destination_requests WHERE actor_id=?",
                    String.class,
                    user.id()))
        .isEqualTo("REJECTED");
    assertThat(destinations.mine(user)).isEmpty();
    assertThatThrownBy(() -> profiles.register(input, "locked", user, "token"))
        .isInstanceOf(ActionPolicyException.class);
    verify(authority, times(1)).activate(any());
    assertThatThrownBy(
            () ->
                profiles.register(
                    new BankDestinations.Input("970436", "9999", "A", "consent-v1"),
                    "locked",
                    user,
                    "token"))
        .isInstanceOf(ConflictException.class);
  }

  @Test
  void soleLegacyBankReconciliationUsesOnlyOwnedAcceptedHistoryOutsideDatabaseLocks() {
    var old = destinations.register(input, "legacy-sole", user, "token", null);
    when(authority.status(user.id(), true))
        .thenThrow(
            new ActionPolicyException(
                503, "BANK_PROFILE_RECONCILIATION_REQUIRED", "legacy", Map.of()));
    when(authority.reconcile(any()))
        .thenAnswer(
            call -> {
              assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
              LegacyHistory history = call.getArgument(0);
              assertThat(history)
                  .isEqualTo(new LegacyHistory(user.id(), old.id(), old.destinationHash()));
              return new Status(
                  user.id(),
                  old.id(),
                  old.destinationHash(),
                  1,
                  true,
                  false,
                  false,
                  List.of(new Responsibility("BOOKING", UUID.randomUUID(), Instant.now())));
            });
    var result = profiles.status(user);
    assertThat(result.path("activeRevisionId").asText()).isEqualTo(old.id().toString());
    assertThat(result.path("canChange").asBoolean()).isFalse();
    assertThat(destinations.mine(user)).hasSize(1);
    verify(authority, never()).activate(any());
  }

  @Test
  void ambiguousLegacyHistoryDoesNotChooseTheMostRecentBankOrSendRawAccountNumbers() {
    destinations.register(input, "legacy-one", user, "token", null);
    destinations.register(
        new BankDestinations.Input("970436", "999999999", "A", "old-consent"),
        "legacy-two",
        user,
        "token",
        null);
    var unavailable =
        new ActionPolicyException(503, "BANK_PROFILE_RECONCILIATION_REQUIRED", "legacy", Map.of());
    when(authority.status(user.id(), true)).thenThrow(unavailable);
    when(authority.reconcile(new LegacyHistory(user.id(), null, null))).thenThrow(unavailable);
    assertThatThrownBy(() -> profiles.status(user)).isInstanceOf(ActionPolicyException.class);
    verify(authority).reconcile(new LegacyHistory(user.id(), null, null));
    verify(authority, never()).activate(any());
    assertThat(destinations.mine(user)).hasSize(2);
  }

  @Test
  void legacyHistoryCannotBeMisidentifiedAsFirstRegistration() {
    destinations.register(input, "legacy", user, "token", null);
    when(authority.status(user.id(), true))
        .thenReturn(new Status(user.id(), null, null, 0, false, true, false, List.of()));
    assertThatThrownBy(() -> profiles.status(user))
        .isInstanceOfSatisfying(
            ActionPolicyException.class, ex -> assertThat(ex.status()).isEqualTo(503));
    assertThatThrownBy(() -> profiles.register(input, "new-key", user, "token"))
        .isInstanceOf(ActionPolicyException.class);
    verify(authority, never()).activate(any());
    assertThat(
            repo.jdbc()
                .queryForObject(
                    "SELECT count(*) FROM bank_destination_requests WHERE actor_id=?",
                    Integer.class,
                    user.id()))
        .isEqualTo(1);
  }

  @Test
  void staleExpectedVersionCannotReplaceTheProfileAndOnlyTheActiveRevisionIsSelectable() {
    var original = destinations.register(input, "original", user, "token", null);
    var active =
        destinations.register(
            new BankDestinations.Input("970436", "000888999", "A", "consent-v1"),
            "active",
            user,
            "token",
            null);
    when(authority.status(user.id(), true))
        .thenReturn(
            new Status(
                user.id(), active.id(), active.destinationHash(), 3, true, false, true, List.of()));
    assertThat(profiles.mine(user))
        .extracting(BankDestinations.Destination::id)
        .containsExactly(active.id());
    assertThatThrownBy(() -> profiles.register(input, "stale", user, "token"))
        .isInstanceOfSatisfying(
            ActionPolicyException.class,
            e -> assertThat(e.code()).isEqualTo("BANK_PROFILE_VERSION_CHANGED"));
    assertThatThrownBy(
            () ->
                profiles.register(
                    new BankDestinations.Input("970436", "123", "A", "consent-v1"),
                    "missing-version",
                    user,
                    "token"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(destinations.privateDestination(original.id(), user.id(), null).accountNumber())
        .isEqualTo(input.accountNumber());
    verify(authority, never()).activate(any());
  }
}
