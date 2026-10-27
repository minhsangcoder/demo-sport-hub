package com.sporthub.common.money;

import com.sporthub.common.security.Signatures;
import java.time.Instant;
import java.util.*;

/**
 * Metadata for a committed Transfer evidence claim, carried only by authenticated service calls.
 */
public record PeerEvidenceReceipt(
    UUID acquisitionId,
    UUID buyerId,
    String termsHash,
    String bankTransactionReference,
    List<UUID> evidenceIds,
    Instant createdAt,
    String fingerprint) {
  public static PeerEvidenceReceipt create(
      UUID acquisition,
      UUID buyer,
      String termsHash,
      String reference,
      List<UUID> evidence,
      Instant createdAt) {
    if (reference == null || evidence == null || evidence.stream().anyMatch(Objects::isNull))
      throw new IllegalArgumentException("Cần ảnh và mã giao dịch hợp lệ.");
    var sorted = evidence.stream().distinct().sorted(Comparator.comparing(UUID::toString)).toList();
    var value =
        new PeerEvidenceReceipt(
            acquisition,
            buyer,
            termsHash,
            reference.trim(),
            sorted,
            createdAt,
            hash(acquisition, buyer, termsHash, reference.trim(), sorted, createdAt));
    value.validate();
    return value;
  }

  public void validate() {
    if (acquisitionId == null
        || buyerId == null
        || createdAt == null
        || termsHash == null
        || !termsHash.matches("[a-f0-9]{64}")
        || bankTransactionReference == null
        || bankTransactionReference.isBlank()
        || bankTransactionReference.length() > 120
        || bankTransactionReference.chars().anyMatch(Character::isISOControl)
        || evidenceIds == null
        || evidenceIds.isEmpty()
        || evidenceIds.size() > 3
        || evidenceIds.stream().anyMatch(Objects::isNull)
        || evidenceIds.stream().distinct().count() != evidenceIds.size()
        || !hash(
                acquisitionId, buyerId, termsHash, bankTransactionReference, evidenceIds, createdAt)
            .equals(fingerprint))
      throw new IllegalArgumentException("Chứng từ khai chuyển khoản P2P chưa hợp lệ.");
  }

  private static String hash(
      UUID acquisition,
      UUID buyer,
      String terms,
      String reference,
      List<UUID> evidence,
      Instant at) {
    return Signatures.sha256(
        "PEER_EVIDENCE|"
            + acquisition
            + "|"
            + buyer
            + "|"
            + terms
            + "|"
            + reference
            + "|"
            + evidence.stream().sorted(Comparator.comparing(UUID::toString)).toList()
            + "|"
            + at);
  }
}
