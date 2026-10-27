package com.sporthub.booking.service;

import com.fasterxml.jackson.databind.*;
import com.sporthub.booking.repository.BookingRepository;
import com.sporthub.common.exception.ForbiddenException;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class PeerMoneyReads {
  private final BookingRepository repo;
  private final PeerUsageStore store;
  private final ObjectMapper json;

  public PeerMoneyReads(BookingRepository repo, PeerUsageStore store, ObjectMapper json) {
    this.repo = repo;
    this.store = store;
    this.json = json;
  }

  public JsonNode read(UUID id, UUID actor) {
    return read(id, actor, null, 100);
  }

  public JsonNode read(UUID id, UUID actor, UUID afterReturn, int size) {
    var a = store.attempt(id);
    if (!a.participant(actor)) throw new ForbiddenException("Bạn không thuộc hồ sơ tiền này.");
    return data(id, afterReturn, size);
  }

  JsonNode forCase(UUID id) {
    return data(id, null, 100);
  }

  private JsonNode data(UUID id, UUID afterReturn, int size) {
    if (size < 1 || size > 100)
      throw new IllegalArgumentException("Chỉ tải tối đa 100 khoản trả mỗi lần.");
    var a = store.attempt(id);
    var result =
        json.createObjectNode()
            .put("acquisitionId", id.toString())
            .put("amount", a.amount().toPlainString())
            .put("currency", "VND")
            .put("termsHash", a.termsHash())
            .put("authority", "COUNTERPARTIES_ONLY");
    var cases =
        repo.jdbc()
            .queryForList(
                "SELECT state FROM peer_money_cases WHERE acquisition_id=?", String.class, id);
    result.put("state", cases.isEmpty() ? "NONE" : cases.getFirst());
    result.set(
        "noClaims",
        json.valueToTree(
            repo.jdbc()
                .queryForList(
                    "SELECT actor_id FROM peer_money_no_claims WHERE acquisition_id=? ORDER BY"
                        + " actor_id",
                    UUID.class,
                    id)));
    var returns =
        repo.jdbc()
            .query(
                "SELECT * FROM peer_return_obligations WHERE acquisition_id=? AND (?::uuid IS NULL"
                    + " OR return_id>?::uuid) ORDER BY return_id LIMIT ?",
                (r, n) -> {
                  var row = new LinkedHashMap<String, Object>();
                  row.put("id", r.getObject("return_id", UUID.class));
                  row.put("amount", r.getBigDecimal("amount"));
                  row.put("state", r.getString("state"));
                  row.put("version", r.getLong("version"));
                  row.put("reference", r.getString("bank_reference"));
                  row.put(
                      "evidenceIds", Arrays.asList((UUID[]) r.getArray("evidence_ids").getArray()));
                  row.put("declaredAt", r.getTimestamp("created_at").toInstant());
                  row.put(
                      "finalizedAt",
                      r.getTimestamp("finalized_at") == null
                          ? null
                          : r.getTimestamp("finalized_at").toInstant());
                  row.put(
                      "confirmationMethod",
                      r.getString("state").equals("BUYER_ACKNOWLEDGED")
                          ? "COUNTERPARTY_ACKNOWLEDGED"
                          : "NONE");
                  return row;
                },
                id,
                afterReturn,
                afterReturn,
                size + 1);
    boolean more = returns.size() > size;
    var page = returns.subList(0, Math.min(returns.size(), size));
    result.set("returns", json.valueToTree(page));
    var pagination = result.putObject("returnPage").put("more", more).put("size", size);
    if (page.isEmpty()) pagination.putNull("nextAfter");
    else pagination.put("nextAfter", page.getLast().get("id").toString());
    result.set(
        "totals",
        json.valueToTree(
            repo.jdbc()
                .queryForObject(
                    "SELECT COALESCE(sum(amount),0) declared,COALESCE(sum(amount) FILTER(WHERE"
                        + " state='BUYER_ACKNOWLEDGED'),0) received, COALESCE(sum(amount)"
                        + " FILTER(WHERE state='AWAITING_BUYER'),0) awaiting,count(*) records,"
                        + " count(*) FILTER(WHERE state='AWAITING_BUYER') pending FROM"
                        + " peer_return_obligations WHERE acquisition_id=?",
                    (r, n) ->
                        Map.of(
                            "declared",
                            r.getBigDecimal("declared").toPlainString(),
                            "received",
                            r.getBigDecimal("received").toPlainString(),
                            "awaitingBuyer",
                            r.getBigDecimal("awaiting").toPlainString(),
                            "records",
                            r.getLong("records"),
                            "pending",
                            r.getLong("pending")),
                    id)));
    result.set(
        "reports",
        json.valueToTree(
            repo.jdbc()
                .query(
                    "SELECT * FROM peer_late_payment_reports WHERE acquisition_id=? ORDER BY"
                        + " reported_at DESC,id DESC LIMIT 100",
                    (r, n) ->
                        Map.of(
                            "id",
                            r.getObject("id", UUID.class),
                            "reporterId",
                            r.getObject("reporter_id", UUID.class),
                            "reference",
                            r.getString("bank_reference"),
                            "evidenceIds",
                            Arrays.asList((UUID[]) r.getArray("evidence_ids").getArray()),
                            "reportedAt",
                            r.getTimestamp("reported_at").toInstant(),
                            "confirmationMethod",
                            "PARTY_DECLARED"),
                    id)));
    return result;
  }
}
