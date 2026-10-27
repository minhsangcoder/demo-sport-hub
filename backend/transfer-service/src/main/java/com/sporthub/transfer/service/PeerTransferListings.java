package com.sporthub.transfer.service;

import static com.sporthub.transfer.web.TransferDtos.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.sporthub.common.exception.*;
import com.sporthub.common.money.*;
import com.sporthub.common.security.RemoteIdentity.Caller;
import com.sporthub.common.security.Signatures;
import com.sporthub.transfer.repository.TransferRepository;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The local intent is durable before RPC; publication becomes usable only after Booking accepts it.
 */
@Service
public class PeerTransferListings {
  private final TransferRepository repo;
  private final TransferDependencies dependencies;
  private final PeerBookingClient booking;
  private final PeerTransferProjection projection;
  private final Clock clock;
  private final TransactionTemplate tx;

  public PeerTransferListings(
      TransferRepository repo,
      TransferDependencies dependencies,
      PeerBookingClient booking,
      PeerTransferProjection projection,
      Clock clock,
      PlatformTransactionManager manager) {
    this.repo = repo;
    this.dependencies = dependencies;
    this.booking = booking;
    this.projection = projection;
    this.clock = clock;
    tx = new TransactionTemplate(manager);
  }

  public boolean isPeer(UUID listing) {
    var row = repo.find(listing);
    return PeerResaleContract.isPeer(row.financeTerms());
  }

  public boolean realBooking(UUID id) {
    return "REAL"
        .equals(dependencies.booking(id).path("priceSnapshot").path("moneyClass").asText());
  }

  public JsonNode quote(Create input, Caller actor) {
    actor.requireRole("CUSTOMER");
    actor.requireActiveVerified();
    if (input.beneficiaryRevisionId() != null)
      throw new IllegalArgumentException(
          "Chuyển nhượng P2P dùng tài khoản ngân hàng cá nhân đang kích hoạt.");
    return booking.market(
        "quote",
        new PeerMarketContract.Quote(
            input.bookingId(), input.price(), input.deadline(), input.bankDestinationRevisionId()),
        null,
        actor);
  }

  public UUID create(Create input, String key, Caller actor) {
    actor.requireRole("CUSTOMER");
    actor.requireActiveVerified();
    checkKey(key);
    String fingerprint = Signatures.sha256(repo.write(input));
    UUID previous = repo.previous(actor.id(), "CREATE", key, fingerprint);
    if (previous != null) {
      publish(previous, actor);
      return previous;
    }
    var terms = quote(input, actor);
    if (input.acceptedTermsHash() == null
        || !input.acceptedTermsHash().equals(terms.path("termsHash").asText()))
      throw new ConflictException(
          "Vui lòng xem và chấp nhận giá, tài khoản nhận và điều khoản chuyển nhượng hiện tại.");
    UUID court = UUID.fromString(terms.path("courtId").asText()),
        facility = UUID.fromString(terms.path("facilityId").asText());
    JsonNode context = dependencies.context(court);
    if (!court.toString().equals(context.path("court").path("id").asText())
        || !facility.toString().equals(context.path("facility").path("id").asText()))
      throw new ConflictException("Thông tin sân và cơ sở chưa khớp điều khoản chuyển nhượng.");
    var venue = venue(context);
    UUID id =
        tx.execute(
            status -> {
              repo.lock(actor.id());
              repo.lock(input.bookingId());
              var prior = repo.previous(actor.id(), "CREATE", key, fingerprint);
              if (prior != null) return prior;
              if (Boolean.TRUE.equals(
                  repo.jdbc()
                      .queryForObject(
                          "SELECT EXISTS(SELECT 1 FROM transfer_listings WHERE booking_id=? AND"
                              + " state IN('ACTIVE','LOCKED','PENDING_AUDIT'))",
                          Boolean.class,
                          input.bookingId())))
                throw new ConflictException("Lượt đặt đã có tin chuyển nhượng đang mở.");
              UUID listing = UUID.randomUUID();
              repo.jdbc()
                  .update(
                      """
INSERT INTO transfer_listings(id,booking_id,seller_id,facility_id,court_id,snapshot,original_amount,price,currency,
  starts_at,ends_at,deadline,state,workflow,finance_terms,protocol,created_at,updated_at)
VALUES(?,?,?,?,?,?::jsonb,?,?,'VND',?,?,?,'ACTIVE','REGISTER',?::jsonb,'PEER_RESALE_V1',?,?)
""",
                      listing,
                      input.bookingId(),
                      actor.id(),
                      facility,
                      court,
                      repo.write(venue),
                      new BigDecimal(terms.path("originalAmount").asText()),
                      PeerResaleTerms.amount(terms),
                      Timestamp.from(Instant.parse(terms.path("startsAt").asText())),
                      Timestamp.from(Instant.parse(terms.path("endsAt").asText())),
                      Timestamp.from(Instant.parse(terms.path("deadline").asText())),
                      terms.toString(),
                      Timestamp.from(clock.instant()),
                      Timestamp.from(clock.instant()));
              repo.remember(actor.id(), "CREATE", key, fingerprint, listing);
              repo.audit(
                  repo.find(listing),
                  actor.id(),
                  "PEER_LISTING_STAGED",
                  Map.of("termsHash", terms.path("termsHash").asText()));
              return listing;
            });
    publish(id, actor);
    return id;
  }

  private void publish(UUID id, Caller actor) {
    var row = repo.find(id);
    if (!PeerResaleContract.isPeer(row.financeTerms()) || !row.workflow().equals("REGISTER"))
      return;
    try {
      booking.market(
          "publish",
          new PeerMarketContract.Publish(id, 0, row.financeTerms()),
          "publish:" + id,
          actor);
      projection.listing(id);
    } catch (ActionPolicyException ex) {
      if (ex.status() < 500)
        tx.executeWithoutResult(
            status -> {
              repo.lock(id);
              var current = repo.find(id);
              if (current.workflow().equals("REGISTER") && current.state().equals("ACTIVE"))
                repo.jdbc()
                    .update(
                        "UPDATE transfer_listings SET"
                            + " state='WITHDRAWN',workflow='REVIEW',last_error=?,updated_at=? WHERE"
                            + " id=?",
                        ex.code(),
                        Timestamp.from(clock.instant()),
                        id);
            });
      throw ex;
    }
  }

  private Map<String, Object> venue(JsonNode context) {
    var facility = context.path("facility");
    var court = context.path("court");
    var result = new LinkedHashMap<String, Object>();
    result.put("facilityName", facility.path("name").asText());
    result.put("courtName", court.path("name").asText());
    result.put("sportCategoryId", court.path("sportCategoryId").asText());
    for (String field :
        List.of("provinceCode", "province", "wardCode", "ward", "timezone", "amenities"))
      result.put(field, facility.path(field));
    result.put("address", facility.path("addressLine").asText());
    return result;
  }

  public static void checkKey(String key) {
    if (key == null || !key.matches("[A-Za-z0-9_.:-]{1,100}"))
      throw new IllegalArgumentException("Cần Idempotency-Key hợp lệ.");
  }
}
