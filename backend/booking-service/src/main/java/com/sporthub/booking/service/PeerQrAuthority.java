package com.sporthub.booking.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.sporthub.booking.repository.BookingRepository;
import com.sporthub.common.exception.*;
import com.sporthub.common.money.PeerResaleTerms;
import java.time.Clock;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** A QR is an instruction for an admitted attempt, never evidence of bank settlement. */
@Service
public class PeerQrAuthority {
  private final BookingRepository repo;
  private final PeerUsageStore attempts;
  private final PeerUsageTimeouts timeouts;
  private final LocalAppliedCollections collections;
  private final Clock clock;

  public PeerQrAuthority(
      BookingRepository repo,
      PeerUsageStore attempts,
      PeerUsageTimeouts timeouts,
      LocalAppliedCollections collections,
      Clock clock) {
    this.repo = repo;
    this.attempts = attempts;
    this.timeouts = timeouts;
    this.collections = collections;
    this.clock = clock;
  }

  @Transactional(noRollbackFor = ConflictException.class)
  public JsonNode instruction(UUID id, UUID actor) {
    var before = attempts.attempt(id);
    if (!before.participant(actor)) throw new ForbiddenException("Bạn không thuộc phiên mua này.");
    repo.lock(before.court());
    timeouts.settleBookingUnderCourt(before.booking());
    var attempt = attempts.attempt(id);
    var booking = repo.find(attempt.booking());
    if (!"WAITING_PAYMENT".equals(attempt.phase())
        || !clock.instant().isBefore(attempt.payBy())
        || !"CONFIRMED".equals(booking.status())
        || !attempt.seller().equals(booking.currentHolderId())
        || !BookingMoneyCohorts.isReal(booking))
      throw new ConflictException(
          "Phiên này không còn nhận thanh toán. Không chuyển theo mã QR cũ.");
    collections.requireFullyPaid(booking);
    var terms =
        repo.read(
            repo.jdbc()
                .queryForObject(
                    "SELECT terms::text FROM peer_attempt_terms WHERE acquisition_id=?",
                    String.class,
                    id));
    PeerResaleTerms.validateAcquisition(terms);
    if (!attempt.termsHash().equals(terms.path("termsHash").asText())
        || !attempt
            .bankRevision()
            .toString()
            .equals(terms.path("bankDestinationRevisionId").asText())
        || !attempt.destinationHash().equals(terms.path("destinationHash").asText())
        || attempt.amount().compareTo(PeerResaleTerms.amount(terms)) != 0)
      throw new ConflictException("Thông tin nhận tiền không khớp điều khoản phiên mua.");
    return repo.read(repo.write(Map.of("attempt", attempts.view(id), "terms", terms)));
  }
}
