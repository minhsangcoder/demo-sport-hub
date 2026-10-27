package com.sporthub.transfer.service;

import com.sporthub.common.exception.*;
import com.sporthub.common.money.PeerResaleContract.Evidence;
import com.sporthub.common.security.RemoteIdentity.Caller;
import com.sporthub.common.security.Signatures;
import com.sporthub.common.upload.UploadValidation;
import com.sporthub.transfer.repository.TransferRepository;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

@Service
public class PeerEvidence {
  public record Image(String contentType, byte[] bytes) {}

  private final TransferRepository repo;
  private final PeerEvidenceAccess access;
  private final PeerEvidenceStorage storage;
  private final Clock clock;
  private final TransactionTemplate tx;

  public PeerEvidence(
      TransferRepository repo,
      PeerEvidenceAccess access,
      PeerEvidenceStorage storage,
      Clock clock,
      PlatformTransactionManager manager) {
    this.repo = repo;
    this.access = access;
    this.storage = storage;
    this.clock = clock;
    tx = new TransactionTemplate(manager);
  }

  public Evidence upload(UUID acquisition, MultipartFile file, String key, Caller actor) {
    access.participant(acquisition, actor);
    PeerTransferListings.checkKey(key);
    if (file == null || file.isEmpty() || file.getSize() > 5 * 1024 * 1024)
      throw new IllegalArgumentException("Ảnh chứng từ phải có dữ liệu và không vượt quá 5 MiB.");
    String type;
    byte[] bytes;
    try {
      type = UploadValidation.image(file);
      if (!type.equals(file.getContentType())) throw new IllegalArgumentException();
      bytes = file.getBytes();
      if (bytes.length != file.getSize()) throw new IllegalArgumentException();
    } catch (Exception ex) {
      throw new IllegalArgumentException("Chỉ nhận ảnh JPEG/PNG hợp lệ, tối đa 20 triệu điểm ảnh.");
    }
    String sha = hash(bytes);
    String fingerprint =
        Signatures.sha256(
            acquisition + "|" + actor.id() + "|" + sha + "|" + type + "|" + bytes.length);
    var row = stage(acquisition, key, actor.id(), type, bytes.length, sha, fingerprint);
    UUID id = (UUID) row.get("id");
    if ("READY".equals(row.get("state"))) return metadata(row);
    if (!"STAGED".equals(row.get("state"))) throw purging();
    storage.put(row.get("object_key").toString(), type, bytes);
    return tx.execute(
        status -> {
          repo.lock(actor.id());
          var current = find(id);
          if (!Set.of("STAGED", "READY").contains(current.get("state"))) throw purging();
          if (!"READY".equals(current.get("state"))) {
            repo.jdbc()
                .update(
                    "UPDATE transfer_evidence SET state='READY' WHERE id=? AND state='STAGED'", id);
            repo.jdbc()
                .update(
                    "INSERT INTO peer_case_evidence(acquisition_id,evidence_id,actor_id,created_at)"
                        + " VALUES(?,?,?,?) ON CONFLICT DO NOTHING",
                    acquisition,
                    id,
                    actor.id(),
                    Timestamp.from(clock.instant()));
          }
          return metadata(find(id));
        });
  }

  private Map<String, Object> stage(
      UUID acquisition,
      String key,
      UUID actor,
      String type,
      long size,
      String sha,
      String fingerprint) {
    return tx.execute(
        status -> {
          repo.lock(actor);
          var previous =
              repo.jdbc()
                  .queryForList(
                      "SELECT * FROM transfer_evidence WHERE uploader_id=? AND idempotency_key=?",
                      actor,
                      key);
          if (!previous.isEmpty()) {
            var row = previous.getFirst();
            if (!fingerprint.equals(row.get("fingerprint")))
              throw new ConflictException("Mã tải ảnh đã dùng cho dữ liệu khác.");
            return row;
          }
          var recent =
              repo.jdbc()
                  .queryForList(
                      "SELECT created_at FROM transfer_evidence WHERE uploader_id=?"
                          + " AND created_at>? ORDER BY created_at LIMIT 10",
                      actor,
                      Timestamp.from(clock.instant().minusSeconds(3600)));
          if (recent.size() >= 10)
            throw new ActionPolicyException(
                429,
                "PEER_UPLOAD_RATE_LIMITED",
                "Bạn đã tải đủ 10 ảnh trong một giờ. Vui lòng thử lại sau.",
                Map.of(
                    "retryAt",
                    ((Timestamp) recent.getFirst().get("created_at"))
                        .toInstant()
                        .plusSeconds(3600)));
          UUID id = UUID.randomUUID();
          repo.jdbc()
              .update(
                  "INSERT INTO"
                      + " transfer_evidence(id,acquisition_id,uploader_id,object_key,sha256,content_type,byte_size,state,idempotency_key,fingerprint,created_at)"
                      + " VALUES(?,?,?,?,?,?,?,'STAGED',?,?,?)",
                  id,
                  acquisition,
                  actor,
                  "peer/" + acquisition + "/" + id,
                  sha,
                  type,
                  size,
                  key,
                  fingerprint,
                  Timestamp.from(clock.instant()));
          return find(id);
        });
  }

  public List<Evidence> list(UUID acquisition, Caller actor, String token) {
    var context = access.view(acquisition, actor, token);
    repo.audit(
        context.listing(),
        actor.id(),
        "PEER_EVIDENCE_INDEX_VIEWED",
        Map.of("acquisitionId", acquisition));
    return references(acquisition);
  }

  public List<Evidence> references(UUID acquisition) {
    access.context(acquisition);
    return repo
        .jdbc()
        .queryForList(
            "SELECT * FROM transfer_evidence WHERE acquisition_id=? AND state='READY'"
                + " ORDER BY created_at DESC,id DESC LIMIT 100",
            acquisition)
        .stream()
        .map(PeerEvidence::metadata)
        .toList();
  }

  public Image image(UUID id, Caller actor, String token) {
    var row = find(id);
    var context = access.view((UUID) row.get("acquisition_id"), actor, token);
    if (!"READY".equals(row.get("state")))
      throw new ConflictException("Ảnh đang tải và chưa thể dùng làm chứng từ.");
    byte[] bytes = storage.get(row.get("object_key").toString());
    if (!hash(bytes).equals(row.get("sha256"))
        || bytes.length != ((Number) row.get("byte_size")).longValue())
      throw PeerEvidenceStorage.unavailable();
    repo.audit(
        context.listing(),
        actor.id(),
        "PEER_EVIDENCE_VIEWED",
        Map.of("acquisitionId", context.acquisition().id(), "evidenceId", id));
    return new Image(row.get("content_type").toString(), bytes);
  }

  public List<Evidence> references(UUID acquisition, List<UUID> ids) {
    access.context(acquisition);
    if (ids == null
        || ids.size() > 20
        || ids.stream().anyMatch(Objects::isNull)
        || ids.stream().distinct().count() != ids.size())
      throw new IllegalArgumentException("Danh sách chứng cứ cần đối chiếu không hợp lệ.");
    if (ids.isEmpty()) return List.of();
    return repo
        .jdbc()
        .queryForList(
            "SELECT * FROM transfer_evidence WHERE acquisition_id=?"
                + " AND state='READY' AND id=ANY(?) ORDER BY id",
            acquisition,
            ids.toArray(UUID[]::new))
        .stream()
        .map(PeerEvidence::metadata)
        .toList();
  }

  public void requireReady(UUID acquisition, UUID uploader, List<UUID> ids) {
    if (ids == null
        || ids.isEmpty()
        || ids.size() > 3
        || ids.stream().anyMatch(Objects::isNull)
        || ids.stream().distinct().count() != ids.size())
      throw new IllegalArgumentException("Cần từ 1 đến 3 ảnh chứng từ khác nhau.");
    Integer count =
        repo.jdbc()
            .queryForObject(
                "SELECT count(*) FROM transfer_evidence WHERE acquisition_id=?"
                    + " AND uploader_id=? AND state='READY' AND id=ANY(?)",
                Integer.class,
                acquisition,
                uploader,
                ids.toArray(UUID[]::new));
    if (count == null || count != ids.size())
      throw new ForbiddenException("Ảnh không thuộc bạn và phiên mua này hoặc chưa sẵn sàng.");
  }

  private Map<String, Object> find(UUID id) {
    var rows = repo.jdbc().queryForList("SELECT * FROM transfer_evidence WHERE id=?", id);
    if (rows.isEmpty()) throw new ResourceNotFoundException("Không tìm thấy ảnh chứng từ.");
    return rows.getFirst();
  }

  private static Evidence metadata(Map<String, Object> row) {
    return new Evidence(
        (UUID) row.get("id"),
        (UUID) row.get("acquisition_id"),
        (UUID) row.get("uploader_id"),
        row.get("content_type").toString(),
        ((Number) row.get("byte_size")).longValue(),
        row.get("sha256").toString(),
        row.get("state").toString(),
        ((Timestamp) row.get("created_at")).toInstant());
  }

  static String hash(byte[] bytes) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (java.security.NoSuchAlgorithmException ex) {
      throw new IllegalStateException(ex);
    }
  }

  private static ConflictException purging() {
    return new ConflictException(
        "Ảnh tải dở đã chuyển sang dọn dẹp. Vui lòng tải ảnh bằng yêu cầu mới.");
  }
}
