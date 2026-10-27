package com.sporthub.payment.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.sporthub.common.exception.*;
import com.sporthub.common.money.PeerResaleTerms;
import com.sporthub.common.security.RemoteIdentity.Caller;
import com.sporthub.payment.provider.SePayQrImages;
import com.sporthub.payment.repository.PaymentRepository;
import com.sporthub.payment.security.PaymentSecretVault;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;

@Service
public class PeerQrInstructions {
  public record Instruction(
      UUID acquisitionId,
      String protocol,
      String phase,
      String amount,
      String currency,
      String memo,
      Instant payBy,
      String qrUrl,
      String bankName,
      String maskedAccountNumber,
      String accountName,
      String accountVerification,
      String confirmationMethod) {}

  private final PaymentRepository repo;
  private final PeerQrContextClient authority;
  private final BankDestinations banks;
  private final SePayQrImages images;
  private final PaymentSecretVault vault;
  private final Clock clock;
  private final AdmissionFailures admission;
  private final ConcurrentMap<UUID, CompletableFuture<byte[]>> pending = new ConcurrentHashMap<>();

  public PeerQrInstructions(
      PaymentRepository repo,
      PeerQrContextClient authority,
      BankDestinations banks,
      SePayQrImages images,
      PaymentSecretVault vault,
      Clock clock,
      AdmissionFailures admission) {
    this.repo = repo;
    this.authority = authority;
    this.banks = banks;
    this.images = images;
    this.vault = vault;
    this.clock = clock;
    this.admission = admission;
  }

  public Instruction instruction(UUID id, Caller actor) {
    var terms = context(id, actor);
    var row = prepare(id, terms);
    var bank = banks.owned(uuid(terms, "bankDestinationRevisionId"), uuid(terms, "sellerId"), null);
    requireDestination(terms, bank);
    return new Instruction(
        id,
        "PEER_RESALE_V1",
        "WAITING_PAYMENT",
        PeerResaleTerms.amount(terms).toPlainString(),
        "VND",
        memo(row),
        expiry(terms),
        "/api/v1/payments/peer-acquisitions/" + id + "/qr",
        bank.bankName(),
        bank.maskedAccountNumber(),
        bank.accountName(),
        "SELF_DECLARED",
        "NONE");
  }

  public byte[] image(UUID id, Caller actor) {
    var terms = context(id, actor);
    byte[] result = singleFlight(id, () -> load(id, terms));
    var latest = context(id, actor);
    if (!terms.path("termsHash").asText().equals(latest.path("termsHash").asText()))
      throw new ConflictException("Điều khoản phiên đã thay đổi. Vui lòng tải lại.");
    return result;
  }

  private JsonNode context(UUID id, Caller actor) {
    actor.requireRole("CUSTOMER");
    var value = authority.instruction(id, actor.id());
    var terms = value.path("terms");
    PeerResaleTerms.validateAcquisition(terms);
    if (!id.equals(uuid(terms, "acquisitionId"))
        || !id.toString().equals(value.path("attempt").path("id").asText()))
      throw PeerQrContextClient.unavailable();
    if (!actor.id().equals(uuid(terms, "sellerId")) && !actor.id().equals(uuid(terms, "buyerId")))
      throw new ForbiddenException("Bạn không thuộc phiên mua này.");
    if (!"WAITING_PAYMENT".equals(value.path("attempt").path("phase").asText())
        || !clock.instant().isBefore(expiry(terms)))
      throw new ConflictException("Phiên không còn nhận thanh toán. Không chuyển theo mã QR cũ.");
    return terms;
  }

  private Map<String, Object> prepare(UUID id, JsonNode terms) {
    vault.requireConfigured();
    repo.jdbc()
        .update(
            "INSERT INTO peer_qr_instructions(acquisition_id,terms_hash,expires_at,created_at)"
                + " VALUES(?,?,?,?) ON CONFLICT(acquisition_id) DO NOTHING",
            id,
            terms.path("termsHash").asText(),
            Timestamp.from(expiry(terms)),
            Timestamp.from(clock.instant()));
    var row =
        repo.jdbc().queryForMap("SELECT * FROM peer_qr_instructions WHERE acquisition_id=?", id);
    if (!terms.path("termsHash").asText().equals(row.get("terms_hash"))
        || !expiry(terms).equals(((Timestamp) row.get("expires_at")).toInstant()))
      throw new ConflictException("Mã QR đã được gắn với điều khoản khác.");
    return row;
  }

  private byte[] load(UUID id, JsonNode terms) {
    var row = prepare(id, terms);
    String context = "peer-qr:" + id + ":" + terms.path("termsHash").asText();
    if (row.get("image_ciphertext") != null)
      return Base64.getDecoder()
          .decode(vault.open(row.get("image_ciphertext").toString(), context));
    var bank =
        banks.privateDestination(
            uuid(terms, "bankDestinationRevisionId"), uuid(terms, "sellerId"), null);
    requireDestination(terms, bank.destination());
    byte[] png;
    try {
      png = images.fetch(bank, PeerResaleTerms.amount(terms), memo(row));
    } catch (ActionPolicyException failure) {
      if (failure.status() == 503) admission.peerFailure(terms);
      throw failure;
    }
    if (!clock.instant().isBefore(expiry(terms)))
      throw new ConflictException("Phiên đã hết hạn. Không chuyển theo mã QR cũ.");
    String encrypted = vault.seal(Base64.getEncoder().encodeToString(png), context);
    repo.jdbc()
        .update(
            "UPDATE peer_qr_instructions SET image_ciphertext=?"
                + " WHERE acquisition_id=? AND terms_hash=? AND image_ciphertext IS NULL",
            encrypted,
            id,
            terms.path("termsHash").asText());
    return png;
  }

  private byte[] singleFlight(UUID id, Supplier<byte[]> work) {
    var own = new CompletableFuture<byte[]>();
    var running = pending.putIfAbsent(id, own);
    if (running == null) {
      try {
        byte[] result = work.get();
        own.complete(result);
        return result;
      } catch (RuntimeException ex) {
        own.completeExceptionally(ex);
        throw ex;
      } finally {
        pending.remove(id, own);
      }
    }
    try {
      return running.get(6, TimeUnit.SECONDS);
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw SePayQrImages.unavailable();
    } catch (ExecutionException | TimeoutException ex) {
      throw SePayQrImages.unavailable();
    }
  }

  private static void requireDestination(JsonNode terms, BankDestinations.Destination bank) {
    if (!terms.path("destinationHash").asText().equals(bank.destinationHash()))
      throw new ConflictException("Tài khoản nhận tiền không khớp điều khoản đã chấp nhận.");
  }

  private static UUID uuid(JsonNode terms, String name) {
    return UUID.fromString(terms.path(name).asText());
  }

  private static Instant expiry(JsonNode terms) {
    return Instant.parse(terms.path("expiresAt").asText());
  }

  private static String memo(Map<String, Object> row) {
    return "SH" + row.get("qr_reference");
  }
}
