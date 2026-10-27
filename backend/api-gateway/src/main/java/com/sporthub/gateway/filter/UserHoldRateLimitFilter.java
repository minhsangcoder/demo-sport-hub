package com.sporthub.gateway.filter;

import com.sporthub.gateway.security.GatewayIdentity;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.cloud.gateway.filter.*;
import org.springframework.core.Ordered;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/** One sliding 60-second budget across all court-hold and resale-acquisition commands. */
@Component
public class UserHoldRateLimitFilter implements GlobalFilter, Ordered {
  private static final DefaultRedisScript<Long> BUDGET = new DefaultRedisScript<>("""
      local t=redis.call('TIME')
      local now=tonumber(t[1])*1000+math.floor(tonumber(t[2])/1000)
      redis.call('ZREMRANGEBYSCORE',KEYS[1],'-inf',now-60000)
      if redis.call('ZCARD',KEYS[1])>=5 then
        local oldest=redis.call('ZRANGE',KEYS[1],0,0,'WITHSCORES')
        return math.max(1,math.ceil((tonumber(oldest[2])+60000-now)/1000))
      end
      redis.call('ZADD',KEYS[1],now,ARGV[1])
      redis.call('PEXPIRE',KEYS[1],60000)
      return 0
      """, Long.class);
  private final ReactiveStringRedisTemplate redis;

  public UserHoldRateLimitFilter(ReactiveStringRedisTemplate redis) { this.redis = redis; }

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
    String path = exchange.getRequest().getPath().value();
    boolean holding = HttpMethod.POST.equals(exchange.getRequest().getMethod())
        && (path.equals("/api/v1/bookings/holds")
            || path.matches("/api/v1/transfers/[0-9a-fA-F-]{36}/acquisitions"));
    if (!holding) return chain.filter(exchange);
    GatewayIdentity caller = exchange.getAttribute(IdentityContextGlobalFilter.TRUSTED_IDENTITY);
    if (caller == null) return reject(exchange, 401, "UNAUTHORIZED", "Vui lòng đăng nhập để giữ sân.", 0);
    return redis.execute(BUDGET, List.of("sporthub:hold-budget:" + caller.userId()),
            List.of(UUID.randomUUID().toString())).single().timeout(Duration.ofSeconds(2))
        .onErrorReturn(-1L)
        .flatMap(retry -> retry == 0 ? chain.filter(exchange) : retry < 0
            ? reject(exchange, 503, "HOLD_POLICY_UNAVAILABLE", "Chưa thể kiểm tra giới hạn giữ sân. Vui lòng thử lại.", 0)
            : reject(exchange, 429, "HOLD_RATE_LIMITED", "Bạn đã gửi 5 yêu cầu giữ sân trong 60 giây. Vui lòng chờ trước khi thử lại.", retry));
  }

  private Mono<Void> reject(ServerWebExchange exchange, int status, String code, String message, long retry) {
    var response = exchange.getResponse();
    response.setStatusCode(HttpStatus.valueOf(status));
    response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
    response.getHeaders().setCacheControl("no-store");
    if (retry > 0) response.getHeaders().set(HttpHeaders.RETRY_AFTER, Long.toString(retry));
    String details = retry > 0 ? ",\"details\":{\"retryAt\":\"" + Instant.now().plusSeconds(retry) + "\"}" : "";
    byte[] body = ("{\"status\":" + status + ",\"code\":\"" + code + "\",\"message\":\"" + message + "\"" + details + "}").getBytes(StandardCharsets.UTF_8);
    return response.writeWith(Mono.just(response.bufferFactory().wrap(body)));
  }

  @Override public int getOrder() { return Ordered.HIGHEST_PRECEDENCE + 20; }
}
