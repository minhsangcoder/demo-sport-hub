package com.sporthub.payment.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.sporthub.common.exception.*;
import com.sporthub.common.security.RemoteIdentity.Caller;
import com.sporthub.common.security.Signatures;
import com.sporthub.payment.repository.PaymentRepository;
import com.sporthub.payment.security.PaymentSecretVault;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class BankDestinations {
  public record Input(
      @NotBlank @Pattern(regexp = "[0-9]{6}") String bankBin,
      @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
          @NotBlank
          @Pattern(regexp = "[0-9]{1,19}")
          String accountNumber,
      @Size(max = 100) String accountName,
      @NotBlank @Size(max = 200) String consentReference,
      @PositiveOrZero Long expectedProfileVersion) {
    public Input(
        String bankBin, String accountNumber, String accountName, String consentReference) {
      this(bankBin, accountNumber, accountName, consentReference, null);
    }

    @Override
    public String toString() {
      return "BankDestinationInput[REDACTED]";
    }
  }

  public record Destination(
      UUID id,
      UUID principalId,
      UUID guestBookingId,
      String bankBin,
      String bankName,
      String maskedAccountNumber,
      String accountName,
      String destinationHash,
      Instant createdAt,
      String verificationMethod) {}

  public record PrivateDestination(Destination destination, String accountNumber) {
    @Override
    public String toString() {
      return "PrivateDestination[REDACTED]";
    }
  }

  private final PaymentRepository repo;
  private final PaymentSecretVault vault;
  private final BankCatalog catalog;
  private final PaymentBankAccess access;
  private final TransactionTemplate tx;

  public BankDestinations(
      PaymentRepository repo,
      PaymentSecretVault vault,
      BankCatalog catalog,
      PaymentBankAccess access,
      PlatformTransactionManager manager) {
    this.repo = repo;
    this.vault = vault;
    this.catalog = catalog;
    this.access = access;
    tx = new TransactionTemplate(manager);
  }

  public List<Destination> mine(Caller caller) {
    caller.requireRole("CUSTOMER");
    return repo.jdbc()
        .query(
            "SELECT d.* FROM customer_bank_destination_revisions d WHERE d.principal_id=? AND NOT"
                + " EXISTS(SELECT 1 FROM bank_destination_requests r WHERE r.revision_id=d.id AND"
                + " r.state IN('STAGED','REJECTED')) ORDER BY d.created_at DESC,d.id LIMIT 100",
            (r, n) -> project(row(r)),
            caller.id());
  }

  public Destination register(
      Input input, String key, Caller caller, String token, UUID guestBooking) {
    return register(input, key, caller, token, guestBooking, false, 0, false);
  }

  Destination stage(
      Input input, String key, Caller caller, long expectedVersion, boolean existingHistory) {
    return register(input, key, caller, null, null, true, expectedVersion, existingHistory);
  }

  Destination staged(UUID id, UUID principal) {
    var row = find(id);
    if (!Objects.equals(row.get("principal_id"), principal) || row.get("guest_booking_id") != null)
      throw new ForbiddenException("Tài khoản nhận tiền không thuộc bạn.");
    return project(row);
  }

  static String fingerprint(Input input, UUID principal, UUID guestBooking) {
    String name = input.accountName() == null ? "" : input.accountName().trim();
    return Signatures.sha256(
        principal
            + "|"
            + guestBooking
            + "|"
            + input.bankBin()
            + "|"
            + input.accountNumber()
            + "|"
            + name
            + "|"
            + input.consentReference()
            + (input.expectedProfileVersion() == null ? "" : "|" + input.expectedProfileVersion()));
  }

  private Destination register(
      Input input,
      String key,
      Caller caller,
      String token,
      UUID guestBooking,
      boolean staging,
      long expectedVersion,
      boolean existingHistory) {
    if (guestBooking == null) caller.requireRole("CUSTOMER");
    else access.guest(guestBooking, caller, token);
    check(input);
    checkKey(key);
    vault.requireConfigured();
    var bank = catalog.require(input.bankBin());
    UUID principal = guestBooking == null ? caller.id() : null;
    String name = input.accountName() == null ? "" : input.accountName().trim();
    String fingerprint = fingerprint(input, principal, guestBooking);
    return tx.execute(
        status -> {
          repo.lock(caller.id());
          var previous =
              repo.jdbc()
                  .queryForList(
                      "SELECT fingerprint,revision_id FROM bank_destination_requests WHERE"
                          + " actor_id=? AND idempotency_key=?",
                      caller.id(),
                      key);
          if (!previous.isEmpty()) {
            if (!fingerprint.equals(previous.getFirst().get("fingerprint")))
              throw new ConflictException("Mã yêu cầu đã dùng cho thông tin khác.");
            return project(find((UUID) previous.getFirst().get("revision_id")));
          }
          UUID id = UUID.randomUUID();
          String destinationHash =
              Signatures.sha256(
                  id
                      + "|"
                      + principal
                      + "|"
                      + guestBooking
                      + "|"
                      + input.bankBin()
                      + "|"
                      + input.accountNumber());
          repo.jdbc()
              .update(
                  "INSERT INTO"
                      + " customer_bank_destination_revisions(id,principal_id,guest_booking_id,bank_bin,bank_name,account_ciphertext,masked_account,account_name,destination_hash,consent_reference,created_by)"
                      + " VALUES(?,?,?,?,?,?,?,?,?,?,?)",
                  id,
                  principal,
                  guestBooking,
                  input.bankBin(),
                  bank.name(),
                  vault.seal(input.accountNumber(), "bank:" + id),
                  mask(input.accountNumber()),
                  name,
                  destinationHash,
                  input.consentReference(),
                  caller.id());
          repo.jdbc()
              .update(
                  "INSERT INTO"
                      + " bank_destination_requests(actor_id,idempotency_key,fingerprint,revision_id,state,expected_profile_version,history_present)"
                      + " VALUES(?,?,?,?,?,?,?)",
                  caller.id(),
                  key,
                  fingerprint,
                  id,
                  staging ? "STAGED" : "LEGACY",
                  staging ? expectedVersion : null,
                  existingHistory);
          return project(find(id));
        });
  }

  public Destination owned(UUID id, UUID principal, UUID guestBooking) {
    return project(ownedRow(id, principal, guestBooking));
  }

  private Map<String, Object> ownedRow(UUID id, UUID principal, UUID guestBooking) {
    var row = find(id);
    if (!Objects.equals(row.get("principal_id"), principal)
        || !Objects.equals(row.get("guest_booking_id"), guestBooking))
      throw new ForbiddenException("Tài khoản nhận tiền không thuộc người được nhận.");
    if (Boolean.TRUE.equals(
        repo.jdbc()
            .queryForObject(
                "SELECT EXISTS(SELECT 1 FROM bank_destination_requests WHERE revision_id=? AND"
                    + " state IN('STAGED','REJECTED'))",
                Boolean.class,
                id)))
      throw new ConflictException("Tài khoản ngân hàng chưa được kích hoạt để dùng cho giao dịch.");
    return row;
  }

  public PrivateDestination privateDestination(UUID id, UUID principal, UUID guestBooking) {
    var row = ownedRow(id, principal, guestBooking);
    Destination destination = project(row);
    return new PrivateDestination(
        destination, vault.open(row.get("account_ciphertext").toString(), "bank:" + id));
  }

  private Map<String, Object> find(UUID id) {
    var rows =
        repo.jdbc()
            .queryForList("SELECT * FROM customer_bank_destination_revisions WHERE id=?", id);
    if (rows.isEmpty()) throw new ResourceNotFoundException("Chưa tìm thấy tài khoản nhận tiền.");
    return rows.getFirst();
  }

  private Destination project(Map<String, Object> row) {
    return new Destination(
        (UUID) row.get("id"),
        (UUID) row.get("principal_id"),
        (UUID) row.get("guest_booking_id"),
        row.get("bank_bin").toString(),
        row.get("bank_name").toString(),
        row.get("masked_account").toString(),
        row.get("account_name").toString(),
        row.get("destination_hash").toString(),
        ((java.sql.Timestamp) row.get("created_at")).toInstant(),
        "SELF_DECLARED");
  }

  private Map<String, Object> row(java.sql.ResultSet rs) throws java.sql.SQLException {
    var result = new HashMap<String, Object>();
    var metadata = rs.getMetaData();
    for (int index = 1; index <= metadata.getColumnCount(); index++)
      result.put(metadata.getColumnLabel(index), rs.getObject(index));
    return result;
  }

  public static String mask(String account) {
    return "***" + account.substring(Math.max(0, account.length() - 4));
  }

  private void check(Input input) {
    if (input == null
        || input.accountNumber() == null
        || !input.accountNumber().matches("[0-9]{1,19}")
        || input.consentReference() == null
        || input.consentReference().isBlank()
        || input.consentReference().length() > 200
        || input.accountName() != null
            && (input.accountName().length() > 100
                || input.accountName().chars().anyMatch(Character::isISOControl)))
      throw new IllegalArgumentException(
          "Nhập STK từ 1–19 chữ số và xác nhận thông tin nhận tiền.");
  }

  static void checkKey(String key) {
    if (key == null || !key.matches("[A-Za-z0-9_.:-]{1,100}"))
      throw new IllegalArgumentException("Cần Idempotency-Key hợp lệ.");
  }
}
