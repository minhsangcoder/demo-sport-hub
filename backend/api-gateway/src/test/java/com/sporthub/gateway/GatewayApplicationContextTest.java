package com.sporthub.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.data.redis.password=test-only",
                "jwt.secret=test-only-secret-that-is-at-least-32-bytes-long"
        })
class GatewayApplicationContextTest {

    @Test
    void applicationContextStarts() {
        // Context startup is the assertion.
    }

    @org.springframework.boot.test.web.server.LocalServerPort int port;
    @Test void managementHandlersCannotBeReachedByAnonymousOrSpoofedAdminRequests() {
        var client=org.springframework.test.web.reactive.server.WebTestClient.bindToServer().baseUrl("http://localhost:"+port).build();
        for(var method:java.util.List.of(org.springframework.http.HttpMethod.GET,org.springframework.http.HttpMethod.POST,org.springframework.http.HttpMethod.DELETE)) {
            client.method(method).uri("/actuator/gateway/routes").header("X-User-Role","ADMIN").exchange().expectStatus().isNotFound();
        }
        client.get().uri("/actuator/env").exchange().expectStatus().isNotFound();
    }

}
