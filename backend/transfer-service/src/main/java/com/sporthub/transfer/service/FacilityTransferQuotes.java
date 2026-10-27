package com.sporthub.transfer.service;

import com.fasterxml.jackson.databind.*;
import com.sporthub.common.exception.ConflictException;
import com.sporthub.common.money.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class FacilityTransferQuotes {
  private final TransferDependencies dependencies;
  private final FacilityPaymentClient payments;
  private final CollectionRuntime runtime;
  private final ObjectMapper json;

  public FacilityTransferQuotes(
      TransferDependencies dependencies,
      FacilityPaymentClient payments,
      CollectionRuntime runtime,
      ObjectMapper json) {
    this.dependencies = dependencies;
    this.payments = payments;
    this.runtime = runtime;
    this.json = json;
  }

  public boolean live() {
    return runtime.live();
  }

  public JsonNode quote(
      JsonNode booking, BigDecimal price, Instant deadline, UUID destination, UUID seller) {
    boolean realBooking =
        "REAL"
            .equals(
                booking
                    .path("moneyClass")
                    .asText(booking.path("priceSnapshot").path("moneyClass").asText()));
    if (runtime.live() != realBooking)
      throw new ConflictException(
          "Lượt đặt không thuộc cùng chế độ thanh toán với thị trường chuyển nhượng.");
    UUID facility = UUID.fromString(booking.path("facilityId").asText());
    var context =
        dependencies.context(UUID.fromString(booking.path("courtId").asText())).path("facility");
    var fee = dependencies.feePolicy(facility);
    try {
      if (!runtime.live())
        return DemoTransferTerms.seller(json, booking, context, fee, seller, price, deadline);
      if (destination == null)
        throw new ConflictException(
            "Cần khai ngân hàng và số tài khoản trước khi đăng chuyển nhượng.");
      return FacilityTransferTerms.seller(
          json,
          booking,
          context,
          fee,
          payments.receiver(facility),
          payments.destination(destination, seller, null, false),
          seller,
          price,
          deadline);
    } catch (IllegalArgumentException ex) {
      throw new ConflictException(
          "Cần kiểm tra phí cơ sở, tài khoản nhận và chính sách hoàn đã chấp nhận.");
    }
  }

  public void requireCurrent(JsonNode terms, UUID facility) {
    if (runtime.live() != FacilityTransferTerms.isReal(terms)
        || !runtime.live() && !DemoTransferTerms.isDemo(terms))
      throw new ConflictException("Điều khoản không thuộc chế độ thanh toán hiện tại.");
    var fee = dependencies.feePolicy(facility);
    if (fee == null
        || !fee.path("configured").asBoolean()
        || !fee.path("termsHash").asText().equals(terms.path("feePolicyHash").asText()))
      throw new ConflictException("Phí cơ sở đã thay đổi. Người bán cần xem và chấp nhận lại.");
    if (runtime.live()) {
      var receiver = payments.receiver(facility);
      if (!receiver.path("channelId").asText().equals(terms.path("channelId").asText())
          || !receiver
              .path("receiverRevisionId")
              .asText()
              .equals(terms.path("receiverRevisionId").asText()))
        throw new ConflictException(
            "Kênh nhận tiền đã thay đổi. Người bán cần chấp nhận lại điều khoản.");
    }
  }
}
