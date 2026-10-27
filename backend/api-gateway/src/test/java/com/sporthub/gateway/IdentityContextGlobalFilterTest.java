package com.sporthub.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import com.sporthub.gateway.config.GatewaySecurityProperties;
import com.sporthub.gateway.filter.IdentityContextGlobalFilter;
import com.sporthub.gateway.routing.PublicRouteMatcher;
import com.sporthub.gateway.security.GatewayAuthenticationResolver;
import com.sporthub.gateway.security.GatewayIdentity;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

class IdentityContextGlobalFilterTest {

  @Test
  void removesEverySpoofableIdentityHeaderFromAnonymousRequest() {
    MockServerHttpRequest request =
        MockServerHttpRequest.get("/api/v1/auth/login")
            .header("Authorization", "Bearer unvalidated-token")
            .header("X-User-Id", "spoofed-user")
            .header("X-User-Role", "ADMIN")
            .header("X-User-Email", "attacker@example.com")
            .header("X-User-Permissions", "*")
            .header("X-User-Facility", "spoofed-facility")
            .header("X-Facility-Bindings", "[]")
            .build();
    MockServerWebExchange exchange = MockServerWebExchange.from(request);
    AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();
    GatewayFilterChain chain =
        captured -> {
          forwarded.set(captured);
          return Mono.empty();
        };
    GatewayAuthenticationResolver noAuthentication = ignored -> Mono.empty();

    GatewaySecurityProperties properties = new GatewaySecurityProperties();
    properties.setPublicPaths(java.util.List.of("/api/v1/auth/login"));
    new IdentityContextGlobalFilter(noAuthentication, new PublicRouteMatcher(properties))
        .filter(exchange, chain)
        .block();

    assertThat(forwarded.get()).isNotNull();
    IdentityContextGlobalFilter.SPOOFABLE_IDENTITY_HEADERS.forEach(
        header ->
            assertThat(forwarded.get().getRequest().getHeaders().containsKey(header)).isFalse());
    assertThat(forwarded.get().getRequest().getHeaders().getFirst("Authorization"))
        .isEqualTo("Bearer unvalidated-token");
  }

  @Test
  void rejectsAnonymousProtectedRequest() {
    MockServerWebExchange exchange =
        MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/users/me").build());
    GatewaySecurityProperties properties = new GatewaySecurityProperties();
    properties.setPublicPaths(java.util.List.of("/api/v1/auth/login"));

    new IdentityContextGlobalFilter(ignored -> Mono.empty(), new PublicRouteMatcher(properties))
        .filter(
            exchange,
            ignored -> Mono.error(new AssertionError("protected request must not be forwarded")))
        .block();

    assertThat(exchange.getResponse().getStatusCode().value()).isEqualTo(401);
  }

  @Test
  void replacesSpoofedHeadersOnlyWithValidatedIdentity() {
    UUID userId = UUID.randomUUID();
    MockServerWebExchange exchange =
        MockServerWebExchange.from(
            MockServerHttpRequest.get("/api/v1/users/me")
                .header("X-User-Id", "attacker")
                .header("X-User-Role", "ADMIN")
                .header("X-User-Permissions", "*")
                .build());
    GatewaySecurityProperties properties = new GatewaySecurityProperties();
    properties.setPublicPaths(List.of("/api/v1/auth/login"));
    AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();

    new IdentityContextGlobalFilter(
            ignored ->
                Mono.just(
                    new GatewayIdentity(
                        userId, "member@sporthub.local", List.of("CUSTOMER", "OWNER"))),
            new PublicRouteMatcher(properties))
        .filter(
            exchange,
            captured -> {
              forwarded.set(captured);
              return Mono.empty();
            })
        .block();

    assertThat(forwarded.get().getRequest().getHeaders().getFirst("X-User-Id"))
        .isEqualTo(userId.toString());
    assertThat(forwarded.get().getRequest().getHeaders().getFirst("X-User-Role"))
        .isEqualTo("CUSTOMER,OWNER");
    assertThat(forwarded.get().getRequest().getHeaders().containsKey("X-User-Permissions"))
        .isFalse();
  }

  @Test
  void expiredAccessTokenDoesNotBlockRefreshOrLogoutButStillBlocksPrivateRoutes() {
    var properties = new GatewaySecurityProperties();
    properties.setPublicPaths(List.of("/api/v1/auth/refresh", "/api/v1/auth/logout"));
    var filter =
        new IdentityContextGlobalFilter(
            ignored -> Mono.error(new IllegalArgumentException("expired access")),
            new PublicRouteMatcher(properties));
    for (String path : List.of("/api/v1/auth/refresh", "/api/v1/auth/logout")) {
      var exchange =
          MockServerWebExchange.from(
              MockServerHttpRequest.post(path)
                  .header("Authorization", "Bearer expired")
                  .header("Cookie", "sporthub_refresh=valid-refresh")
                  .header("X-Internal-Role", "ADMIN")
                  .build());
      var forwarded = new AtomicReference<ServerWebExchange>();
      filter
          .filter(
              exchange,
              next -> {
                forwarded.set(next);
                return Mono.empty();
              })
          .block();
      assertThat(forwarded.get()).isNotNull();
      assertThat(forwarded.get().getRequest().getHeaders().getFirst("Cookie"))
          .isEqualTo("sporthub_refresh=valid-refresh");
      assertThat(forwarded.get().getRequest().getHeaders().containsKey("X-Internal-Role"))
          .isFalse();
    }
    var privateRequest =
        MockServerWebExchange.from(
            MockServerHttpRequest.get("/api/v1/admin/accounts")
                .header("Authorization", "Bearer expired")
                .build());
    filter
        .filter(privateRequest, next -> Mono.error(new AssertionError("must not forward")))
        .block();
    assertThat(privateRequest.getResponse().getStatusCode().value()).isEqualTo(401);
  }

  @Test
  void identityOutageReturns503WithoutForwardingOrImpersonatingExpiredCredentials() {
    var properties = new GatewaySecurityProperties();
    properties.setPublicPaths(List.of("/api/v1/auth/refresh"));
    var filter =
        new IdentityContextGlobalFilter(
            ignored ->
                Mono.error(new java.util.concurrent.TimeoutException("private upstream detail")),
            new PublicRouteMatcher(properties));
    var exchange =
        MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/users/me").build());
    filter.filter(exchange, next -> Mono.error(new AssertionError("must not forward"))).block();
    assertThat(exchange.getResponse().getStatusCode().value()).isEqualTo(503);
    assertThat(exchange.getResponse().getBodyAsString().block())
        .doesNotContain("private upstream detail");
  }

  @Test
  void internalProtocolsNeverReachThePublicGatewayEvenWithAuthenticationOrEncodedSegments() {
    var properties = new GatewaySecurityProperties();
    properties.setPublicPaths(List.of("/api/v1/**"));
    var filter =
        new IdentityContextGlobalFilter(
            ignored -> Mono.error(new AssertionError("must not authenticate service-only route")),
            new PublicRouteMatcher(properties));
    for (String path :
        List.of(
            "/api/v1/internal/bookings/bank-profiles/status",
            "/api/v1/transfers/internal/peer/one/evidence-references",
            "/api/v1/transfers/%69nternal/peer/one/evidence-references",
            "/api/v1/transfers/%2569nternal/peer/one/evidence-references",
            "/api/v1/transfers/internal;ignored=1/peer/one/evidence-references")) {
      var exchange =
          MockServerWebExchange.from(
              MockServerHttpRequest.method(
                      org.springframework.http.HttpMethod.POST, java.net.URI.create(path))
                  .header("Authorization", "Bearer session")
                  .header("X-Service-Signature", "forged")
                  .build());
      filter
          .filter(exchange, ignored -> Mono.error(new AssertionError("must not forward")))
          .block();
      assertThat(exchange.getResponse().getStatusCode().value()).isEqualTo(404);
    }
  }

  @Test
  void serviceHeadersAreRemovedFromOtherwiseLegitimateUserRoutes() {
    var properties = new GatewaySecurityProperties();
    properties.setPublicPaths(List.of("/api/v1/auth/login"));
    var exchange =
        MockServerWebExchange.from(
            MockServerHttpRequest.post("/api/v1/auth/login")
                .header("X-Service-Time", "123")
                .header("x-service-signature", "forged")
                .build());
    var filter =
        new IdentityContextGlobalFilter(
            ignored -> Mono.empty(), new PublicRouteMatcher(properties));
    filter
        .filter(
            exchange,
            next -> {
              assertThat(next.getRequest().getHeaders().keySet())
                  .noneMatch(name -> name.toLowerCase().startsWith("x-service-"));
              return Mono.empty();
            })
        .block();
  }
}
