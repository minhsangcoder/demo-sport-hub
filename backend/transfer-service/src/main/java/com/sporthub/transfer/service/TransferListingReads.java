package com.sporthub.transfer.service;

import static com.sporthub.transfer.web.TransferDtos.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sporthub.common.exception.*;
import com.sporthub.common.security.RemoteIdentity.Caller;
import com.sporthub.transfer.repository.TransferRepository;
import com.sporthub.transfer.repository.TransferRepository.Row;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;

final class TransferListingReads {
  private final TransferRepository repo;
  private final TransferDependencies deps;
  private final Clock clock;
  private final ObjectMapper json;
  private final FacilityTransferQuotes facilityQuotes;

  static TransferListingReads create(
      TransferRepository repo,
      TransferDependencies deps,
      Clock clock,
      ObjectMapper json,
      FacilityTransferQuotes facilityQuotes) {
    return new TransferListingReads(repo, deps, clock, json, facilityQuotes);
  }

  TransferListingReads(
      TransferRepository repo,
      TransferDependencies deps,
      Clock clock,
      ObjectMapper json,
      FacilityTransferQuotes facilityQuotes) {
    this.repo = repo;
    this.deps = deps;
    this.clock = clock;
    this.json = json;
    this.facilityQuotes = facilityQuotes;
  }

  public List<Listing> market(
      String q,
      UUID category,
      Instant after,
      Instant before,
      BigDecimal maxPrice,
      String sort,
      Caller caller) {
    caller.requireRole("CUSTOMER");
    sort = sort == null ? "start" : sort;
    if (!Set.of("start", "price", "discount").contains(sort)
        || q != null && q.length() > 180
        || after != null && before != null && after.isAfter(before)
        || maxPrice != null && maxPrice.signum() < 0)
      throw new IllegalArgumentException("Invalid transfer search filters");
    StringBuilder sql =
        new StringBuilder(
            "SELECT * FROM transfer_listings WHERE state='ACTIVE' AND workflow='READY' AND"
                + " deadline>? AND starts_at>?");
    var args = new ArrayList<Object>();
    args.add(Timestamp.from(clock.instant()));
    args.add(Timestamp.from(clock.instant()));
    if (facilityQuotes.live()) sql.append(" AND finance_terms->>'mode'='REAL'");
    if (q != null && !q.isBlank()) {
      sql.append(
          " AND concat_ws('"
              + " ',snapshot->>'facilityName',snapshot->>'courtName',snapshot->>'address',snapshot->>'province',snapshot->>'ward')"
              + " ILIKE ?");
      args.add("%" + q.trim().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%");
    }
    if (category != null) {
      sql.append(" AND snapshot->>'sportCategoryId'=?");
      args.add(category.toString());
    }
    if (after != null) {
      sql.append(" AND starts_at>=?");
      args.add(Timestamp.from(after));
    }
    if (before != null) {
      sql.append(" AND starts_at<=?");
      args.add(Timestamp.from(before));
    }
    if (maxPrice != null) {
      sql.append(" AND price<=?");
      args.add(maxPrice);
    }
    sql.append(
        switch (sort) {
          case "price" -> " ORDER BY price,id";
          case "discount" -> " ORDER BY (original_amount-price)/original_amount DESC,id";
          default -> " ORDER BY starts_at,id";
        });
    sql.append(" LIMIT 100 OFFSET ?");
    var result = new ArrayList<Listing>();
    int offset = 0;
    while (result.size() < 100) {
      var pageArgs = new ArrayList<>(args);
      pageArgs.add(offset);
      var rows = repo.rows(sql.toString(), pageArgs.toArray());
      for (var r : rows) {
        if (valid(r)) result.add(repo.view(r, caller.id(), true));
        if (result.size() == 100) break;
      }
      if (rows.size() < 100) break;
      offset += 100;
    }
    return result;
  }

  public List<Listing> mine(Caller caller) {
    return repo
        .rows(
            "SELECT * FROM transfer_listings WHERE seller_id=? ORDER BY created_at DESC LIMIT 100",
            caller.id())
        .stream()
        .map(r -> repo.view(r, caller.id(), false))
        .toList();
  }

  public List<Acquisition> purchases(Caller caller) {
    return repo
        .jdbc()
        .queryForList(
            "SELECT a.id FROM transfer_acquisitions a JOIN transfer_listings l ON l.id=a.listing_id"
                + " WHERE a.buyer_id=? OR (a.protocol='PEER_RESALE_V1' AND l.seller_id=?) ORDER BY"
                + " a.created_at DESC,a.id LIMIT 100",
            UUID.class,
            caller.id(),
            caller.id())
        .stream()
        .map(repo::acquisition)
        .toList();
  }

  public Listing detail(UUID id, Caller caller) {
    caller.requireRole("CUSTOMER");
    var r = repo.find(id);
    return repo.view(
        r, caller.id(), r.state().equals("ACTIVE") && r.workflow().equals("READY") && valid(r));
  }

  public boolean valid(Row r) {
    if (com.sporthub.common.money.PeerResaleContract.isPeer(r.financeTerms())) {
      if (!validBooking(r)) return false;
      var current = deps.peerListing(r.id());
      if (current == null
          || !"ACTIVE".equals(current.path("state").asText())
          || current.path("version").asLong() != r.version()
          || !current
              .path("terms")
              .path("termsHash")
              .asText()
              .equals(r.financeTerms().path("termsHash").asText())) return false;
      var policy = deps.policy(r.facilityId());
      return policy != null
          && policy.path("configured").asBoolean()
          && policy.path("enabled").asBoolean()
          && !r.deadline()
              .isAfter(r.startsAt().minusSeconds(policy.path("minLeadSeconds").asLong()))
          && r.startsAt()
              .minusSeconds(policy.path("minLeadSeconds").asLong())
              .isAfter(clock.instant());
    }
    if (facilityQuotes.live()
        && !com.sporthub.common.money.FacilityTransferTerms.isReal(r.financeTerms())) return false;
    if (!validBooking(r)) return false;
    var p = deps.policy(r.facilityId());
    return p != null
        && p.path("configured").asBoolean()
        && p.path("enabled").asBoolean()
        && r.startsAt().minusSeconds(p.path("minLeadSeconds").asLong()).isAfter(clock.instant());
  }

  public boolean validBooking(Row r) {
    if (!r.deadline().isAfter(clock.instant())) return false;
    var b = deps.booking(r.bookingId());
    return b != null
        && !(r.state().equals("ACTIVE") && b.path("resaleConsumed").asBoolean())
        && b.path("id").asText().equals(r.bookingId().toString())
        && b.path("transferBlockedReason").asText().isBlank()
        && b.path("status").asText().equals("CONFIRMED")
        && !b.path("paidAt").isNull()
        && !b.path("paidAt").isMissingNode()
        && b.path("currentHolderId").asText().equals(r.sellerId().toString())
        && b.path("facilityId").asText().equals(r.facilityId().toString())
        && b.path("courtId").asText().equals(r.courtId().toString())
        && b.path("amount").decimalValue().compareTo(r.originalAmount()) == 0
        && b.path("currency").asText().equals(r.currency())
        && Instant.parse(b.path("startsAt").asText()).equals(r.startsAt())
        && Instant.parse(b.path("endsAt").asText()).equals(r.endsAt())
        && r.startsAt().isAfter(clock.instant());
  }

  public Row owner(UUID id, Caller caller) {
    var r = repo.find(id);
    if (!r.sellerId().equals(caller.id()))
      throw new ForbiddenException("Listing belongs to another seller");
    return r;
  }

  public void validateBooking(JsonNode b) {
    if (b.path("resaleConsumed").asBoolean())
      throw new ConflictException("Mỗi lượt đặt chỉ được chuyển nhượng thành công một lần.");
    if (!b.path("transferBlockedReason").asText().isBlank())
      throw new ConflictException(b.path("transferBlockedReason").asText());
    if (!b.path("status").asText().equals("CONFIRMED")
        || (b.path("paidAt").isNull() || b.path("paidAt").isMissingNode())
        || !Instant.parse(b.path("startsAt").asText()).isAfter(clock.instant()))
      throw new ConflictException("A paid confirmed future booking is required");
  }

  public Instant policy(UUID id, Instant starts) {
    var p = deps.policy(id);
    if (!p.path("configured").asBoolean())
      throw new ConflictException(
          "BLOCKED_RULE: facility Owner must configure its transfer eligibility policy");
    Instant cutoff = starts.minusSeconds(p.path("minLeadSeconds").asLong());
    if (!p.path("enabled").asBoolean() || !cutoff.isAfter(clock.instant()))
      throw new ConflictException("Facility transfer policy does not permit this booking");
    return cutoff;
  }

  public void price(BigDecimal amount, BigDecimal original, String currency) {
    if (amount.signum() <= 0
        || amount.compareTo(original) > 0
        || amount.scale() > 2
        || currency.equals("VND") && amount.stripTrailingZeros().scale() > 0)
      throw new ConflictException(
          "Transfer price must be positive and no greater than the original paid amount");
  }

  public void deadline(Instant value, Instant starts) {
    if (!value.isAfter(clock.instant()) || value.isAfter(starts))
      throw new ConflictException(
          "Transfer deadline must be in the future and no later than play starts");
  }

  public com.fasterxml.jackson.databind.JsonNode quote(
      Create input, Caller caller, boolean financeEnabled) {
    caller.requireRole("CUSTOMER");
    caller.requireActiveVerified();
    deps.marketAllowed(caller.id());
    if (!financeEnabled)
      throw new ConflictException("New transfer finance is disabled during rollout");
    var b = deps.booking(input.bookingId());
    if (!b.path("currentHolderId").asText().equals(caller.id().toString()))
      throw new ForbiddenException("Only your current booking can be transferred");
    validateBooking(b);
    price(input.price(), b.path("amount").decimalValue(), b.path("currency").asText());
    deadline(input.deadline(), Instant.parse(b.path("startsAt").asText()));
    policy(
        UUID.fromString(b.path("facilityId").asText()), Instant.parse(b.path("startsAt").asText()));
    return facilityQuotes.quote(
        b,
        input.price(),
        input.deadline(),
        (facilityQuotes.live() ? input.bankDestinationRevisionId() : input.beneficiaryRevisionId()),
        caller.id());
  }

  public com.fasterxml.jackson.databind.JsonNode quoteEdit(
      UUID id, Edit input, Caller caller, boolean financeEnabled) {
    caller.requireRole("CUSTOMER");
    var listing = owner(id, caller);
    TransferService.requireLegacy(listing.financeTerms());
    if (!financeEnabled)
      throw new ConflictException("New transfer finance is disabled during rollout");
    if (!listing.state().equals("ACTIVE") || !listing.workflow().equals("READY"))
      throw new ConflictException("Only an unlocked active listing may accept new terms");
    return quote(
        new Create(
            listing.bookingId(),
            input.price(),
            input.deadline(),
            input.beneficiaryRevisionId(),
            null,
            input.bankDestinationRevisionId()),
        caller,
        financeEnabled);
  }
}
