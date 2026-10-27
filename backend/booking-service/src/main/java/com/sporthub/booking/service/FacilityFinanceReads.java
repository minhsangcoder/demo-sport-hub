package com.sporthub.booking.service;

import com.sporthub.booking.repository.BookingRepository;
import com.sporthub.common.exception.ForbiddenException;
import com.sporthub.common.security.RemoteIdentity.Caller;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class FacilityFinanceReads {
  private final BookingRepository repo;
  private final BookingDependencies dependencies;

  public FacilityFinanceReads(BookingRepository repo, BookingDependencies dependencies) {
    this.repo = repo;
    this.dependencies = dependencies;
  }

  public Map<String, Object> facility(UUID facility, Caller actor, String token) {
    boolean payout = allowed(facility, actor, token, "TRANSFER_PAYOUT"),
        refund = allowed(facility, actor, token, "BOOKING_REFUND");
    if (!payout && !refund)
      throw new ForbiddenException("Bạn chưa được cấp quyền chi trả hoặc hoàn tiền tại cơ sở này.");
    var obligations =
        repo
            .jdbc()
            .queryForList(
                """
SELECT o.*,p.id operation_id,p.version operation_version,p.claimed_by,r.id receipt_id,r.confirmed_at,r.confirmation_method
FROM facility_disbursement_obligations o
LEFT JOIN facility_disbursement_operations p ON p.obligation_id=o.id AND p.state<>'RELEASED'
LEFT JOIN facility_disbursement_receipts r ON r.obligation_id=o.id
WHERE o.facility_id=? AND ((o.kind='SELLER_PAYOUT' AND ?) OR (o.kind<>'SELLER_PAYOUT' AND ?))
ORDER BY o.created_at DESC,o.id LIMIT 200
""",
                facility,
                payout,
                refund)
            .stream()
            .map(FacilityFinanceReads::project)
            .toList();
    return Map.of(
        "mode",
        "REAL",
        "canPaySeller",
        payout,
        "canRefund",
        refund,
        "obligations",
        obligations,
        "settlements",
        payout ? settlements(facility, null) : List.of(),
        "refunds",
        refund ? refunds(facility) : List.of(),
        "limit",
        200);
  }

  public Map<String, Object> mine(Caller actor) {
    actor.requireRole("CUSTOMER");
    var obligations =
        repo
            .jdbc()
            .queryForList(
                """
SELECT o.id,o.booking_id,o.facility_id,o.kind,o.amount,o.state,o.version,o.destination_revision_id,o.created_at,
 r.id receipt_id,r.confirmed_at,r.confirmation_method,r.bank_reference
FROM facility_disbursement_obligations o LEFT JOIN facility_disbursement_receipts r ON r.obligation_id=o.id
WHERE o.payee_id=? ORDER BY o.created_at DESC,o.id LIMIT 200
""",
                actor.id())
            .stream()
            .map(FacilityFinanceReads::project)
            .toList();
    return Map.of(
        "mode",
        "REAL",
        "obligations",
        obligations,
        "settlements",
        settlements(null, actor.id()),
        "limit",
        200);
  }

  private List<Map<String, Object>> settlements(UUID facility, UUID participant) {
    return repo
        .jdbc()
        .queryForList(
            """
SELECT s.acquisition_id,s.payment_id,s.booking_id,s.facility_id,s.seller_id,s.buyer_id,s.amount,s.fee_bps,s.fee_amount,s.seller_net_amount,
 s.handoff_at,s.payout_obligation_id,o.state payout_state,r.confirmed_at,r.confirmation_method
FROM facility_transfer_settlements s LEFT JOIN facility_disbursement_obligations o ON o.id=s.payout_obligation_id
LEFT JOIN facility_disbursement_receipts r ON r.obligation_id=o.id
WHERE (?::uuid IS NULL OR s.facility_id=?) AND (?::uuid IS NULL OR s.seller_id=? OR s.buyer_id=?)
ORDER BY s.handoff_at DESC,s.acquisition_id LIMIT 200
""",
            facility,
            facility,
            participant,
            participant,
            participant)
        .stream()
        .map(FacilityFinanceReads::project)
        .toList();
  }

  private List<Map<String, Object>> refunds(UUID facility) {
    return repo
        .jdbc()
        .queryForList(
            "SELECT id,booking_id,reason,base_amount,rate_bps,target_amount,created_at FROM"
                + " facility_refund_entitlements WHERE facility_id=? ORDER BY created_at DESC,id"
                + " LIMIT 200",
            facility)
        .stream()
        .map(FacilityFinanceReads::project)
        .toList();
  }

  private boolean allowed(UUID facility, Caller actor, String token, String permission) {
    try {
      dependencies.facilityPermission(facility, actor, token, permission);
      return true;
    } catch (ForbiddenException ex) {
      return false;
    }
  }

  public static Map<String, Object> project(Map<String, Object> row) {
    var result = new LinkedHashMap<String, Object>();
    row.forEach(
        (key, value) -> {
          String[] parts = key.split("_");
          var name = new StringBuilder(parts[0]);
          for (int index = 1; index < parts.length; index++)
            name.append(Character.toUpperCase(parts[index].charAt(0)))
                .append(parts[index].substring(1));
          result.put(
              name.toString(),
              value instanceof BigDecimal amount
                  ? amount.toPlainString()
                  : value instanceof Timestamp time ? time.toInstant() : value);
        });
    if ("MANUALLY_RECORDED".equals(row.get("confirmation_method")))
      result.put("confirmationLabel", "Cơ sở xác nhận đã chuyển");
    return result;
  }
}
