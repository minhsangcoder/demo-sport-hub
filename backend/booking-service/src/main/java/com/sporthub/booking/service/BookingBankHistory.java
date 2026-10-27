package com.sporthub.booking.service;

import com.sporthub.booking.repository.BookingRepository;
import com.sporthub.common.money.BankProfileContract.LegacyHistory;
import com.sporthub.common.security.Signatures;
import java.sql.Timestamp;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Import only a sole existing Payment revision; ambiguous history never selects a new recipient.
 */
@Service
public class BookingBankHistory {
  private final BookingRepository repo;
  private final BookingUserFence fence;
  private final Clock clock;

  public BookingBankHistory(BookingRepository repo, BookingUserFence fence, Clock clock) {
    this.repo = repo;
    this.fence = fence;
    this.clock = clock;
  }

  @Transactional
  public void reconcile(LegacyHistory input) {
    if (input == null
        || input.principalId() == null
        || (input.soleRevisionId() == null) != (input.soleDestinationHash() == null)
        || input.soleDestinationHash() != null
            && !input.soleDestinationHash().matches("[a-f0-9]{64}"))
      throw new IllegalArgumentException("Dữ kiện hồ sơ ngân hàng cũ không hợp lệ.");
    fence.lock(input.principalId());
    if (Boolean.TRUE.equals(
        repo.jdbc()
            .queryForObject(
                "SELECT EXISTS(SELECT 1 FROM user_bank_profiles WHERE principal_id=?)",
                Boolean.class,
                input.principalId()))) return;
    boolean ambiguous = input.soleRevisionId() == null;
    var now = clock.instant().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
    repo.jdbc()
        .update(
            """
INSERT INTO user_bank_profiles(principal_id,active_revision_id,destination_hash,initialized,
 version,reconciliation_required,updated_at) VALUES(?,?,?,TRUE,?,?,?)
""",
            input.principalId(),
            input.soleRevisionId(),
            input.soleDestinationHash(),
            ambiguous ? 0 : 1,
            ambiguous,
            Timestamp.from(now));
    if (!ambiguous)
      repo.jdbc()
          .update(
              """
INSERT INTO bank_profile_activation_decisions(principal_id,idempotency_key,fingerprint,revision_id,
 destination_hash,version,decision,decided_at) VALUES(?,'legacy-profile-v1',?,?,?,1,'ACTIVATED',?)
""",
              input.principalId(),
              Signatures.sha256(
                  "LEGACY_PROFILE|"
                      + input.principalId()
                      + "|"
                      + input.soleRevisionId()
                      + "|"
                      + input.soleDestinationHash()),
              input.soleRevisionId(),
              input.soleDestinationHash(),
              Timestamp.from(now));
  }
}
