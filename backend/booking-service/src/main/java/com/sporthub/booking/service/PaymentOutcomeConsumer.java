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

@Service
public class PaymentOutcomeConsumer {
  private final BookingRepository repo;
  private final ObjectMapper json;
  private final Clock clock;
  private final GroupService groups;
  private final ConfigurationGuardService configuration;
  private final BookingLifecycle lifecycle;
  private final BookingDemoFinance demo;
  private final BookingRealReceipts real;
  private final BookingRealFunds funds;

  public PaymentOutcomeConsumer(
      BookingRepository repo,
      ObjectMapper json,
      Clock clock,
      GroupService groups,
      ConfigurationGuardService configuration,
      BookingLifecycle lifecycle,
      BookingDemoFinance demo,
      BookingRealReceipts real,
      BookingRealFunds funds) {
    this.repo = repo;
    this.json = json;
    this.clock = clock;
    this.groups = groups;
    this.configuration = configuration;
    this.lifecycle = lifecycle;
    this.demo = demo;
    this.real = real;
    this.funds = funds;
  }

  @RabbitListener(queues = BookingConfig.PAYMENT_QUEUE)
  @Transactional
  public void receive(Message message) {
    JsonNode event;
    try {
      event = json.readTree(message.getBody());
    } catch (Exception ex) {
      throw new AmqpRejectAndDontRequeueException("Malformed payment event", ex);
    }
    apply(event);
  }

  @Transactional
  public void apply(JsonNode event) {
    if (!event.path("eventType").asText().equals("payment.completed")
        || !Set.of(1, 2).contains(event.path("eventVersion").asInt())
        || !event.path("producer").asText().equals("payment-service"))
      throw new AmqpRejectAndDontRequeueException("Unsupported payment envelope");
    var payload = event.path("payload");
    if (payload.path("purpose").asText().equals("TRANSFER")) {
      if (event.path("eventVersion").asInt() == 2) {
        var b = repo.find(UUID.fromString(payload.path("bookingId").asText()));
        BookingUserFence.money(repo, b, UUID.fromString(payload.path("payerId").asText()));
        repo.lock(b.courtId());
        real.record(event, b);
        return;
      }
      if (payload.path("provider").asText().equals("DEMO")
          && com.sporthub.common.money.DemoTransferTerms.isDemo(payload.path("transferTerms"))) {
        var b = repo.find(UUID.fromString(payload.path("bookingId").asText()));
        BookingUserFence.money(repo, b, UUID.fromString(payload.path("payerId").asText()));
        repo.lock(b.courtId());
        demo.recordCollection(event);
      }
      return;
    }
    UUID eventId, bookingId, paymentId, payer;
    Instant paidAt;
    try {
      eventId = UUID.fromString(event.path("eventId").asText());
      bookingId = UUID.fromString(payload.path("bookingId").asText());
      paymentId = UUID.fromString(payload.path("paymentId").asText());
      payer = UUID.fromString(payload.path("payerId").asText());
      if (!paymentId.toString().equals(event.path("aggregateId").asText())
          || !Set.of("SUCCESS", "FAILED").contains(payload.path("status").asText()))
        throw new IllegalArgumentException();
      if (payload.path("purpose").asText().equals("GROUP_CONTRIBUTION"))
        UUID.fromString(payload.path("memberId").asText());
      com.sporthub.common.money.Money.positive(
          payload.path("amount").decimalValue(), payload.path("currency").asText());
      paidAt = Instant.parse(payload.path("paidAt").asText());
    } catch (Exception ex) {
      throw new AmqpRejectAndDontRequeueException("Invalid payment payload", ex);
    }
    var booking = repo.find(bookingId);
    BookingUserFence.money(repo, booking, payer);
    repo.lock(booking.courtId());
    booking = repo.find(bookingId);
    configuration.lockReservationFacility(booking.facilityId());
    real.validateEnvelope(event, booking);
    if (repo.jdbc()
            .update(
                "INSERT INTO event_inbox(consumer,event_id) VALUES('booking-payment',?) ON CONFLICT"
                    + " DO NOTHING",
                eventId)
        == 0) return;
    String fingerprint =
        com.sporthub.common.money.BookingPaymentFingerprint.of(
            bookingId,
            payer,
            payload.path("memberId").asText(),
            payload.path("purpose").asText(),
            payload.path("status").asText(),
            payload.path("amount").decimalValue(),
            payload.path("currency").asText(),
            paidAt);
    if (repo.jdbc()
            .update(
                "INSERT INTO booking_payment_receipts(payment_id,booking_id,fingerprint)"
                    + " VALUES(?,?,?) ON CONFLICT DO NOTHING",
                paymentId,
                bookingId,
                fingerprint)
        == 0) {
      if (!fingerprint.equals(
          repo.jdbc()
              .queryForObject(
                  "SELECT fingerprint FROM booking_payment_receipts WHERE payment_id=?",
                  String.class,
                  paymentId))) review(bookingId, paymentId, payer, "PAYMENT_PAYLOAD_CONFLICT");
      return;
    }
    if (!payload.path("status").asText().equals("SUCCESS")) {
      repo.history(bookingId, payer, "PAYMENT_FAILED", Map.of("paymentId", paymentId));
      AdmissionLedger.paymentFailure(repo, bookingId, clock.instant());
      return;
    }
    if (payload.has("canonical")
        && (!payload.path("canonical").isBoolean() || !payload.path("canonical").booleanValue())) {
      review(bookingId, paymentId, payer, "NON_CANONICAL_PAYMENT");
      return;
    }
    demo.recordCollection(event);
    real.record(event, booking);
    boolean group =
        repo.jdbc()
                .queryForObject(
                    "SELECT count(*) FROM booking_groups WHERE id=?", Integer.class, bookingId)
            > 0;
    if (group && payload.path("purpose").asText().equals("GROUP_CONTRIBUTION")) {
      groups.paid(bookingId, paymentId, payer, payload);
      return;
    }
    if (group || !payload.path("purpose").asText().equals("BOOKING")) {
      repo.jdbc()
          .update(
              "INSERT INTO payment_reconciliation(id,booking_id,payment_id,reason)"
                  + " VALUES(?,?,?,'PURPOSE_MISMATCH') ON CONFLICT(payment_id) DO NOTHING",
              UUID.randomUUID(),
              bookingId,
              paymentId);
      return;
    }
    if (booking.paymentId() != null && booking.paymentId().equals(paymentId)) return;
    String reason = null;
    if (!Objects.equals(booking.currentHolderId(), payer)
        && !(booking.source().equals("OFFLINE_COUNTER") && booking.createdBy().equals(payer)))
      reason = "PAYER_MISMATCH";
    else if (booking.amount().compareTo(payload.path("amount").decimalValue()) != 0
        || !booking.currency().equals(payload.path("currency").asText()))
      reason = "AMOUNT_MISMATCH";
    else if (!booking.status().equals("PENDING")) reason = "BOOKING_NOT_PENDING";
    else if (!paidAt.isBefore(booking.holdExpiresAt())
        || !clock.instant().isBefore(booking.holdExpiresAt())) reason = "LATE_PAYMENT";
    else if (!lifecycle.hasLiveHold(booking, clock.instant())) reason = "RESERVATION_NOT_HELD";
    else if (repo.jdbc()
            .queryForObject(
                "SELECT count(*) FROM bookings WHERE payment_id=? AND id<>?",
                Integer.class,
                paymentId,
                bookingId)
        > 0) reason = "PAYMENT_ALREADY_APPLIED";
    if (reason != null) {
      if (event.path("eventVersion").asInt() == 2) funds.applicationFailed(booking, paymentId);
      review(bookingId, paymentId, payer, reason);
      return;
    }
    lifecycle.transition(
        booking,
        "CONFIRMED",
        payer,
        "CONFIRMED",
        Map.of("paymentId", paymentId),
        paidAt,
        paymentId);
  }

  private void review(UUID booking, UUID payment, UUID payer, String reason) {
    repo.jdbc()
        .update(
            "INSERT INTO payment_reconciliation(id,booking_id,payment_id,reason) VALUES(?,?,?,?) ON"
                + " CONFLICT(payment_id) DO NOTHING",
            UUID.randomUUID(),
            booking,
            payment,
            reason);
    repo.history(
        booking, payer, "PAYMENT_REQUIRES_REVIEW", Map.of("paymentId", payment, "reason", reason));
  }
}
