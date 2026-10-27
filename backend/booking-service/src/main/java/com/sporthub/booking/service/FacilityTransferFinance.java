package com.sporthub.booking.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.sporthub.booking.repository.BookingRepository;
import com.sporthub.booking.web.BookingDtos.Booking;
import com.sporthub.common.exception.ConflictException;
import com.sporthub.common.money.*;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@Service
public class FacilityTransferFinance {
  private final BookingRepository repo;
  private final FacilityRefundEntitlements refunds;

  public FacilityTransferFinance(BookingRepository repo, FacilityRefundEntitlements refunds) {
    this.repo = repo;
    this.refunds = refunds;
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public void requireCollection(TransferLeaseService.Command command, Booking booking) {
    if (!BookingMoneyCohorts.isReal(booking)) {
      if (FacilityTransferTerms.isReal(command.financeTerms()))
        throw new ConflictException("Lượt đặt không thuộc chế độ REAL.");
      return;
    }
    var terms = command.financeTerms();
    TransferFinancialTerms.validateAcquisition(terms);
    if (!FacilityTransferTerms.isReal(terms)
        || !booking.id().toString().equals(terms.path("bookingId").asText())
        || !booking.facilityId().toString().equals(terms.path("facilityId").asText())
        || !command.acquisitionId().toString().equals(terms.path("acquisitionId").asText())
        || !command.sellerId().toString().equals(terms.path("sellerId").asText())
        || !command.buyerId().toString().equals(terms.path("buyerId").asText())
        || command.price().compareTo(TransferFinancialTerms.decimal(terms, "amount")) != 0)
      throw new ConflictException("Quyền chuyển nhượng và điều khoản REAL không khớp.");
    var rows =
        repo.jdbc()
            .queryForList(
                "SELECT * FROM booking_real_collections WHERE payment_id=?", command.paymentId());
    if (rows.isEmpty())
      throw new ResponseStatusException(
          HttpStatus.SERVICE_UNAVAILABLE,
          "Đang đồng bộ chứng từ thanh toán REAL. Thử lại đúng yêu cầu bàn giao.");
    var row = rows.getFirst();
    if (!booking.id().equals(row.get("booking_id"))
        || !command.acquisitionId().equals(row.get("acquisition_id"))
        || !command.buyerId().equals(row.get("payer_id"))
        || !"TRANSFER".equals(row.get("purpose"))
        || command.price().compareTo((BigDecimal) row.get("amount")) != 0
        || !command.paidAt().equals(((Timestamp) row.get("paid_at")).toInstant())
        || !repo.read(row.get("transfer_terms").toString()).equals(repo.read(repo.write(terms)))
        || !command.paymentId().equals(row.get("proof_id")))
      throw new ConflictException("Chứng từ thu không thuộc giao dịch bàn giao này.");
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public void completed(JsonNode terms, UUID payment, Booking booking, Instant handoffAt) {
    if (!FacilityTransferTerms.isReal(terms)) return;
    TransferFinancialTerms.validateAcquisition(terms);
    UUID acquisition = id(terms, "acquisitionId");
    var decision =
        repo.jdbc()
            .queryForMap(
                "SELECT * FROM transfer_acquisition_decisions WHERE acquisition_id=?", acquisition);
    if (!"COMPLETED".equals(decision.get("decision"))
        || !payment.equals(decision.get("payment_id"))
        || !id(terms, "buyerId").equals(booking.currentHolderId()))
      throw new ConflictException("Chưa có quyết định bàn giao thành công.");
    BigDecimal amount = TransferFinancialTerms.decimal(terms, "amount"),
        fee = TransferFinancialTerms.decimal(terms, "quotedFeeAmount"),
        net = TransferFinancialTerms.decimal(terms, "quotedSellerNetAmount");
    UUID obligation = null;
    if (net.signum() > 0) {
      String source = "SELLER:" + acquisition;
      var prior =
          repo.jdbc()
              .queryForList(
                  "SELECT id FROM facility_disbursement_obligations WHERE source_key=?", source);
      obligation = prior.isEmpty() ? UUID.randomUUID() : (UUID) prior.getFirst().get("id");
      if (prior.isEmpty())
        repo.jdbc()
            .update(
                "INSERT INTO"
                    + " facility_disbursement_obligations(id,source_key,booking_id,facility_id,receiver_owner_id,payment_id,kind,payee_id,amount,destination_revision_id,destination_hash,state,created_at)"
                    + " VALUES(?,?,?,?,?,?,'SELLER_PAYOUT',?,?,?,?, 'UNPAID',?)",
                obligation,
                source,
                booking.id(),
                booking.facilityId(),
                id(terms, "receiverOwnerId"),
                payment,
                id(terms, "sellerId"),
                net,
                id(terms, "sellerDestinationRevisionId"),
                terms.path("sellerDestinationHash").asText(),
                Timestamp.from(handoffAt));
    }
    repo.jdbc()
        .update(
            "INSERT INTO"
                + " facility_transfer_settlements(acquisition_id,payment_id,booking_id,facility_id,court_id,receiver_owner_id,seller_id,buyer_id,amount,fee_bps,policy_version,fee_amount,seller_net_amount,terms_hash,terms,handoff_at,payout_obligation_id)"
                + " VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?::jsonb,?,?) ON CONFLICT(acquisition_id) DO"
                + " NOTHING",
            acquisition,
            payment,
            booking.id(),
            booking.facilityId(),
            booking.courtId(),
            id(terms, "receiverOwnerId"),
            id(terms, "sellerId"),
            id(terms, "buyerId"),
            amount,
            terms.path("feeBps").intValue(),
            terms.path("policyVersion").longValue(),
            fee,
            net,
            terms.path("termsHash").asText(),
            repo.write(terms),
            Timestamp.from(handoffAt),
            obligation);
    if (!terms
        .path("termsHash")
        .asText()
        .equals(
            repo.jdbc()
                .queryForObject(
                    "SELECT terms_hash FROM facility_transfer_settlements WHERE acquisition_id=?",
                    String.class,
                    acquisition)))
      throw new ConflictException("Điều khoản tài chính bàn giao không khớp.");
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public void aborted(JsonNode terms, UUID payment, Booking booking) {
    if (!FacilityTransferTerms.isReal(terms) || payment == null) return;
    UUID acquisition = id(terms, "acquisitionId");
    var decision =
        repo.jdbc()
            .queryForList(
                "SELECT decision,payment_id FROM transfer_acquisition_decisions WHERE"
                    + " acquisition_id=?",
                acquisition);
    if (decision.isEmpty()
        || !"ABORTED".equals(decision.getFirst().get("decision"))
        || !payment.equals(decision.getFirst().get("payment_id"))) return;
    var source =
        repo.jdbc()
            .queryForList(
                "SELECT amount FROM booking_real_collections WHERE payment_id=? AND"
                    + " acquisition_id=? AND purpose='TRANSFER'",
                payment,
                acquisition);
    if (source.isEmpty())
      return; // A later durable projection will materialize the already-fenced technical
    // obligation.
    refunds.technical(
        booking,
        payment,
        id(terms, "buyerId"),
        id(terms, "receiverOwnerId"),
        acquisition,
        "ABORT:" + acquisition,
        "TECHNICAL_TRANSFER_FAILURE",
        (BigDecimal) source.getFirst().get("amount"));
  }

  private UUID id(JsonNode node, String field) {
    return UUID.fromString(node.path(field).asText());
  }
}
