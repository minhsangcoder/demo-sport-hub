package com.sporthub.booking.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.sporthub.booking.repository.BookingRepository;
import com.sporthub.common.exception.*;
import com.sporthub.common.money.PeerResaleContract;
import java.math.BigDecimal;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class PeerUsageStore {
  public record Attempt(
      UUID id,
      UUID listing,
      UUID booking,
      UUID facility,
      UUID court,
      UUID seller,
      UUID buyer,
      BigDecimal amount,
      String currency,
      Instant startsAt,
      Instant endsAt,
      UUID bankRevision,
      String destinationHash,
      String refundPolicyHash,
      String termsHash,
      Instant payBy,
      String phase,
      String confirmationMethod,
      long version) {
    public boolean terminal() {
      return Set.of("COMPLETED", "EXPIRED", "ABORTED_A_RETAINS", "TIMED_OUT_A_RETAINS")
          .contains(phase);
    }

    public boolean participant(UUID actor) {
      return actor.equals(seller) || actor.equals(buyer);
    }
  }

  private final BookingRepository repo;

  public PeerUsageStore(BookingRepository repo) {
    this.repo = repo;
  }

  public Attempt attempt(UUID id) {
    return repo
        .jdbc()
        .query("SELECT * FROM peer_usage_attempts WHERE acquisition_id=?", (r, n) -> map(r), id)
        .stream()
        .findFirst()
        .orElseThrow(
            () -> new ResourceNotFoundException("Không tìm thấy phiên chuyển nhượng P2P."));
  }

  public JsonNode view(UUID id) {
    var values = views(List.of(id));
    if (values.isEmpty())
      throw new ResourceNotFoundException("Không tìm thấy phiên chuyển nhượng P2P.");
    return values.getFirst();
  }

  public List<JsonNode> views(List<UUID> ids) {
    if (ids.isEmpty()) return List.of();
    if (ids.size() > 100) throw new IllegalArgumentException("Chỉ tải tối đa 100 phiên mỗi lần.");
    var values =
        repo.jdbc()
            .query(
                "SELECT a.*,d.decision AS usage_decision,d.reason AS decision_reason,d.actor_id AS"
                    + " decision_actor,d.decided_at AS decision_time,d.booking_version AS"
                    + " decision_version FROM peer_usage_attempts a LEFT JOIN peer_usage_decisions"
                    + " d ON d.acquisition_id=a.acquisition_id WHERE a.acquisition_id=ANY(?)",
                (r, n) -> project(r),
                new Object[] {ids.toArray(UUID[]::new)});
    var byId = new HashMap<String, JsonNode>();
    values.forEach(value -> byId.put(value.path("id").asText(), value));
    return ids.stream().map(id -> byId.get(id.toString())).filter(Objects::nonNull).toList();
  }

  private JsonNode project(ResultSet r) throws SQLException {
    var a = map(r);
    JsonNode decision = null;
    if (r.getString("usage_decision") != null) {
      var fields = new LinkedHashMap<String, Object>();
      fields.put("decision", r.getString("usage_decision"));
      fields.put("reason", r.getString("decision_reason"));
      fields.put("actorId", r.getObject("decision_actor", UUID.class));
      fields.put("decidedAt", r.getTimestamp("decision_time").toInstant());
      fields.put("bookingVersion", r.getLong("decision_version"));
      decision = repo.read(repo.write(fields));
    }
    return repo.read(
        repo.write(
            new PeerResaleContract.Attempt(
                a.id(),
                a.listing(),
                a.booking(),
                a.seller(),
                a.buyer(),
                PeerResaleContract.PROTOCOL,
                PeerResaleContract.Phase.valueOf(a.phase()),
                a.version(),
                a.amount(),
                a.currency(),
                a.payBy(),
                a.startsAt(),
                null,
                a.termsHash(),
                PeerResaleContract.ConfirmationMethod.valueOf(a.confirmationMethod()),
                decision)));
  }

  public void phase(Attempt a, String phase, String method, Instant now) {
    int changed =
        repo.jdbc()
            .update(
                """
UPDATE peer_usage_attempts SET phase=?,confirmation_method=?,version=version+1,updated_at=?,
payment_declared_at=CASE WHEN ?='PAYMENT_DECLARED' THEN ? ELSE payment_declared_at END,
seller_acknowledged_at=CASE WHEN ?='COUNTERPARTY_ACKNOWLEDGED' THEN ? ELSE seller_acknowledged_at END
WHERE acquisition_id=? AND version=?
""",
                phase,
                method,
                Timestamp.from(now),
                phase,
                Timestamp.from(now),
                method,
                Timestamp.from(now),
                a.id(),
                a.version());
    if (changed != 1)
      throw new ConflictException("Phiên chuyển nhượng vừa thay đổi. Vui lòng tải lại.");
  }

  public void expected(Attempt a, PeerResaleContract.Command command) {
    if (command == null
        || command.version() != a.version()
        || !a.termsHash().equals(command.termsHash()))
      throw new ConflictException("Phiên bản hoặc điều khoản chuyển nhượng đã thay đổi.");
  }

  private static UUID uuid(ResultSet r, String key) throws SQLException {
    return r.getObject(key, UUID.class);
  }

  private static Attempt map(ResultSet r) throws SQLException {
    return new Attempt(
        uuid(r, "acquisition_id"),
        uuid(r, "listing_id"),
        uuid(r, "booking_id"),
        uuid(r, "facility_id"),
        uuid(r, "court_id"),
        uuid(r, "seller_id"),
        uuid(r, "buyer_id"),
        r.getBigDecimal("amount"),
        r.getString("currency"),
        at(r, "starts_at"),
        at(r, "ends_at"),
        uuid(r, "bank_revision_id"),
        r.getString("destination_hash"),
        r.getString("refund_policy_hash"),
        r.getString("terms_hash"),
        at(r, "pay_by"),
        r.getString("phase"),
        r.getString("confirmation_method"),
        r.getLong("version"));
  }

  private static Instant at(ResultSet r, String key) throws SQLException {
    return r.getTimestamp(key).toInstant();
  }
}
