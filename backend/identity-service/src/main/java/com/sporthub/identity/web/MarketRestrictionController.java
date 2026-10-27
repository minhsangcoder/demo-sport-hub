package com.sporthub.identity.web;

import com.sporthub.common.dto.ApiResponse;
import com.sporthub.identity.security.AuthenticatedUser;
import java.sql.Timestamp;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users/me/market-restriction")
public class MarketRestrictionController {
  private final JdbcTemplate jdbc;

  public MarketRestrictionController(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @GetMapping
  public ApiResponse<Map<String, Object>> current(
      @AuthenticationPrincipal AuthenticatedUser actor,
      jakarta.servlet.http.HttpServletResponse response) {
    response.setHeader("Cache-Control", "private, no-store");
    var rows =
        jdbc.queryForList(
            "SELECT version,strike_count,permanent,blocked_until,updated_at FROM"
                + " user_market_restrictions WHERE user_id=?",
            actor.userId());
    var result = new LinkedHashMap<String, Object>();
    result.put("projectionOnly", true);
    result.put("authority", "BOOKING");
    result.put("state", rows.isEmpty() ? "NOT_RECORDED" : "RECORDED");
    if (!rows.isEmpty()) {
      var row = rows.getFirst();
      result.put("version", row.get("version"));
      result.put("strikeCount", row.get("strike_count"));
      result.put("permanent", row.get("permanent"));
      result.put(
          "blockedUntil", row.get("blocked_until") instanceof Timestamp at ? at.toInstant() : null);
      result.put("updatedAt", ((Timestamp) row.get("updated_at")).toInstant());
    }
    return ApiResponse.success(result);
  }
}
