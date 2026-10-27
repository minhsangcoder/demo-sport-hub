package com.sporthub.common.fixture;

import jakarta.servlet.http.HttpServletRequest;
import java.time.*;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** Test artifact overlay only; never copied into backend production sources. */
@Configuration
@RestController
@RequestMapping("/__fixture/clock")
@ConditionalOnProperty(name = "SPORTHUB_E2E_CLOCK_ENABLED", havingValue = "true")
public class FixtureClockConfig {
  private final AtomicReference<Instant> fixed = new AtomicReference<>();
  private final String token;

  public FixtureClockConfig(@Value("${SPORTHUB_E2E_CLOCK_TOKEN}") String token) {
    if (!token.matches("[a-f0-9]{32}")) throw new IllegalStateException("Fixture token required");
    this.token = token;
  }

  @Bean
  @Primary
  public Clock fixtureClock() {
    return clock(ZoneOffset.UTC);
  }

  private Clock clock(ZoneId zone) {
    return new Clock() {
      public ZoneId getZone() { return zone; }
      public Clock withZone(ZoneId next) {
        return clock(next);
      }
      public Instant instant() {
        var at = fixed.get();
        return at == null ? Instant.now() : at;
      }
    };
  }

  @PostMapping
  public Map<String, String> advance(@RequestBody Map<String, String> body, HttpServletRequest request) {
    if (!token.equals(request.getHeader("X-Fixture-Token"))
        || !java.net.InetAddress.getLoopbackAddress().getHostAddress().equals(request.getRemoteAddr())
            && !"127.0.0.1".equals(request.getRemoteAddr()) && !"0:0:0:0:0:0:0:1".equals(request.getRemoteAddr()))
      throw new ResponseStatusException(HttpStatus.FORBIDDEN);
    var target = Instant.parse(body.get("instant"));
    if (body.size() != 1 || target.isAfter(Instant.now().plus(Duration.ofDays(90))))
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
    fixed.updateAndGet(old -> {
      if (target.isBefore(old == null ? Instant.now() : old))
        throw new ResponseStatusException(HttpStatus.CONFLICT, "Fixture time cannot go backwards");
      return target;
    });
    return Map.of("instant", target.toString());
  }
}
