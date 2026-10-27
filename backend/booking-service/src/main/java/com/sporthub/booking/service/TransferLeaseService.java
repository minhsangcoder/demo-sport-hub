package com.sporthub.booking.service;

import com.sporthub.booking.repository.BookingRepository;
import com.sporthub.booking.web.BookingDtos.Booking;
import com.sporthub.common.exception.*;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class TransferLeaseService {
  public record Command(
      UUID listingId,
      UUID bookingId,
      UUID sellerId,
      BigDecimal price,
      Instant deadline,
      UUID acquisitionId,
      UUID buyerId,
      Instant expiresAt,
      UUID paymentId,
      Instant paidAt,
      String currency,
      com.fasterxml.jackson.databind.JsonNode financeTerms) {
    public Command(
        UUID listingId,
        UUID bookingId,
        UUID sellerId,
        BigDecimal price,
        Instant deadline,
        UUID acquisitionId,
        UUID buyerId,
        Instant expiresAt,
        UUID paymentId,
        Instant paidAt,
        String currency) {
      this(
          listingId,
          bookingId,
          sellerId,
          price,
          deadline,
          acquisitionId,
          buyerId,
          expiresAt,
          paymentId,
          paidAt,
          currency,
          null);
    }
  }

  private final TransferLeaseState parts;
  private final BookingRepository repo;
  private final Clock clock;
  private final LocalAppliedCollections collections;
  private final BookingDemoFinance demo;
  private final FacilityTransferFinance real;
  private final BookingAdmission admission;

  @org.springframework.beans.factory.annotation.Autowired
  public TransferLeaseService(
      BookingRepository repo,
      Clock clock,
      LocalAppliedCollections collections,
      BookingDemoFinance demo,
      FacilityTransferFinance real,
      BookingAdmission admission) {
    this.repo = repo;
    this.clock = clock;
    this.parts = TransferLeaseState.create(repo, clock, collections);
    this.collections = Objects.requireNonNull(collections);
    this.demo = demo;
    this.real = real;
    this.admission = admission;
  }

  public TransferLeaseService(
      BookingRepository repo,
      Clock clock,
      LocalAppliedCollections collections,
      BookingDemoFinance demo,
      FacilityTransferFinance real) {
    this(repo, clock, collections, demo, real, new BookingAdmission(repo, clock));
  }

  public TransferLeaseService(
      BookingRepository repo,
      Clock clock,
      LocalAppliedCollections collections,
      BookingDemoFinance demo) {
    this(repo, clock, collections, demo, null);
  }

  public TransferLeaseService(
      BookingRepository repo, Clock clock, LocalAppliedCollections collections) {
    this(repo, clock, collections, null);
  }

  public TransferLeaseService(BookingRepository repo, Clock clock) {
    this(repo, clock, new LocalAppliedCollections(repo, clock));
  }

  public com.fasterxml.jackson.databind.JsonNode marketAdmission(UUID principal) {
    BookingUserFence.lock(repo, principal);
    PeerMarketAccess.allowed(repo, clock, principal);
    return repo.read(repo.write(Map.of("allowed", true)));
  }

  @Transactional(readOnly = true)
  public Map<String, Object> receipt(UUID listing) {
    return parts.receipt(listing);
  }

  public com.fasterxml.jackson.databind.JsonNode inspect(UUID id) {
    return parts.inspect(id);
  }

  public Booking command(String action, Command c) {
    if (c.listingId() == null || c.bookingId() == null || c.sellerId() == null)
      throw new IllegalArgumentException("Transfer identity is required");
    var b = repo.find(c.bookingId());
    BookingUserFence.lock(repo, b.currentHolderId(), c.sellerId(), c.buyerId());
    if (action.equals("lock") && c.buyerId() != null)
      admission.lockCourts(c.buyerId(), b.courtId());
    else repo.lock(b.courtId());
    parts.requireLegacy(c);
    b = repo.find(b.id());
    if (action.equals("complete")
        && (BookingMoneyCohorts.isReal(b)
            || com.sporthub.common.money.FacilityTransferTerms.isReal(c.financeTerms()))) {
      if (real == null) throw new ConflictException("REAL transfer handler is unavailable");
      real.requireCollection(c, b);
    }
    var rows =
        repo.jdbc().queryForList("SELECT * FROM transfer_leases WHERE listing_id=?", c.listingId());
    var lease = rows.isEmpty() ? null : rows.get(0);
    if (action.equals("edit") || action.equals("register") && lease == null)
      PeerMarketAccess.allowed(repo, clock, c.sellerId());
    if (action.equals("lock")
        && c.buyerId() != null
        && (lease == null || !"LOCKED".equals(lease.get("state")))) {
      PeerMarketAccess.allowed(repo, clock, c.buyerId());
      admission.requireAvailable(c.buyerId());
    }
    if (action.equals("edit")
        || action.equals("register") && lease == null
        || action.equals("lock") && (lease == null || !"LOCKED".equals(lease.get("state"))))
      PeerHandoffs.requireUnused(repo, b.id());
    if (c.acquisitionId() != null && Set.of("lock", "complete").contains(action)) {
      var decisions =
          repo.jdbc()
              .queryForList(
                  "SELECT * FROM transfer_acquisition_decisions WHERE acquisition_id=?",
                  c.acquisitionId());
      if (!decisions.isEmpty()) {
        var decision = decisions.getFirst();
        parts.identity(decision, c);
        if (decision.get("decision").equals("ABORTED"))
          throw new ConflictException(
              "Acquisition was authoritatively aborted; it cannot lock or complete");
        if (action.equals("complete") && fingerprint(c).equals(decision.get("command_fingerprint")))
          return b;
        throw new ConflictException("Acquisition already has an immutable completed decision");
      }
    }
    if (lease != null
        && (!lease.get("booking_id").equals(b.id())
            || !lease.get("seller_id").equals(c.sellerId())))
      throw new ConflictException("Transfer lease identity mismatch");
    if (action.equals("complete") && lease != null && lease.get("state").equals("COMPLETED")) {
      var proof =
          repo.jdbc()
              .queryForList(
                  "SELECT command_fingerprint FROM transfer_handoffs WHERE listing_id=?",
                  c.listingId());
      if (!Objects.equals(lease.get("payment_id"), c.paymentId())
          || !Objects.equals(lease.get("buyer_id"), c.buyerId())
          || !Objects.equals(lease.get("acquisition_id"), c.acquisitionId())
          || c.price() == null
          || ((BigDecimal) lease.get("price")).compareTo(c.price()) != 0
          || !b.currency().equals(c.currency())
          || (!proof.isEmpty()
              && !proof.getFirst().get("command_fingerprint").equals(fingerprint(c))))
        throw new ConflictException("Transfer was completed with different input");
      return b;
    }
    if (action.equals("withdraw") && lease == null) return b;
    if (action.equals("withdraw") || action.equals("unlock")) {
      if (lease == null) throw new ResourceNotFoundException("Transfer lease not found");
      if (lease.get("state").equals("COMPLETED"))
        throw new ConflictException("Completed transfer cannot be released");
      if (action.equals("withdraw") && lease.get("state").equals("RELEASED")) return b;
      if (action.equals("unlock")
          && !Objects.equals(lease.get("acquisition_id"), c.acquisitionId())) return b;
      String state =
          action.equals("withdraw") || !instant(lease, "deadline").isAfter(clock.instant())
              ? "RELEASED"
              : "ACTIVE";
      repo.jdbc()
          .update(
              "UPDATE transfer_leases SET"
                  + " state=?,buyer_id=NULL,acquisition_id=NULL,expires_at=NULL,updated_at=NOW()"
                  + " WHERE listing_id=?",
              state,
              c.listingId());
      parts.audit(c, action);
      return b;
    }
    parts.eligible(b, c.sellerId());
    if (action.equals("register") || action.equals("edit")) {
      if (c.price() == null
          || c.price().signum() <= 0
          || c.price().compareTo(b.amount()) > 0
          || c.price().stripTrailingZeros().scale() > 0)
        throw new ConflictException(
            "Transfer price exceeds original booking amount or is not integral VND");
      if (c.deadline() == null
          || !c.deadline().isAfter(clock.instant())
          || c.deadline().isAfter(b.startsAt()))
        throw new ConflictException("Transfer deadline is outside booking interval");
      if (lease != null) {
        if (!lease.get("state").equals("ACTIVE"))
          throw new ConflictException("Transfer listing is locked or final");
        boolean same =
            ((BigDecimal) lease.get("price")).compareTo(c.price()) == 0
                && instant(lease, "deadline").equals(c.deadline());
        if (action.equals("register")) {
          if (!same) throw new ConflictException("Listing registration input changed");
          return b;
        }
        if (same) return b;
      } else {
        Integer active =
            repo.jdbc()
                .queryForObject(
                    "SELECT count(*) FROM transfer_leases WHERE booking_id=? AND state IN"
                        + " ('ACTIVE','LOCKED') AND (deadline>? OR protocol='PEER_RESALE_V1')",
                    Integer.class,
                    b.id(),
                    Timestamp.from(clock.instant()));
        if (active > 0)
          throw new ConflictException("Booking already has an active transfer listing");
        repo.jdbc()
            .update(
                "UPDATE transfer_leases SET state='RELEASED' WHERE booking_id=? AND state IN"
                    + " ('ACTIVE','LOCKED') AND protocol='LEGACY_OWNER' AND deadline<=?",
                b.id(),
                Timestamp.from(clock.instant()));
      }
      repo.jdbc()
          .update(
              "INSERT INTO transfer_leases(listing_id,booking_id,seller_id,price,deadline,state)"
                  + " VALUES(?,?,?,?,?,'ACTIVE') ON CONFLICT(listing_id) DO UPDATE SET"
                  + " price=EXCLUDED.price,deadline=EXCLUDED.deadline,updated_at=NOW()",
              c.listingId(),
              b.id(),
              c.sellerId(),
              c.price(),
              Timestamp.from(c.deadline()));
      parts.audit(c, action);
      return b;
    }
    if (lease == null) throw new ResourceNotFoundException("Transfer lease not found");
    if (action.equals("lock")) {
      if (c.acquisitionId() == null
          || c.buyerId() == null
          || c.buyerId().equals(c.sellerId())
          || c.expiresAt() == null
          || !c.expiresAt().isAfter(clock.instant())
          || c.expiresAt().isAfter(instant(lease, "deadline")))
        throw new ConflictException("Invalid transfer acquisition");
      if (lease.get("state").equals("LOCKED")
          && Objects.equals(lease.get("acquisition_id"), c.acquisitionId())
          && Objects.equals(lease.get("buyer_id"), c.buyerId())) return b;
      if (lease.get("state").equals("LOCKED")
          && instant(lease, "expires_at").isAfter(clock.instant()))
        throw new ConflictException("Another buyer already locked this booking");
      if (!Set.of("ACTIVE", "LOCKED").contains(lease.get("state"))
          || !instant(lease, "deadline").isAfter(clock.instant()))
        throw new ConflictException("Transfer listing is no longer active");
      repo.jdbc()
          .update(
              "UPDATE transfer_leases SET"
                  + " state='LOCKED',acquisition_id=?,buyer_id=?,expires_at=?,updated_at=NOW()"
                  + " WHERE listing_id=?",
              c.acquisitionId(),
              c.buyerId(),
              Timestamp.from(c.expiresAt()),
              c.listingId());
      parts.audit(c, action);
      return b;
    }
    if (action.equals("complete")) {
      if (!lease.get("state").equals("LOCKED")
          || !Objects.equals(lease.get("acquisition_id"), c.acquisitionId())
          || !Objects.equals(lease.get("buyer_id"), c.buyerId())
          || c.paymentId() == null
          || c.paidAt() == null
          || c.price() == null
          || !c.paidAt().isBefore(instant(lease, "expires_at"))
          || ((BigDecimal) lease.get("price")).compareTo(c.price()) != 0
          || !b.currency().equals(c.currency())
          || !instant(lease, "expires_at").equals(c.expiresAt())
          || !instant(lease, "deadline").equals(c.deadline()))
        throw new ConflictException("Verified payment does not match the booking transfer lease");
      String fingerprint = fingerprint(c);
      Instant handoffAt = clock.instant();
      repo.jdbc()
          .update(
              "UPDATE bookings SET"
                  + " customer_id=?,current_holder_id=?,booking_code=upper(substr(replace(uuid_generate_v4()::text,'-',''),1,10)),checkin_token_version=checkin_token_version+1,version=version+1,updated_at=NOW()"
                  + " WHERE id=?",
              c.buyerId(),
              c.buyerId(),
              b.id());
      repo.jdbc()
          .update(
              "UPDATE slot_reservations SET holder_id=? WHERE id=(SELECT reservation_id FROM"
                  + " bookings WHERE id=?)",
              c.buyerId(),
              b.id());
      repo.jdbc()
          .update(
              "UPDATE transfer_leases SET state='COMPLETED',payment_id=?,updated_at=NOW() WHERE"
                  + " listing_id=?",
              c.paymentId(),
              c.listingId());
      repo.jdbc()
          .update(
              "INSERT INTO"
                  + " transfer_handoffs(listing_id,booking_id,seller_id,acquisition_id,buyer_id,payment_id,amount,currency,paid_at,expires_at,deadline,command_fingerprint,transferred_at)"
                  + " VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)",
              c.listingId(),
              b.id(),
              c.sellerId(),
              c.acquisitionId(),
              c.buyerId(),
              c.paymentId(),
              c.price(),
              c.currency(),
              Timestamp.from(c.paidAt()),
              Timestamp.from(c.expiresAt()),
              Timestamp.from(c.deadline()),
              fingerprint,
              Timestamp.from(handoffAt));
      repo.jdbc()
          .update(
              "INSERT INTO"
                  + " transfer_acquisition_decisions(acquisition_id,listing_id,booking_id,decision,command_fingerprint,payment_id,seller_id,buyer_id,amount,currency,reason,decided_at,booking_version)"
                  + " VALUES(?,?,?,'COMPLETED',?,?,?,?,?,?,?, ?,?)",
              c.acquisitionId(),
              c.listingId(),
              b.id(),
              fingerprint,
              c.paymentId(),
              c.sellerId(),
              c.buyerId(),
              c.price(),
              c.currency(),
              "Verified handoff committed",
              Timestamp.from(handoffAt),
              b.version() + 1);
      if (com.sporthub.common.money.FacilityTransferTerms.isReal(c.financeTerms()))
        real.completed(c.financeTerms(), c.paymentId(), repo.find(b.id()), handoffAt);
      else if (c.financeTerms() != null) {
        if (demo == null) throw new ConflictException("DEMO finance handler is unavailable");
        demo.completed(c.financeTerms(), c.paymentId(), repo.find(b.id()), handoffAt);
      }
      repo.history(
          b.id(),
          c.buyerId(),
          "TRANSFER_COMPLETED",
          Map.of(
              "listingId",
              c.listingId(),
              "previousHolderId",
              c.sellerId(),
              "newHolderId",
              c.buyerId(),
              "paymentId",
              c.paymentId(),
              "acquisitionId",
              c.acquisitionId(),
              "amount",
              c.price(),
              "currency",
              c.currency(),
              "paidAt",
              c.paidAt()));
      parts.audit(c, action);
      return repo.find(b.id());
    }
    throw new IllegalArgumentException("Unsupported transfer command");
  }

  /** The same court lock serializes this fence with complete/check-in/cancellation. */
  public Map<String, Object> abort(Command c, String reason) {
    return parts.abort(c, reason, demo, real);
  }

  @Transactional(readOnly = true)
  public Map<String, Object> decision(UUID acquisition) {
    return parts.decision(acquisition);
  }

  private static Instant instant(Map<String, Object> row, String key) {
    return TransferLeaseState.instant(row, key);
  }

  private static String fingerprint(Command c) {
    return TransferLeaseState.fingerprint(c);
  }
}
