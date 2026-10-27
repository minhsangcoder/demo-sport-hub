package com.sporthub.payment.service;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.*;
import org.springframework.web.server.ResponseStatusException;

/** Public bank metadata only. Never sends an account number to VietQR. */
@Service
public class BankCatalog {
  public record Bank(String bin, String name, String shortName) {}

  public record Catalog(Instant updatedAt, boolean stale, List<Bank> banks) {}

  private final RestClient http;
  private final Clock clock;
  private Catalog cached;
  private Instant retryAfter = Instant.MIN;

  @org.springframework.beans.factory.annotation.Autowired
  public BankCatalog(
      @Value("${BANK_CATALOG_URL:https://api.vietqr.io/v2/banks}") String url,
      Clock clock,
      @Value("${PAYMENT_TEST_PROVIDER_ALLOWED:false}") boolean allowTest,
      org.springframework.core.env.Environment environment) {
    var uri = java.net.URI.create(url);
    boolean production =
        environment.acceptsProfiles(
            org.springframework.core.env.Profiles.of("live", "prod", "production"));
    if (!"https://api.vietqr.io/v2/banks".equals(url)
        && (!allowTest || production || !Set.of("localhost", "127.0.0.1").contains(uri.getHost())))
      throw new IllegalStateException("Unsupported bank metadata origin");
    var factory =
        new org.springframework.http.client.JdkClientHttpRequestFactory(
            java.net.http.HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build());
    factory.setReadTimeout(Duration.ofSeconds(5));
    http = RestClient.builder().baseUrl(url).requestFactory(factory).build();
    this.clock = clock;
  }

  BankCatalog(RestClient http, Clock clock) {
    this.http = http;
    this.clock = clock;
  }

  public synchronized Catalog read() {
    Instant now = clock.instant();
    if (cached != null && now.isBefore(cached.updatedAt().plus(Duration.ofDays(1)))) return cached;
    if (!now.isBefore(retryAfter)) {
      try {
        JsonNode response = http.get().retrieve().body(JsonNode.class);
        if (response == null
            || !"00".equals(response.path("code").asText())
            || !response.path("data").isArray()) throw new IllegalArgumentException();
        var banks = new TreeMap<String, Bank>();
        for (var row : response.path("data")) {
          if (row.path("transferSupported").asInt() != 1) continue;
          String bin = row.path("bin").asText(),
              name = row.path("name").asText(),
              shortName = row.path("shortName").asText();
          if (!bin.matches("[0-9]{6}")
              || name.isBlank()
              || name.length() > 150
              || shortName.isBlank()
              || shortName.length() > 80
              || banks.containsKey(bin)) throw new IllegalArgumentException();
          banks.put(bin, new Bank(bin, name, shortName));
        }
        if (banks.isEmpty()) throw new IllegalArgumentException();
        cached = new Catalog(now, false, List.copyOf(banks.values()));
        retryAfter = now.plus(Duration.ofDays(1));
        return cached;
      } catch (RestClientException | IllegalArgumentException ex) {
        retryAfter = now.plusSeconds(60);
      }
    }
    if (cached != null && now.isBefore(cached.updatedAt().plus(Duration.ofDays(7))))
      return new Catalog(cached.updatedAt(), true, cached.banks());
    throw new ResponseStatusException(
        HttpStatus.SERVICE_UNAVAILABLE, "Chưa tải được danh mục ngân hàng. Vui lòng thử lại sau.");
  }

  public Bank require(String bin) {
    if (bin == null || !bin.matches("[0-9]{6}"))
      throw new IllegalArgumentException("Mã ngân hàng phải gồm 6 chữ số.");
    return read().banks().stream()
        .filter(b -> b.bin().equals(bin))
        .findFirst()
        .orElseThrow(
            () -> new IllegalArgumentException("Ngân hàng chưa hỗ trợ chuyển khoản VietQR."));
  }
}
