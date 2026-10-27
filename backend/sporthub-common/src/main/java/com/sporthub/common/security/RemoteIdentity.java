package com.sporthub.common.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.sporthub.common.exception.ForbiddenException;
import com.sporthub.common.exception.UnauthorizedException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** Technical caller context fetched from Identity. Never trusts client identity headers. */
@Component
public class RemoteIdentity {
  private final RestClient client;

  @org.springframework.beans.factory.annotation.Autowired
  public RemoteIdentity(@Value("${IDENTITY_SERVICE_URL:http://localhost:8081}") String url) {
    var factory =
        new org.springframework.http.client.JdkClientHttpRequestFactory(
            java.net.http.HttpClient.newBuilder()
                .connectTimeout(java.time.Duration.ofSeconds(3))
                .build());
    factory.setReadTimeout(java.time.Duration.ofSeconds(5));
    client = RestClient.builder().baseUrl(url).requestFactory(factory).build();
  }

  RemoteIdentity(RestClient client) {
    this.client = client;
  }

  public Caller current(HttpServletRequest request) {
    if (request.getAttribute("sporthub.caller") instanceof Caller caller) return caller;
    String token = request.getHeader("Authorization");
    if (token == null || !token.startsWith("Bearer "))
      throw new UnauthorizedException("Authentication required");
    JsonNode user;
    try {
      JsonNode body =
          client
              .get()
              .uri("/api/v1/users/me/access")
              .header("Authorization", token)
              .retrieve()
              .body(JsonNode.class);
      user = body == null ? null : body.get("data");
    } catch (org.springframework.web.client.HttpClientErrorException ex) {
      throw new UnauthorizedException("Session is invalid or expired");
    } catch (org.springframework.web.client.RestClientException ex) {
      throw new org.springframework.web.server.ResponseStatusException(
          org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
          "Authentication service is temporarily unavailable");
    }
    if (user == null
        || !user.isObject()
        || !user.path("roles").isArray()
        || user.path("roles").isEmpty()
        || !user.path("facilityBindings").isArray())
      throw new UnauthorizedException("Invalid authentication context");
    UUID id;
    try {
      id = UUID.fromString(user.path("id").asText());
    } catch (IllegalArgumentException ex) {
      throw new UnauthorizedException("Invalid authentication context");
    }
    var roles = new HashSet<String>();
    user.path("roles").forEach(role -> roles.add(role.asText()));
    var bindings = new HashMap<UUID, Set<String>>();
    user.path("facilityBindings")
        .forEach(
            binding -> {
              var permissions = new HashSet<String>();
              binding.path("permissions").forEach(p -> permissions.add(p.asText()));
              bindings.put(
                  UUID.fromString(binding.path("facilityId").asText()), Set.copyOf(permissions));
            });
    Caller caller =
        new Caller(
            id,
            user.path("fullName").asText(),
            Set.copyOf(roles),
            Map.copyOf(bindings),
            user.path("status").asText("UNKNOWN"),
            user.path("emailVerified").isBoolean() && user.path("emailVerified").booleanValue(),
            user.path("phoneVerified").isBoolean() && user.path("phoneVerified").booleanValue());
    request.setAttribute("sporthub.caller", caller);
    return caller;
  }

  public record Caller(
      UUID id,
      String fullName,
      Set<String> roles,
      Map<UUID, Set<String>> facilityBindings,
      String status,
      boolean emailVerified,
      boolean phoneVerified) {
    public Caller(
        UUID id, String fullName, Set<String> roles, Map<UUID, Set<String>> facilityBindings) {
      this(id, fullName, roles, facilityBindings, "UNKNOWN", false, false);
    }

    public boolean hasRole(String role) {
      return roles.contains(role);
    }

    public void requireRole(String role) {
      if (!hasRole(role)) throw new ForbiddenException("Bạn không có quyền thực hiện thao tác này.");
    }

    public void requireActiveVerified() {
      if (!"ACTIVE".equals(status) || !(emailVerified || phoneVerified))
        throw new com.sporthub.common.exception.ActionPolicyException(
            403,
            "ACCOUNT_VERIFICATION_REQUIRED",
            "Cần tài khoản đang hoạt động và đã xác thực email hoặc số điện thoại.",
            Map.of());
    }
  }
}
