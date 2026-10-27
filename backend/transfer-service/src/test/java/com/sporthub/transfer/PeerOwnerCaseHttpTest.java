package com.sporthub.transfer;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.*;
import com.sporthub.common.exception.GlobalExceptionHandler;
import com.sporthub.common.security.RemoteIdentity;
import com.sporthub.common.security.RemoteIdentity.Caller;
import com.sporthub.transfer.service.PeerBookingClient;
import com.sporthub.transfer.web.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class PeerOwnerCaseHttpTest {
  @Test
  void onlyOwnerCanSubmitUsageOutcomesAndMoneyBackdateFieldsAreRejected() throws Exception {
    var booking = mock(PeerBookingClient.class);
    var identity = mock(RemoteIdentity.class);
    var json =
        new ObjectMapper()
            .findAndRegisterModules()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    UUID facility = UUID.randomUUID(), id = UUID.randomUUID();
    var owner = new Caller(UUID.randomUUID(), "Owner", Set.of("OWNER"), Map.of());
    when(identity.current(any())).thenReturn(owner);
    var http =
        MockMvcBuilders.standaloneSetup(new PeerOwnerCaseController(booking, identity))
            .setMessageConverters(new MappingJackson2HttpMessageConverter(json))
            .setControllerAdvice(new GlobalExceptionHandler())
            .addFilters(new TransferPrivacyFilter())
            .build();
    String base = "/api/v1/transfers/facilities/" + facility + "/cases/" + id;
    for (String field : List.of("returnAmount", "moneyDisposition", "recipientId"))
      http.perform(
              post(base + "/resolve")
                  .contentType("application/json")
                  .header("Idempotency-Key", "key")
                  .content(
                      "{\"version\":2,\"outcome\":\"SELLER_RETAINS\",\"reason\":\"Giữ quyền"
                          + " sân\",\"evidenceIds\":[],\""
                          + field
                          + "\":\"forged\"}"))
          .andExpect(status().isBadRequest());
    http.perform(
            post(base + "/presence")
                .contentType("application/json")
                .header("Idempotency-Key", "key")
                .content(
                    "{\"party\":\"BUYER\",\"reason\":\"Có"
                        + " mặt\",\"observedAt\":\"2026-01-01T00:00:00Z\"}"))
        .andExpect(status().isBadRequest());
    verifyNoInteractions(booking);
    when(identity.current(any()))
        .thenReturn(new Caller(UUID.randomUUID(), "Admin", Set.of("ADMIN"), Map.of()));
    http.perform(get(base)).andExpect(status().isForbidden());
    http.perform(
            post(base + "/resolve")
                .contentType("application/json")
                .header("Idempotency-Key", "key")
                .content(
                    "{\"version\":2,\"outcome\":\"SELLER_RETAINS\",\"reason\":\"Giữ quyền"
                        + " sân\",\"evidenceIds\":[]}"))
        .andExpect(status().isForbidden());
    verifyNoInteractions(booking);
    var staff =
        new Caller(
            UUID.randomUUID(),
            "Staff",
            Set.of("STAFF"),
            Map.of(facility, Set.of("BOOKING_CHECK_IN")));
    when(identity.current(any())).thenReturn(staff);
    http.perform(
            post(base + "/resolve")
                .contentType("application/json")
                .header("Idempotency-Key", "key")
                .content(
                    "{\"version\":2,\"outcome\":\"SELLER_RETAINS\",\"reason\":\"Giữ quyền"
                        + " sân\",\"evidenceIds\":[]}"))
        .andExpect(status().isForbidden());
    verifyNoInteractions(booking);
    when(booking.caseCommand(
            eq(facility), eq(id), eq("presence"), any(), eq("key"), eq("Bearer session")))
        .thenReturn(json.createObjectNode().put("phase", "DISPUTED"));
    http.perform(
            post(base + "/presence")
                .contentType("application/json")
                .header("Idempotency-Key", "key")
                .header("Authorization", "Bearer session")
                .content("{\"party\":\"BUYER\",\"reason\":\"Có mặt\"}"))
        .andExpect(status().isOk());
  }
}
