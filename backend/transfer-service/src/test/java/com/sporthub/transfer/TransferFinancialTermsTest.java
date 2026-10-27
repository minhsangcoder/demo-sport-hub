package com.sporthub.transfer;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sporthub.common.exception.*;
import com.sporthub.common.security.RemoteIdentity.Caller;
import com.sporthub.common.security.Signatures;
import com.sporthub.transfer.repository.TransferRepository;
import com.sporthub.transfer.service.*;
import com.sporthub.transfer.web.TransferDtos.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

class TransferFinancialTermsTest {
  static PostgreSQLContainer<?> pg;
  JdbcTemplate jdbc;
  TransferRepository repo;
  TransferService service;
  TransferDependencies deps;
  TransactionTemplate tx;
  ObjectMapper json = new ObjectMapper().findAndRegisterModules();
  Clock clock = Clock.fixed(Instant.parse("2026-10-21T00:00:00Z"), ZoneOffset.UTC);
  Caller seller, buyer;
  UUID booking, facility, court, beneficiary;
  ObjectNode source;
  Instant start, deadline;

  @BeforeAll
  static void start() {
    pg = new PostgreSQLContainer<>("postgres:16-alpine");
    pg.start();
  }

  @AfterAll
  static void stop() {
    if (pg != null) pg.stop();
  }

  @BeforeEach
  void setup() {
    String schema = "transfer_finance_" + UUID.randomUUID().toString().replace("-", "");
    Flyway.configure()
        .dataSource(pg.getJdbcUrl(), pg.getUsername(), pg.getPassword())
        .schemas(schema)
        .defaultSchema(schema)
        .load()
        .migrate();
    var ds =
        new DriverManagerDataSource(
            pg.getJdbcUrl()
                + (pg.getJdbcUrl().contains("?") ? "&" : "?")
                + "currentSchema="
                + schema
                + ",public",
            pg.getUsername(),
            pg.getPassword());
    jdbc = new JdbcTemplate(ds);
    repo = new TransferRepository(jdbc, json);
    tx = new TransactionTemplate(new DataSourceTransactionManager(ds));
    deps = mock(TransferDependencies.class);
    service = new TransferService(repo, deps, clock, 600, json, true, true, demoQuotes());
    seller = customer();
    buyer = customer();
    booking = UUID.randomUUID();
    facility = UUID.randomUUID();
    court = UUID.randomUUID();
    beneficiary = UUID.randomUUID();
    start = clock.instant().plusSeconds(86400);
    deadline = start.minusSeconds(7200);
    source = json.createObjectNode();
    source
        .put("id", booking.toString())
        .put("facilityId", facility.toString())
        .put("courtId", court.toString())
        .put("currentHolderId", seller.id().toString())
        .put("status", "CONFIRMED")
        .put("paidAt", clock.instant().toString())
        .put("startsAt", start.toString())
        .put("endsAt", start.plusSeconds(3600).toString())
        .put("amount", 120000)
        .put("currency", "VND");
    source.set(
        "refundPolicy",
        json.valueToTree(
            Map.of(
                "configured",
                true,
                "id",
                UUID.randomUUID(),
                "version",
                1,
                "rateBps",
                8000,
                "currency",
                "VND",
                "rounding",
                "HALF_UP",
                "termsHash",
                Signatures.sha256(facility + "|1|8000|VND|HALF_UP|FACILITY_POLICY_CANCELLATION"))));
    when(deps.booking(booking)).thenReturn(source);
    when(deps.policy(facility))
        .thenReturn(
            json.valueToTree(Map.of("configured", true, "enabled", true, "minLeadSeconds", 3600)));
    when(deps.context(court))
        .thenReturn(
            json.valueToTree(
                Map.of(
                    "facility",
                    Map.of(
                        "id",
                        facility,
                        "ownerId",
                        UUID.randomUUID(),
                        "name",
                        "Finance fixture",
                        "addressLine",
                        "Fixture address",
                        "provinceCode",
                        "01",
                        "province",
                        "Hà Nội",
                        "wardCode",
                        "00004",
                        "ward",
                        "Ba Đình",
                        "timezone",
                        "Asia/Ho_Chi_Minh",
                        "amenities",
                        List.of()),
                    "court",
                    Map.of("name", "Court 1", "sportCategoryId", UUID.randomUUID()))));
    when(deps.feePolicy(facility))
        .thenReturn(
            json.valueToTree(
                Map.of(
                    "configured",
                    true,
                    "version",
                    1,
                    "feeBps",
                    500,
                    "currency",
                    "VND",
                    "rounding",
                    "HALF_UP",
                    "termsHash",
                    Signatures.sha256(facility + "|1|500|VND|HALF_UP|FACILITY_TRANSFER_FEE"))));
    when(deps.beneficiary(beneficiary, seller.id()))
        .thenReturn(
            json.valueToTree(
                Map.of(
                    "revisionId",
                    beneficiary,
                    "sellerId",
                    seller.id(),
                    "provider",
                    "DEMO",
                    "providerAccount",
                    "demo-main",
                    "currency",
                    "VND",
                    "moneyClass",
                    "DEMO",
                    "verified",
                    true)));
  }

  FacilityTransferQuotes demoQuotes() {
    return new FacilityTransferQuotes(
        deps,
        mock(com.sporthub.common.money.FacilityPaymentClient.class),
        new com.sporthub.common.money.CollectionRuntime(
            "DEMO", new org.springframework.core.env.StandardEnvironment()),
        json);
  }

  Caller customer() {
    return new Caller(UUID.randomUUID(), "Customer", Set.of("CUSTOMER"), Map.of(), "ACTIVE", true, false);
  }

  Create quoteInput() {
    return new Create(booking, new BigDecimal("100000"), deadline, beneficiary, null);
  }

  UUID listing() {
    var quote = service.quote(quoteInput(), seller);
    UUID id =
        tx.execute(
            s ->
                service.create(
                    new Create(
                        booking,
                        new BigDecimal("100000"),
                        deadline,
                        beneficiary,
                        quote.path("termsHash").asText()),
                    UUID.randomUUID().toString(),
                    seller));
    jdbc.update("UPDATE transfer_listings SET workflow='READY' WHERE id=?", id);
    return id;
  }

  @Test
  void sellerAcceptsExactPriceFeeAndOwnDestinationBeforePublishingAndBuyerAcceptsSamePolicy()
      throws Exception {
    var q = service.quote(quoteInput(), seller);
    assertThat(q.path("quotedFeeAmount").asText()).isEqualTo("5000");
    assertThat(q.path("quotedSellerNetAmount").asText()).isEqualTo("95000");
    UUID listing = listing();
    var view = service.detail(listing, buyer);
    String publicJson = json.writeValueAsString(view);
    assertThat(publicJson)
        .doesNotContain(
            "bookingId",
            "sellerId",
            "beneficiaryRevisionId",
            "providerAccount",
            seller.id().toString());
    assertThatThrownBy(
            () -> tx.execute(s -> service.acquire(listing, UUID.randomUUID().toString(), buyer)))
        .isInstanceOf(ConflictException.class);
    String key = UUID.randomUUID().toString();
    UUID acquisition =
        tx.execute(
            s ->
                service.acquire(
                    listing, key, buyer, view.financeTerms().path("termsHash").asText()));
    UUID replay =
        tx.execute(
            s ->
                service.acquire(
                    listing, key, buyer, view.financeTerms().path("termsHash").asText()));
    assertThat(replay).isEqualTo(acquisition);
    var a = service.acquisition(acquisition, buyer);
    assertThat(a.financeTerms().path("refundPolicy").path("rateBps").asInt()).isEqualTo(8000);
    assertThat(a.financeTerms().path("buyerId").asText()).isEqualTo(buyer.id().toString());
    assertThat(a.financeTerms().path("amount").asText()).isEqualTo("100000");
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "UPDATE transfer_acquisitions SET"
                        + " finance_terms=jsonb_set(finance_terms,'{quotedFeeAmount}','0') WHERE"
                        + " id=?",
                    acquisition))
        .isInstanceOf(org.springframework.dao.DataAccessException.class);
  }

  @Test
  void demoNeedsOwnerFeeAndFreshConsentButNoVerifiedDestination() {
    assertThatCode(
            () -> service.quote(new Create(booking, new BigDecimal("100000"), deadline), seller))
        .doesNotThrowAnyException();
    assertThatThrownBy(() -> service.quote(quoteInput(), buyer))
        .isInstanceOf(ForbiddenException.class);
    var q = service.quote(quoteInput(), seller);
    assertThatThrownBy(
            () ->
                tx.execute(
                    s ->
                        service.create(
                            new Create(
                                booking,
                                new BigDecimal("90000"),
                                deadline,
                                beneficiary,
                                q.path("termsHash").asText()),
                            UUID.randomUUID().toString(),
                            seller)))
        .isInstanceOf(ConflictException.class);
    when(deps.feePolicy(facility)).thenReturn(json.valueToTree(Map.of("configured", false)));
    assertThatThrownBy(
            () ->
                tx.execute(
                    s ->
                        service.create(
                            new Create(
                                booking,
                                new BigDecimal("100000"),
                                deadline,
                                beneficiary,
                                q.path("termsHash").asText()),
                            UUID.randomUUID().toString(),
                            seller)))
        .isInstanceOf(ConflictException.class);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM transfer_listings", Integer.class))
        .isZero();
    verify(deps, never()).beneficiary(any(), any());
  }

  @Test
  void changedPriceRequiresNewConsentAndLockedSnapshotCannotBeReplaced() {
    UUID id = listing();
    var old = repo.find(id).financeTerms();
    assertThatThrownBy(
            () ->
                tx.executeWithoutResult(
                    s ->
                        service.edit(
                            id,
                            new Edit(
                                new BigDecimal("90000"),
                                deadline,
                                beneficiary,
                                old.path("termsHash").asText()),
                            seller)))
        .isInstanceOf(ConflictException.class);
    var q =
        service.quote(
            new Create(booking, new BigDecimal("90000"), deadline, beneficiary, null), seller);
    tx.executeWithoutResult(
        s ->
            service.edit(
                id,
                new Edit(
                    new BigDecimal("90000"), deadline, beneficiary, q.path("termsHash").asText()),
                seller));
    jdbc.update("UPDATE transfer_listings SET workflow='READY' WHERE id=?", id);
    UUID acquisition =
        tx.execute(
            s ->
                service.acquire(
                    id, UUID.randomUUID().toString(), buyer, q.path("termsHash").asText()));
    assertThatThrownBy(
            () ->
                tx.executeWithoutResult(
                    s ->
                        service.edit(
                            id,
                            new Edit(
                                new BigDecimal("80000"),
                                deadline,
                                beneficiary,
                                q.path("termsHash").asText()),
                            seller)))
        .isInstanceOf(ConflictException.class);
    assertThat(
            service
                .acquisition(acquisition, buyer)
                .financeTerms()
                .path("quotedSellerNetAmount")
                .asText())
        .isEqualTo("85500");
  }

  @Test
  void legacyListingNeedsExplicitSellerAcceptanceWhenNewCohortIsEnabled() {
    var legacyService =
        new TransferService(repo, deps, clock, 600, json, false, true, demoQuotes());
    UUID id =
        tx.execute(
            s ->
                legacyService.create(
                    new Create(booking, new BigDecimal("100000"), deadline),
                    UUID.randomUUID().toString(),
                    seller));
    jdbc.update("UPDATE transfer_listings SET workflow='READY' WHERE id=?", id);
    assertThat(repo.find(id).financeTerms()).isNull();
    assertThatThrownBy(
            () -> tx.execute(s -> service.acquire(id, UUID.randomUUID().toString(), buyer)))
        .isInstanceOf(ConflictException.class)
        .hasMessageContaining("explicitly");
    var q = service.quote(quoteInput(), seller);
    tx.executeWithoutResult(
        s ->
            service.edit(
                id,
                new Edit(
                    new BigDecimal("100000"), deadline, beneficiary, q.path("termsHash").asText()),
                seller));
    assertThat(repo.find(id).financeTerms().path("quotedFeeAmount").asText()).isEqualTo("5000");
  }

  record Locked(UUID listing, UUID acquisition) {}

  Locked locked() {
    UUID id = listing();
    String hash = repo.find(id).financeTerms().path("termsHash").asText();
    UUID acquisition =
        tx.execute(s -> service.acquire(id, UUID.randomUUID().toString(), buyer, hash));
    jdbc.update("UPDATE transfer_listings SET workflow='READY' WHERE id=?", id);
    return new Locked(id, acquisition);
  }

  com.sporthub.common.event.ReliableOutbox outbox() {
    return new com.sporthub.common.event.ReliableOutbox(
        jdbc,
        json,
        mock(org.springframework.amqp.rabbit.core.RabbitTemplate.class),
        tx.getTransactionManager());
  }

  TransferWorkflow workflow() {
    return new TransferWorkflow(repo, deps, tx.getTransactionManager(), clock, outbox(), 10);
  }

  TransferPaymentConsumer consumer() {
    return new TransferPaymentConsumer(repo, json, clock, workflow());
  }

  JsonNode outcome(Locked f, UUID payment, boolean canonical) {
    var a = repo.acquisition(f.acquisition());
    var p =
        json.createObjectNode()
            .put("acquisitionId", a.id().toString())
            .put("listingId", f.listing().toString())
            .put("bookingId", booking.toString())
            .put("sellerId", seller.id().toString())
            .put("buyerId", buyer.id().toString())
            .put("paymentId", payment.toString())
            .put("payerId", buyer.id().toString())
            .put("purpose", "TRANSFER")
            .put("amount", 100000)
            .put("currency", "VND")
            .put("status", "SUCCESS")
            .put("paidAt", clock.instant().toString());
    p.putNull("memberId");
    if (canonical) {
      p.put("canonicalDecision", "SETTLED_OBLIGATION")
          .put(
              "termsHash",
              a.financeTerms() == null
                  ? "a".repeat(64)
                  : a.financeTerms().path("termsHash").asText());
      p.put("collectionFingerprint", com.sporthub.common.money.BookingPaymentFingerprint.of(p));
    }
    return json.valueToTree(
        com.sporthub.common.event.DomainEvent.create(
            canonical ? "transfer.collection.verified" : "payment.completed",
            1,
            "payment-service",
            payment,
            p));
  }

  @Test
  void financialAcquisitionOnlyConsumesCanonicalCollectionAndCannotHandoffSecondSuccess() {
    var f = locked();
    var payments = consumer();
    UUID canonical = UUID.randomUUID(), extra = UUID.randomUUID();
    tx.executeWithoutResult(s -> payments.apply(outcome(f, extra, false)));
    assertThat(repo.acquisition(f.acquisition()).paymentId()).isNull();
    tx.executeWithoutResult(s -> payments.apply(outcome(f, canonical, true)));
    tx.executeWithoutResult(s -> payments.apply(outcome(f, extra, true)));
    assertThat(repo.acquisition(f.acquisition()).paymentId()).isEqualTo(canonical);
    assertThat(repo.acquisition(f.acquisition()).state()).isEqualTo("PENDING_HANDOFF");
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM transfer_reconciliation WHERE payment_id=?",
                Integer.class,
                extra))
        .isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM event_outbox WHERE"
                    + " body->>'eventType'='transfer.collection.review.required'",
                Integer.class))
        .isEqualTo(1);
  }

  @Test
  void canonicalCollectionWithDifferentAcceptedTermsStaysInReview() {
    var f = locked();
    var event = (ObjectNode) outcome(f, UUID.randomUUID(), true);
    ((ObjectNode) event.path("payload")).put("termsHash", "0".repeat(64));
    tx.executeWithoutResult(s -> consumer().apply(event));
    assertThat(repo.acquisition(f.acquisition()).state()).isEqualTo("PENDING");
    assertThat(
            jdbc.queryForObject(
                "SELECT reason FROM transfer_reconciliation WHERE listing_id=?",
                String.class,
                f.listing()))
        .isEqualTo("CANONICAL_COLLECTION_PROOF_MISMATCH");
  }

  ObjectNode abortProof(Locked f, UUID payment) {
    var a = repo.acquisition(f.acquisition());
    return json.createObjectNode()
        .put("decision", "ABORTED")
        .put("origin", "ACQUISITION_DECISION")
        .put("acquisitionId", a.id().toString())
        .put("listingId", f.listing().toString())
        .put("bookingId", booking.toString())
        .put("sellerId", seller.id().toString())
        .put("buyerId", buyer.id().toString())
        .put("paymentId", payment.toString())
        .put("amount", a.amount())
        .put("currency", "VND")
        .put("decidedAt", clock.instant().toString());
  }

  @Test
  void rejectedDemoHandoffUsesTheAuthoritativeAbortAndClosesTheAcquisition() {
    var f = locked();
    UUID payment = UUID.randomUUID();
    tx.executeWithoutResult(s -> consumer().apply(outcome(f, payment, true)));
    when(deps.receipt(f.listing())).thenReturn(json.createObjectNode().put("state", "ACTIVE"));
    when(deps.abort(eq(f.acquisition()), any(), anyString())).thenReturn(abortProof(f, payment));
    workflow().sync(f.listing());
    assertThat(repo.acquisition(f.acquisition()).state()).isEqualTo("FAILED");
    assertThat(repo.find(f.listing()).workflow()).isEqualTo("UNLOCK");
    assertThat(
            jdbc.queryForObject(
                "SELECT abort_decision->>'decision' FROM transfer_acquisitions WHERE id=?",
                String.class,
                f.acquisition()))
        .isEqualTo("ABORTED");
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM event_outbox WHERE"
                    + " body->>'eventType'='transfer.handoff.completed'",
                Integer.class))
        .isZero();
    verify(deps)
        .abort(
            eq(f.acquisition()),
            argThat(command -> ((Map<?, ?>) command).containsKey("financeTerms")),
            eq("HANDOFF_REJECTED"));
  }

  @Test
  void unavailableAbortNeverPretendsThatBuyerWasRefundedOrSellerWasPaid() {
    var f = locked();
    UUID payment = UUID.randomUUID();
    tx.executeWithoutResult(s -> consumer().apply(outcome(f, payment, true)));
    when(deps.receipt(f.listing())).thenReturn(json.createObjectNode().put("state", "ACTIVE"));
    when(deps.abort(eq(f.acquisition()), any(), anyString()))
        .thenThrow(new TransferDependencies.Unavailable("BOOKING_UNAVAILABLE"));
    workflow().sync(f.listing());
    assertThat(repo.acquisition(f.acquisition()).state()).isIn("PENDING_HANDOFF", "PENDING_AUDIT");
    assertThat(
            jdbc.queryForObject(
                "SELECT abort_decision FROM transfer_acquisitions WHERE id=?",
                Object.class,
                f.acquisition()))
        .isNull();
  }

  @Test
  void changedOwnerFeeRequiresFreshSellerConsentBeforeLockButNeverRepricesAnAcquisition() {
    UUID id = listing();
    String old = repo.find(id).financeTerms().path("termsHash").asText();
    when(deps.feePolicy(facility))
        .thenReturn(
            json.valueToTree(
                Map.of(
                    "configured",
                    true,
                    "version",
                    2,
                    "feeBps",
                    1000,
                    "termsHash",
                    Signatures.sha256(facility + "|2|1000|VND|HALF_UP|FACILITY_TRANSFER_FEE"))));
    assertThatThrownBy(
            () -> tx.execute(s -> service.acquire(id, UUID.randomUUID().toString(), buyer, old)))
        .isInstanceOf(ConflictException.class);
    var quote = service.quoteEdit(id, new Edit(new BigDecimal("100000"), deadline), seller);
    tx.executeWithoutResult(
        s ->
            service.edit(
                id,
                new Edit(
                    new BigDecimal("100000"), deadline, null, quote.path("termsHash").asText()),
                seller));
    jdbc.update("UPDATE transfer_listings SET workflow='READY' WHERE id=?", id);
    UUID acquisition =
        tx.execute(
            s ->
                service.acquire(
                    id, UUID.randomUUID().toString(), buyer, quote.path("termsHash").asText()));
    when(deps.feePolicy(facility))
        .thenReturn(
            json.valueToTree(
                Map.of(
                    "configured",
                    true,
                    "version",
                    3,
                    "feeBps",
                    5000,
                    "termsHash",
                    Signatures.sha256(facility + "|3|5000|VND|HALF_UP|FACILITY_TRANSFER_FEE"))));
    assertThat(repo.acquisition(acquisition).financeTerms().path("feeBps").asInt()).isEqualTo(1000);
    assertThat(repo.acquisition(acquisition).financeTerms().path("quotedSellerNetAmount").asText())
        .isEqualTo("90000");
  }

  @Test
  void handoffSuccessAndOutboxCommitTogetherAndRecoverLostLocalResponse() {
    var f = locked();
    var a = repo.acquisition(f.acquisition());
    UUID payment = UUID.randomUUID();
    tx.executeWithoutResult(s -> consumer().apply(outcome(f, payment, true)));
    var lease =
        new java.util.concurrent.atomic.AtomicReference<>(
            json.createObjectNode()
                .put("state", "LOCKED")
                .put("listingId", f.listing().toString())
                .put("bookingId", booking.toString())
                .put("sellerId", seller.id().toString())
                .put("acquisitionId", a.id().toString())
                .put("buyerId", buyer.id().toString()));
    var decision = new java.util.concurrent.atomic.AtomicReference<ObjectNode>();
    when(deps.receipt(f.listing())).thenAnswer(call -> lease.get().deepCopy());
    when(deps.command(eq("complete"), any()))
        .thenAnswer(
            call -> {
              Map<String, Object> c = call.getArgument(1);
              String fp =
                  com.sporthub.common.money.TransferHandoff.fingerprint(
                      f.listing(),
                      booking,
                      seller.id(),
                      a.id(),
                      buyer.id(),
                      payment,
                      a.amount(),
                      a.currency(),
                      Instant.parse(c.get("paidAt").toString()),
                      a.expiresAt(),
                      deadline);
              lease
                  .get()
                  .put("state", "COMPLETED")
                  .put("commandFingerprint", fp)
                  .put("paymentId", payment.toString());
              decision.set(
                  json.createObjectNode()
                      .put("decision", "COMPLETED")
                      .put("acquisitionId", a.id().toString())
                      .put("paymentId", payment.toString())
                      .put("commandFingerprint", fp)
                      .put("decidedAt", clock.instant().toString())
                      .put("bookingVersion", 1));
              return lease.get();
            });
    when(deps.decision(a.id())).thenAnswer(call -> decision.get());
    jdbc.execute(
        "CREATE FUNCTION reject_handoff_outbox() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF"
            + " NEW.body->>'eventType'='transfer.handoff.completed' THEN RAISE EXCEPTION"
            + " 'Controlled outbox failure'; END IF;RETURN NEW;END $$");
    jdbc.execute(
        "CREATE TRIGGER reject_handoff_outbox BEFORE INSERT ON event_outbox FOR EACH ROW EXECUTE"
            + " FUNCTION reject_handoff_outbox()");
    var workflow = workflow();
    assertThatThrownBy(() -> workflow.sync(f.listing()))
        .isInstanceOf(org.springframework.dao.DataAccessException.class);
    assertThat(lease.get().path("state").asText()).isEqualTo("COMPLETED");
    assertThat(repo.acquisition(a.id()).state()).isEqualTo("PENDING_HANDOFF");
    jdbc.execute("DROP TRIGGER reject_handoff_outbox ON event_outbox");
    workflow.sync(f.listing());
    workflow.sync(f.listing());
    assertThat(repo.acquisition(a.id()).state()).isEqualTo("SUCCESS");
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM event_outbox WHERE"
                    + " body->>'eventType'='transfer.handoff.completed'",
                Integer.class))
        .isEqualTo(1);
    verify(deps, times(1)).command(eq("complete"), any());
  }
}
