package com.sporthub.payment.provider;

import com.sporthub.common.exception.ForbiddenException;
import com.sporthub.payment.service.PayosChannels;
import com.sporthub.payment.web.PaymentDtos.*;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
public class PayosPaymentProvider implements PaymentProvider {
  private final PayosClient client;
  private final PayosChannels channels;

  public PayosPaymentProvider(PayosClient client, PayosChannels channels) {
    this.client = client;
    this.channels = channels;
  }

  public String name() {
    return "PAYOS";
  }

  public OrderResult create(OrderContext context) {
    outsideTransaction();
    var receiver = channels.context(context.receiverRevisionId());
    if (!"VND".equals(context.currency()))
      throw new IllegalArgumentException("payOS chỉ hỗ trợ khoản thu VND.");
    var data =
        client.create(
            new PayosClient.Order(
                context.paymentId(),
                context.orderCode(),
                context.amount(),
                context.description(),
                context.expiresAt()),
            channels.reconciliationCredentials(receiver.channelId()));
    if (!receiver.bankBin().equals(data.path("bin").asText())
        || !receiver.accountNumber().equals(data.path("accountNumber").asText()))
      throw new PayosClient.Failure("RECEIVER_MISMATCH");
    return result(data);
  }

  public OrderResult query(OrderContext context) {
    outsideTransaction();
    var receiver = channels.context(context.receiverRevisionId());
    return result(
        client.query(
            context.orderCode(), channels.reconciliationCredentials(receiver.channelId())));
  }

  public OrderResult cancel(OrderContext context) {
    outsideTransaction();
    var receiver = channels.context(context.receiverRevisionId());
    return result(
        client.cancel(
            context.orderCode(), channels.reconciliationCredentials(receiver.channelId())));
  }

  public String create(UUID payment, BigDecimal amount, String currency) {
    throw new ForbiddenException("REAL cần payment intent và kênh nhận tiền do server chốt.");
  }

  public void verify(Callback callback, String signature) {
    throw new ForbiddenException("REAL chỉ nhận webhook payOS native.");
  }

  public void simulate(Payment payment, String outcome) {
    throw new ForbiddenException("Không thể mô phỏng thanh toán REAL.");
  }

  public String refund(String transaction, BigDecimal amount, String key) {
    throw new ForbiddenException("Cơ sở thực hiện hoàn tiền thủ công bằng VietQR.");
  }

  private OrderResult result(com.fasterxml.jackson.databind.JsonNode data) {
    return new OrderResult(
        data.path("paymentLinkId").asText(data.path("id").asText()),
        data.path("qrCode").asText(null),
        data.path("checkoutUrl").asText(null),
        data);
  }

  private void outsideTransaction() {
    if (TransactionSynchronizationManager.isActualTransactionActive())
      throw new IllegalStateException("Provider calls must run outside database transactions");
  }
}
