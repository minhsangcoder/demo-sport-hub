package com.sporthub.common.money;

import static org.assertj.core.api.Assertions.*;
import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.Test;

class StrictPeerInputsTest {
  @Test
  void moneyActorAndBackdatedAttendanceFieldsAreRejectedEvenIfTheApplicationIgnoresOtherUnknownJsonFields() throws Exception {
    var json=new ObjectMapper().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    String resolution="{\"version\":2,\"outcome\":\"SELLER_RETAINS\",\"reason\":\"Chỉ phân xử quyền sử dụng\",\"evidenceIds\":[]}";
    assertThatCode(()->json.readValue(resolution,PeerResaleContract.Resolution.class)).doesNotThrowAnyException();
    for(String field:new String[]{"returnAmount","moneyDisposition","recipientId","paymentId","sellerId"}) {
      var forged=(com.fasterxml.jackson.databind.node.ObjectNode)json.readTree(resolution);
      forged.put(field,"attacker-controlled");
      assertThatThrownBy(()->json.treeToValue(forged,PeerResaleContract.Resolution.class)).isInstanceOf(JsonMappingException.class);
    }
    assertThatThrownBy(()->json.readValue("{\"party\":\"BUYER\",\"reason\":\"Có mặt\",\"observedAt\":\"2026-10-26T01:00:00Z\"}",PeerResaleContract.Presence.class))
        .isInstanceOf(JsonMappingException.class);
    assertThatThrownBy(()->json.readValue("{\"version\":1,\"termsHash\":\""+"a".repeat(64)+"\",\"holderId\":\"buyer\"}",PeerResaleContract.Command.class))
        .isInstanceOf(JsonMappingException.class);
  }
}
