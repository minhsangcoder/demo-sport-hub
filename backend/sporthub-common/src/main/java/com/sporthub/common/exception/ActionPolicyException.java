package com.sporthub.common.exception;

import java.util.Map;

/** A service-owned policy code and safe retry/context details in the shared HTTP envelope. */
public class ActionPolicyException extends RuntimeException {
  private final int status;
  private final String code;
  private final Map<String, Object> details;

  public ActionPolicyException(
      int status, String code, String message, Map<String, Object> details) {
    super(message);
    this.status = status;
    this.code = code;
    this.details = Map.copyOf(details);
  }

  public int status() {
    return status;
  }

  public String code() {
    return code;
  }

  public Map<String, Object> details() {
    return details;
  }
}
