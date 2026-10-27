package com.sporthub.booking.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.sporthub.booking.repository.BookingRepository;
import com.sporthub.booking.web.BookingDtos.Booking;
import com.sporthub.common.exception.*;
import com.sporthub.common.money.*;
import com.sporthub.common.security.RemoteIdentity.Caller;
import com.sporthub.common.security.Signatures;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.TransactionTemplate;

/** Application DEMO facts and direct, scoped execution. No bank or funding pipeline. */
@Service
public class BookingDemoFinance {
  public record Outcome(String outcome) {}

  public record Entitlement(
      Booking booking,
      UUID quoteId,
      UUID acquisitionId,
      String reason,
      String sourceKey,
      BigDecimal base,
      int rateBps,
      BigDecimal target,
      long policyVersion,
      String policyHash,
      List<DemoRefundMath.Allocation> allocations,
      UUID requestedBy) {}

  private final DemoFinanceReads parts;
  private final BookingRepository repo;
  private final BookingDependencies dependencies;
  private final Clock clock;
  private final TransactionTemplate tx;
  private final CollectionRuntime runtime;

  public BookingDemoFinance(
      BookingRepository repo,
      BookingDependencies dependencies,
      Clock clock,
      PlatformTransactionManager manager,
      CollectionRuntime runtime) {
    this.repo = repo;
    this.dependencies = dependencies;
    this.clock = clock;
    this.parts = DemoFinanceReads.create(repo, dependencies, clock);
    this.tx = new TransactionTemplate(manager);
    this.runtime = runtime;
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public void recordCollection(JsonNode event) {
    var p = event.path("payload");
    if (!p.path("provider").asText().equals("DEMO")
        || !p.path("status").asText().equals("SUCCESS")
        || !p.path("canonical").asBoolean()) return;
    var amount = p.path("amount").decimalValue();
    if (amount.stripTrailingZeros().scale() > 0)
      return; // Preserve fractional legacy data without rounding it.
    UUID bookingId = id(p, "bookingId"), payment = id(p, "paymentId"), payer = id(p, "payerId");
    var b = repo.find(bookingId);
    String purpose = p.path("purpose").asText();
    UUID acquisition = null, member = null;
    JsonNode terms = null;
    if (purpose.equals("TRANSFER")) {
      terms = p.path("transferTerms");
      if (!DemoTransferTerms.isDemo(terms)) return;
      TransferFinancialTerms.validateAcquisition(terms);
      acquisition = id(terms, "acquisitionId");
      if (!p.path("acquisitionId").asText().equals(acquisition.toString())
          || !payer.toString().equals(terms.path("buyerId").asText())
          || !b.id().toString().equals(terms.path("bookingId").asText())
          || !b.facilityId().toString().equals(terms.path("facilityId").asText())
          || !b.courtId().toString().equals(terms.path("courtId").asText())
          || amount.compareTo(TransferFinancialTerms.decimal(terms, "amount")) != 0)
        throw new ConflictException("DEMO collection and acquisition differ");
    } else if (purpose.equals("GROUP_CONTRIBUTION")) member = id(p, "memberId");
    else if (!purpose.equals("BOOKING"))
      throw new IllegalArgumentException("Unknown collection purpose");
    if (!b.currency().equals(p.path("currency").asText()))
      throw new ConflictException("DEMO currency differs");
    String fingerprint = BookingPaymentFingerprint.of(p);
    int inserted =
        repo.jdbc()
            .update(
                "INSERT INTO"
                    + " booking_demo_collections(payment_id,booking_id,payer_id,member_id,acquisition_id,purpose,amount,currency,paid_at,source_event_id,fingerprint,transfer_terms)"
                    + " VALUES(?,?,?,?,?,?,?,?,?,?,?,?::jsonb) ON CONFLICT(payment_id) DO NOTHING",
                payment,
                b.id(),
                payer,
                member,
                acquisition,
                purpose,
                amount,
                b.currency(),
                time(Instant.parse(p.path("paidAt").asText())),
                id(event, "eventId"),
                fingerprint,
                terms == null ? null : repo.write(terms));
    if (inserted == 0
        && !fingerprint.equals(
            repo.jdbc()
                .queryForObject(
                    "SELECT fingerprint FROM booking_demo_collections WHERE payment_id=?",
                    String.class,
                    payment))) throw new ConflictException("DEMO payment identity conflict");
    if (acquisition != null) {
      var decisions =
          repo.jdbc()
              .queryForList(
                  "SELECT decision,payment_id FROM transfer_acquisition_decisions WHERE"
                      + " acquisition_id=?",
                  acquisition);
      if (!decisions.isEmpty()
          && decisions.getFirst().get("decision").equals("ABORTED")
          && payment.equals(decisions.getFirst().get("payment_id"))) aborted(terms, payment, b);
    }
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public void completed(JsonNode terms, UUID payment, Booking b, Instant handoffAt) {
    if (!DemoTransferTerms.isDemo(terms)) return;
    TransferFinancialTerms.validateAcquisition(terms);
    UUID acquisition = id(terms, "acquisitionId");
    var decision =
        repo.jdbc()
            .queryForMap(
                "SELECT * FROM transfer_acquisition_decisions WHERE acquisition_id=?", acquisition);
    validateDecision(terms, payment, b, decision, "COMPLETED");
    if (!id(terms, "buyerId").equals(b.currentHolderId()))
      throw new ConflictException("Buyer ownership is not committed");
    var fee = TransferFinancialTerms.decimal(terms, "quotedFeeAmount");
    var net = TransferFinancialTerms.decimal(terms, "quotedSellerNetAmount");
    repo.jdbc()
        .update(
            """
INSERT INTO demo_transfer_settlements(acquisition_id,payment_id,booking_id,facility_id,court_id,receiver_owner_id,seller_id,buyer_id,
  amount,fee_bps,policy_version,fee_amount,seller_net_amount,terms_hash,terms,handoff_at,payout_state)
VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?::jsonb,?,?) ON CONFLICT(acquisition_id) DO NOTHING
""",
            acquisition,
            payment,
            b.id(),
            b.facilityId(),
            b.courtId(),
            id(terms, "receiverOwnerId"),
            id(terms, "sellerId"),
            id(terms, "buyerId"),
            TransferFinancialTerms.decimal(terms, "amount"),
            terms.path("feeBps").intValue(),
            terms.path("policyVersion").longValue(),
            fee,
            net,
            terms.path("termsHash").asText(),
            repo.write(terms),
            time(handoffAt),
            net.signum() == 0 ? "NOT_REQUIRED" : "UNPAID");
    if (!terms
        .path("termsHash")
        .asText()
        .equals(
            repo.jdbc()
                .queryForObject(
                    "SELECT terms_hash FROM demo_transfer_settlements WHERE acquisition_id=?",
                    String.class,
                    acquisition)))
      throw new ConflictException("DEMO handoff financial identity differs");
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public void aborted(JsonNode terms, UUID payment, Booking b) {
    if (!DemoTransferTerms.isDemo(terms)) return;
    TransferFinancialTerms.validateAcquisition(terms);
    UUID acquisition = id(terms, "acquisitionId");
    var decision =
        repo.jdbc()
            .queryForMap(
                "SELECT * FROM transfer_acquisition_decisions WHERE acquisition_id=?", acquisition);
    validateDecision(terms, payment, b, decision, "ABORTED");
    var price = TransferFinancialTerms.decimal(terms, "amount");
    UUID buyer = id(terms, "buyerId");
    var allocation = new DemoRefundMath.Allocation(payment, buyer, price, price);
    createRefund(
        new Entitlement(
            b,
            null,
            acquisition,
            "TECHNICAL_TRANSFER_FAILURE",
            "TECHNICAL:" + payment,
            price,
            10000,
            price,
            terms.path("refundPolicy").path("version").longValue(),
            terms.path("refundPolicy").path("termsHash").asText(),
            List.of(allocation),
            buyer));
  }

  private void validateDecision(
      JsonNode terms, UUID payment, Booking b, Map<String, Object> decision, String expected) {
    if (!expected.equals(decision.get("decision"))
        || !payment.equals(decision.get("payment_id"))
        || !b.id().equals(decision.get("booking_id"))
        || !id(terms, "sellerId").equals(decision.get("seller_id"))
        || !id(terms, "buyerId").equals(decision.get("buyer_id"))
        || !id(terms, "listingId").equals(decision.get("listing_id"))
        || !b.facilityId().equals(id(terms, "facilityId"))
        || !b.courtId().equals(id(terms, "courtId"))
        || TransferFinancialTerms.decimal(terms, "amount")
                .compareTo((BigDecimal) decision.get("amount"))
            != 0
        || !terms.path("currency").asText().equals(decision.get("currency")))
      throw new ConflictException("DEMO financial decision does not match handoff");
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public UUID createRefund(Entitlement e) {
    var existing =
        repo.jdbc()
            .queryForList("SELECT id FROM demo_refund_requests WHERE source_key=?", e.sourceKey());
    if (!existing.isEmpty()) return (UUID) existing.getFirst().get("id");
    UUID id = UUID.randomUUID();
    repo.jdbc()
        .update(
            """
INSERT INTO demo_refund_requests(id,source_key,booking_id,facility_id,court_id,quote_id,acquisition_id,reason,base_amount,rate_bps,target_amount,
  policy_version,policy_hash,allocations,requested_by,state,created_at)
VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?::jsonb,?,?,?)
""",
            id,
            e.sourceKey(),
            e.booking().id(),
            e.booking().facilityId(),
            e.booking().courtId(),
            e.quoteId(),
            e.acquisitionId(),
            e.reason(),
            e.base(),
            e.rateBps(),
            e.target(),
            e.policyVersion(),
            e.policyHash(),
            repo.write(e.allocations()),
            e.requestedBy(),
            e.target().signum() == 0 ? "NO_REFUND" : "PENDING",
            time(clock.instant()));
    return id;
  }

  public Map<String, Object> paySeller(
      UUID acquisition, Outcome input, String key, Caller actor, String token) {
    runtime.requireDemo();
    var before = settlement(acquisition, false);
    dependencies.facilityPermission(
        (UUID) before.get("facility_id"), actor, token, "TRANSFER_PAYOUT");
    return tx.execute(
        status -> {
          repo.lock(actor.id());
          repo.lock(acquisition);
          var row = settlement(acquisition, true);
          String fingerprint = fingerprint("SELLER_PAYOUT", acquisition, input, key);
          var replay = replay(actor, "SELLER_PAYOUT", key, fingerprint);
          if (replay != null) return replay;
          if (!Set.of("PAID", "NOT_REQUIRED").contains(row.get("payout_state"))) {
            event(
                "SELLER_PAYOUT",
                acquisition,
                (UUID) row.get("payment_id"),
                (UUID) row.get("booking_id"),
                (UUID) row.get("facility_id"),
                (UUID) row.get("seller_id"),
                (BigDecimal) row.get("seller_net_amount"),
                input.outcome(),
                actor,
                key);
            boolean success = input.outcome().equals("SUCCESS");
            repo.jdbc()
                .update(
                    "UPDATE demo_transfer_settlements SET"
                        + " payout_state=?,paid_at=?,performed_by=?,version=version+1 WHERE"
                        + " acquisition_id=?",
                    success ? "PAID" : "FAILED",
                    success ? time(clock.instant()) : null,
                    success ? actor.id() : null,
                    acquisition);
          }
          var result = project(settlement(acquisition, false));
          remember(actor, "SELLER_PAYOUT", key, fingerprint, result);
          return result;
        });
  }

  public Map<String, Object> refund(
      UUID id, Outcome input, String key, Caller actor, String token) {
    runtime.requireDemo();
    var before = refundRow(id, false);
    dependencies.facilityPermission(
        (UUID) before.get("facility_id"), actor, token, "BOOKING_REFUND");
    return tx.execute(
        status -> {
          repo.lock(actor.id());
          repo.lock(id);
          var row = refundRow(id, true);
          String fingerprint = fingerprint("REFUND", id, input, key);
          var replay = replay(actor, "REFUND", key, fingerprint);
          if (replay != null) return replay;
          if (!Set.of("REFUNDED", "NO_REFUND").contains(row.get("state"))) {
            var allocations = repo.read(row.get("allocations").toString());
            String kind =
                row.get("reason").equals("POLICY_CANCELLATION")
                    ? "POLICY_REFUND"
                    : "TECHNICAL_REFUND";
            for (var a : allocations) {
              var amount = TransferFinancialTerms.decimal(a, "refundAmount");
              if (amount.signum() > 0)
                event(
                    kind,
                    id,
                    id(a, "paymentId"),
                    (UUID) row.get("booking_id"),
                    (UUID) row.get("facility_id"),
                    id(a, "payerId"),
                    amount,
                    input.outcome(),
                    actor,
                    key);
            }
            boolean success = input.outcome().equals("SUCCESS");
            repo.jdbc()
                .update(
                    "UPDATE demo_refund_requests SET"
                        + " state=?,refunded_at=?,performed_by=?,version=version+1 WHERE id=?",
                    success ? "REFUNDED" : "FAILED",
                    success ? time(clock.instant()) : null,
                    success ? actor.id() : null,
                    id);
          }
          var result = project(refundRow(id, false));
          remember(actor, "REFUND", key, fingerprint, result);
          return result;
        });
  }

  public Map<String, Object> facility(UUID facility, Caller actor, String token) {
    return parts.facility(facility, actor, token);
  }

  public Map<String, Object> mine(Caller actor) {
    return parts.mine(actor);
  }

  public Map<String, Object> refundView(UUID id) {
    return parts.refundView(id);
  }

  @Transactional(readOnly = true)
  public Map<String, Object> monitor(Instant from, Instant to, Caller actor) {
    return parts.monitor(from, to, actor);
  }

  private boolean allowed(UUID facility, Caller actor, String token, String permission) {
    return parts.allowed(facility, actor, token, permission);
  }

  private Map<String, Object> settlement(UUID id, boolean lock) {
    var rows =
        repo.jdbc()
            .queryForList(
                "SELECT * FROM demo_transfer_settlements WHERE acquisition_id=?"
                    + (lock ? " FOR UPDATE" : ""),
                id);
    if (rows.isEmpty()) throw new ResourceNotFoundException("DEMO settlement not found");
    return rows.getFirst();
  }

  private Map<String, Object> refundRow(UUID id, boolean lock) {
    return parts.refundRow(id, lock);
  }

  private String fingerprint(String operation, UUID id, Outcome input, String key) {
    if (key == null
        || !key.matches("[A-Za-z0-9_.:-]{1,100}")
        || input == null
        || input.outcome() == null
        || !Set.of("SUCCESS", "FAILED").contains(input.outcome()))
      throw new IllegalArgumentException(
          "Supply a valid Idempotency-Key and DEMO SUCCESS or FAILED outcome");
    return Signatures.sha256(operation + "|" + id + "|" + input.outcome());
  }

  private Map<String, Object> replay(
      Caller actor, String operation, String key, String fingerprint) {
    var old =
        repo.jdbc()
            .queryForList(
                "SELECT fingerprint,result::text FROM demo_finance_commands WHERE actor_id=? AND"
                    + " operation=? AND idempotency_key=?",
                actor.id(),
                operation,
                key);
    if (old.isEmpty()) return null;
    if (!fingerprint.equals(old.getFirst().get("fingerprint")))
      throw new ConflictException("Idempotency key reused with a different command");
    return jsonMap(repo.read(old.getFirst().get("result").toString()));
  }

  private void remember(
      Caller actor, String operation, String key, String fingerprint, Map<String, Object> result) {
    repo.jdbc()
        .update(
            "INSERT INTO"
                + " demo_finance_commands(actor_id,operation,idempotency_key,fingerprint,result,created_at)"
                + " VALUES(?,?,?, ?,?::jsonb,?)",
            actor.id(),
            operation,
            key,
            fingerprint,
            repo.write(result),
            time(clock.instant()));
  }

  private void event(
      String kind,
      UUID subject,
      UUID payment,
      UUID booking,
      UUID facility,
      UUID payee,
      BigDecimal amount,
      String outcome,
      Caller actor,
      String key) {
    boolean guest =
        !kind.equals("SELLER_PAYOUT")
            && repo.jdbc()
                    .queryForObject(
                        "SELECT count(*) FROM counter_booking_guests g JOIN demo_refund_requests r"
                            + " ON r.booking_id=g.booking_id WHERE r.id=? AND r.acquisition_id IS"
                            + " NULL AND g.linked_customer_id IS NULL",
                        Integer.class,
                        subject)
                > 0;
    repo.jdbc()
        .update(
            "INSERT INTO"
                + " demo_money_events(id,kind,subject_id,payment_id,booking_id,facility_id,payee_id,guest_booking_id,amount,currency,outcome,performed_by,performed_at,command_key)"
                + " VALUES(?,?,?,?,?,?,?,?,?,'VND',?,?,?,?)",
            UUID.randomUUID(),
            kind,
            subject,
            payment,
            booking,
            facility,
            guest ? null : payee,
            guest ? booking : null,
            amount,
            outcome,
            actor.id(),
            time(clock.instant()),
            key);
    repo.history(
        booking,
        actor.id(),
        "DEMO_" + kind,
        Map.of("subjectId", subject, "paymentId", payment, "amount", amount, "outcome", outcome));
  }

  public Map<String, Object> project(Map<String, Object> row) {
    return parts.project(row);
  }

  private Map<String, Object> jsonMap(JsonNode json) {
    return parts.jsonMap(json);
  }

  private static UUID id(JsonNode p, String field) {
    return UUID.fromString(p.path(field).asText());
  }

  private static Timestamp time(Instant at) {
    return DemoFinanceReads.time(at);
  }
}
