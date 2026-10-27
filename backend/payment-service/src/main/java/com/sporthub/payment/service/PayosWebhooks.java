package com.sporthub.payment.service;

import com.fasterxml.jackson.databind.*;
import com.sporthub.common.exception.*;
import com.sporthub.common.security.Signatures;
import com.sporthub.payment.provider.*;
import com.sporthub.payment.repository.PaymentRepository;
import com.sporthub.payment.security.PaymentSecretVault;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class PayosWebhooks {
  private final PaymentRepository repo;
  private final PayosChannels channels;
  private final ObjectMapper json;
  private final PaymentSecretVault vault;
  private final TransactionTemplate tx;

  public PayosWebhooks(
      PaymentRepository repo,
      PayosChannels channels,
      ObjectMapper json,
      PaymentSecretVault vault,
      PlatformTransactionManager manager) {
    this.repo = repo;
    this.channels = channels;
    this.json = json;
    this.vault = vault;
    tx = new TransactionTemplate(manager);
  }

  public void receive(UUID channel, String raw) {
    if (raw == null || raw.length() > 65536)
      throw new IllegalArgumentException("Webhook payOS quá lớn.");
    JsonNode envelope;
    try {
      envelope = json.readTree(raw);
    } catch (Exception ex) {
      throw new IllegalArgumentException("Webhook payOS không hợp lệ.");
    }
    JsonNode data = envelope.path("data");
    var credentials = channels.webhookCredentials(channel);
    if (credentials.isEmpty())
      throw new ResourceNotFoundException("Không tìm thấy kênh thanh toán.");
    boolean signed = false;
    for (var key : credentials)
      try {
        PayosSignature.verify(data, envelope.path("signature").asText(), key.checksumKey());
        signed = true;
        break;
      } catch (ForbiddenException ignored) {
      }
    if (!signed) throw new ForbiddenException("Chữ ký payOS không hợp lệ.");
    if (!data.path("orderCode").isIntegralNumber() || !data.path("orderCode").canConvertToLong())
      throw new IllegalArgumentException("Thiếu mã đơn ngân hàng.");
    long code = data.path("orderCode").longValue();
    String fingerprint = Signatures.sha256(PayosSignature.canonical(data));
    String reference = data.path("reference").asText();
    if (reference.isBlank()) reference = "SAMPLE-" + fingerprint;
    if (reference.length() > 120 || reference.chars().anyMatch(Character::isISOControl))
      throw new IllegalArgumentException("Mã giao dịch ngân hàng không hợp lệ.");
    String nativeReference = reference;
    UUID id = UUID.randomUUID();
    String cipher = vault.seal(raw, "observation:" + id);
    tx.executeWithoutResult(
        status -> {
          repo.lockCallback("PAYOS:" + channel, nativeReference);
          var duplicates =
              repo.jdbc()
                  .queryForList(
                      "SELECT id FROM payos_bank_observations WHERE channel_id=? AND"
                          + " native_reference=? AND fingerprint=?",
                      channel,
                      nativeReference,
                      fingerprint);
          if (!duplicates.isEmpty())
            return; // previous ACK can be replayed after the durable commit
          var sessions =
              repo.jdbc()
                  .queryForList(
                      "SELECT * FROM payos_qr_sessions WHERE channel_id=? AND order_code=?",
                      channel,
                      code);
          UUID payment = sessions.isEmpty() ? null : (UUID) sessions.getFirst().get("payment_id");
          String state = "IGNORED", reason = "SAMPLE_OR_FOREIGN_ORDER";
          if (payment != null) {
            var session = sessions.getFirst();
            var receiver = channels.context((UUID) session.get("receiver_revision_id"));
            boolean success =
                envelope.path("success").asBoolean()
                    && "00".equals(envelope.path("code").asText())
                    && "00".equals(data.path("code").asText());
            boolean foreignLink =
                session.get("native_link_id") != null
                    && !session.get("native_link_id").equals(data.path("paymentLinkId").asText());
            boolean binding =
                receiver.accountNumber() != null
                    && receiver.accountNumber().equals(data.path("accountNumber").asText())
                    && "VND".equals(data.path("currency").asText())
                    && data.path("amount").decimalValue().signum() > 0
                    && data.path("amount").decimalValue().stripTrailingZeros().scale() <= 0
                    && (session.get("native_link_id") == null
                        || session
                            .get("native_link_id")
                            .equals(data.path("paymentLinkId").asText()));
            boolean collision =
                repo.jdbc()
                        .queryForObject(
                            "SELECT count(*) FROM payos_bank_observations WHERE channel_id=? AND"
                                + " native_reference=? AND fingerprint<>?",
                            Integer.class,
                            channel,
                            nativeReference,
                            fingerprint)
                    > 0;
            if (foreignLink) {
              state = "IGNORED";
              reason = "SAMPLE_OR_FOREIGN_NATIVE_LINK";
            } else if (success && binding && !collision) {
              state = "OBSERVED";
              reason = null;
              repo.jdbc()
                  .update(
                      "UPDATE payos_qr_sessions SET next_attempt_at=clock_timestamp() WHERE"
                          + " payment_id=? AND state<>'REVIEW'",
                      payment);
            } else {
              state = "QUARANTINED";
              reason = "WEBHOOK_BINDING_CONFLICT";
              repo.jdbc()
                  .update(
                      "UPDATE payos_qr_sessions SET"
                          + " state='REVIEW',last_error_code='WEBHOOK_BINDING_CONFLICT' WHERE"
                          + " payment_id=?",
                      payment);
            }
          }
          repo.jdbc()
              .update(
                  "INSERT INTO"
                      + " payos_bank_observations(id,channel_id,payment_id,native_reference,order_code,native_link_id,fingerprint,raw_ciphertext)"
                      + " VALUES(?,?,?,?,?,?,?,?)",
                  id,
                  channel,
                  payment,
                  nativeReference,
                  code,
                  data.path("paymentLinkId").asText(null),
                  fingerprint,
                  cipher);
          repo.jdbc()
              .update(
                  "INSERT INTO payos_observation_processing(observation_id,state,reason)"
                      + " VALUES(?,?,?)",
                  id,
                  state,
                  reason);
        });
  }
}
