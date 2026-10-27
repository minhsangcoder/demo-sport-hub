package com.sporthub.booking.service;

import com.sporthub.booking.repository.BookingRepository;
import com.sporthub.booking.service.TransferLeaseService.Command;
import com.sporthub.booking.web.BookingDtos.Booking;
import com.sporthub.common.exception.*;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;

final class TransferLeaseState {
  private final BookingRepository repo;
  private final Clock clock;
  private final LocalAppliedCollections collections;

  static TransferLeaseState create(
      BookingRepository repo, Clock clock, LocalAppliedCollections collections) {
    return new TransferLeaseState(repo, clock, collections);
  }

  TransferLeaseState(BookingRepository repo, Clock clock, LocalAppliedCollections collections) {
    this.repo = repo;
    this.clock = clock;
    this.collections = collections;
  }

  public Map<String, Object> receipt(UUID listing) {
    var rows =
        repo.jdbc()
            .queryForList(
                """
                SELECT l.*,b.currency,h.command_fingerprint,h.paid_at,h.transferred_at
                FROM transfer_leases l JOIN bookings b ON b.id=l.booking_id
                LEFT JOIN transfer_handoffs h ON h.listing_id=l.listing_id WHERE l.listing_id=?
                """,
                listing);
    if (rows.isEmpty()) throw new ResourceNotFoundException("Transfer lease not found");
    var l = rows.getFirst();
    var view = new LinkedHashMap<String, Object>();
    for (String k :
        List.of(
            "listing_id",
            "booking_id",
            "seller_id",
            "acquisition_id",
            "buyer_id",
            "payment_id",
            "price",
            "currency",
            "state",
            "command_fingerprint")) {
      String[] parts = k.split("_");
      StringBuilder name = new StringBuilder(parts[0]);
      for (int i = 1; i < parts.length; i++)
        name.append(Character.toUpperCase(parts[i].charAt(0))).append(parts[i].substring(1));
      view.put(name.toString(), l.get(k));
    }
    for (String k : List.of("deadline", "expires_at", "paid_at", "transferred_at")) {
      String name =
          switch (k) {
            case "expires_at" -> "expiresAt";
            case "paid_at" -> "paidAt";
            case "transferred_at" -> "transferredAt";
            default -> k;
          };
      view.put(name, l.get(k) == null ? null : ((Timestamp) l.get(k)).toInstant());
    }
    return view;
  }

  public com.fasterxml.jackson.databind.JsonNode inspect(UUID id) {
    var b = repo.find(id);
    var view = (com.fasterxml.jackson.databind.node.ObjectNode) repo.read(repo.write(b));
    view.put("resaleConsumed", PeerHandoffs.consumed(repo, id));
    var policies =
        repo.jdbc()
            .queryForList("SELECT * FROM booking_refund_policy_snapshots WHERE booking_id=?", id);
    if (!policies.isEmpty()) {
      var p = policies.getFirst();
      view.set(
          "refundPolicy",
          repo.read(
              repo.write(
                  Map.of(
                      "configured",
                      true,
                      "id",
                      p.get("policy_id"),
                      "version",
                      p.get("policy_version"),
                      "rateBps",
                      p.get("refund_rate_bps"),
                      "currency",
                      p.get("currency"),
                      "rounding",
                      p.get("rounding"),
                      "termsHash",
                      p.get("terms_hash")))));
    }
    if (b.currentHolderId() == null) {
      view.put("transferBlockedReason", "Guest walk-in bookings cannot be transferred");
      return view;
    }
    try {
      eligible(b, b.currentHolderId());
      view.put("transferBlockedReason", "");
    } catch (ConflictException ex) {
      view.put("transferBlockedReason", ex.getMessage());
    } catch (ForbiddenException ex) {
      view.put("transferBlockedReason", ex.getMessage());
    }
    return view;
  }

  public Map<String, Object> decision(UUID acquisition) {
    var stored =
        repo.jdbc()
            .queryForList(
                "SELECT * FROM transfer_acquisition_decisions WHERE acquisition_id=?", acquisition);
    if (!stored.isEmpty()) return decisionView(stored.getFirst(), "ACQUISITION_DECISION");
    var handoff =
        repo.jdbc()
            .queryForList(
                "SELECT h.*,b.version booking_version,'COMPLETED'::text decision,'Existing"
                    + " immutable handoff receipt'::text reason,h.transferred_at decided_at FROM"
                    + " transfer_handoffs h JOIN bookings b ON b.id=h.booking_id WHERE"
                    + " h.acquisition_id=?",
                acquisition);
    if (!handoff.isEmpty()) return decisionView(handoff.getFirst(), "LEGACY_HANDOFF_RECEIPT");
    if (repo.jdbc()
            .queryForObject(
                "SELECT count(*) FROM transfer_leases WHERE acquisition_id=? AND state='COMPLETED'",
                Integer.class,
                acquisition)
        > 0)
      return Map.of(
          "acquisitionId",
          acquisition,
          "decision",
          "REVIEW",
          "reason",
          "LEGACY_HANDOFF_RECEIPT_MISSING");
    return Map.of("acquisitionId", acquisition, "decision", "UNKNOWN");
  }

  public Map<String, Object> decisionView(Map<String, Object> row, String origin) {
    var result = new LinkedHashMap<String, Object>();
    result.put("origin", origin);
    for (String key :
        List.of(
            "acquisition_id",
            "listing_id",
            "booking_id",
            "decision",
            "command_fingerprint",
            "payment_id",
            "seller_id",
            "buyer_id",
            "amount",
            "currency",
            "reason",
            "decided_at",
            "booking_version")) {
      String[] parts = key.split("_");
      StringBuilder name = new StringBuilder(parts[0]);
      for (int i = 1; i < parts.length; i++)
        name.append(Character.toUpperCase(parts[i].charAt(0))).append(parts[i].substring(1));
      Object value = row.get(key);
      result.put(name.toString(), value instanceof Timestamp time ? time.toInstant() : value);
    }
    return result;
  }

  public void identity(Map<String, Object> row, Command c) {
    Object listing = row.containsKey("listing_id") ? row.get("listing_id") : row.get("listingId");
    Object booking = row.containsKey("booking_id") ? row.get("booking_id") : row.get("bookingId");
    Object seller = row.containsKey("seller_id") ? row.get("seller_id") : row.get("sellerId");
    Object buyer = row.containsKey("buyer_id") ? row.get("buyer_id") : row.get("buyerId");
    if (!Objects.equals(listing, c.listingId())
        || !Objects.equals(booking, c.bookingId())
        || !Objects.equals(seller, c.sellerId())
        || !Objects.equals(buyer, c.buyerId()))
      throw new ConflictException("Acquisition decision identity mismatch");
  }

  public void eligible(Booking b, UUID seller) {
    if (b.currentHolderId() == null || seller == null || !b.currentHolderId().equals(seller))
      throw new ForbiddenException("Only the current booking holder may list a transfer");
    if (!b.status().equals("CONFIRMED")
        || b.paidAt() == null
        || !b.startsAt().isAfter(clock.instant()))
      throw new ConflictException("Only paid confirmed future bookings can be transferred");
    collections.requireFullyPaid(b);
    if (b.source().equals("OFFLINE_COUNTER")
        && b.currentHolderId().equals(b.createdBy())
        && repo.jdbc()
                .queryForObject(
                    "SELECT (SELECT count(*) FROM counter_booking_guests WHERE booking_id=? AND"
                        + " linked_customer_id=?)+(SELECT count(*) FROM transfer_leases WHERE"
                        + " booking_id=? AND buyer_id=? AND state='COMPLETED')",
                    Integer.class,
                    b.id(),
                    b.currentHolderId(),
                    b.id(),
                    b.currentHolderId())
            == 0)
      throw new ConflictException(
          "BLOCKED_RULE: counter operator is not a verified usage owner; link the actual customer"
              + " before transferring");
    if (repo.jdbc()
            .queryForObject("SELECT count(*) FROM booking_groups WHERE id=?", Integer.class, b.id())
        > 0)
      throw new ConflictException(
          "BLOCKED_RULE: paid group ownership changes need an approved member policy");
    if (repo.jdbc()
            .queryForObject(
                "SELECT count(*) FROM payment_reconciliation WHERE booking_id=? AND"
                    + " state='REQUIRES_REVIEW'",
                Integer.class,
                b.id())
        > 0) throw new ConflictException("Booking payment is under reconciliation");
  }

  public static Instant instant(Map<String, Object> row, String key) {
    return ((Timestamp) row.get(key)).toInstant();
  }

  public static String fingerprint(Command c) {
    return com.sporthub.common.money.TransferHandoff.fingerprint(
        c.listingId(),
        c.bookingId(),
        c.sellerId(),
        c.acquisitionId(),
        c.buyerId(),
        c.paymentId(),
        c.price(),
        c.currency(),
        c.paidAt(),
        c.expiresAt(),
        c.deadline());
  }

  public void audit(Command c, String action) {
    repo.jdbc()
        .update(
            "INSERT INTO transfer_lease_audit(id,listing_id,action,details) VALUES(?,?,?,?::jsonb)",
            UUID.randomUUID(),
            c.listingId(),
            action,
            repo.write(c));
  }

  public void requireLegacy(Command c) {
    if (com.sporthub.common.money.PeerResaleContract.isPeer(c.financeTerms())
        || repo.jdbc()
                .queryForObject(
                    "SELECT count(*) FROM transfer_leases WHERE listing_id=? AND"
                        + " protocol='PEER_RESALE_V1'",
                    Integer.class,
                    c.listingId())
            > 0)
      throw new ConflictException("Lệnh thanh toán chuyển nhượng cũ không áp dụng cho phiên P2P.");
  }

  public Map<String, Object> abort(
      Command c, String reason, BookingDemoFinance demo, FacilityTransferFinance real) {
    if (c == null
        || c.listingId() == null
        || c.bookingId() == null
        || c.sellerId() == null
        || c.acquisitionId() == null
        || c.buyerId() == null
        || c.buyerId().equals(c.sellerId())
        || reason == null
        || reason.isBlank()
        || reason.length() > 500)
      throw new IllegalArgumentException("Bound acquisition identity and abort reason required");
    var b = repo.find(c.bookingId());
    BookingUserFence.lock(repo, b.currentHolderId(), c.sellerId(), c.buyerId());
    repo.lock(b.courtId());
    requireLegacy(c);
    b = repo.find(b.id());
    var existing = decision(c.acquisitionId());
    if (!existing.get("decision").equals("UNKNOWN")) {
      if (existing.get("decision").equals("REVIEW"))
        throw new ConflictException(
            "Legacy completed acquisition has no verifiable handoff receipt");
      identity(existing, c);
      return existing;
    }
    var rows =
        repo.jdbc().queryForList("SELECT * FROM transfer_leases WHERE listing_id=?", c.listingId());
    var lease = rows.isEmpty() ? null : rows.getFirst();
    if (lease != null
        && (!b.id().equals(lease.get("booking_id"))
            || !c.sellerId().equals(lease.get("seller_id"))))
      throw new ConflictException("Abort lease identity mismatch");
    if (lease == null && !Objects.equals(b.currentHolderId(), c.sellerId()))
      throw new ConflictException("Unknown acquisition seller does not own the booking");
    if (lease != null
        && c.acquisitionId().equals(lease.get("acquisition_id"))
        && !c.buyerId().equals(lease.get("buyer_id")))
      throw new ConflictException("Abort buyer differs from the locked acquisition");
    if (c.price() == null
        || c.price().signum() <= 0
        || c.price().stripTrailingZeros().scale() > 0
        || c.price().compareTo(b.amount()) > 0
        || !b.currency().equals(c.currency()))
      throw new ConflictException("Abort amount and currency must match the acquisition terms");
    String fingerprint =
        com.sporthub.common.security.Signatures.sha256(
            "ABORT|"
                + c.listingId()
                + "|"
                + c.bookingId()
                + "|"
                + c.sellerId()
                + "|"
                + c.acquisitionId()
                + "|"
                + c.buyerId()
                + "|"
                + c.paymentId()
                + "|"
                + c.price().stripTrailingZeros().toPlainString()
                + "|"
                + c.currency());
    repo.jdbc()
        .update(
            "INSERT INTO"
                + " transfer_acquisition_decisions(acquisition_id,listing_id,booking_id,decision,command_fingerprint,payment_id,seller_id,buyer_id,amount,currency,reason,decided_at,booking_version)"
                + " VALUES(?,?,?,'ABORTED',?,?,?,?,?,?,?,?,?)",
            c.acquisitionId(),
            c.listingId(),
            b.id(),
            fingerprint,
            c.paymentId(),
            c.sellerId(),
            c.buyerId(),
            c.price(),
            c.currency(),
            reason,
            Timestamp.from(clock.instant()),
            b.version());
    if (c.financeTerms() != null && c.paymentId() != null) {
      if (com.sporthub.common.money.FacilityTransferTerms.isReal(c.financeTerms())) {
        if (real == null) throw new ConflictException("REAL transfer handler is unavailable");
        real.aborted(c.financeTerms(), c.paymentId(), b);
      } else {
        if (demo == null) throw new ConflictException("DEMO finance handler is unavailable");
        demo.aborted(c.financeTerms(), c.paymentId(), b);
      }
    }
    if (lease != null
        && c.acquisitionId().equals(lease.get("acquisition_id"))
        && lease.get("state").equals("LOCKED")) {
      repo.jdbc()
          .update(
              "UPDATE transfer_leases SET"
                  + " state=?,acquisition_id=NULL,buyer_id=NULL,expires_at=NULL,updated_at=NOW()"
                  + " WHERE listing_id=?",
              instant(lease, "deadline").isAfter(clock.instant()) ? "ACTIVE" : "RELEASED",
              c.listingId());
      audit(c, "ABORTED");
    }
    return decision(c.acquisitionId());
  }
}
