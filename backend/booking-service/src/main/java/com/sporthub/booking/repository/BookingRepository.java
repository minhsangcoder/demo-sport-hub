package com.sporthub.booking.repository;

import static com.sporthub.booking.web.BookingDtos.*;

import com.fasterxml.jackson.databind.*;
import com.sporthub.common.exception.ResourceNotFoundException;
import java.sql.*;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class BookingRepository {
  private final JdbcTemplate jdbc;
  private final ObjectMapper json;

  public BookingRepository(JdbcTemplate jdbc, ObjectMapper json) {
    this.jdbc = jdbc;
    this.json = json;
  }

  public JdbcTemplate jdbc() {
    return jdbc;
  }

  public static org.springframework.jdbc.support.SqlArrayValue uuidArray(Collection<UUID> ids) {
    // Bind the array type once; casting text to uuid[] can reparse the list for every row.
    return new org.springframework.jdbc.support.SqlArrayValue("uuid", ids.toArray());
  }

  public void lock(UUID id) {
    jdbc.queryForObject(
        "SELECT pg_advisory_xact_lock(hashtextextended(?,0))", Object.class, id.toString());
  }

  public Hold hold(UUID id) {
    return jdbc
        .query(
            "SELECT * FROM slot_reservations WHERE id=?",
            (r, n) ->
                new Hold(
                    id,
                    uuid(r, "court_id"),
                    uuid(r, "facility_id"),
                    uuid(r, "holder_id"),
                    instant(r, "starts_at"),
                    instant(r, "ends_at"),
                    instant(r, "expires_at"),
                    r.getString("state"),
                    read(r.getString("quote"))),
            id)
        .stream()
        .findFirst()
        .orElseThrow(() -> new ResourceNotFoundException("Hold not found"));
  }

  public Booking find(UUID id) {
    return list("SELECT * FROM bookings WHERE id=?", id).stream()
        .findFirst()
        .orElseThrow(() -> new ResourceNotFoundException("Booking not found"));
  }

  public List<Booking> mine(UUID id) {
    return list(
        """
SELECT b.* FROM bookings b
WHERE (b.current_holder_id=? OR b.customer_id=? OR EXISTS(SELECT 1 FROM transfer_leases l WHERE l.booking_id=b.id AND l.seller_id=? AND l.state='COMPLETED'))
  AND NOT (b.source='OFFLINE_COUNTER' AND b.created_by=?
           AND NOT EXISTS(SELECT 1 FROM counter_booking_guests g WHERE g.booking_id=b.id AND g.linked_customer_id=?)
           AND NOT EXISTS(SELECT 1 FROM transfer_leases l WHERE l.booking_id=b.id AND l.buyer_id=? AND l.state='COMPLETED'))
ORDER BY b.starts_at DESC LIMIT 200
""",
        id,
        id,
        id,
        id,
        id,
        id);
  }

  public List<Booking> facility(UUID id) {
    return list("SELECT * FROM bookings WHERE facility_id=? ORDER BY starts_at DESC LIMIT 500", id);
  }

  public List<Booking> list(String sql, Object... args) {
    return jdbc.query(sql, mapper(), args);
  }

  public List<History> history(UUID id) {
    return jdbc.query(
        "SELECT * FROM booking_history WHERE booking_id=? ORDER BY created_at,id",
        (r, n) ->
            new History(
                uuid(r, "id"),
                r.getString("action"),
                uuid(r, "actor_id"),
                read(r.getString("details")),
                instant(r, "created_at")),
        id);
  }

  public void history(UUID id, UUID actor, String action, Object details) {
    var value = json.valueToTree(details);
    var enriched =
        value.isObject()
            ? ((com.fasterxml.jackson.databind.node.ObjectNode) value).deepCopy()
            : json.createObjectNode();
    var booking = find(id);
    enriched.put("bookingSource", booking.source()).put("status", booking.status());
    if (booking.currentHolderId() == null) enriched.putNull("currentHolderId");
    else enriched.put("currentHolderId", booking.currentHolderId().toString());
    if (booking.customerId() == null) enriched.putNull("customerId");
    else enriched.put("customerId", booking.customerId().toString());
    enriched.put("createdBy", booking.createdBy().toString());
    if (!enriched.has("changeSource"))
      enriched.put(
          "changeSource",
          actor == null
              ? "SYSTEM"
              : action.startsWith("TRANSFER")
                  ? "TRANSFER_COMMAND"
                  : action.startsWith("PAYMENT")
                          || action.equals("CONFIRMED")
                          || action.equals("GROUP_CONFIRMED")
                      ? "PAYMENT_EVENT"
                      : action.startsWith("GROUP")
                          ? "GROUP_API"
                          : action.equals("BOOKING_CREATED")
                                  && booking.source().equals("OFFLINE_COUNTER")
                              ? "COUNTER_API"
                              : "BOOKING_API");
    if (!enriched.has("paymentId") && booking.paymentId() != null)
      enriched.put("paymentId", booking.paymentId().toString());
    if (action.equals("BOOKING_CREATED")) {
      enriched.putNull("previousStatus");
      enriched.put("newStatus", booking.status());
    }
    jdbc.update(
        "INSERT INTO booking_history(id,booking_id,actor_id,action,details,created_at)"
            + " VALUES(?,?,?,?,?::jsonb,clock_timestamp())",
        UUID.randomUUID(),
        id,
        actor,
        action,
        write(enriched));
  }

  public void auditContext(UUID actor, String source) {
    jdbc.queryForObject(
        "SELECT set_config('sporthub.booking_actor',?,true)",
        String.class,
        actor == null ? "" : actor.toString());
    jdbc.queryForObject(
        "SELECT set_config('sporthub.booking_change_source',?,true)", String.class, source);
  }

  public String write(Object value) {
    try {
      return json.writeValueAsString(value);
    } catch (Exception ex) {
      throw new IllegalStateException(ex);
    }
  }

  public JsonNode read(String value) {
    try {
      return json.readTree(value);
    } catch (Exception ex) {
      throw new IllegalStateException(ex);
    }
  }

  private RowMapper<Booking> mapper() {
    return (r, n) ->
        new Booking(
            uuid(r, "id"),
            uuid(r, "facility_id"),
            uuid(r, "court_id"),
            uuid(r, "customer_id"),
            uuid(r, "current_holder_id"),
            uuid(r, "created_by"),
            instant(r, "starts_at"),
            instant(r, "ends_at"),
            r.getString("status"),
            r.getString("source"),
            r.getString("guest_name"),
            r.getString("guest_phone"),
            r.getBigDecimal("amount"),
            r.getString("currency"),
            read(r.getString("price_snapshot")),
            instant(r, "hold_expires_at"),
            uuid(r, "payment_id"),
            instant(r, "paid_at"),
            instant(r, "checked_in_at"),
            instant(r, "completed_at"),
            r.getLong("version"),
            r.getInt("checkin_token_version"),
            r.getString("booking_code"));
  }

  public static UUID uuid(ResultSet r, String key) throws SQLException {
    return r.getObject(key, UUID.class);
  }

  public static Instant instant(ResultSet r, String key) throws SQLException {
    var time = r.getTimestamp(key);
    return time == null ? null : time.toInstant();
  }
}
