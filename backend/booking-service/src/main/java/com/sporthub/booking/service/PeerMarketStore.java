package com.sporthub.booking.service;

import com.fasterxml.jackson.databind.*;
import com.sporthub.booking.repository.BookingRepository;
import com.sporthub.common.event.*;
import com.sporthub.common.exception.*;
import com.sporthub.common.security.Signatures;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class PeerMarketStore {
  private final BookingRepository repo;
  private final ObjectMapper json;
  private final Clock clock;

  public PeerMarketStore(BookingRepository repo, ObjectMapper json, Clock clock) {
    this.repo = repo;
    this.json = json;
    this.clock = clock;
  }

  public Map<String, Object> lease(UUID listing) {
    var rows =
        repo.jdbc()
            .queryForList(
                "SELECT * FROM transfer_leases WHERE listing_id=? AND protocol='PEER_RESALE_V1'",
                listing);
    if (rows.isEmpty())
      throw new ResourceNotFoundException("Không tìm thấy tin chuyển nhượng trực tiếp.");
    return rows.getFirst();
  }

  public JsonNode listing(UUID listing) {
    var lease = lease(listing);
    var terms =
        repo.jdbc()
            .queryForList(
                "SELECT version,terms FROM peer_listing_terms WHERE listing_id=? ORDER BY version"
                    + " DESC LIMIT 1",
                listing);
    if (terms.isEmpty())
      throw new ConflictException("Điều khoản tin chuyển nhượng chưa được đồng bộ.");
    var result =
        json.createObjectNode()
            .put("id", listing.toString())
            .put("protocol", "PEER_RESALE_V1")
            .put("state", lease.get("state").toString())
            .put("version", ((Number) terms.getFirst().get("version")).longValue());
    result.set("terms", repo.read(terms.getFirst().get("terms").toString()));
    if (lease.get("acquisition_id") != null)
      result.put("acquisitionId", lease.get("acquisition_id").toString());
    return result;
  }

  public String fingerprint(UUID resource, Object input) {
    return Signatures.sha256(
        resource + "|" + com.sporthub.common.security.CanonicalCommandJson.write(json, input));
  }

  public JsonNode replay(UUID actor, String action, String key, String fingerprint) {
    var rows =
        repo.jdbc()
            .queryForList(
                "SELECT fingerprint,result FROM peer_usage_commands WHERE actor_id=? AND action=?"
                    + " AND idempotency_key=?",
                actor,
                action,
                key);
    if (rows.isEmpty()) return null;
    if (!fingerprint.equals(rows.getFirst().get("fingerprint")))
      throw new ConflictException("Mã yêu cầu đã dùng cho giao dịch khác.");
    return repo.read(rows.getFirst().get("result").toString());
  }

  public JsonNode remember(
      UUID actor,
      String action,
      String key,
      String fingerprint,
      UUID resource,
      JsonNode result,
      String eventType) {
    var canonical = repo.read(result.toString());
    repo.jdbc()
        .update(
            "INSERT INTO"
                + " peer_usage_commands(actor_id,action,idempotency_key,fingerprint,result,created_at)"
                + " VALUES(?,?,?,?,?::jsonb,?)",
            actor,
            action,
            key,
            fingerprint,
            canonical.toString(),
            Timestamp.from(clock.instant()));
    ReliableOutbox.enqueue(
        repo.jdbc(),
        json,
        RabbitMQConfig.EXCHANGE_BOOKING,
        DomainEvent.create(eventType, 1, "booking-service", resource, canonical));
    return canonical;
  }
}
