package com.sporthub.common.money;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.*;
import org.springframework.stereotype.Component;

/** Deployment-owned mode. Browser/provider labels never select the money cohort. */
@Component
public class CollectionRuntime {
  private final boolean live;

  public CollectionRuntime(
      @Value("${SPORTHUB_MONEY_MODE:DEMO}") String configured, Environment environment) {
    if (!java.util.Set.of("DEMO", "REAL").contains(configured))
      throw new IllegalStateException("SPORTHUB_MONEY_MODE must be DEMO or REAL");
    live =
        "REAL".equals(configured)
            || environment.acceptsProfiles(Profiles.of("live", "prod", "production"));
  }

  public boolean live() {
    return live;
  }

  public void requireDemo() {
    if (live)
      throw new com.sporthub.common.exception.ForbiddenException(
          "Môi trường REAL không hỗ trợ xác nhận tiền mô phỏng.");
  }
}
