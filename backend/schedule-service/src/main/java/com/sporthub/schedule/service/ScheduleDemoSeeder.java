package com.sporthub.schedule.service;

import static com.sporthub.schedule.web.ScheduleDtos.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.sporthub.common.security.RemoteIdentity.Caller;
import com.sporthub.schedule.repository.ScheduleRepository;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@Profile("(local | demo) & !live & !prod & !production")
@ConditionalOnProperty(name = "DEMO_SEED_ENABLED", havingValue = "true")
public class ScheduleDemoSeeder implements ApplicationRunner {
  private final ScheduleService service;
  private final ScheduleRepository repo;
  private final RestClient identity, facility;
  private final String password;
  private boolean completed;
  private final org.springframework.transaction.support.TransactionTemplate transaction;

  public ScheduleDemoSeeder(
      ScheduleService service,
      ScheduleRepository repo,
      @Value("${IDENTITY_SERVICE_URL:http://localhost:8081}") String identityUrl,
      @Value("${FACILITY_SERVICE_URL:http://localhost:8082}") String facilityUrl,
      @Value("${DEMO_PASSWORD:}") String password,
      org.springframework.transaction.PlatformTransactionManager manager) {
    this.service = service;
    this.repo = repo;
    this.password = password;
    this.transaction = new org.springframework.transaction.support.TransactionTemplate(manager);
    var factory =
        new org.springframework.http.client.JdkClientHttpRequestFactory(
            java.net.http.HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build());
    factory.setReadTimeout(Duration.ofSeconds(5));
    identity = RestClient.builder().baseUrl(identityUrl).requestFactory(factory).build();
    facility = RestClient.builder().baseUrl(facilityUrl).requestFactory(factory).build();
  }

  @Override
  public synchronized void run(ApplicationArguments args) {
    if (completed) return;
    try {
      seed();
      completed = true;
    } catch (RuntimeException unavailable) {
      org.slf4j.LoggerFactory.getLogger(ScheduleDemoSeeder.class)
          .warn(
              "Demo schedule setup deferred until dependencies are available ({})",
              unavailable.getClass().getSimpleName());
    }
  }

  @org.springframework.scheduling.annotation.Scheduled(
      initialDelayString = "${schedule.demo.seed-delay-ms:15000}",
      fixedDelayString = "${schedule.demo.seed-delay-ms:15000}")
  public void retry() {
    run(null);
  }

  private void seed() {
    var session =
        identity
            .post()
            .uri("/api/v1/auth/login")
            .body(Map.of("identifier", "owner@sporthub.local", "password", password))
            .retrieve()
            .body(JsonNode.class)
            .path("data");
    String token = "Bearer " + session.path("accessToken").asText();
    var caller =
        new Caller(
            UUID.fromString(session.path("user").path("id").asText()),
            "Demo Owner",
            Set.of("OWNER"),
            Map.of());
    try {
      var owned =
          facility
              .get()
              .uri("/api/v1/owner/facilities")
              .header("Authorization", token)
              .retrieve()
              .body(JsonNode.class)
              .path("data");
      for (var f : owned)
        if (f.path("status").asText().equals("ACTIVE")) {
          UUID id = UUID.fromString(f.path("id").asText());
          if (!repo.hours(id).isEmpty()) continue;
          var intervals = new ArrayList<Interval>();
          for (int day = 1; day <= 7; day++)
            intervals.add(new Interval(day, LocalTime.of(6, 0), LocalTime.of(22, 0), 60));
          transaction.executeWithoutResult(
              status -> {
                service.replaceHours(id, new HoursInput(null, intervals), caller, token);
                for (int day = 1; day <= 7; day++)
                  service.addPrice(
                      id,
                      new PriceInput(
                          null,
                          null,
                          day,
                          null,
                          LocalTime.of(6, 0),
                          LocalTime.of(22, 0),
                          new BigDecimal("150000"),
                          0,
                          "Giá demo giờ thường",
                          LocalDate.now().minusDays(1),
                          null,
                          "VND"),
                      caller,
                      token);
              });
        }
    } finally {
      identity
          .post()
          .uri("/api/v1/auth/logout")
          .header("Authorization", token)
          .retrieve()
          .toBodilessEntity();
    }
  }
}
