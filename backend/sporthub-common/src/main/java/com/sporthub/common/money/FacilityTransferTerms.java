package com.sporthub.common.money;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sporthub.common.security.Signatures;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

/** Facility REAL protocol: the platform neither receives the resale price nor earns its fee. */
public final class FacilityTransferTerms {
  public static final String PROTOCOL = "FACILITY_REAL_V1";

  private FacilityTransferTerms() {}

  public static boolean isReal(JsonNode terms) {
    return terms != null && "REAL".equals(terms.path("mode").asText());
  }

  public static ObjectNode seller(
      ObjectMapper json,
      JsonNode booking,
      JsonNode facility,
      JsonNode feePolicy,
      JsonNode receiver,
      JsonNode destination,
      UUID seller,
      BigDecimal price,
      Instant deadline) {
    if (feePolicy == null
        || !feePolicy.path("configured").asBoolean()
        || !feePolicy.path("feeBps").isIntegralNumber()
        || !feePolicy.path("version").isIntegralNumber())
      throw new IllegalArgumentException("Chủ sân cần cấu hình phí chuyển nhượng.");
    UUID facilityId = UUID.fromString(booking.path("facilityId").asText()),
        owner = UUID.fromString(facility.path("ownerId").asText());
    if (!facilityId.toString().equals(facility.path("id").asText())
        || !facilityId.toString().equals(receiver.path("facilityId").asText())
        || !owner.toString().equals(receiver.path("receiverOwnerId").asText())
        || !receiver.path("status").asText().equals("READY"))
      throw new IllegalArgumentException("Kênh nhận tiền của cơ sở chưa sẵn sàng.");
    if (destination == null
        || !seller.toString().equals(destination.path("principalId").asText())
        || destination.hasNonNull("guestBookingId"))
      throw new IllegalArgumentException("Chọn tài khoản ngân hàng của chính người chuyển nhượng.");
    var fee =
        TransferFeeTerms.quote(
            price, feePolicy.path("feeBps").intValue(), feePolicy.path("version").longValue());
    var p =
        json.createObjectNode()
            .put("mode", "REAL")
            .put("protocol", PROTOCOL)
            .put("provider", "PAYOS")
            .put("moneyClass", "REAL");
    p.put("bookingId", booking.path("id").asText())
        .put("facilityId", facilityId.toString())
        .put("courtId", booking.path("courtId").asText())
        .put("sellerId", seller.toString());
    p.put("receiverFacilityId", facilityId.toString())
        .put("receiverOwnerId", owner.toString())
        .put("receiverName", facility.path("name").asText());
    p.put("channelId", receiver.path("channelId").asText())
        .put("receiverRevisionId", receiver.path("receiverRevisionId").asText());
    p.put("sellerDestinationRevisionId", destination.path("id").asText())
        .put("sellerDestinationHash", destination.path("destinationHash").asText());
    p.put("policyVersion", fee.policyVersion())
        .put("feeBps", fee.feeBps())
        .put("feePolicyHash", feePolicy.path("termsHash").asText());
    p.put("amount", fee.amount().toPlainString())
        .put("quotedFeeAmount", fee.quotedFeeAmount().toPlainString())
        .put("quotedSellerNetAmount", fee.quotedSellerNetAmount().toPlainString());
    p.put("currency", "VND")
        .put("rounding", "HALF_UP")
        .put("deadline", deadline.truncatedTo(ChronoUnit.MILLIS).toString());
    p.set("refundPolicy", booking.path("refundPolicy").deepCopy());
    p.put("termsHash", hash(p));
    validateSeller(p);
    return p;
  }

  public static void validateSeller(JsonNode p) {
    if (!isReal(p)
        || !PROTOCOL.equals(p.path("protocol").asText())
        || !"PAYOS".equals(p.path("provider").asText())
        || !"REAL".equals(p.path("moneyClass").asText()))
      throw new IllegalArgumentException("Cần giao thức tài chính REAL của cơ sở.");
    for (String field :
        List.of(
            "bookingId",
            "facilityId",
            "courtId",
            "sellerId",
            "receiverFacilityId",
            "receiverOwnerId",
            "channelId",
            "receiverRevisionId",
            "sellerDestinationRevisionId")) UUID.fromString(p.path(field).asText());
    if (!p.path("facilityId").asText().equals(p.path("receiverFacilityId").asText())
        || p.path("receiverName").asText().isBlank()
        || !p.path("sellerDestinationHash").asText().matches("[a-f0-9]{64}")
        || !"VND".equals(p.path("currency").asText())
        || !"HALF_UP".equals(p.path("rounding").asText())
        || !p.path("feeBps").isIntegralNumber()
        || !p.path("feeBps").canConvertToInt()
        || !p.path("policyVersion").isIntegralNumber()
        || !p.path("policyVersion").canConvertToLong())
      throw new IllegalArgumentException("Thông tin kênh hoặc chính sách phí không hợp lệ.");
    var fee =
        TransferFeeTerms.quote(
            TransferFinancialTerms.decimal(p, "amount"),
            p.path("feeBps").intValue(),
            p.path("policyVersion").longValue());
    if (fee.quotedFeeAmount().compareTo(TransferFinancialTerms.decimal(p, "quotedFeeAmount")) != 0
        || fee.quotedSellerNetAmount()
                .compareTo(TransferFinancialTerms.decimal(p, "quotedSellerNetAmount"))
            != 0) throw new IllegalArgumentException("Số tiền và phí cơ sở không khớp.");
    if (!Signatures.sha256(
            p.path("facilityId").asText()
                + "|"
                + fee.policyVersion()
                + "|"
                + fee.feeBps()
                + "|VND|HALF_UP|FACILITY_TRANSFER_FEE")
        .equals(p.path("feePolicyHash").asText()))
      throw new IllegalArgumentException("Chính sách phí sai phạm vi.");
    var r = p.path("refundPolicy");
    UUID.fromString(r.path("id").asText());
    if (!r.path("configured").asBoolean()
        || !r.path("version").isIntegralNumber()
        || !r.path("version").canConvertToLong()
        || r.path("version").longValue() < 1
        || !r.path("rateBps").isIntegralNumber()
        || !r.path("rateBps").canConvertToInt()
        || r.path("rateBps").intValue() < 0
        || r.path("rateBps").intValue() > 10000
        || !"VND".equals(r.path("currency").asText())
        || !"HALF_UP".equals(r.path("rounding").asText())
        || !Signatures.sha256(
                p.path("facilityId").asText()
                    + "|"
                    + r.path("version").longValue()
                    + "|"
                    + r.path("rateBps").intValue()
                    + "|VND|HALF_UP|FACILITY_POLICY_CANCELLATION")
            .equals(r.path("termsHash").asText()))
      throw new IllegalArgumentException("Chính sách hoàn đã chấp nhận không hợp lệ.");
    Instant.parse(p.path("deadline").asText());
    if (!hash(p)
        .equals(p.path(p.has("sellerTermsHash") ? "sellerTermsHash" : "termsHash").asText()))
      throw new IllegalArgumentException("Điều khoản chuyển nhượng đã thay đổi.");
    for (String field : List.of("accountNumber", "apiKey", "checksumKey", "credentialsCiphertext"))
      if (p.has(field))
        throw new IllegalArgumentException(
            "Điều khoản không được chứa thông tin ngân hàng bí mật.");
  }

  private static String hash(JsonNode p) {
    var values = new ArrayList<String>();
    for (String field :
        List.of(
            "mode",
            "protocol",
            "provider",
            "moneyClass",
            "bookingId",
            "facilityId",
            "courtId",
            "sellerId",
            "receiverFacilityId",
            "receiverOwnerId",
            "receiverName",
            "channelId",
            "receiverRevisionId",
            "sellerDestinationRevisionId",
            "sellerDestinationHash",
            "policyVersion",
            "feeBps",
            "feePolicyHash",
            "amount",
            "quotedFeeAmount",
            "quotedSellerNetAmount",
            "currency",
            "rounding",
            "deadline")) values.add(p.path(field).asText());
    values.add(p.path("refundPolicy").path("termsHash").asText());
    return Signatures.sha256(PROTOCOL + "|" + String.join("|", values));
  }
}
