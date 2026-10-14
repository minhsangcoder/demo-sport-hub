package com.sporthub.identity.service;

import com.sporthub.identity.domain.AccountStatus;
import com.sporthub.identity.domain.RefreshToken;
import com.sporthub.identity.domain.User;
import com.sporthub.identity.exception.IdentityException;
import com.sporthub.identity.repository.RefreshTokenRepository;
import com.sporthub.identity.security.JwtService;
import com.sporthub.identity.web.dto.AuthResult;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RefreshSessionService {
    private final RefreshTokenRepository repository;
    private final SecretHashingService secrets;
    private final JwtService jwtService;
    private final UserViewMapper userViewMapper;
    private final UserSecurityLock securityLock;
    private final jakarta.persistence.EntityManager entities;

    @Transactional
    public void revokeAll(UUID userId) {
        securityLock.lock(userId);
        repository.findByUserIdAndRevokedFalse(userId).forEach(session -> {
            session.setRevoked(true);
            session.setRevokedAt(Instant.now());
        });
    }

    @Transactional
    public SessionTokens issue(User user, RequestMetadata metadata) {
        return issue(user, metadata, null);
    }

    @Transactional
    public SessionTokens rotate(String rawRefreshToken, RequestMetadata metadata) {
        String hash = secrets.sha256(rawRefreshToken);
        RefreshToken candidate = repository.findByTokenHash(hash).orElseThrow(this::invalidRefreshToken);
        securityLock.lock(candidate.getUser().getId());
        RefreshToken current = repository.findWithLockByTokenHash(hash).orElseThrow(this::invalidRefreshToken);
        entities.refresh(current);
        securityLock.current(current.getUser());
        Instant now = Instant.now();
        if (!current.isActiveAt(now) || current.getUser().getStatus() != AccountStatus.ACTIVE) {
            throw invalidRefreshToken();
        }
        current.setRevoked(true);
        current.setRevokedAt(now);
        current.setLastUsedAt(now);
        return issue(current.getUser(), metadata, current.getId());
    }

    @Transactional
    public java.util.Set<UUID> revoke(String rawRefreshToken, UUID accessSessionId) {
        var candidates = new java.util.LinkedHashMap<UUID, RefreshToken>();
        if(rawRefreshToken != null && !rawRefreshToken.isBlank())
            repository.findByTokenHash(secrets.sha256(rawRefreshToken)).ifPresent(v -> candidates.put(v.getId(),v));
        if(accessSessionId != null) repository.findById(accessSessionId).ifPresent(v -> candidates.put(v.getId(),v));
        candidates.values().stream().map(v -> v.getUser().getId()).distinct().sorted().forEach(securityLock::lock);
        for(UUID id : candidates.keySet().stream().sorted().toList()) {
            var current = repository.findWithLockById(id).orElseThrow(this::invalidRefreshToken);
            entities.refresh(current);
            if(!current.isRevoked()) { current.setRevoked(true); current.setRevokedAt(Instant.now()); }
        }
        return candidates.values().stream().map(v -> v.getUser().getId()).collect(java.util.stream.Collectors.toSet());
    }

    private SessionTokens issue(User user, RequestMetadata metadata, UUID rotatedFromId) {
        securityLock.lock(user.getId());
        if(user.getStatus() != AccountStatus.ACTIVE || user.getRoles().isEmpty()) throw invalidRefreshToken();
        String rawRefreshToken = secrets.randomToken();
        RefreshToken session = repository.save(RefreshToken.builder()
                .user(user)
                .tokenHash(secrets.sha256(rawRefreshToken))
                .deviceInfo(metadata.userAgent())
                .ipAddress(metadata.ipAddress())
                .expiresAt(Instant.now().plusMillis(jwtService.refreshTokenLifetimeMillis()))
                .rotatedFromId(rotatedFromId)
                .build());
        String accessToken = jwtService.createAccessToken(user, session.getId());
        AuthResult response = new AuthResult(
                accessToken,
                "Bearer",
                jwtService.accessTokenLifetimeSeconds(),
                userViewMapper.toView(user));
        return new SessionTokens(response, rawRefreshToken);
    }

    private IdentityException invalidRefreshToken() {
        return new IdentityException(HttpStatus.UNAUTHORIZED, "IDENTITY-REFRESH-INVALID", "Refresh token is invalid or expired");
    }
}
