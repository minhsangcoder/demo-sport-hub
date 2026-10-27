package com.sporthub.booking;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.sporthub.booking.service.BookingBankProfiles;
import com.sporthub.common.money.BankProfileContract.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@org.springframework.test.annotation.DirtiesContext(
    classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
class LegacyGroupBankProfileTest extends BookingIntegrationTest {
  @Autowired BookingBankProfiles banks;

  private void register(UUID principal) {
    banks.activate(
        new Activation(
            principal, UUID.randomUUID(), "a".repeat(64), 0, false, key(), "b".repeat(64)));
  }

  @Test
  void bankLegacyPaidGroupRemainsLockedAtTimeoutBeforeAndAfterTheWorkerButUnpaidMemberDoesNot() {
    var member = customer();
    var group = reminderGroup(member);
    var payment = UUID.randomUUID();
    consumer.apply(contribution(group, recipient(group, user), user, payment));
    register(user.id());
    register(member.id());
    when(clock.instant()).thenReturn(group.deadline());
    assertThat(banks.status(user.id(), true).responsibilities())
        .extracting(Responsibility::kind)
        .containsExactly("LEGACY_GROUP_REFUND_REVIEW");
    assertThat(banks.status(member.id(), true).canChange()).isTrue();
    service.expireCourt(court);
    assertThat(banks.status(user.id(), true).responsibilities())
        .extracting(Responsibility::resourceId)
        .containsExactly(payment);
    assertThat(banks.status(user.id(), true).canChange()).isFalse();
  }

  @Test
  void bankLegacyLateContributionReviewLocksItsActualPayerEvenWithoutAnAppliedContribution() {
    var member = customer();
    var group = reminderGroup(member);
    when(clock.instant()).thenReturn(group.deadline());
    service.expireCourt(court);
    var payment = UUID.randomUUID();
    consumer.apply(contribution(group, recipient(group, member), member, payment));
    assertThat(
            repo.jdbc()
                .queryForObject(
                    "SELECT count(*) FROM group_contributions WHERE payment_id=?",
                    Integer.class,
                    payment))
        .isZero();
    register(member.id());
    assertThat(banks.status(member.id(), true).responsibilities())
        .extracting(Responsibility::kind)
        .containsExactly("LEGACY_GROUP_REFUND_REVIEW");
    register(user.id());
    assertThat(banks.status(user.id(), true).canChange()).isTrue();
    assertThatThrownBy(
            () ->
                banks.activate(
                    new Activation(
                        member.id(),
                        UUID.randomUUID(),
                        "c".repeat(64),
                        1,
                        true,
                        key(),
                        "d".repeat(64))))
        .isInstanceOf(com.sporthub.common.exception.ActionPolicyException.class);
  }
}
