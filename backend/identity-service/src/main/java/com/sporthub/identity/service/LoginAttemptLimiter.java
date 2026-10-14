package com.sporthub.identity.service;

import com.sporthub.common.security.Signatures;
import com.sporthub.identity.exception.IdentityException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Locale;

/** A shared attempt budget, independent of IP headers and account existence. */
@Component
public class LoginAttemptLimiter {
    private static final DefaultRedisScript<Long> RESERVE = new DefaultRedisScript<>("""
        local n = redis.call('INCR', KEYS[1])
        if n == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]) end
        if n > tonumber(ARGV[2]) then return 0 end
        return 1
        """, Long.class);
    private final StringRedisTemplate redis;
    private final int attempts;
    private final long windowSeconds;

    public LoginAttemptLimiter(StringRedisTemplate redis,
            @Value("${sporthub.identity.login-max-attempts:5}") int attempts,
            @Value("${sporthub.identity.login-window-seconds:900}") long windowSeconds) {
        if (attempts < 1 || windowSeconds < 1) throw new IllegalArgumentException("Invalid login attempt limits");
        this.redis = redis; this.attempts = attempts; this.windowSeconds = windowSeconds;
    }

    public void reserve(String identifier) {
        try {
            Long allowed = redis.execute(RESERVE, List.of(key(identifier)),
                    Long.toString(windowSeconds), Integer.toString(attempts));
            if (allowed == null) throw unavailable();
            if (allowed != 1) throw new IdentityException(HttpStatus.TOO_MANY_REQUESTS,
                    "IDENTITY-LOGIN-THROTTLED", "Too many sign-in attempts. Please try again later");
        } catch (DataAccessException exception) { throw unavailable(); }
    }

    public void succeeded(String identifier) {
        try { redis.delete(key(identifier)); }
        catch (DataAccessException exception) { throw unavailable(); }
    }

    private String key(String identifier) {
        return "sporthub:login-attempts:v1:" + Signatures.sha256(identifier.trim().toLowerCase(Locale.ROOT));
    }

    private IdentityException unavailable() {
        return new IdentityException(HttpStatus.SERVICE_UNAVAILABLE, "IDENTITY-AUTH-UNAVAILABLE",
                "Sign-in is temporarily unavailable. Please try again later");
    }
}
