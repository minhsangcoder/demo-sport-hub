package com.sporthub.identity.config;

import com.sporthub.identity.exception.IdentityException;
import com.sporthub.identity.service.AuthService;
import com.sporthub.identity.service.GoogleIdentity;
import com.sporthub.identity.service.RequestMetadata;
import com.sporthub.identity.service.SessionTokens;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;

import java.io.IOException;
import java.time.Duration;

@Component
@RequiredArgsConstructor
public class GoogleOAuthSuccessHandler implements AuthenticationSuccessHandler, AuthenticationFailureHandler {
    private final AuthService authService;
    private final IdentityProperties properties;

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request, HttpServletResponse response, Authentication authentication)
            throws IOException, ServletException {
        if (!(authentication.getPrincipal() instanceof OidcUser google)) {
            response.sendRedirect(frontendUrl("/login?authError=google"));
            return;
        }

        try {
            Object verified = google.getClaims().get("email_verified");
            SessionTokens tokens = authService.loginWithGoogle(new GoogleIdentity(
                    google.getSubject(),
                    google.getEmail(),
                    Boolean.TRUE.equals(verified),
                    google.getFullName(),
                    google.getPicture()), RequestMetadata.from(request));
            response.addHeader(HttpHeaders.SET_COOKIE, refreshCookie(tokens.refreshToken()).toString());
            response.sendRedirect(frontendUrl("/auth/callback"));
        } catch (IdentityException exception) {
            String error = "IDENTITY-GOOGLE-EMAIL-UNVERIFIED".equals(exception.getCode())
                    ? "google_email_unverified" : "google_account_unavailable";
            response.sendRedirect(frontendUrl("/login?authError=" + error));
        }
    }

    @Override
    public void onAuthenticationFailure(
            HttpServletRequest request, HttpServletResponse response,
            org.springframework.security.core.AuthenticationException exception)
            throws IOException, ServletException {
        response.sendRedirect(frontendUrl("/login?authError=google"));
    }

    private ResponseCookie refreshCookie(String token) {
        return ResponseCookie.from("sporthub_refresh", token)
                .httpOnly(true)
                .secure(properties.secureCookie())
                .sameSite("Lax")
                .path("/api/v1/auth")
                .maxAge(Duration.ofDays(7))
                .build();
    }

    private String frontendUrl(String path) {
        String baseUrl = properties.frontendBaseUrl();
        return (baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl) + path;
    }
}
