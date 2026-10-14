package com.sporthub.common.security;

import com.sporthub.common.exception.UnauthorizedException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class RemoteIdentityTest {
    @Test void ignoresSpoofedHeadersAndRequiresBearerCredentials() {
        var identity = new RemoteIdentity(RestClient.create("http://identity.test"));
        var request = new MockHttpServletRequest();
        request.addHeader("X-User-Id", "victim"); request.addHeader("X-User-Role", "ADMIN");
        request.addHeader("X-User-Permissions", "*");
        assertThatThrownBy(() -> identity.current(request)).isInstanceOf(UnauthorizedException.class);
    }

    @Test void usesLiveIdentityRolesAndBindingsAndCachesOnlyWithinTheRequest() {
        var builder = RestClient.builder().baseUrl("http://identity.test");
        var server = MockRestServiceServer.bindTo(builder).build();
        var identity = new RemoteIdentity(builder.build());
        String id = java.util.UUID.randomUUID().toString();
        server.expect(requestTo("http://identity.test/api/v1/users/me/access"))
                .andExpect(header("Authorization", "Bearer test-access"))
                .andRespond(withSuccess("{\"data\":{\"id\":\"" + id
                        + "\",\"fullName\":\"User\",\"roles\":[\"CUSTOMER\"],\"facilityBindings\":[]}}", MediaType.APPLICATION_JSON));
        var request = bearer(); request.addHeader("X-User-Role", "ADMIN");
        var caller = identity.current(request);
        assertThat(caller.id().toString()).isEqualTo(id);
        assertThat(caller.roles()).containsExactly("CUSTOMER");
        assertThat(caller.facilityBindings()).isEmpty();
        assertThat(identity.current(request)).isSameAs(caller);
        server.verify();
    }

    @Test void revokedOrLockedIdentityNeverProducesACaller() {
        var builder = RestClient.builder().baseUrl("http://identity.test");
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(anything()).andRespond(withStatus(HttpStatus.UNAUTHORIZED)
                .body("sensitive-upstream-credential"));
        var identity = new RemoteIdentity(builder.build());
        assertThatThrownBy(() -> identity.current(bearer())).isInstanceOf(UnauthorizedException.class)
                .hasMessage("Session is invalid or expired");
        server.verify();
    }

    @Test void malformedContextFailsClosedAndOutageIsNotReportedAsExpiredToken() {
        for (String body : java.util.List.of("{}", "{\"data\":{\"id\":\"bad\",\"roles\":[],\"facilityBindings\":[]}}")) {
            var builder = RestClient.builder().baseUrl("http://identity.test");
            var server = MockRestServiceServer.bindTo(builder).build();
            server.expect(anything()).andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
            var identity = new RemoteIdentity(builder.build());
            assertThatThrownBy(() -> identity.current(bearer())).isInstanceOf(UnauthorizedException.class);
        }
        var builder = RestClient.builder().baseUrl("http://identity.test");
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(anything()).andRespond(withServerError().body("private upstream body"));
        var identity = new RemoteIdentity(builder.build());
        assertThatThrownBy(() -> identity.current(bearer()))
                .isInstanceOfSatisfying(org.springframework.web.server.ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode().value()).isEqualTo(503))
                .hasMessageNotContaining("private upstream body");
    }

    private MockHttpServletRequest bearer() {
        var r = new MockHttpServletRequest(); r.addHeader("Authorization", "Bearer test-access"); return r;
    }
}
