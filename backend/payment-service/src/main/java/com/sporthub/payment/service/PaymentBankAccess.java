package com.sporthub.payment.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.sporthub.common.exception.*;
import com.sporthub.common.security.RemoteIdentity.Caller;
import java.time.Duration;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.*;
import org.springframework.web.server.ResponseStatusException;

@Component
public class PaymentBankAccess {
  private final RestClient facility, booking;

  public PaymentBankAccess(
      @Value("${FACILITY_SERVICE_URL:http://localhost:8082}") String facilityUrl,
      @Value("${BOOKING_SERVICE_URL:http://localhost:8084}") String bookingUrl) {
    var factory =
        new org.springframework.http.client.JdkClientHttpRequestFactory(
            java.net.http.HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build());
    factory.setReadTimeout(Duration.ofSeconds(5));
    facility = RestClient.builder().baseUrl(facilityUrl).requestFactory(factory).build();
    booking = RestClient.builder().baseUrl(bookingUrl).requestFactory(factory).build();
  }

  public UUID owner(UUID facilityId, Caller caller, String token) {
    caller.requireRole("OWNER");
    try {
      var response =
          facility
              .get()
              .uri("/api/v1/owner/facilities/" + facilityId)
              .header("Authorization", token)
              .retrieve()
              .body(JsonNode.class);
      var data = response == null ? null : response.path("data");
      if (data == null
          || !facilityId.toString().equals(data.path("id").asText())
          || !caller.id().toString().equals(data.path("ownerId").asText()))
        throw new ForbiddenException("Cơ sở không thuộc quyền sở hữu của bạn.");
      return caller.id();
    } catch (HttpClientErrorException ex) {
      throw new ForbiddenException("Không có quyền cấu hình thanh toán của cơ sở.");
    } catch (RestClientException ex) {
      throw new ResponseStatusException(
          HttpStatus.SERVICE_UNAVAILABLE, "Chưa xác minh được quyền sở hữu cơ sở.");
    }
  }

  public void guest(UUID bookingId, Caller caller, String token) {
    try {
      var response =
          booking
              .get()
              .uri("/api/v1/bookings/" + bookingId + "/refund-recipient-context")
              .header("Authorization", token)
              .retrieve()
              .body(JsonNode.class);
      var data = response == null ? null : response.path("data");
      if (data == null
          || !bookingId.toString().equals(data.path("bookingId").asText())
          || !data.path("guest").asBoolean())
        throw new ForbiddenException("Không thể ghi tài khoản nhận tiền cho khách này.");
    } catch (HttpClientErrorException ex) {
      throw new ForbiddenException("Cần quyền hoàn tiền tại đúng cơ sở của khách vãng lai.");
    } catch (RestClientException ex) {
      throw new ResponseStatusException(
          HttpStatus.SERVICE_UNAVAILABLE, "Chưa xác minh được khách nhận tiền.");
    }
  }
}
