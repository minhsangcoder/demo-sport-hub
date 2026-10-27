package com.sporthub.payment.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.sporthub.common.money.ExactDecimalJson;
import java.math.BigDecimal;
import java.net.URI;
import java.time.*;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.*;

@Component
public class PayosClient {
  public static final long MAX_ORDER_CODE = 78364164095L;

  public record Order(
      UUID paymentId, long orderCode, BigDecimal amount, String description, Instant expiresAt) {}

  public record Receiver(String bankBin, String accountNumber, String accountName) {}

  public static final class Failure extends RuntimeException {
    private final String code;

    public Failure(String code) {
      super("Kênh payOS chưa thể xử lý yêu cầu (" + code + ").");
      this.code = code;
    }

    public String code() {
      return code;
    }
  }

  private final RestClient http;
  private final String appOrigin, webhookOrigin;
  private final boolean allowTest;

  @org.springframework.beans.factory.annotation.Autowired
  public PayosClient(
      @Value("${PAYOS_API_URL:https://api-merchant.payos.vn}") String apiUrl,
      @Value("${APP_PUBLIC_URL:http://localhost:3001}") String appOrigin,
      @Value("${PAYMENT_WEBHOOK_ORIGIN:}") String webhookOrigin,
      @Value("${PAYMENT_TEST_PROVIDER_ALLOWED:false}") boolean allowTest,
      org.springframework.core.env.Environment environment) {
    boolean production =
        environment.acceptsProfiles(
            org.springframework.core.env.Profiles.of("live", "prod", "production"));
    URI api = URI.create(apiUrl);
    if (!"https://api-merchant.payos.vn".equals(apiUrl)
        && (!allowTest || production || !loopback(api)))
      throw new IllegalStateException("PAYOS_API_URL must use the official HTTPS origin");
    var factory =
        new org.springframework.http.client.JdkClientHttpRequestFactory(
            java.net.http.HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .followRedirects(java.net.http.HttpClient.Redirect.NEVER)
                .build());
    factory.setReadTimeout(Duration.ofSeconds(6));
    this.http =
        ExactDecimalJson.restClient(RestClient.builder())
            .baseUrl(apiUrl)
            .requestFactory(factory)
            .build();
    this.appOrigin = appOrigin.replaceAll("/+$", "");
    this.webhookOrigin = webhookOrigin.replaceAll("/+$", "");
    this.allowTest = allowTest && !production;
  }

  PayosClient(RestClient http, String appOrigin, String webhookOrigin) {
    this.http = http;
    this.appOrigin = appOrigin;
    this.webhookOrigin = webhookOrigin;
    this.allowTest = false;
  }

  public static String description(long orderCode) {
    if (orderCode < 1 || orderCode > MAX_ORDER_CODE)
      throw new IllegalArgumentException("Đã hết dải mã đơn payOS.");
    String encoded = Long.toString(orderCode, 36).toUpperCase(java.util.Locale.ROOT);
    return "SH" + "0".repeat(7 - encoded.length()) + encoded;
  }

  public JsonNode create(Order order, PayosCredentials credentials) {
    validateOrigin(appOrigin, allowTest);
    if (!description(order.orderCode()).equals(order.description())
        || order.amount().signum() <= 0
        || order.amount().stripTrailingZeros().scale() > 0
        || order.expiresAt().getEpochSecond() > Integer.MAX_VALUE)
      throw new IllegalArgumentException("Đơn thu payOS không hợp lệ.");
    String returnUrl = appOrigin + "/customer/payments/" + order.paymentId();
    var body =
        JsonNodeFactory.instance
            .objectNode()
            .put("orderCode", order.orderCode())
            .put("amount", order.amount().longValueExact())
            .put("description", order.description())
            .put("returnUrl", returnUrl)
            .put("cancelUrl", returnUrl);
    String signature = PayosSignature.sign(body, credentials.checksumKey());
    body.put("expiredAt", order.expiresAt().getEpochSecond()).put("signature", signature);
    JsonNode data = call(HttpMethod.POST, "/v2/payment-requests", body, credentials, true);
    if (data.path("orderCode").longValue() != order.orderCode()
        || data.path("amount").decimalValue().compareTo(order.amount()) != 0
        || !"VND".equals(data.path("currency").asText())
        || !order.description().equals(data.path("description").asText())
        || data.path("paymentLinkId").asText().isBlank()
        || data.path("qrCode").asText().isBlank()) throw new Failure("BINDING_MISMATCH");
    URI checkout;
    try {
      checkout = URI.create(data.path("checkoutUrl").asText());
    } catch (IllegalArgumentException ex) {
      throw new Failure("CHECKOUT_URL_INVALID");
    }
    if (!"https".equals(checkout.getScheme())
        || !"pay.payos.vn".equals(checkout.getHost())
        || checkout.getUserInfo() != null
        || checkout.getPort() != -1) throw new Failure("CHECKOUT_URL_INVALID");
    return data;
  }

  public JsonNode query(long orderCode, PayosCredentials credentials) {
    description(orderCode);
    return call(HttpMethod.GET, "/v2/payment-requests/" + orderCode, null, credentials, true);
  }

  public JsonNode cancel(long orderCode, PayosCredentials credentials) {
    description(orderCode);
    return call(
        HttpMethod.POST,
        "/v2/payment-requests/" + orderCode + "/cancel",
        JsonNodeFactory.instance
            .objectNode()
            .put("cancellationReason", "SportHub payment deadline closed"),
        credentials,
        true);
  }

  public Receiver confirmWebhook(UUID channelId, PayosCredentials credentials) {
    if (webhookOrigin.isBlank()) throw new Failure("WEBHOOK_ORIGIN_MISSING");
    validateOrigin(webhookOrigin, allowTest);
    JsonNode data =
        call(
            HttpMethod.POST,
            "/confirm-webhook",
            JsonNodeFactory.instance.objectNode().put("webhookUrl", webhookUrl(channelId)),
            credentials,
            false);
    String number = data.path("accountNumber").asText(), name = data.path("accountName").asText();
    if (!number.matches("[0-9]{1,19}") || name.isBlank() || name.length() > 150)
      throw new Failure("RECEIVER_METADATA_INCOMPLETE");
    if (data.hasNonNull("webhookUrl")
        && !webhookUrl(channelId).equals(data.path("webhookUrl").asText()))
      throw new Failure("WEBHOOK_BINDING_MISMATCH");
    return new Receiver(null, number, name);
  }

  public String webhookUrl(UUID channelId) {
    return webhookOrigin + "/api/v1/payments/providers/payos/" + channelId + "/webhook";
  }

  private JsonNode call(
      HttpMethod method, String path, JsonNode body, PayosCredentials credentials, boolean signed) {
    try {
      var request =
          http.method(method)
              .uri(path)
              .header("x-client-id", credentials.clientId())
              .header("x-api-key", credentials.apiKey());
      if (body != null) request.contentType(MediaType.APPLICATION_JSON).body(body);
      JsonNode response = request.retrieve().body(JsonNode.class);
      if (response == null || !"00".equals(response.path("code").asText())) {
        throw new Failure("PROVIDER_REJECTED");
      }
      JsonNode data = response.path("data");
      if (signed)
        PayosSignature.verify(data, response.path("signature").asText(), credentials.checksumKey());
      if (!data.isObject()) throw new Failure("INVALID_RESPONSE");
      return data;
    } catch (HttpClientErrorException.NotFound ex) {
      throw new Failure("NOT_FOUND");
    } catch (HttpClientErrorException.TooManyRequests ex) {
      throw new Failure("RATE_LIMITED");
    } catch (RestClientException ex) {
      throw new Failure("TRANSPORT_UNKNOWN");
    }
  }

  private static boolean loopback(URI uri) {
    return ("http".equals(uri.getScheme()) || "https".equals(uri.getScheme()))
        && java.util.Set.of("localhost", "127.0.0.1", "[::1]").contains(uri.getHost())
        && uri.getUserInfo() == null;
  }

  private static void validateOrigin(String value, boolean allowTest) {
    URI uri = URI.create(value);
    if (uri.getHost() == null
        || uri.getUserInfo() != null
        || uri.getQuery() != null
        || uri.getFragment() != null
        || !java.util.Set.of("", "/").contains(uri.getPath())
        || (!"https".equals(uri.getScheme()) && !(allowTest && loopback(uri))))
      throw new IllegalStateException("Payment public origins require HTTPS");
  }
}
