package com.sporthub.common.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockFilterChain;
import static org.assertj.core.api.Assertions.*;

class ManagementAccessFilterTest {
    @Test void blocksManagementReadAndWriteEvenWithSpoofedAdminHeaders() throws Exception {
        for (String path : java.util.List.of("/actuator", "/actuator/info", "/actuator/env", "/actuator/gateway/routes")) {
            for (String method : java.util.List.of("GET", "POST", "DELETE", "PUT", "PATCH", "HEAD", "OPTIONS")) {
                var request = new MockHttpServletRequest(method, path);
                request.addHeader("X-User-Role", "ADMIN");
                var response = new MockHttpServletResponse(); var chain = new MockFilterChain();
                new ManagementAccessFilter().doFilter(request, response, chain);
                assertThat(response.getStatus()).isEqualTo(404); assertThat(chain.getRequest()).isNull();
            }
        }
    }
    @Test void allowsReadOnlyHealthAndPreventsApiCaching() throws Exception {
        for (String method : java.util.List.of("GET", "HEAD")) {
            var chain = new MockFilterChain();
            new ManagementAccessFilter().doFilter(new MockHttpServletRequest(method, "/actuator/health"),
                    new MockHttpServletResponse(), chain);
            assertThat(chain.getRequest()).isNotNull();
        }
        var response = new MockHttpServletResponse(); var chain = new MockFilterChain();
        new ManagementAccessFilter().doFilter(new MockHttpServletRequest("POST", "/api/v1/auth/refresh"), response, chain);
        assertThat(chain.getRequest()).isNotNull(); assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
    }
}
