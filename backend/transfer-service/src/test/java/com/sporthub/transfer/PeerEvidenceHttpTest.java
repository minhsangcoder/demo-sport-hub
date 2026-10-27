package com.sporthub.transfer;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sporthub.common.exception.GlobalExceptionHandler;
import com.sporthub.common.security.*;
import com.sporthub.common.security.RemoteIdentity.Caller;
import com.sporthub.transfer.service.PeerEvidence;
import com.sporthub.transfer.web.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class PeerEvidenceHttpTest {
  @Test
  void proxyIsPrivateAndMetadataProtocolRequiresServiceSignatureBeforeAnyRead() throws Exception {
    var evidence = mock(PeerEvidence.class);
    var identity = mock(RemoteIdentity.class);
    var buyer = new Caller(UUID.randomUUID(), "B", Set.of("CUSTOMER"), Map.of());
    when(identity.current(any())).thenReturn(buyer);
    UUID id = UUID.randomUUID(), attempt = UUID.randomUUID();
    when(evidence.image(id, buyer, "Bearer session"))
        .thenReturn(new PeerEvidence.Image("image/png", new byte[] {1}));
    String secret = "synthetic-service-secret-at-least-thirty-two-characters";
    var http =
        MockMvcBuilders.standaloneSetup(
                new PeerEvidenceController(evidence, identity, secret, new ObjectMapper()))
            .setControllerAdvice(new GlobalExceptionHandler())
            .addFilters(new TransferPrivacyFilter())
            .build();
    http.perform(
            get("/api/v1/transfers/evidence/" + id)
                .header("Authorization", "Bearer session")
                .header("X-User-Id", UUID.randomUUID().toString()))
        .andExpect(status().isOk())
        .andExpect(content().contentType("image/png"))
        .andExpect(
            header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")))
        .andExpect(header().string("Referrer-Policy", "no-referrer"));
    verify(evidence).image(id, buyer, "Bearer session");
    String path = "/api/v1/transfers/internal/peer/" + attempt + "/evidence-references";
    String body = "{\"ids\":[\"" + id + "\"]}";
    http.perform(
            post(path)
                .contentType("application/json")
                .content(body)
                .header("Authorization", "Bearer session"))
        .andExpect(status().is4xxClientError());
    verify(evidence, never()).references(eq(attempt), anyList());
    when(evidence.references(attempt, List.of(id))).thenReturn(List.of());
    long time = Instant.now().getEpochSecond();
    http.perform(
            post(path)
                .contentType("application/json")
                .content(body)
                .header("X-Service-Time", time)
                .header("X-Service-Signature", ServiceCalls.sign(secret, "POST", path, time, body)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data").isArray());
    verify(evidence).references(attempt, List.of(id));
  }
}
