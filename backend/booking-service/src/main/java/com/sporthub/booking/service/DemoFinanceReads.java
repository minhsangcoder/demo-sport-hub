package com.sporthub.booking.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.sporthub.booking.repository.BookingRepository;
import com.sporthub.common.exception.*;
import com.sporthub.common.money.*;
import com.sporthub.common.security.RemoteIdentity.Caller;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.springframework.transaction.annotation.*;

final class DemoFinanceReads {
  private final BookingRepository repo;
  private final BookingDependencies dependencies;
  private final Clock clock;

  static DemoFinanceReads create(
      BookingRepository repo, BookingDependencies dependencies, Clock clock) {
    return new DemoFinanceReads(repo, dependencies, clock);
  }

  DemoFinanceReads(BookingRepository repo, BookingDependencies dependencies, Clock clock) {
    this.repo = repo;
    this.dependencies = dependencies;
    this.clock = clock;
  }

  public Map<String, Object> facility(UUID facility, Caller actor, String token) {
    boolean payout = allowed(facility, actor, token, "TRANSFER_PAYOUT"),
        refund = allowed(facility, actor, token, "BOOKING_REFUND");
    if (!payout && !refund) throw new ForbiddenException("Missing facility money permission");
    return Map.of(
        "mode",
        "DEMO",
        "canPaySeller",
        payout,
        "canRefund",
        refund,
        "settlements",
        payout
            ? repo
                .jdbc()
                .queryForList(
                    "SELECT * FROM demo_transfer_settlements WHERE facility_id=? ORDER BY"
                        + " handoff_at DESC,acquisition_id LIMIT 200",
                    facility)
                .stream()
                .map(this::project)
                .toList()
            : List.of(),
        "refunds",
        refund
            ? repo
                .jdbc()
                .queryForList(
                    "SELECT * FROM demo_refund_requests WHERE facility_id=? ORDER BY created_at"
                        + " DESC,id LIMIT 200",
                    facility)
                .stream()
                .map(this::project)
                .toList()
            : List.of());
  }

  public Map<String, Object> mine(Caller actor) {
    actor.requireRole("CUSTOMER");
    var settlements =
        repo
            .jdbc()
            .queryForList(
                "SELECT * FROM demo_transfer_settlements WHERE seller_id=? OR buyer_id=? ORDER BY"
                    + " handoff_at DESC,acquisition_id LIMIT 200",
                actor.id(),
                actor.id())
            .stream()
            .map(this::project)
            .toList();
    var refunds =
        repo
            .jdbc()
            .queryForList(
                "SELECT * FROM demo_refund_requests r WHERE EXISTS(SELECT 1 FROM"
                    + " jsonb_array_elements(r.allocations) a WHERE a->>'payerId'=?) AND NOT"
                    + " (r.acquisition_id IS NULL AND EXISTS(SELECT 1 FROM counter_booking_guests g"
                    + " WHERE g.booking_id=r.booking_id AND g.linked_customer_id IS NULL)) ORDER BY"
                    + " created_at DESC,id LIMIT 200",
                actor.id().toString())
            .stream()
            .map(
                row -> {
                  var view = project(row);
                  var own = repo.read(row.get("allocations").toString());
                  var selected = new ArrayList<JsonNode>();
                  BigDecimal amount = BigDecimal.ZERO;
                  for (var a : own)
                    if (actor.id().toString().equals(a.path("payerId").asText())) {
                      selected.add(a);
                      amount = amount.add(TransferFinancialTerms.decimal(a, "refundAmount"));
                    }
                  view.put("allocations", selected);
                  view.put("yourRefundAmount", amount.toPlainString());
                  return view;
                })
            .toList();
    return Map.of("mode", "DEMO", "settlements", settlements, "refunds", refunds);
  }

  public Map<String, Object> refundView(UUID id) {
    return project(refundRow(id, false));
  }

  public Map<String, Object> monitor(Instant from, Instant to, Caller actor) {
    actor.requireRole("ADMIN");
    var window =
        com.sporthub.common.time.ReportWindow.of(from, to, "Asia/Ho_Chi_Minh", clock.instant());
    var settlements =
        repo
            .jdbc()
            .queryForList(
                "SELECT"
                    + " acquisition_id,booking_id,facility_id,amount,fee_amount,seller_net_amount,payout_state,handoff_at,paid_at,terms->>'receiverName'"
                    + " facility_name FROM demo_transfer_settlements WHERE handoff_at>=? AND"
                    + " handoff_at<? ORDER BY handoff_at DESC,acquisition_id LIMIT 200",
                time(window.from()),
                time(window.to()))
            .stream()
            .map(this::project)
            .toList();
    var refunds =
        repo
            .jdbc()
            .queryForList(
                "SELECT"
                    + " id,booking_id,facility_id,reason,base_amount,rate_bps,target_amount,state,created_at,refunded_at"
                    + " FROM demo_refund_requests WHERE created_at>=? AND created_at<? ORDER BY"
                    + " created_at DESC,id LIMIT 200",
                time(window.from()),
                time(window.to()))
            .stream()
            .map(this::project)
            .toList();
    return Map.of(
        "mode",
        "DEMO",
        "from",
        window.from(),
        "to",
        window.to(),
        "settlements",
        settlements,
        "refunds",
        refunds,
        "limit",
        200,
        "dateBasis",
        "handoffAt for seller obligations; createdAt for refund requests; current execution state");
  }

  public boolean allowed(UUID facility, Caller actor, String token, String permission) {
    try {
      dependencies.facilityPermission(facility, actor, token, permission);
      return true;
    } catch (ForbiddenException ex) {
      return false;
    }
  }

  public Map<String, Object> refundRow(UUID id, boolean lock) {
    var rows =
        repo.jdbc()
            .queryForList(
                "SELECT * FROM demo_refund_requests WHERE id=?" + (lock ? " FOR UPDATE" : ""), id);
    if (rows.isEmpty()) throw new ResourceNotFoundException("DEMO refund not found");
    return rows.getFirst();
  }

  public Map<String, Object> project(Map<String, Object> row) {
    var result = new LinkedHashMap<String, Object>();
    row.forEach(
        (key, value) -> {
          if (key.equals("terms")) return;
          var name = new StringBuilder();
          boolean upper = false;
          for (char c : key.toCharArray()) {
            if (c == '_') {
              upper = true;
              continue;
            }
            name.append(upper ? Character.toUpperCase(c) : c);
            upper = false;
          }
          result.put(
              name.toString(),
              value instanceof BigDecimal a
                  ? a.toPlainString()
                  : value instanceof Timestamp t
                      ? t.toInstant()
                      : key.equals("allocations") && value != null
                          ? repo.read(value.toString())
                          : value);
        });
    if (row.containsKey("allocations") && row.get("acquisition_id") == null) {
      var guests =
          repo.jdbc()
              .queryForList(
                  "SELECT guest_name FROM counter_booking_guests WHERE booking_id=? AND"
                      + " linked_customer_id IS NULL",
                  row.get("booking_id"));
      if (!guests.isEmpty()) {
        result.put("recipientKind", "GUEST");
        result.put("recipientLabel", guests.getFirst().get("guest_name"));
      }
    }
    result.put("mode", "DEMO");
    result.put("currency", "VND");
    return result;
  }

  public Map<String, Object> jsonMap(JsonNode json) {
    var result = new LinkedHashMap<String, Object>();
    json.fields()
        .forEachRemaining(
            entry ->
                result.put(
                    entry.getKey(),
                    entry.getValue().isTextual()
                        ? entry.getValue().asText()
                        : entry.getValue().isNull() ? null : entry.getValue()));
    return result;
  }

  public static Timestamp time(Instant at) {
    return Timestamp.from(at);
  }
}
