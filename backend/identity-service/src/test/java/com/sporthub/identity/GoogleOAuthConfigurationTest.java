package com.sporthub.identity;

import com.sporthub.identity.config.GoogleOAuthClientConfig;
import com.sporthub.identity.config.GoogleOAuthSuccessHandler;
import com.sporthub.identity.config.IdentityProperties;
import com.sporthub.identity.config.IdentitySecurityConfig;
import com.sporthub.identity.security.JwtAuthenticationFilter;
import com.sporthub.identity.web.GoogleOAuthController;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.util.UriComponentsBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GoogleOAuthConfigurationTest {
    private final WebApplicationContextRunner contextRunner = new WebApplicationContextRunner()
            .withUserConfiguration(SecurityTestConfig.class)
            .withPropertyValues("sporthub.identity.frontend-base-url=http://localhost:13000/",
                    "GOOGLE_CLIENT_ID=", "GOOGLE_CLIENT_SECRET=");

    @Test
    void missingCredentialsRedirectToAnActionableLoginError() {
        contextRunner.run(context -> mockMvc(context.getSourceApplicationContext())
                .perform(get("/api/v1/auth/oauth2/authorization/google"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("http://localhost:13000/login?authError=google_not_configured")));
    }

    @Test
    void partialCredentialsKeepGoogleUnavailable() {
        contextRunner.withPropertyValues("GOOGLE_CLIENT_ID=test-client")
                .run(context -> mockMvc(context.getSourceApplicationContext())
                        .perform(get("/api/v1/auth/oauth2/authorization/google"))
                        .andExpect(status().isFound())
                        .andExpect(redirectedUrl("http://localhost:13000/login?authError=google_not_configured")));
    }

    @Test
    void configuredGoogleStartsOidcUsingTheFrontendOriginByDefault() {
        assertGoogleAuthorization("http://localhost:3000/api/v1/auth/oauth2/callback/google",
                "sporthub.identity.frontend-base-url=http://localhost:3000/");
    }

    @Test
    void blankRedirectUriUsesTheFrontendOrigin() {
        assertGoogleAuthorization("http://localhost:13000/api/v1/auth/oauth2/callback/google",
                "GOOGLE_REDIRECT_URI=  ");
    }

    @Test
    void explicitRedirectUriIsPreserved() {
        assertGoogleAuthorization("https://sporthub.example.com/api/v1/auth/oauth2/callback/google",
                "GOOGLE_REDIRECT_URI=https://sporthub.example.com/api/v1/auth/oauth2/callback/google");
    }

    private void assertGoogleAuthorization(String expectedCallback, String... properties) {
        contextRunner.withPropertyValues("GOOGLE_CLIENT_ID=test-client", "GOOGLE_CLIENT_SECRET=test-secret")
                .withPropertyValues(properties)
                .run(context -> {
                    var result = mockMvc(context.getSourceApplicationContext())
                            .perform(get("/api/v1/auth/oauth2/authorization/google"))
                            .andExpect(status().isFound())
                            .andReturn();
                    String location = result.getResponse().getRedirectedUrl();
                    assertThat(location).startsWith("https://accounts.google.com/o/oauth2/v2/auth?");
                    var query = UriComponentsBuilder.fromUriString(location).build().getQueryParams();
                    assertThat(query.getFirst("redirect_uri")).isEqualTo(expectedCallback);
                    assertThat(query.getFirst("response_type")).isEqualTo("code");
                    assertThat(query.getFirst("scope")).contains("openid", "profile", "email");
                    assertThat(query.getFirst("state")).isNotBlank();
                    assertThat(result.getRequest().getSession(false)).isNotNull();
                });
    }

    private MockMvc mockMvc(org.springframework.web.context.WebApplicationContext context) {
        return MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableWebSecurity
    @EnableWebMvc
    @EnableConfigurationProperties(IdentityProperties.class)
    @Import({IdentitySecurityConfig.class, GoogleOAuthClientConfig.class, GoogleOAuthController.class})
    static class SecurityTestConfig {
        @Bean
        JwtAuthenticationFilter jwtAuthenticationFilter() {
            // Public OAuth requests have no Bearer token, so no JWT collaborators are invoked.
            return new JwtAuthenticationFilter(null, null);
        }

        @Bean
        GoogleOAuthSuccessHandler googleOAuthSuccessHandler(IdentityProperties properties) {
            return new GoogleOAuthSuccessHandler(null, properties);
        }
    }
}
