package com.sporthub.transfer;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.*;
import com.sporthub.common.exception.GlobalExceptionHandler;
import com.sporthub.common.security.RemoteIdentity;
import com.sporthub.common.security.RemoteIdentity.Caller;
import com.sporthub.transfer.service.PeerMoneyCommands;
import com.sporthub.transfer.web.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class PeerMoneyHttpTest {
  @Test
  void directReturnCannotChooseBankAssuranceOwnerFeeRecipientOrServerReturnId() throws Exception {
    var money = mock(PeerMoneyCommands.class);
    var identity = mock(RemoteIdentity.class);
    var actor = new Caller(UUID.randomUUID(), "A", Set.of("CUSTOMER"), Map.of());
    when(identity.current(any())).thenReturn(actor);
    var json =
        new ObjectMapper()
            .findAndRegisterModules()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    var http =
        MockMvcBuilders.standaloneSetup(new PeerMoneyController(money, identity))
            .setMessageConverters(new MappingJackson2HttpMessageConverter(json))
            .setControllerAdvice(new GlobalExceptionHandler())
            .addFilters(new TransferPrivacyFilter())
            .build();
    UUID id = UUID.randomUUID(), evidence = UUID.randomUUID();
    String hash = "a".repeat(64);
    String base = "/api/v1/transfers/acquisitions/" + id;
    String input =
        "{\"termsHash\":\""
            + hash
            + "\",\"amount\":50000,\"bankTransactionReference\":\"A RETURN\",\"evidenceIds\":[\""
            + evidence
            + "\"]";
    for (String field :
        List.of("confirmationMethod", "feeBps", "refundRate", "recipientId", "returnId"))
      http.perform(
              post(base + "/returns")
                  .contentType("application/json")
                  .header("Idempotency-Key", "key")
                  .content(input + ",\"" + field + "\":\"forged\"}"))
          .andExpect(status().isBadRequest());
    http.perform(
            post(base + "/returns")
                .contentType("application/json")
                .header("Idempotency-Key", "key")
                .content((input + "}").replace("50000", "0.5")))
        .andExpect(status().isBadRequest());
    verifyNoInteractions(money);
    when(money.returned(eq(id), any(), eq("key"), eq(actor)))
        .thenReturn(json.createObjectNode().put("phase", "ABORTED_A_RETAINS"));
    http.perform(
            post(base + "/returns")
                .contentType("application/json")
                .header("Idempotency-Key", "key")
                .header("X-User-Id", UUID.randomUUID().toString())
                .content(input + "}"))
        .andExpect(status().isOk())
        .andExpect(
            header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")));
    verify(money).returned(eq(id), any(), eq("key"), eq(actor));
  }
}
