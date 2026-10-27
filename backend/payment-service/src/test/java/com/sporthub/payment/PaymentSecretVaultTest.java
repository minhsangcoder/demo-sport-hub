package com.sporthub.payment;

import static org.assertj.core.api.Assertions.*;

import com.sporthub.payment.security.PaymentSecretVault;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class PaymentSecretVaultTest {
  private String key(String id, byte value) {
    var bytes = new byte[32];
    java.util.Arrays.fill(bytes, value);
    return id + ":" + Base64.getEncoder().encodeToString(bytes);
  }

  @Test
  void authenticatesScopeRandomizesNonceAndSupportsRetainedKeys() {
    String old = key("v1", (byte) 1), next = key("v2", (byte) 2), secret = "0000123456789";
    var vault = new PaymentSecretVault(old);
    String encrypted = vault.seal(secret, "bank:alice");
    assertThat(encrypted).doesNotContain(secret).isNotEqualTo(vault.seal(secret, "bank:alice"));
    assertThat(vault.open(encrypted, "bank:alice")).isEqualTo(secret);
    assertThat(new PaymentSecretVault(next + "," + old).open(encrypted, "bank:alice"))
        .isEqualTo(secret);
    assertThatThrownBy(() -> vault.open(encrypted, "bank:bob")).hasMessageNotContaining(secret);
    assertThatThrownBy(() -> new PaymentSecretVault(next).open(encrypted, "bank:alice"))
        .hasMessageNotContaining(secret);
    String corrupted = encrypted.substring(0, encrypted.length() - 3) + "AAA";
    assertThatThrownBy(() -> vault.open(corrupted, "bank:alice"))
        .isInstanceOf(RuntimeException.class);
  }

  @Test
  void absentMalformedOrWrongKeysFailClosed() {
    assertThatThrownBy(() -> new PaymentSecretVault("").seal("secret", "scope"))
        .hasMessageNotContaining("secret");
    assertThatThrownBy(() -> new PaymentSecretVault("v1:bad")).hasMessageNotContaining("bad");
    assertThatThrownBy(
            () -> new PaymentSecretVault(key("v1", (byte) 2) + "," + key("v1", (byte) 3)))
        .isInstanceOf(IllegalStateException.class);
  }
}
