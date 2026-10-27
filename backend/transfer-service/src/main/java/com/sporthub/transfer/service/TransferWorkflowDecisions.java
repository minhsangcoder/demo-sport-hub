package com.sporthub.transfer.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.sporthub.common.event.*;
import com.sporthub.common.exception.*;
import com.sporthub.common.money.TransferHandoff;
import com.sporthub.transfer.repository.TransferRepository;
import com.sporthub.transfer.repository.TransferRepository.Row;
import java.time.*;
import java.util.*;

final class TransferWorkflowDecisions {
  private final TransferRepository repo;
  private final TransferDependencies dependencies;

  static TransferWorkflowDecisions create(
      TransferRepository repo, TransferDependencies dependencies) {
    return new TransferWorkflowDecisions(repo, dependencies);
  }

  TransferWorkflowDecisions(TransferRepository repo, TransferDependencies dependencies) {
    this.repo = repo;
    this.dependencies = dependencies;
  }

  public Map<String, Object> command(Row r) {
    var c = new HashMap<String, Object>();
    c.put("listingId", r.id());
    c.put("bookingId", r.bookingId());
    c.put("sellerId", r.sellerId());
    c.put("price", r.price());
    c.put("deadline", r.deadline());
    if (r.activeAcquisitionId() != null) {
      var finance = repo.acquisition(r.activeAcquisitionId()).financeTerms();
      if ((com.sporthub.common.money.DemoTransferTerms.isDemo(finance)
          || com.sporthub.common.money.FacilityTransferTerms.isReal(finance)))
        c.put("financeTerms", finance);
    }
    if (r.activeAcquisitionId() != null) {
      var a = repo.acquisition(r.activeAcquisitionId());
      c.put("acquisitionId", a.id());
      c.put("buyerId", a.buyerId());
      c.put("expiresAt", a.expiresAt());
      if (r.workflow().equals("HANDOFF")) {
        var p =
            repo.read(
                repo.jdbc()
                    .queryForObject(
                        "SELECT payment_payload::text FROM transfer_acquisitions WHERE id=?",
                        String.class,
                        a.id()));
        c.put("paymentId", a.paymentId());
        c.put("paidAt", p.path("paidAt").asText());
        c.put("currency", a.currency());
        c.put("price", a.amount());
      }
    }
    return c;
  }

  public JsonNode handoff(Row r, Map<String, Object> command) {
    var a = repo.acquisition(r.activeAcquisitionId());
    var paid = Instant.parse(command.get("paidAt").toString());
    String expected =
        TransferHandoff.fingerprint(
            r.id(),
            r.bookingId(),
            r.sellerId(),
            a.id(),
            a.buyerId(),
            a.paymentId(),
            a.amount(),
            a.currency(),
            paid,
            a.expiresAt(),
            r.deadline());
    var proof = dependencies.receipt(r.id());
    if (proof == null) throw new TransferDependencies.Unavailable("BOOKING_PROTOCOL_ERROR");
    if (proof.path("state").asText().equals("COMPLETED")) verifyCompleted(proof, expected);
    else {
      if (!proof.path("state").asText().equals("LOCKED")
          || !proof.path("acquisitionId").asText().equals(a.id().toString())
          || !proof.path("buyerId").asText().equals(a.buyerId().toString()))
        throw new ConflictException("Booking lease no longer belongs to this acquisition");
      dependencies.command("complete", command);
      proof = dependencies.receipt(r.id());
      verifyCompleted(proof, expected);
    }
    if (a.financeTerms() != null) {
      var decision = dependencies.decision(a.id());
      if (decision == null
          || !decision.path("decision").asText().equals("COMPLETED")
          || !expected.equals(decision.path("commandFingerprint").asText())
          || !a.id().toString().equals(decision.path("acquisitionId").asText())
          || !a.paymentId().toString().equals(decision.path("paymentId").asText())
          || !decision.hasNonNull("decidedAt"))
        throw new TransferDependencies.Unavailable("BOOKING_DECISION_PROOF_UNAVAILABLE");
      return decision;
    }
    return proof;
  }

  public void verifyCompleted(JsonNode proof, String expected) {
    if (proof == null || !proof.path("state").asText().equals("COMPLETED"))
      throw new TransferDependencies.Unavailable("BOOKING_PROOF_UNAVAILABLE");
    if (proof.path("commandFingerprint").isNull()
        || proof.path("commandFingerprint").isMissingNode())
      throw new TransferDependencies.Unavailable("LEGACY_HANDOFF_RECEIPT_MISSING");
    if (!expected.equals(proof.path("commandFingerprint").asText()))
      throw new ConflictException("Handoff receipt does not match the verified payment");
  }

  public boolean validAbort(
      Row r, com.sporthub.transfer.web.TransferDtos.Acquisition a, JsonNode d, UUID payment) {
    return d != null
        && d.path("decision").asText().equals("ABORTED")
        && d.path("origin").asText().equals("ACQUISITION_DECISION")
        && a.id().toString().equals(d.path("acquisitionId").asText())
        && r.id().toString().equals(d.path("listingId").asText())
        && r.bookingId().toString().equals(d.path("bookingId").asText())
        && r.sellerId().toString().equals(d.path("sellerId").asText())
        && a.buyerId().toString().equals(d.path("buyerId").asText())
        && payment.toString().equals(d.path("paymentId").asText())
        && a.amount().compareTo(d.path("amount").decimalValue()) == 0
        && a.currency().equals(d.path("currency").asText());
  }
}
