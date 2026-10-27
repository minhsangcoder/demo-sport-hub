package com.sporthub.payment.web;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.sporthub.common.exception.*;
import com.sporthub.common.security.RemoteIdentity;
import com.sporthub.common.security.RemoteIdentity.Caller;
import com.sporthub.payment.service.PeerQrInstructions;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class PeerQrControllerTest {
  @Test
  void imageIsAuthenticatedPrivateAndNeverAProviderRedirect() throws Exception {
    var service = mock(PeerQrInstructions.class);
    var identity = mock(RemoteIdentity.class);
    var buyer = new Caller(UUID.randomUUID(), "B", Set.of("CUSTOMER"), Map.of());
    UUID id = UUID.randomUUID();
    when(identity.current(any())).thenReturn(buyer);
    when(service.image(id, buyer)).thenReturn(new byte[] {1, 2, 3});
    var http =
        MockMvcBuilders.standaloneSetup(new PeerQrController(service, identity))
            .setControllerAdvice(new GlobalExceptionHandler())
            .addFilters(new PaymentPrivateResponseFilter())
            .build();
    String path = "/api/v1/payments/peer-acquisitions/" + id + "/qr";
    http.perform(
            get(path)
                .header("Authorization", "Bearer live-session")
                .header("X-User-Id", UUID.randomUUID().toString()))
        .andExpect(status().isOk())
        .andExpect(content().contentType("image/png"))
        .andExpect(
            header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")))
        .andExpect(header().string("Referrer-Policy", "no-referrer"))
        .andExpect(header().doesNotExist("Location"));
    verify(service).image(id, buyer);
    when(service.image(id, buyer))
        .thenThrow(new ForbiddenException("Bạn không thuộc phiên mua này."));
    http.perform(get(path))
        .andExpect(status().isForbidden())
        .andExpect(
            header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")));
    when(identity.current(any()))
        .thenThrow(new UnauthorizedException("Phiên đăng nhập không hợp lệ."));
    http.perform(get(path)).andExpect(status().isUnauthorized());
  }
}
