package com.sporthub.payment.provider;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class PayosClientTest {
  private final ObjectMapper json = new ObjectMapper();
  // Public fixture from payOS signature documentation; not a deployment credential.
  private final String fixtureKey =
      "1a54716c8f0efb2744fb28b6e38b25da7f67a925d98bc1c18bd8faaecadd7675";

  @Test
  void verifiesPublishedNativeFixtureWithoutDemoTimestampWindow() throws Exception {
    var data =
        json.readTree(
            """
{"orderCode":123,"amount":3000,"description":"VQRIO123","accountNumber":"12345678","reference":"TF230204212323",
"transactionDateTime":"2023-02-04 18:25:00","currency":"VND","paymentLinkId":"124c33293c43417ab7879e14c8d9eb18",
"code":"00","desc":"Thành công","counterAccountBankId":"","counterAccountBankName":"","counterAccountName":"",
"counterAccountNumber":"","virtualAccountName":"","virtualAccountNumber":""}
""");
    PayosSignature.verify(
        data, "412e915d2871504ed31be63c8f62a149a4410d34c4c42affc9006ef9917eaa03", fixtureKey);
    ((com.fasterxml.jackson.databind.node.ObjectNode) data).put("amount", 3001);
    assertThatThrownBy(
            () ->
                PayosSignature.verify(
                    data,
                    "412e915d2871504ed31be63c8f62a149a4410d34c4c42affc9006ef9917eaa03",
                    fixtureKey))
        .hasMessageContaining("Chữ ký");
    assertThat(
            PayosSignature.canonical(
                json.readTree("{\"z\":null,\"a\":[{\"z\":1,\"a\":2}],\"n\":\"undefined\"}")))
        .isEqualTo("a=[{\"a\":2,\"z\":1}]&n=&z=");
  }

  @Test
  void descriptionFitsNineCharactersAtBoundariesAndIsInjective() {
    assertThat(PayosClient.description(1)).isEqualTo("SH0000001");
    assertThat(PayosClient.description(PayosClient.MAX_ORDER_CODE)).isEqualTo("SHZZZZZZZ");
    var seen = new java.util.HashSet<String>();
    for (long code = 1; code <= 10000; code++) {
      var value = PayosClient.description(code);
      assertThat(value).matches("[A-Z0-9]{9}");
      assertThat(seen.add(value)).isTrue();
    }
    assertThatThrownBy(() -> PayosClient.description(0))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> PayosClient.description(PayosClient.MAX_ORDER_CODE + 1))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void signsExactShortDescriptionAndChecksResponseMoneyAndCheckoutOrigin() throws Exception {
    var builder = RestClient.builder().baseUrl("https://api-merchant.payos.vn");
    var server = MockRestServiceServer.bindTo(builder).build();
    var client =
        new PayosClient(builder.build(), "https://sporthub.example", "https://gateway.example");
    var credentials = new PayosCredentials("synthetic-client", "synthetic-api", fixtureKey);
    UUID payment = UUID.randomUUID();
    var order =
        new PayosClient.Order(
            payment,
            1,
            new BigDecimal("100000"),
            "SH0000001",
            Instant.parse("2027-01-01T00:00:00Z"));
    var data =
        json.readTree(
            """
{"orderCode":1,"amount":100000,"description":"SH0000001","currency":"VND","bin":"970436","accountNumber":"00012345",
"accountName":"SYNTHETIC OWNER","paymentLinkId":"link1","qrCode":"native-qr","checkoutUrl":"https://pay.payos.vn/web/link1"}
""");
    String response = json.createObjectNode().put("code", "00").set("data", data).toString();
    var envelope = (com.fasterxml.jackson.databind.node.ObjectNode) json.readTree(response);
    envelope.put("signature", PayosSignature.sign(data, fixtureKey));
    server
        .expect(requestTo("https://api-merchant.payos.vn/v2/payment-requests"))
        .andExpect(header("x-api-key", "synthetic-api"))
        .andExpect(
            request -> {
              var outgoing =
                  json.readTree(
                      ((org.springframework.mock.http.client.MockClientHttpRequest) request)
                          .getBodyAsString());
              assertThat(outgoing.path("description").asText()).isEqualTo("SH0000001");
              var signed = outgoing.deepCopy();
              ((com.fasterxml.jackson.databind.node.ObjectNode) signed)
                  .remove(java.util.List.of("signature", "expiredAt"));
              assertThat(outgoing.path("signature").asText())
                  .isEqualTo(PayosSignature.sign(signed, fixtureKey));
              assertThat(outgoing.path("expiredAt").longValue())
                  .isEqualTo(order.expiresAt().getEpochSecond());
            })
        .andRespond(withSuccess(envelope.toString(), MediaType.APPLICATION_JSON));
    assertThat(client.create(order, credentials).path("paymentLinkId").asText()).isEqualTo("link1");
    server.verify();
    assertThat(credentials.toString()).doesNotContain("synthetic-api", fixtureKey);
  }

  @Test
  void rejectsSignedWrongAmountAndHostWithoutEchoingProviderBody() throws Exception {
    for (String change : new String[] {"amount", "checkoutUrl"}) {
      var builder = RestClient.builder().baseUrl("https://api-merchant.payos.vn");
      var server = MockRestServiceServer.bindTo(builder).build();
      var client =
          new PayosClient(builder.build(), "https://sporthub.example", "https://gateway.example");
      var data =
          json.createObjectNode()
              .put("orderCode", 2)
              .put("amount", 100)
              .put("description", "SH0000002")
              .put("currency", "VND")
              .put("paymentLinkId", "link")
              .put("qrCode", "qr")
              .put("checkoutUrl", "https://pay.payos.vn/web/link");
      if (change.equals("amount")) data.put("amount", 101);
      else data.put("checkoutUrl", "https://pay.payos.vn.attacker.example/web/link");
      var response =
          json.createObjectNode()
              .put("code", "00")
              .put("signature", PayosSignature.sign(data, fixtureKey));
      response.set("data", data);
      server
          .expect(requestTo("https://api-merchant.payos.vn/v2/payment-requests"))
          .andRespond(withSuccess(response.toString(), MediaType.APPLICATION_JSON));
      assertThatThrownBy(
              () ->
                  client.create(
                      new PayosClient.Order(
                          UUID.randomUUID(),
                          2,
                          BigDecimal.valueOf(100),
                          "SH0000002",
                          Instant.parse("2027-01-01T00:00:00Z")),
                      new PayosCredentials("client", "api", fixtureKey)))
          .isInstanceOf(PayosClient.Failure.class);
      server.verify();
    }
  }

  @Test
  void queryAndCancelRequireNativeSignatureAndNeverTreatBusinessRejectionAsNotFound() {
    var builder = RestClient.builder().baseUrl("https://api-merchant.payos.vn");
    var server = MockRestServiceServer.bindTo(builder).build();
    var client =
        new PayosClient(builder.build(), "https://sporthub.example", "https://gateway.example");
    var credentials = new PayosCredentials("client", "api", fixtureKey);
    var data =
        json.createObjectNode()
            .put("id", "link")
            .put("orderCode", 12)
            .put("amount", 100)
            .put("amountPaid", 0)
            .put("status", "CANCELLED");
    data.putArray("transactions");
    var envelope =
        json.createObjectNode()
            .put("code", "00")
            .put("signature", PayosSignature.sign(data, fixtureKey));
    envelope.set("data", data);
    server
        .expect(requestTo("https://api-merchant.payos.vn/v2/payment-requests/12"))
        .andRespond(withSuccess(envelope.toString(), MediaType.APPLICATION_JSON));
    server
        .expect(requestTo("https://api-merchant.payos.vn/v2/payment-requests/12/cancel"))
        .andExpect(method(org.springframework.http.HttpMethod.POST))
        .andRespond(withSuccess(envelope.toString(), MediaType.APPLICATION_JSON));
    server
        .expect(requestTo("https://api-merchant.payos.vn/v2/payment-requests/13"))
        .andRespond(
            withSuccess(
                "{\"code\":\"231\",\"desc\":\"do not echo sensitive provider message\"}",
                MediaType.APPLICATION_JSON));
    assertThat(client.query(12, credentials).path("amountPaid").asInt()).isZero();
    assertThat(client.cancel(12, credentials).path("status").asText()).isEqualTo("CANCELLED");
    assertThatThrownBy(() -> client.query(13, credentials))
        .isInstanceOf(PayosClient.Failure.class)
        .hasMessageNotContaining("sensitive")
        .hasMessageContaining("PROVIDER_REJECTED");
    server.verify();
  }
}
