package com.sporthub.transfer.service;

import static com.sporthub.transfer.web.TransferDtos.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sporthub.common.exception.*;
import com.sporthub.common.money.TransferFinancialTerms;
import com.sporthub.common.security.RemoteIdentity.Caller;
import com.sporthub.common.security.Signatures;
import com.sporthub.transfer.repository.TransferRepository;
import com.sporthub.transfer.repository.TransferRepository.Row;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class TransferService {
  private final TransferListingReads parts;
  private final TransferRepository repo;
  private final TransferDependencies deps;
  private final Clock clock;
  private final int lockSeconds;
  private final ObjectMapper json;
  private final boolean financeEnabled, acquisitionsEnabled;
  private final FacilityTransferQuotes facilityQuotes;

  public TransferService(
      TransferRepository repo,
      TransferDependencies deps,
      Clock clock,
      @Value("${TRANSFER_ACQUISITION_SECONDS:600}") int lockSeconds,
      ObjectMapper json,
      @Value("${transfer.finance.enabled:true}") boolean financeEnabled,
      @Value("${transfer.acquisitions.enabled:true}") boolean acquisitionsEnabled,
      FacilityTransferQuotes facilityQuotes) {
    if (lockSeconds < 1) throw new IllegalArgumentException("Acquisition timeout must be positive");
    this.repo = repo;
    this.deps = deps;
    this.clock = clock;
    this.parts = TransferListingReads.create(repo, deps, clock, json, facilityQuotes);
    this.lockSeconds = lockSeconds;
    this.json = json;
    this.financeEnabled = financeEnabled;
    this.acquisitionsEnabled = acquisitionsEnabled;
    this.facilityQuotes = facilityQuotes;
  }

  public JsonNode quote(Create input, Caller caller) {
    return parts.quote(input, caller, financeEnabled);
  }

  public JsonNode quoteEdit(UUID id, Edit input, Caller caller) {
    return parts.quoteEdit(id, input, caller, financeEnabled);
  }

  private JsonNode acceptedFinance(
      JsonNode booking,
      BigDecimal price,
      Instant deadline,
      UUID beneficiary,
      String expectedHash,
      UUID seller) {
    if (!financeEnabled) {
      if (beneficiary != null || expectedHash != null)
        throw new ConflictException("New transfer finance is disabled during rollout");
      return null;
    }
    var quote = facilityQuotes.quote(booking, price, deadline, beneficiary, seller);
    if (!quote.path("termsHash").asText().equals(expectedHash))
      throw new ConflictException(
          "Review and accept the receiving facility, P/F/N and refund policy before publishing");
    return quote;
  }

  @Transactional
  public UUID create(Create input, String key, Caller caller) {
    caller.requireRole("CUSTOMER");
    caller.requireActiveVerified();
    key(key);
    repo.lock(caller.id());
    String fingerprint = Signatures.sha256(repo.write(input));
    var previous = repo.previous(caller.id(), "CREATE", key, fingerprint);
    if (previous != null) return previous;
    repo.lock(input.bookingId());
    var b = deps.booking(input.bookingId());
    if (!b.path("currentHolderId").asText().equals(caller.id().toString()))
      throw new ForbiddenException("Only your current booking can be transferred");
    parts.validateBooking(b);
    UUID facility = UUID.fromString(b.path("facilityId").asText()),
        court = UUID.fromString(b.path("courtId").asText());
    parts.policy(facility, Instant.parse(b.path("startsAt").asText()));
    var original = b.path("amount").decimalValue();
    parts.price(input.price(), original, b.path("currency").asText());
    parts.deadline(input.deadline(), Instant.parse(b.path("startsAt").asText()));
    var finance =
        acceptedFinance(
            b,
            input.price(),
            input.deadline(),
            (facilityQuotes.live()
                ? input.bankDestinationRevisionId()
                : input.beneficiaryRevisionId()),
            input.acceptedTermsHash(),
            caller.id());
    if (repo.jdbc()
            .queryForObject(
                "SELECT count(*) FROM transfer_listings WHERE booking_id=? AND state IN"
                    + " ('ACTIVE','LOCKED','PENDING_AUDIT')",
                Integer.class,
                input.bookingId())
        > 0) throw new ConflictException("Booking already has an active transfer listing");
    var context = deps.context(court);
    var venue =
        Map.of(
            "facilityName",
            context.path("facility").path("name").asText(),
            "courtName",
            context.path("court").path("name").asText(),
            "sportCategoryId",
            context.path("court").path("sportCategoryId").asText(),
            "address",
            context.path("facility").path("addressLine").asText(),
            "provinceCode",
            context.path("facility").path("provinceCode"),
            "province",
            context.path("facility").path("province").asText(),
            "wardCode",
            context.path("facility").path("wardCode"),
            "ward",
            context.path("facility").path("ward").asText(),
            "timezone",
            context.path("facility").path("timezone").asText(),
            "amenities",
            context.path("facility").path("amenities"));
    UUID id = UUID.randomUUID();
    repo.jdbc()
        .update(
            "INSERT INTO"
                + " transfer_listings(id,booking_id,seller_id,facility_id,court_id,snapshot,original_amount,price,currency,starts_at,ends_at,deadline,state,workflow,finance_terms)"
                + " VALUES(?,?,?,?,?,?::jsonb,?,?,?,?,?,?,'ACTIVE','REGISTER',?::jsonb)",
            id,
            input.bookingId(),
            caller.id(),
            facility,
            court,
            repo.write(venue),
            original,
            input.price(),
            b.path("currency").asText(),
            Timestamp.from(Instant.parse(b.path("startsAt").asText())),
            Timestamp.from(Instant.parse(b.path("endsAt").asText())),
            Timestamp.from(input.deadline().truncatedTo(ChronoUnit.MILLIS)),
            finance == null ? null : repo.write(finance));
    repo.remember(caller.id(), "CREATE", key, fingerprint, id);
    repo.audit(repo.find(id), caller.id(), "LISTING_CREATED", input);
    return id;
  }

  @Transactional(
      readOnly = true,
      isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
  public List<Listing> market(
      String q,
      UUID category,
      Instant after,
      Instant before,
      BigDecimal maxPrice,
      String sort,
      Caller caller) {
    return parts.market(q, category, after, before, maxPrice, sort, caller);
  }

  public List<Listing> mine(Caller caller) {
    return parts.mine(caller);
  }

  public List<Acquisition> purchases(Caller caller) {
    return parts.purchases(caller);
  }

  public Listing detail(UUID id, Caller caller) {
    return parts.detail(id, caller);
  }

  @Transactional
  public UUID acquire(UUID id, String key, Caller caller) {
    return acquire(id, key, caller, null);
  }

  @Transactional
  public UUID acquire(UUID id, String key, Caller caller, String acceptedHash) {
    caller.requireRole("CUSTOMER");
    caller.requireActiveVerified();
    key(key);
    repo.lock(caller.id());
    String fp = Signatures.sha256(acceptedHash == null ? id.toString() : id + "|" + acceptedHash);
    var previous = repo.previous(caller.id(), "ACQUIRE", key, fp);
    if (previous != null) return previous;
    if (!acquisitionsEnabled)
      throw new ConflictException(
          "New acquisitions are paused; existing money reconciliation continues");
    repo.lock(id);
    var r = repo.find(id);
    if (r.sellerId().equals(caller.id()))
      throw new ForbiddenException("You cannot acquire your own listing");
    requireLegacy(r.financeTerms());
    if (!r.state().equals("ACTIVE") || !r.workflow().equals("READY") || !validBooking(r))
      throw new ConflictException("Listing is locked, expired or no longer eligible");
    Instant cutoff = parts.policy(r.facilityId(), r.startsAt());
    if (r.financeTerms() != null) {
      if (!financeEnabled) throw new ConflictException("New financial acquisitions are paused");
      TransferFinancialTerms.validateSeller(r.financeTerms());
      if (!r.financeTerms().path("termsHash").asText().equals(acceptedHash))
        throw new ConflictException("Review the listing price and refund policy before acquiring");
      facilityQuotes.requireCurrent(r.financeTerms(), r.facilityId());
    } else if (financeEnabled)
      throw new ConflictException(
          "Seller must explicitly accept new financial terms for this legacy listing");
    Instant expiry = clock.instant().plusSeconds(lockSeconds).truncatedTo(ChronoUnit.MILLIS);
    if (expiry.isAfter(r.deadline())) expiry = r.deadline();
    if (expiry.isAfter(cutoff)) expiry = cutoff.truncatedTo(ChronoUnit.MILLIS);
    if (!expiry.isAfter(clock.instant()))
      throw new ConflictException("Transfer acquisition deadline elapsed during validation");
    UUID acquisition = UUID.randomUUID();
    var financial =
        r.financeTerms() == null
            ? null
            : TransferFinancialTerms.acquisition(
                r.financeTerms(),
                id,
                r.version(),
                acquisition,
                caller.id(),
                expiry,
                clock.instant().truncatedTo(ChronoUnit.MICROS));
    repo.jdbc()
        .update(
            "INSERT INTO"
                + " transfer_acquisitions(id,listing_id,buyer_id,amount,currency,expires_at,state,finance_terms)"
                + " VALUES(?,?,?,?,?,?,'PENDING',?::jsonb)",
            acquisition,
            id,
            caller.id(),
            r.price(),
            r.currency(),
            Timestamp.from(expiry),
            financial == null ? null : repo.write(financial));
    repo.jdbc()
        .update(
            "UPDATE transfer_listings SET"
                + " state='LOCKED',workflow='LOCK',active_acquisition_id=?,attempts=0,last_error=NULL,next_attempt_at=?,version=version+1,updated_at=NOW()"
                + " WHERE id=?",
            acquisition,
            Timestamp.from(clock.instant()),
            id);
    repo.remember(caller.id(), "ACQUIRE", key, fp, acquisition);
    repo.audit(r, caller.id(), "ACQUISITION_CREATED", Map.of("acquisitionId", acquisition));
    return acquisition;
  }

  public Acquisition acquisition(UUID id, Caller caller) {
    var a = repo.acquisition(id);
    if (!a.buyerId().equals(caller.id())
        && !(com.sporthub.common.money.PeerResaleContract.isPeer(a.financeTerms())
            && repo.find(a.listingId()).sellerId().equals(caller.id())))
      throw new ForbiddenException("Acquisition belongs to another buyer");
    return a;
  }

  @Transactional
  public Payable payable(UUID id, Caller caller) {
    var before = acquisition(id, caller);
    requireLegacy(before.financeTerms());
    repo.lock(before.listingId());
    var a = acquisition(id, caller);
    var r = repo.find(a.listingId());
    if (financeEnabled
        && !(facilityQuotes.live()
            ? com.sporthub.common.money.FacilityTransferTerms.isReal(a.financeTerms())
            : com.sporthub.common.money.DemoTransferTerms.isDemo(a.financeTerms())))
      throw new ConflictException(
          "Legacy acquisition needs review or cancellation before accepting new facility DEMO"
              + " terms");
    if (!a.state().equals("PENDING")
        || !a.expiresAt().isAfter(clock.instant())
        || !r.workflow().equals("READY")
        || !r.state().equals("LOCKED")
        || !id.equals(r.activeAcquisitionId())
        || !valid(r)) throw new ConflictException("Transfer acquisition is no longer payable");
    var lease = deps.receipt(r.id());
    if (lease == null
        || !lease.path("state").asText().equals("LOCKED")
        || !lease.path("acquisitionId").asText().equals(id.toString())
        || !lease.path("buyerId").asText().equals(caller.id().toString())
        || !lease.path("bookingId").asText().equals(r.bookingId().toString())
        || !lease.path("sellerId").asText().equals(r.sellerId().toString())
        || lease.path("price").decimalValue().compareTo(a.amount()) != 0
        || !a.currency().equals(lease.path("currency").asText())
        || !a.expiresAt().equals(Instant.parse(lease.path("expiresAt").asText())))
      throw new ConflictException("Booking does not hold the matching transfer lease");
    if (!a.expiresAt().isAfter(clock.instant()))
      throw new ConflictException("Transfer deadline elapsed during payable validation");
    return new Payable(
        r.bookingId(),
        caller.id(),
        r.sellerId(),
        id,
        a.amount(),
        a.currency(),
        a.expiresAt(),
        "TRANSFER",
        a.financeTerms());
  }

  @Transactional
  public void cancel(UUID id, Caller caller) {
    var a = acquisition(id, caller);
    requireLegacy(a.financeTerms());
    repo.lock(a.listingId());
    a = repo.acquisition(id);
    if (a.state().equals("CANCELLED")) return;
    if (!a.state().equals("PENDING"))
      throw new ConflictException("Only an unpaid pending acquisition can be cancelled");
    repo.jdbc()
        .update(
            "UPDATE transfer_acquisitions SET state='CANCELLED',updated_at=NOW() WHERE id=?", id);
    repo.jdbc()
        .update(
            "UPDATE transfer_listings SET workflow='UNLOCK',next_attempt_at=NOW(),updated_at=NOW()"
                + " WHERE id=? AND active_acquisition_id=?",
            a.listingId(),
            id);
    repo.audit(
        repo.find(a.listingId()),
        caller.id(),
        "ACQUISITION_CANCELLED",
        Map.of("acquisitionId", id));
  }

  @Transactional
  public void edit(UUID id, Edit input, Caller caller) {
    repo.lock(id);
    var r = parts.owner(id, caller);
    requireLegacy(r.financeTerms());
    boolean same =
        r.price().compareTo(input.price()) == 0
            && r.deadline().equals(input.deadline().truncatedTo(ChronoUnit.MILLIS));
    if (!r.state().equals("ACTIVE")
        || !(r.workflow().equals("READY") || same && r.workflow().equals("EDIT"))
        || !valid(r))
      throw new ConflictException("Only an eligible unlocked active listing can be edited");
    parts.price(input.price(), r.originalAmount(), r.currency());
    parts.deadline(input.deadline(), r.startsAt());
    parts.policy(r.facilityId(), r.startsAt());
    if (r.financeTerms() != null && !financeEnabled)
      throw new ConflictException("New financial terms are paused");
    var finance =
        acceptedFinance(
            deps.booking(r.bookingId()),
            input.price(),
            input.deadline(),
            (facilityQuotes.live()
                ? input.bankDestinationRevisionId()
                : input.beneficiaryRevisionId()),
            input.acceptedTermsHash(),
            caller.id());
    boolean sameTerms =
        finance == null
            ? r.financeTerms() == null
            : r.financeTerms() != null
                && finance
                    .path("termsHash")
                    .asText()
                    .equals(r.financeTerms().path("termsHash").asText());
    if (same && sameTerms) return;
    if (!r.workflow().equals("READY"))
      throw new ConflictException("Wait for the previous edit before accepting different terms");
    repo.jdbc()
        .update(
            "UPDATE transfer_listings SET"
                + " price=?,deadline=?,finance_terms=?::jsonb,workflow='EDIT',attempts=0,last_error=NULL,next_attempt_at=?,version=version+1,updated_at=NOW()"
                + " WHERE id=?",
            input.price(),
            Timestamp.from(input.deadline().truncatedTo(ChronoUnit.MILLIS)),
            finance == null ? null : repo.write(finance),
            Timestamp.from(clock.instant()),
            id);
    repo.audit(
        r,
        caller.id(),
        "LISTING_EDITED",
        Map.of(
            "previousPrice",
            r.price(),
            "previousDeadline",
            r.deadline(),
            "price",
            input.price(),
            "deadline",
            input.deadline()));
  }

  @Transactional
  public void withdraw(UUID id, Caller caller) {
    repo.lock(id);
    var r = parts.owner(id, caller);
    requireLegacy(r.financeTerms());
    if (r.state().equals("WITHDRAWN")) return;
    if (!r.state().equals("ACTIVE") || !r.workflow().equals("READY"))
      throw new ConflictException("Only an unlocked active listing can be withdrawn");
    repo.jdbc()
        .update(
            "UPDATE transfer_listings SET"
                + " state='WITHDRAWN',workflow='WITHDRAW',next_attempt_at=NOW(),version=version+1,updated_at=NOW()"
                + " WHERE id=?",
            id);
    repo.audit(r, caller.id(), "LISTING_WITHDRAWN", Map.of());
  }

  public Map<String, Object> synchronization(UUID id, Caller caller) {
    var r = repo.find(id);
    participant(r, caller);
    var data =
        repo.jdbc()
            .queryForMap(
                "SELECT workflow,attempts,next_attempt_at,last_error,retry_workflow FROM"
                    + " transfer_listings WHERE id=?",
                id);
    var result = new LinkedHashMap<String, Object>();
    result.put("listingId", id);
    result.put("state", r.state());
    result.put("workflow", r.workflow());
    result.put("attempts", data.get("attempts"));
    result.put("nextAttemptAt", ((Timestamp) data.get("next_attempt_at")).toInstant());
    String error = (String) data.get("last_error");
    result.put("lastErrorCode", safeError(error));
    result.put(
        "retryable",
        r.workflow().equals("REVIEW")
            && data.get("retry_workflow") != null
            && error != null
            && error.startsWith("TECHNICAL_RETRY_EXHAUSTED:"));
    return result;
  }

  @Transactional
  public void retrySynchronization(UUID id, Caller caller) {
    repo.lock(id);
    var r = repo.find(id);
    participant(r, caller);
    requireLegacy(r.financeTerms());
    if (r.workflow().equals("HANDOFF") || r.state().equals("COMPLETED")) return;
    var data =
        repo.jdbc()
            .queryForMap("SELECT last_error,retry_workflow FROM transfer_listings WHERE id=?", id);
    String error = (String) data.get("last_error"), stage = (String) data.get("retry_workflow");
    if (!r.workflow().equals("REVIEW")
        || stage == null
        || error == null
        || !error.startsWith("TECHNICAL_RETRY_EXHAUSTED:"))
      throw new ConflictException(
          "Only technical retry exhaustion can be retried; business or financial review needs"
              + " resolution");
    if (stage.equals("HANDOFF")) {
      var a = repo.acquisition(r.activeAcquisitionId());
      if (!a.state().equals("PENDING_AUDIT") || a.paymentId() == null)
        throw new ConflictException("Verified handoff command is missing");
      repo.jdbc()
          .update(
              "UPDATE transfer_acquisitions SET state='PENDING_HANDOFF',updated_at=NOW() WHERE"
                  + " id=?",
              a.id());
    }
    repo.jdbc()
        .update(
            "UPDATE transfer_listings SET state=CASE WHEN ?='HANDOFF' THEN 'LOCKED' ELSE state"
                + " END,workflow=?,attempts=0,last_error=NULL,retry_workflow=NULL,next_attempt_at=?,updated_at=NOW()"
                + " WHERE id=?",
            stage,
            stage,
            Timestamp.from(clock.instant()),
            id);
    repo.audit(r, caller.id(), "TRANSFER_TECHNICAL_RETRY_REQUESTED", Map.of("workflow", stage));
  }

  private void participant(Row r, Caller caller) {
    if (r.sellerId().equals(caller.id())) return;
    if (r.activeAcquisitionId() != null
        && repo.acquisition(r.activeAcquisitionId()).buyerId().equals(caller.id())) return;
    throw new ForbiddenException("Transfer synchronization belongs to another seller or buyer");
  }

  private String safeError(String error) {
    if (error == null) return null;
    return error.matches("(?:TECHNICAL_RETRY_EXHAUSTED:)?[A-Z_]{1,80}")
        ? error
        : "LEGACY_TRANSFER_ERROR";
  }

  private boolean valid(Row r) {
    return parts.valid(r);
  }

  private boolean validBooking(Row r) {
    return parts.validBooking(r);
  }

  private void key(String key) {
    if (key == null || !key.matches("[A-Za-z0-9_.:-]{1,100}"))
      throw new IllegalArgumentException("A valid Idempotency-Key is required");
  }

  static void requireLegacy(JsonNode terms) {
    if (com.sporthub.common.money.PeerResaleContract.isPeer(terms))
      throw new ConflictException(
          "Phiên P2P dùng lệnh xác nhận giữa người mua và người bán; không tạo thanh toán cho cơ"
              + " sở.");
  }
}
