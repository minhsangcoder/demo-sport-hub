package com.sporthub.booking.service;

import com.fasterxml.jackson.databind.*;
import com.sporthub.booking.config.BookingConfig;
import com.sporthub.booking.repository.BookingRepository;
import java.time.*;
import java.util.*;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** A failed QR/provider can exempt its hold but cannot alter any payment or ownership fact. */
@Service
public class AdmissionFailureConsumer {
  private final BookingRepository repo;
  private final ObjectMapper json;
  private final Clock clock;

  public AdmissionFailureConsumer(BookingRepository repo, ObjectMapper json, Clock clock) {
    this.repo = repo;
    this.json = json;
    this.clock = clock;
  }

  @RabbitListener(queues = BookingConfig.ADMISSION_FAILURE_QUEUE)
  @Transactional
  public void receive(Message message) {
    JsonNode value;
    try {
      value = json.readTree(message.getBody());
    } catch (Exception failure) {
      throw reject();
    }
    apply(value);
  }

  @Transactional
  public void apply(JsonNode event) {
    UUID source, booking, user, eventId;
    Instant at;
    String kind;
    try {
      var payload = event.path("payload");
      if (!event.path("eventType").asText().equals("payment.admission.exempted")
          || event.path("eventVersion").asInt() != 1
          || !event.path("producer").asText().equals("payment-service")
          || !payload.path("reason").asText().equals("PROVIDER_UNAVAILABLE")) throw reject();
      var fields = new HashSet<String>();
      payload.fieldNames().forEachRemaining(fields::add);
      if (!fields.equals(
          Set.of("kind", "sourceId", "bookingId", "principalId", "reason", "failedAt")))
        throw reject();
      kind = payload.path("kind").asText();
      if (!Set.of("NATIVE_PAYMENT", "PEER_ACQUISITION").contains(kind)) throw reject();
      source = UUID.fromString(payload.path("sourceId").asText());
      booking = UUID.fromString(payload.path("bookingId").asText());
      user = UUID.fromString(payload.path("principalId").asText());
      eventId = UUID.fromString(event.path("eventId").asText());
      at = Instant.parse(payload.path("failedAt").asText());
      if (!source.toString().equals(event.path("aggregateId").asText())
          || at.isAfter(clock.instant().plusSeconds(5))) throw reject();
    } catch (RuntimeException failure) {
      throw reject();
    }
    var b = repo.find(booking);
    if (!BookingMoneyCohorts.isReal(b)) throw reject();
    BookingUserFence.money(repo, b, user);
    repo.lock(b.courtId());
    UUID claim;
    if (kind.equals("PEER_ACQUISITION")) {
      var rows =
          repo.jdbc()
              .queryForList(
                  "SELECT acquisition_id FROM peer_usage_attempts WHERE acquisition_id=? AND"
                      + " booking_id=? AND buyer_id=? AND created_at<=?",
                  UUID.class,
                  source,
                  booking,
                  user,
                  java.sql.Timestamp.from(at));
      if (rows.isEmpty()) throw reject();
      claim = source;
    } else {
      boolean participant =
          Objects.equals(b.createdBy(), user)
              || Objects.equals(b.currentHolderId(), user)
              || Boolean.TRUE.equals(
                  repo.jdbc()
                      .queryForObject(
                          "SELECT EXISTS(SELECT 1 FROM group_members WHERE group_id=? AND"
                              + " user_id=?)",
                          Boolean.class,
                          booking,
                          user));
      if (!participant) throw reject();
      claim =
          repo.jdbc()
              .queryForObject(
                  "SELECT reservation_id FROM bookings WHERE id=?", UUID.class, booking);
    }
    if (repo.jdbc()
            .update(
                "INSERT INTO event_inbox(consumer,event_id) VALUES('booking-admission-failures',?)"
                    + " ON CONFLICT DO NOTHING",
                eventId)
        == 0) return;
    AdmissionLedger.exempt(repo, claim, "SYSTEM_ERROR", at);
  }

  private static AmqpRejectAndDontRequeueException reject() {
    return new AmqpRejectAndDontRequeueException("Invalid technical admission failure binding");
  }
}
