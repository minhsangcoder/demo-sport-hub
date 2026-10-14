package com.sporthub.identity.service;

import com.sporthub.identity.domain.*;
import com.sporthub.identity.exception.IdentityException;
import com.sporthub.identity.repository.UserProfileRepository;
import com.sporthub.identity.repository.UserRepository;
import com.sporthub.identity.web.dto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final UserRepository userRepository;
    private final UserProfileRepository profileRepository;
    private final PasswordEncoder passwordEncoder;
    private final ChallengeService challengeService;
    private final RefreshSessionService refreshSessionService;
    private final AuditService auditService;
    private final LoginAttemptLimiter loginAttempts;
    private final UserSecurityLock securityLock;
    private String dummyPasswordHash;

    @jakarta.annotation.PostConstruct
    void initializeDummyPassword() {
        dummyPasswordHash = passwordEncoder.encode("synthetic-credential-timing-equalizer");
    }

    @Transactional
    public RegisterResult register(RegisterRequest request, RequestMetadata metadata) {
        if(request.ownerOption())throw new IdentityException(HttpStatus.BAD_REQUEST,"IDENTITY-OWNER-SIGNUP","Owner signup requires multipart request and verification documents");
        String email = normalizeEmail(request.email());
        String phone = blankToNull(request.phone());
        if (email == null && phone == null) throw new IdentityException(HttpStatus.BAD_REQUEST, "IDENTITY-VALIDATION", "Email or phone is required");
        if (email != null && (userRepository.existsByEmailIgnoreCase(email) || userRepository.existsByPendingEmailIgnoreCase(email))) {
            throw conflict("IDENTITY-EMAIL-EXISTS", "Email is already registered");
        }
        if (phone != null && (userRepository.existsByPhone(phone) || userRepository.existsByPendingPhone(phone))) {
            throw conflict("IDENTITY-PHONE-EXISTS", "Phone is already registered");
        }

        User user = userRepository.save(User.builder()
                .email(email)
                .phone(phone)
                .passwordHash(passwordEncoder.encode(request.password()))
                .status(AccountStatus.PENDING_VERIFICATION)
                .build());
        UserProfile profile = profileRepository.save(UserProfile.builder()
                .user(user)
                .fullName(request.fullName().trim())
                .build());
        user.setProfile(profile);

        ChallengeIssued challenge = challengeService.issue(user, email != null ? OtpPurpose.EMAIL_VERIFY : OtpPurpose.PHONE_VERIFY,
                email != null ? email : phone);
        auditService.record(user.getId(), "ACCOUNT_REGISTERED", "USER", user.getId(), metadata);
        return new RegisterResult(user.getId(), user.getStatus(), challenge.id(), challenge.expiresAt());
    }

    @Transactional(noRollbackFor = IdentityException.class)
    public VerificationResult verify(VerificationRequest request, RequestMetadata metadata) {
        OtpCode challenge = challengeService.consume(request, null);
        User user = securityLock.current(challenge.getUser());
        if ((challenge.getPurpose() == OtpPurpose.EMAIL_VERIFY || challenge.getPurpose() == OtpPurpose.PHONE_VERIFY)
                && user.getStatus() != AccountStatus.PENDING_VERIFICATION)
            throw new IdentityException(HttpStatus.FORBIDDEN, "IDENTITY-ACCOUNT-INACTIVE", "Account cannot be activated");
        if ((challenge.getPurpose() == OtpPurpose.EMAIL_CHANGE || challenge.getPurpose() == OtpPurpose.PHONE_CHANGE)
                && user.getStatus() != AccountStatus.ACTIVE)
            throw new IdentityException(HttpStatus.FORBIDDEN, "IDENTITY-ACCOUNT-INACTIVE", "Account is not active");
        switch (challenge.getPurpose()) {
            case EMAIL_VERIFY -> {
                user.setEmailVerified(true);
                user.setStatus(AccountStatus.ACTIVE);
                user.getRoles().add(Role.CUSTOMER);
            }
            case PHONE_VERIFY -> {
                user.setPhoneVerified(true);
                user.setStatus(AccountStatus.ACTIVE);
                user.getRoles().add(Role.CUSTOMER);
            }
            case EMAIL_CHANGE -> {
                if (user.getPendingEmail() == null || !user.getPendingEmail().equals(challenge.getRecipient())) {
                    throw new IdentityException(HttpStatus.UNPROCESSABLE_ENTITY, "IDENTITY-CHANGE-NOT-PENDING", "Email change is no longer pending");
                }
                challengeService.invalidatePasswordResets(user, user.getEmail());
                user.setEmail(user.getPendingEmail());
                user.setPendingEmail(null);
                user.setEmailVerified(true);
            }
            case PHONE_CHANGE -> {
                if (user.getPendingPhone() == null || !user.getPendingPhone().equals(challenge.getRecipient())) {
                    throw new IdentityException(HttpStatus.UNPROCESSABLE_ENTITY, "IDENTITY-CHANGE-NOT-PENDING", "Phone change is no longer pending");
                }
                challengeService.invalidatePasswordResets(user, user.getPhone());
                user.setPhone(user.getPendingPhone());
                user.setPendingPhone(null);
                user.setPhoneVerified(true);
            }
            case PASSWORD_RESET -> throw new IdentityException(
                    HttpStatus.UNPROCESSABLE_ENTITY, "IDENTITY-CHALLENGE-INVALID", "Challenge is invalid");
        }
        auditService.record(user.getId(), "CHALLENGE_VERIFIED_" + challenge.getPurpose(), "USER", user.getId(), metadata);
        return new VerificationResult(user.getId(), true, user.getStatus());
    }

    @Transactional
    public SessionTokens login(LoginRequest request, RequestMetadata metadata) {
        String identifier = request.identifier().trim();
        boolean emailLogin = identifier.contains("@");
        loginAttempts.reserve(identifier);
        User user = (emailLogin ? userRepository.findByEmailIgnoreCase(identifier)
                : userRepository.findByPhone(identifier)).orElse(null);
        if(user != null) user = securityLock.current(user);
        boolean matches = passwordEncoder.matches(request.password(),
                user == null || user.getPasswordHash() == null ? dummyPasswordHash : user.getPasswordHash());
        if (user == null || user.getPasswordHash() == null || !matches) {
            throw invalidCredentials();
        }
        boolean identifierVerified = emailLogin ? user.isEmailVerified() : user.isPhoneVerified();
        if (user.getStatus() != AccountStatus.ACTIVE || !identifierVerified || user.getRoles().isEmpty()) {
            throw new IdentityException(HttpStatus.FORBIDDEN, "IDENTITY-ACCOUNT-INACTIVE", "Account is not active");
        }
        loginAttempts.succeeded(identifier);
        user.setLastLoginAt(Instant.now());
        SessionTokens tokens = refreshSessionService.issue(user, metadata);
        auditService.record(user.getId(), "LOGIN", "USER", user.getId(), metadata);
        return tokens;
    }

    @Transactional
    public SessionTokens loginWithGoogle(GoogleIdentity identity, RequestMetadata metadata) {
        if (!identity.emailVerified() || identity.googleSub() == null || identity.googleSub().isBlank()
                || identity.email() == null || !identity.email().contains("@")) {
            throw new IdentityException(HttpStatus.UNAUTHORIZED, "IDENTITY-GOOGLE-CLAIMS",
                    "Google did not provide a verified email address");
        }

        String email = normalizeEmail(identity.email());
        User user = userRepository.findByGoogleSub(identity.googleSub()).orElse(null);
        if(user != null) user = securityLock.current(user);
        if (user == null) {
            user = userRepository.findByEmailIgnoreCase(email).orElse(null);
            if (user == null) {
                String fullName = identity.name() == null || identity.name().isBlank()
                        ? email.substring(0, email.indexOf('@')) : identity.name().trim();
                user = userRepository.save(User.builder()
                        .email(email)
                        .passwordHash(null)
                        .provider(AuthProvider.GOOGLE)
                        .googleSub(identity.googleSub())
                        .status(AccountStatus.ACTIVE)
                        .emailVerified(true)
                        .roles(new java.util.LinkedHashSet<>(java.util.Set.of(Role.CUSTOMER)))
                        .build());
                UserProfile profile = profileRepository.save(UserProfile.builder()
                        .user(user)
                        .fullName(fullName)
                        .avatarUrl(verifiedAvatar(identity.avatarUrl()))
                        .build());
                user.setProfile(profile);
            } else {
                user = securityLock.current(user);
                if (!user.isEmailVerified()) {
                    throw new IdentityException(HttpStatus.FORBIDDEN, "IDENTITY-GOOGLE-EMAIL-UNVERIFIED",
                            "Verify this email address before linking a Google account");
                }
                if (user.getProvider() == AuthProvider.GOOGLE
                        && !identity.googleSub().equals(user.getGoogleSub())) {
                    throw new IdentityException(HttpStatus.CONFLICT, "IDENTITY-GOOGLE-ACCOUNT-CONFLICT",
                            "This email is already linked to another Google account");
                }
                user.setProvider(AuthProvider.GOOGLE);
                user.setGoogleSub(identity.googleSub());
                UserProfile profile = user.getProfile();
                if (profile != null) {
                    if ((profile.getFullName() == null || profile.getFullName().isBlank())
                            && identity.name() != null && !identity.name().isBlank()) {
                        profile.setFullName(identity.name().trim());
                    }
                    String avatarUrl = verifiedAvatar(identity.avatarUrl());
                    if ((profile.getAvatarUrl() == null || profile.getAvatarUrl().isBlank()) && avatarUrl != null) {
                        profile.setAvatarUrl(avatarUrl);
                    }
                }
            }
        }

        if (user.getStatus() != AccountStatus.ACTIVE || user.getRoles().isEmpty()) {
            throw new IdentityException(HttpStatus.FORBIDDEN, "IDENTITY-ACCOUNT-INACTIVE", "Account is not active");
        }
        user.setLastLoginAt(Instant.now());
        SessionTokens tokens = refreshSessionService.issue(user, metadata);
        auditService.record(user.getId(), "GOOGLE_LOGIN", "USER", user.getId(), metadata);
        return tokens;
    }

    @Transactional
    public SessionTokens refresh(String rawRefreshToken, RequestMetadata metadata) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw new IdentityException(
                    HttpStatus.UNAUTHORIZED,
                    "IDENTITY-REFRESH-INVALID",
                    "Refresh token is invalid or expired");
        }
        SessionTokens tokens = refreshSessionService.rotate(rawRefreshToken, metadata);
        auditService.record(tokens.response().user().id(), "SESSION_REFRESHED", "USER", tokens.response().user().id(), metadata);
        return tokens;
    }

    @Transactional
    public void logout(String rawRefreshToken, UUID accessSessionId, UUID userId, RequestMetadata metadata) {
        var revokedUsers = refreshSessionService.revoke(rawRefreshToken, accessSessionId);
        for (UUID revokedUser : revokedUsers)
            auditService.record(revokedUser, "LOGOUT", "USER", revokedUser, metadata);
    }

    @Transactional
    public void forgotPassword(ForgotPasswordRequest request, RequestMetadata metadata) {
        String identifier = request.identifier().trim();
        var account = identifier.contains("@") ? userRepository.findByEmailIgnoreCase(identifier) : userRepository.findByPhone(identifier);
        account.ifPresent(candidate -> {
            User user = securityLock.current(candidate);
            boolean email = identifier.contains("@");
            boolean verified = email ? user.isEmailVerified() && user.getEmail() != null && user.getEmail().equalsIgnoreCase(identifier)
                    : user.isPhoneVerified() && identifier.equals(user.getPhone());
            if (user.getStatus() == AccountStatus.ACTIVE && verified) {
                challengeService.issue(user, OtpPurpose.PASSWORD_RESET, email ? user.getEmail() : user.getPhone());
                auditService.record(user.getId(), "PASSWORD_RESET_REQUESTED", "USER", user.getId(), metadata);
            }
        });
    }

    @Transactional(noRollbackFor = IdentityException.class)
    public void resetPassword(ResetPasswordRequest request, RequestMetadata metadata) {
        OtpCode challenge = challengeService.consumeReset(request.challengeId(), request.code());
        User user = securityLock.current(challenge.getUser());
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        refreshSessionService.revokeAll(user.getId());
        auditService.record(user.getId(), "PASSWORD_RESET_COMPLETED", "USER", user.getId(), metadata);
    }

    private String normalizeEmail(String email) {
        return email == null || email.isBlank() ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private IdentityException invalidCredentials() {
        return new IdentityException(HttpStatus.UNAUTHORIZED, "IDENTITY-CREDENTIALS-INVALID", "Email, phone, or password is invalid");
    }

    private IdentityException conflict(String code, String message) {
        return new IdentityException(HttpStatus.CONFLICT, code, message);
    }

    private String verifiedAvatar(String avatarUrl) {
        return avatarUrl != null && avatarUrl.startsWith("https://") && avatarUrl.length() <= 2048
                ? avatarUrl : null;
    }
}
