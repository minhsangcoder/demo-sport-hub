package com.sporthub.payment.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.*;
import com.sporthub.common.exception.*;
import com.sporthub.common.money.PeerResaleTerms;
import com.sporthub.common.security.*;
import com.sporthub.common.security.RemoteIdentity.Caller;
import com.sporthub.payment.provider.SePayQrImages;
import com.sporthub.payment.repository.PaymentRepository;
import com.sporthub.payment.security.PaymentSecretVault;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.testcontainers.containers.PostgreSQLContainer;

class PeerQrInstructionsTest {
  static PostgreSQLContainer<?> pg;
  static PaymentRepository repo;
  static DataSourceTransactionManager manager;
  final ObjectMapper json = new ObjectMapper().findAndRegisterModules();
  final Instant now = Instant.parse("2026-10-25T06:00:00Z");
  UUID id;
  Caller seller, buyer;
  Clock clock;
  PeerQrContextClient authority;
  SePayQrImages provider;
  BankDestinations banks;
  PaymentSecretVault vault;
  PeerQrInstructions service;
  JsonNode terms;

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
  void setup() throws Exception {
    id = UUID.randomUUID();
    seller = actor("A");
    buyer = actor("B");
    clock = mock(Clock.class);
    when(clock.instant()).thenReturn(now);
    vault = new PaymentSecretVault("test:" + Base64.getEncoder().encodeToString(new byte[32]));
    var catalog = mock(BankCatalog.class);
    when(catalog.require("970436")).thenReturn(new BankCatalog.Bank("970436", "VCB", "VCB"));
    banks = new BankDestinations(repo, vault, catalog, mock(PaymentBankAccess.class), manager);
    var destination =
        banks.register(
            new BankDestinations.Input("970436", "000123456789", "A", "consent"),
            "bank",
            seller,
            "unused",
            null);
    UUID facility = UUID.randomUUID();
    var booking =
        json.createObjectNode()
            .put("id", UUID.randomUUID().toString())
            .put("facilityId", facility.toString())
            .put("courtId", UUID.randomUUID().toString())
            .put("currentHolderId", seller.id().toString())
            .put("currency", "VND")
            .put("status", "CONFIRMED")
            .put("amount", "120000")
            .put("startsAt", now.plusSeconds(3600).toString())
            .put("endsAt", now.plusSeconds(7200).toString());
    booking.putObject("priceSnapshot").put("moneyClass", "REAL");
    var policy =
        json.createObjectNode()
            .put("configured", true)
            .put("id", UUID.randomUUID().toString())
            .put("version", 1)
            .put("rateBps", 8000)
            .put("currency", "VND")
            .put("rounding", "HALF_UP")
            .put(
                "termsHash",
                Signatures.sha256(facility + "|1|8000|VND|HALF_UP|FACILITY_POLICY_CANCELLATION"));
    var sell =
        PeerResaleTerms.seller(
            json,
            booking,
            seller.id(),
            new BigDecimal("80000"),
            now.plusSeconds(3600),
            json.valueToTree(destination),
            policy);
    terms =
        PeerResaleTerms.acquisition(
            sell, UUID.randomUUID(), 0, id, buyer.id(), now.plusSeconds(300), now);
    var context = json.createObjectNode();
    context.set("terms", terms);
    context.putObject("attempt").put("id", id.toString()).put("phase", "WAITING_PAYMENT");
    authority = mock(PeerQrContextClient.class);
    when(authority.instruction(eq(id), any()))
        .thenAnswer(
            call -> {
              assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
              return context;
            });
    provider = mock(SePayQrImages.class);
    when(provider.fetch(any(), any(), any()))
        .thenAnswer(
            call -> {
              assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
              BankDestinations.PrivateDestination bank = call.getArgument(0);
              assertThat(bank.accountNumber()).isEqualTo("000123456789");
              assertThat(call.<BigDecimal>getArgument(1)).isEqualByComparingTo("80000");
              return new byte[] {1, 2, 3};
            });
    service =
        new PeerQrInstructions(
            repo,
            authority,
            banks,
            provider,
            vault,
            clock,
            new AdmissionFailures(repo, json, clock, manager));
  }

  private Caller actor(String name) {
    return new Caller(UUID.randomUUID(), name, Set.of("CUSTOMER"), Map.of());
  }

  @Test
  void retriesAndRestartReuseEncryptedImageMemoAndOriginalDeadline() throws Exception {
    var instruction = service.instruction(id, buyer);
    assertThat(instruction.memo()).matches("SH[0-9]{1,18}");
    assertThat(instruction.payBy()).isEqualTo(now.plusSeconds(300));
    assertThat(json.writeValueAsString(instruction))
        .doesNotContain("000123456789", "vietqr.app", "BANK_VERIFIED");
    assertThat(service.image(id, buyer)).containsExactly(1, 2, 3);
    when(clock.instant()).thenReturn(now.plusSeconds(200));
    var restarted =
        new PeerQrInstructions(
            repo,
            authority,
            banks,
            provider,
            vault,
            clock,
            new AdmissionFailures(repo, json, clock, manager));
    assertThat(restarted.image(id, seller)).containsExactly(1, 2, 3);
    assertThat(restarted.instruction(id, buyer).memo()).isEqualTo(instruction.memo());
    verify(provider, times(1)).fetch(any(), any(), eq(instruction.memo()));
    var row =
        repo.jdbc().queryForMap("SELECT * FROM peer_qr_instructions WHERE acquisition_id=?", id);
    assertThat(row.get("image_ciphertext").toString()).doesNotContain("AQID");
    assertThatThrownBy(
            () ->
                repo.jdbc()
                    .update(
                        "UPDATE peer_qr_instructions SET expires_at=expires_at+interval '1 minute'"
                            + " WHERE acquisition_id=?",
                        id))
        .isInstanceOf(org.springframework.dao.DataAccessException.class);
    assertThat(repo.jdbc().queryForObject("SELECT count(*) FROM payment_orders", Integer.class))
        .isZero();
    assertThat(
            repo.jdbc().queryForObject("SELECT count(*) FROM transfer_settlements", Integer.class))
        .isZero();
  }

  @Test
  void concurrentPollingJoinsOneProviderRequestAndEveryCallerStillChecksAuthority()
      throws Exception {
    var entered = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    doAnswer(
            call -> {
              entered.countDown();
              release.await(3, TimeUnit.SECONDS);
              return new byte[] {4};
            })
        .when(provider)
        .fetch(any(), any(), any());
    try (var pool = Executors.newFixedThreadPool(6)) {
      var jobs = new ArrayList<Future<byte[]>>();
      jobs.add(pool.submit(() -> service.image(id, buyer)));
      assertThat(entered.await(3, TimeUnit.SECONDS)).isTrue();
      for (int n = 0; n < 5; n++) jobs.add(pool.submit(() -> service.image(id, buyer)));
      release.countDown();
      for (var job : jobs) assertThat(job.get(5, TimeUnit.SECONDS)).containsExactly(4);
    }
    verify(provider, times(1)).fetch(any(), any(), any());
    verify(authority, times(12)).instruction(id, buyer.id());
  }

  @Test
  void providerFailureKeepsMemoAndRetryDoesNotExtendDeadline() {
    String memo = service.instruction(id, buyer).memo();
    doThrow(SePayQrImages.unavailable())
        .doThrow(SePayQrImages.unavailable())
        .doReturn(new byte[] {9})
        .when(provider)
        .fetch(any(), any(), any());
    assertThatThrownBy(() -> service.image(id, buyer)).isInstanceOf(ActionPolicyException.class);
    assertThatThrownBy(() -> service.image(id, buyer)).isInstanceOf(ActionPolicyException.class);
    assertThat(
            repo.jdbc()
                .queryForObject(
                    "SELECT count(*) FROM payment_admission_failures WHERE kind='PEER_ACQUISITION'"
                        + " AND source_id=?",
                    Integer.class,
                    id))
        .isEqualTo(1);
    var event =
        repo.jdbc()
            .queryForObject(
                "SELECT body::text FROM event_outbox WHERE routing_key='payment.admission.exempted'"
                    + " AND body->>'aggregateId'=?",
                String.class,
                id.toString());
    assertThat(event)
        .contains("PROVIDER_UNAVAILABLE", buyer.id().toString(), now.toString())
        .doesNotContain("000123456789", "BANK_VERIFIED", "checksumKey");
    assertThat(service.instruction(id, buyer).memo()).isEqualTo(memo);
    when(clock.instant()).thenReturn(now.plusSeconds(299));
    assertThat(service.image(id, buyer)).containsExactly(9);
    when(clock.instant()).thenReturn(now.plusSeconds(300));
    assertThatThrownBy(() -> service.image(id, buyer)).isInstanceOf(ConflictException.class);
    verify(provider, times(3)).fetch(any(), any(), eq(memo));
  }

  @Test
  void privateAccessAndPostFetchRevalidationDenyStaleOrForeignReads() {
    assertThatThrownBy(() -> service.image(id, actor("outsider")))
        .isInstanceOf(ForbiddenException.class);
    verifyNoInteractions(provider);
    var calls = new AtomicInteger();
    when(authority.instruction(id, buyer.id()))
        .thenAnswer(
            call -> {
              if (calls.incrementAndGet() > 1) throw new ConflictException("Đã đóng phiên.");
              var context = json.createObjectNode();
              context.set("terms", terms);
              context.putObject("attempt").put("id", id.toString()).put("phase", "WAITING_PAYMENT");
              return context;
            });
    assertThatThrownBy(() -> service.image(id, buyer)).isInstanceOf(ConflictException.class);
    assertThat(repo.jdbc().queryForObject("SELECT count(*) FROM payment_orders", Integer.class))
        .isZero();
  }
}
