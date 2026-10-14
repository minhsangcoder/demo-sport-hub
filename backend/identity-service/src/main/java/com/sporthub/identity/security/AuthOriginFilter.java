package com.sporthub.identity.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sporthub.common.dto.ErrorResponse;
import com.sporthub.identity.config.IdentityProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Set;

/** Rejects browser-originated auth mutations from origins outside deployment configuration. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class AuthOriginFilter extends OncePerRequestFilter {
    private final Set<String> origins;
    private final ObjectMapper json;

    public AuthOriginFilter(IdentityProperties properties, ObjectMapper json,
            @Value("${CORS_ALLOWED_ORIGIN:http://localhost:3000}") String corsOrigin) {
        this.json = json;
        origins = Set.copyOf(List.of(origin(properties.frontendBaseUrl()), origin(corsOrigin)));
    }

    private String origin(String value) {
        URI uri = URI.create(value);
        if (uri.getHost() == null || uri.getUserInfo() != null
                || !(uri.getScheme().equals("https") || uri.getScheme().equals("http")))
            throw new IllegalArgumentException("Auth origins must be HTTP(S) URLs without credentials");
        return uri.getScheme() + "://" + uri.getRawAuthority();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws IOException, ServletException {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        String origin = request.getHeader("Origin");
        if (request.getMethod().equals("POST") && path.startsWith("/api/v1/auth/")
                && !path.startsWith("/api/v1/auth/oauth2/") && origin != null && !origins.contains(origin)) {
            response.setStatus(403);
            response.setContentType("application/json");
            json.writeValue(response.getOutputStream(), ErrorResponse.builder().status(403)
                    .code("IDENTITY-ORIGIN-FORBIDDEN").message("This origin is not permitted for authentication")
                    .build());
            return;
        }
        chain.doFilter(request, response);
    }
}
