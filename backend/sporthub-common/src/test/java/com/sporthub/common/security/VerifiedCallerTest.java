package com.sporthub.common.security;

import static org.assertj.core.api.Assertions.*;
import com.sporthub.common.exception.ActionPolicyException;
import java.util.*;
import org.junit.jupiter.api.Test;

class VerifiedCallerTest {
  private RemoteIdentity.Caller caller(String status, boolean email, boolean phone) {
    return new RemoteIdentity.Caller(UUID.randomUUID(), "Khách", Set.of("CUSTOMER"), Map.of(), status, email, phone);
  }
  @Test
  void activeAccountNeedsEitherVerifiedContactAndMissingVerificationNeverDefaultsToTrusted() {
    assertThatCode(() -> caller("ACTIVE", true, false).requireActiveVerified()).doesNotThrowAnyException();
    assertThatCode(() -> caller("ACTIVE", false, true).requireActiveVerified()).doesNotThrowAnyException();
    assertThatThrownBy(() -> caller("ACTIVE", false, false).requireActiveVerified()).isInstanceOf(ActionPolicyException.class);
    assertThatThrownBy(() -> caller("SUSPENDED", true, true).requireActiveVerified()).isInstanceOf(ActionPolicyException.class);
    var unknown = new RemoteIdentity.Caller(UUID.randomUUID(), "Khách", Set.of("CUSTOMER"), Map.of());
    assertThatThrownBy(unknown::requireActiveVerified).isInstanceOf(ActionPolicyException.class);
  }
}
