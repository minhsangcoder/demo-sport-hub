package com.sporthub.payment.provider;

import static com.sporthub.payment.web.PaymentDtos.*;

import java.math.BigDecimal;
import java.util.UUID;

public interface PaymentProvider {
  record OrderContext(
      UUID paymentId,
      UUID receiverRevisionId,
      long orderCode,
      BigDecimal amount,
      String currency,
      String description,
      java.time.Instant expiresAt) {}

  record OrderResult(
      String nativeReference,
      String qrCode,
      String checkoutUrl,
      com.fasterxml.jackson.databind.JsonNode data) {}

  default OrderResult create(OrderContext context) {
    throw new UnsupportedOperationException("Provider does not issue bank QR sessions");
  }

  default OrderResult query(OrderContext context) {
    throw new UnsupportedOperationException("Provider does not query bank orders");
  }

  default OrderResult cancel(OrderContext context) {
    throw new UnsupportedOperationException("Provider does not cancel bank orders");
  }

  String name();

  String create(UUID paymentId, BigDecimal amount, String currency);

  void verify(Callback callback, String signature);

  void simulate(Payment payment, String outcome);

  String refund(String transactionId, BigDecimal amount, String idempotencyKey);
}
