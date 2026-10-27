package com.sporthub.payment.security;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.*;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * AES-GCM with purpose/scope binding. First key writes; retained keys read historical revisions.
 */
@Component
public class PaymentSecretVault {
  private final Map<String, SecretKeySpec> keys;
  private final String active;
  private final SecureRandom random = new SecureRandom();

  public PaymentSecretVault(@Value("${PAYMENT_SECRET_KEYS:}") String configured) {
    var parsed = new LinkedHashMap<String, SecretKeySpec>();
    try {
      if (configured != null && !configured.isBlank())
        for (String part : configured.split(",")) {
          String[] entry = part.trim().split(":", 2);
          if (entry.length != 2 || !entry[0].matches("[A-Za-z0-9_-]{1,24}"))
            throw new IllegalArgumentException();
          byte[] key = Base64.getDecoder().decode(entry[1]);
          if (key.length != 32
              || parsed.putIfAbsent(entry[0], new SecretKeySpec(key, "AES")) != null)
            throw new IllegalArgumentException();
          Arrays.fill(key, (byte) 0);
        }
    } catch (RuntimeException ex) {
      throw new IllegalStateException(
          "PAYMENT_SECRET_KEYS must contain unique key-id:base64 AES-256 entries");
    }
    keys = Collections.unmodifiableMap(parsed);
    active = parsed.isEmpty() ? null : parsed.keySet().iterator().next();
  }

  public String seal(String plaintext, String context) {
    requireConfigured();
    if (plaintext == null || context == null || context.isBlank())
      throw new IllegalArgumentException("Missing secret context");
    try {
      byte[] nonce = new byte[12];
      random.nextBytes(nonce);
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.ENCRYPT_MODE, keys.get(active), new GCMParameterSpec(128, nonce));
      cipher.updateAAD(context.getBytes(StandardCharsets.UTF_8));
      var encoder = Base64.getUrlEncoder().withoutPadding();
      return active
          + "."
          + encoder.encodeToString(nonce)
          + "."
          + encoder.encodeToString(cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception ex) {
      throw unavailable();
    }
  }

  public String open(String ciphertext, String context) {
    requireConfigured();
    try {
      String[] parts = ciphertext.split("\\.", -1);
      if (parts.length != 3 || context == null || context.isBlank() || !keys.containsKey(parts[0]))
        throw new IllegalArgumentException();
      var decoder = Base64.getUrlDecoder();
      byte[] nonce = decoder.decode(parts[1]);
      if (nonce.length != 12) throw new IllegalArgumentException();
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.DECRYPT_MODE, keys.get(parts[0]), new GCMParameterSpec(128, nonce));
      cipher.updateAAD(context.getBytes(StandardCharsets.UTF_8));
      return new String(cipher.doFinal(decoder.decode(parts[2])), StandardCharsets.UTF_8);
    } catch (Exception ex) {
      throw unavailable();
    }
  }

  public void requireConfigured() {
    if (active == null) throw unavailable();
  }

  private ResponseStatusException unavailable() {
    // Never attach a crypto/provider exception which might echo credentials or plaintext.
    return new ResponseStatusException(
        HttpStatus.SERVICE_UNAVAILABLE,
        "Không thể đọc cấu hình thanh toán an toàn. Liên hệ quản trị hệ thống.");
  }
}
