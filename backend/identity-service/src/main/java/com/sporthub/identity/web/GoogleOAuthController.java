package com.sporthub.identity.web;

import com.sporthub.identity.config.IdentityProperties;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

@RestController
@RequiredArgsConstructor
public class GoogleOAuthController {
    private final IdentityProperties properties;

    // When Google is configured, Spring Security handles this URL before MVC.
    @GetMapping("/api/v1/auth/oauth2/authorization/google")
    public void googleUnavailable(HttpServletResponse response) throws IOException {
        String frontendUrl = properties.frontendBaseUrl().replaceAll("/+$", "");
        response.sendRedirect(frontendUrl + "/login?authError=google_not_configured");
    }
}
