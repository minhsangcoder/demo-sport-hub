package com.sporthub.transfer.service;

import com.sporthub.common.exception.*;
import com.sporthub.common.money.PeerResaleContract.*;
import com.sporthub.common.security.RemoteIdentity.Caller;
import com.sporthub.common.security.Signatures;
import com.sporthub.transfer.repository.TransferRepository;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Text exchange has no reservation, money or membership side effect. */
@Service
public class PeerConversations {
  private final TransferRepository repo;
  private final Clock clock;

  public PeerConversations(TransferRepository repo, Clock clock) {
    this.repo = repo;
    this.clock = clock;
  }

  public List<Message> messages(UUID id, long after, int size, Caller actor) {
    return messages(id, after, null, size, actor);
  }

  public List<Message> messages(UUID id, Long after, Long before, int size, Caller actor) {
    participant(id, actor);
    if ((after != null && after < 0)
        || (before != null && before <= 0)
        || (after != null && before != null)
        || size < 1 || size > 100)
      throw new IllegalArgumentException("Con trỏ hoặc số tin cần tải không hợp lệ.");
    String sql = after != null
        ? "SELECT * FROM transfer_messages WHERE conversation_id=? AND cursor>? ORDER BY cursor LIMIT ?"
        : "SELECT * FROM (SELECT * FROM transfer_messages WHERE conversation_id=? AND cursor<?"
            + " ORDER BY cursor DESC LIMIT ?) recent ORDER BY cursor";
    return repo.jdbc()
        .query(
            sql,
            (r, n) ->
                new Message(
                    r.getLong("cursor"),
                    r.getObject("id", UUID.class),
                    r.getObject("sender_id", UUID.class),
                    r.getString("text"),
                    r.getTimestamp("created_at").toInstant()),
            id,
            after != null ? after : before != null ? before : Long.MAX_VALUE,
            size);
  }

  @Transactional
  public Message send(UUID id, MessageInput input, String key, Caller actor) {
    participant(id, actor);
    PeerTransferListings.checkKey(key);
    String raw = input == null || input.text() == null ? "" : input.text();
    String text = raw.trim();
    if (text.isBlank()
        || raw.length() > 2000
        || raw.matches("(?s).*[\\x00-\\x08\\x0b\\x0c\\x0e-\\x1f\\x7f].*"))
      throw new IllegalArgumentException("Tin nhắn cần từ 1 đến 2000 ký tự văn bản.");
    repo.lock(actor.id());
    // Serializing each conversation also keeps cursor order aligned with commit order.
    repo.lock(id);
    String fingerprint = Signatures.sha256(id + "|" + actor.id() + "|" + text);
    var previous =
        repo.jdbc()
            .queryForList(
                "SELECT id,fingerprint,cursor,created_at FROM transfer_messages"
                    + " WHERE conversation_id=? AND sender_id=? AND idempotency_key=?",
                id,
                actor.id(),
                key);
    if (!previous.isEmpty()) {
      var row = previous.getFirst();
      if (!fingerprint.equals(row.get("fingerprint")))
        throw new ConflictException("Mã gửi tin đã dùng cho nội dung khác.");
      return new Message(
          ((Number) row.get("cursor")).longValue(),
          (UUID) row.get("id"),
          actor.id(),
          text,
          ((Timestamp) row.get("created_at")).toInstant());
    }
    var recent =
        repo.jdbc()
            .queryForList(
                "SELECT created_at FROM transfer_messages WHERE conversation_id=?"
                    + " AND sender_id=? AND created_at>? ORDER BY created_at LIMIT 10",
                id,
                actor.id(),
                Timestamp.from(clock.instant().minusSeconds(60)));
    if (recent.size() >= 10)
      throw new ActionPolicyException(
          429,
          "PEER_CHAT_RATE_LIMITED",
          "Bạn gửi tin quá nhanh. Mỗi phòng chỉ nhận tối đa 10 tin/phút từ bạn.",
          Map.of(
              "retryAt",
              ((Timestamp) recent.getFirst().get("created_at")).toInstant().plusSeconds(60)));
    UUID message = UUID.randomUUID();
    Instant now = clock.instant().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
    Long cursor =
        repo.jdbc()
            .queryForObject(
                "INSERT INTO"
                    + " transfer_messages(id,conversation_id,sender_id,text,idempotency_key,fingerprint,created_at)"
                    + " VALUES(?,?,?,?,?,?,?) RETURNING cursor",
                Long.class,
                message,
                id,
                actor.id(),
                text,
                key,
                fingerprint,
                Timestamp.from(now));
    return new Message(cursor, message, actor.id(), text, now);
  }

  private void participant(UUID id, Caller actor) {
    actor.requireRole("CUSTOMER");
    var rows =
        repo.jdbc()
            .queryForList("SELECT seller_id,buyer_id FROM transfer_conversations WHERE id=?", id);
    if (rows.isEmpty())
      throw new ResourceNotFoundException("Không tìm thấy cuộc trao đổi chuyển nhượng.");
    var row = rows.getFirst();
    if (!actor.id().equals(row.get("seller_id")) && !actor.id().equals(row.get("buyer_id")))
      throw new ForbiddenException("Bạn không thuộc cuộc trao đổi này.");
    if (!Boolean.TRUE.equals(
        repo.jdbc()
            .queryForObject(
                "SELECT EXISTS(SELECT 1 FROM transfer_acquisitions WHERE conversation_id=? AND"
                    + " protocol='PEER_RESALE_V1' AND peer_projection IS NOT NULL)",
                Boolean.class,
                id)))
      throw new ActionPolicyException(
          503,
          "PEER_CONVERSATION_PENDING",
          "Cuộc trao đổi đang chờ xác nhận phiên mua. Vui lòng tải lại sau.",
          Map.of());
  }
}
