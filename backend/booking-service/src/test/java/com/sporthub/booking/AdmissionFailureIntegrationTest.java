package com.sporthub.booking;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.sporthub.booking.service.*;
import com.sporthub.common.event.DomainEvent;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

class AdmissionFailureIntegrationTest extends HoldProtectionTestSupport {
  @Autowired AdmissionFailureConsumer failures;
  @Autowired HoldProtection protection;

  JsonNode failure(String kind, UUID source, UUID booking, UUID user, Instant at) {
    return json.valueToTree(
        DomainEvent.create(
            "payment.admission.exempted",
            1,
            "payment-service",
            source,
            Map.of(
                "kind",
                kind,
                "sourceId",
                source,
                "bookingId",
                booking,
                "principalId",
                user,
                "reason",
                "PROVIDER_UNAVAILABLE",
                "failedAt",
                at)));
  }

  @Test
  void technicalLateNativeFailureCompensatesAbandonmentOnceWithoutGrantingPaidUsage() {
    var booking = pending();
    UUID source =
        repo.jdbc()
            .queryForObject(
                "SELECT reservation_id FROM bookings WHERE id=?", UUID.class, booking.id());
    when(clock.instant()).thenReturn(booking.holdExpiresAt().plusSeconds(1));
    service.expireCourt(court);
    var event =
        failure(
            "NATIVE_PAYMENT", UUID.randomUUID(), booking.id(), customer.id(), now.plusSeconds(1));
    failures.apply(event);
    failures.apply(event);
    assertThat(
            repo.jdbc()
                .queryForObject(
                    "SELECT count(*) FROM booking_abandonment_events WHERE source_id=?",
                    Integer.class,
                    source))
        .isEqualTo(1);
    assertThat(
            repo.jdbc()
                .queryForObject(
                    "SELECT count(*) FROM booking_admission_exemptions WHERE source_id=?",
                    Integer.class,
                    source))
        .isEqualTo(1);
    assertThat(repo.find(booking.id()).status()).isEqualTo("EXPIRED");
    assertThat(repo.find(booking.id()).paymentId()).isNull();
    assertThat(service.hold(input(), key(), customer, "token", false).state()).isEqualTo("HOLD");
  }

  @Test
  void technicalPeerQrFailureExemptsOnlyThatBuyerAttemptAndDoesNotChangeMoneyOrHolder() {
    var sale = listing();
    var buyer = buyer();
    var command = input(sale);
    acquisitions.acquire(command, key(), buyer);
    when(clock.instant()).thenReturn(now.plusSeconds(301));
    timeouts.tick();
    var before = repo.find(sale.booking().id());
    failures.apply(
        failure(
            "PEER_ACQUISITION",
            command.acquisitionId(),
            sale.booking().id(),
            buyer.id(),
            now.plusSeconds(1)));
    assertThat(
            repo.jdbc()
                .queryForObject(
                    "SELECT count(*) FROM booking_admission_exemptions WHERE source_id=?",
                    Integer.class,
                    command.acquisitionId()))
        .isEqualTo(1);
    assertThat(
            acquisitions.acquire(input(sale), key(), buyer).path("attempt").path("phase").asText())
        .isEqualTo("WAITING_PAYMENT");
    assertThat(repo.find(before.id()).currentHolderId()).isEqualTo(customer.id());
    assertThat(repo.find(before.id()).paymentId()).isEqualTo(before.paymentId());
    assertThat(
            repo.jdbc()
                .queryForObject(
                    "SELECT count(*) FROM facility_disbursement_obligations WHERE booking_id=?",
                    Integer.class,
                    before.id()))
        .isZero();
  }

  @Test
  void technicalFailureBindingRejectsForgedActorFutureTimeOrMonetaryFieldsAndRollsBackWithInbox() {
    var booking = pending();
    var valid = failure("NATIVE_PAYMENT", UUID.randomUUID(), booking.id(), customer.id(), now);
    var monetary = valid.deepCopy();
    ((com.fasterxml.jackson.databind.node.ObjectNode) monetary.path("payload"))
        .put("amount", 100000);
    assertThatThrownBy(() -> failures.apply(monetary))
        .isInstanceOf(org.springframework.amqp.AmqpRejectAndDontRequeueException.class);
    assertThatThrownBy(
            () ->
                failures.apply(
                    failure(
                        "NATIVE_PAYMENT", UUID.randomUUID(), booking.id(), UUID.randomUUID(), now)))
        .isInstanceOf(org.springframework.amqp.AmqpRejectAndDontRequeueException.class);
    assertThatThrownBy(
            () ->
                failures.apply(
                    failure(
                        "NATIVE_PAYMENT",
                        UUID.randomUUID(),
                        booking.id(),
                        customer.id(),
                        now.plusSeconds(30))))
        .isInstanceOf(org.springframework.amqp.AmqpRejectAndDontRequeueException.class);
    new TransactionTemplate(manager)
        .executeWithoutResult(
            status -> {
              failures.apply(valid);
              status.setRollbackOnly();
            });
    assertThat(
            repo.jdbc()
                .queryForObject(
                    "SELECT count(*) FROM event_inbox WHERE consumer='booking-admission-failures'"
                        + " AND event_id=?",
                    Integer.class,
                    UUID.fromString(valid.path("eventId").asText())))
        .isZero();
    assertThat(
            repo.jdbc()
                .queryForObject(
                    "SELECT count(*) FROM booking_admission_exemptions WHERE source_id=(SELECT"
                        + " reservation_id FROM bookings WHERE id=?)",
                    Integer.class,
                    booking.id()))
        .isZero();
  }
}
