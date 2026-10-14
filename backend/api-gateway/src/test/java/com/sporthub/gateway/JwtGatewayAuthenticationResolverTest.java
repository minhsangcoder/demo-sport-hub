package com.sporthub.gateway;

import com.sporthub.gateway.config.GatewayJwtProperties;
import com.sporthub.gateway.security.GatewayIdentity;
import com.sporthub.gateway.security.JwtGatewayAuthenticationResolver;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtGatewayAuthenticationResolverTest {
    private static final String SECRET = "test-only-secret-that-is-at-least-32-bytes-long";

    @Test
    void validatesTokenAndReturnsAllRoles() {
        UUID userId = UUID.randomUUID();
        String token = Jwts.builder()
                .subject(userId.toString())
                .claim("email", "member@sporthub.local")
                .claim("roles", List.of("CUSTOMER", "OWNER"))
                .expiration(Date.from(Instant.now().plusSeconds(60)))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/users/me")
                .header("Authorization", "Bearer " + token).build());

        GatewayIdentity identity = new JwtGatewayAuthenticationResolver(new GatewayJwtProperties(SECRET),
                org.springframework.web.reactive.function.client.WebClient.create())
                .validateToken(token).block();

        assertThat(identity.userId()).isEqualTo(userId);
        assertThat(identity.roles()).containsExactly("CUSTOMER", "OWNER");
    }

    @Test
    void rejectsExpiredToken() {
        String token = Jwts.builder()
                .subject(UUID.randomUUID().toString())
                .claim("roles", List.of("CUSTOMER"))
                .expiration(Date.from(Instant.now().minusSeconds(1)))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/users/me")
                .header("Authorization", "Bearer " + token).build());

        assertThatThrownBy(() -> new JwtGatewayAuthenticationResolver(new GatewayJwtProperties(SECRET),
                org.springframework.web.reactive.function.client.WebClient.create())
                .validateToken(token).block()).isInstanceOf(RuntimeException.class);
    }

    @Test void resolverUsesLiveRolesAndRejectsRevokedAndLockedSessions() {
        UUID user=UUID.randomUUID();String token=Jwts.builder().subject(user.toString()).claim("roles",List.of("ADMIN"))
            .expiration(Date.from(Instant.now().plusSeconds(60))).signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8))).compact();
        var request=MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/admin/accounts").header("Authorization","Bearer "+token).build());
        var live=org.springframework.web.reactive.function.client.WebClient.builder().exchangeFunction(r->reactor.core.publisher.Mono.just(org.springframework.web.reactive.function.client.ClientResponse.create(org.springframework.http.HttpStatus.OK)
            .header("Content-Type","application/json").body("{\"data\":{\"id\":\""+user+"\",\"status\":\"ACTIVE\",\"roles\":[\"CUSTOMER\"]}}").build())).build();
        assertThat(new JwtGatewayAuthenticationResolver(new GatewayJwtProperties(SECRET),live).resolve(request).block().roles()).containsExactly("CUSTOMER");
        for(var status:List.of(org.springframework.http.HttpStatus.UNAUTHORIZED,org.springframework.http.HttpStatus.FORBIDDEN)) {
            var rejected=org.springframework.web.reactive.function.client.WebClient.builder().exchangeFunction(r->reactor.core.publisher.Mono.just(org.springframework.web.reactive.function.client.ClientResponse.create(status).build())).build();
            assertThatThrownBy(()->new JwtGatewayAuthenticationResolver(new GatewayJwtProperties(SECRET),rejected).resolve(request).block()).isInstanceOf(RuntimeException.class);
        }
    }
}
