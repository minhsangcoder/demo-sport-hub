package com.sporthub.payment.provider;

import com.fasterxml.jackson.annotation.JsonIgnoreType;

/** Internal-only secret material. Never a controller response or exception detail. */
@JsonIgnoreType
public final class PayosCredentials {
  private final String clientId, apiKey, checksumKey;

  public PayosCredentials(String clientId, String apiKey, String checksumKey) {
    if (!valid(clientId) || !valid(apiKey) || !valid(checksumKey))
      throw new IllegalArgumentException("Cần đủ ba khóa kênh payOS hợp lệ.");
    this.clientId = clientId;
    this.apiKey = apiKey;
    this.checksumKey = checksumKey;
  }

  private boolean valid(String value) {
    return value != null
        && !value.isBlank()
        && value.length() <= 200
        && !value.chars().anyMatch(Character::isISOControl);
  }

  @com.fasterxml.jackson.annotation.JsonIgnore
  public String clientId() {
    return clientId;
  }

  @com.fasterxml.jackson.annotation.JsonIgnore
  public String apiKey() {
    return apiKey;
  }

  @com.fasterxml.jackson.annotation.JsonIgnore
  public String checksumKey() {
    return checksumKey;
  }

  @Override
  public String toString() {
    return "PayosCredentials[REDACTED]";
  }
}
