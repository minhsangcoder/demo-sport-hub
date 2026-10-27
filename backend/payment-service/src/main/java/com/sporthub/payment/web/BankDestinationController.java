package com.sporthub.payment.web;

import com.sporthub.common.dto.ApiResponse;
import com.sporthub.common.security.RemoteIdentity;
import com.sporthub.payment.service.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/payments")
public class BankDestinationController {
  private final BankDestinations destinations;
  private final BankCatalog catalog;
  private final RemoteIdentity identity;
  private final PersonalBankProfiles profiles;

  public BankDestinationController(
      BankDestinations destinations,
      BankCatalog catalog,
      RemoteIdentity identity,
      PersonalBankProfiles profiles) {
    this.destinations = destinations;
    this.catalog = catalog;
    this.identity = identity;
    this.profiles = profiles;
  }

  @GetMapping("/banks")
  public ApiResponse<BankCatalog.Catalog> banks(HttpServletRequest r) {
    identity.current(r);
    return ApiResponse.success(catalog.read());
  }

  @GetMapping("/bank-destinations")
  public ApiResponse<List<BankDestinations.Destination>> mine(HttpServletRequest r) {
    return ApiResponse.success(profiles.mine(identity.current(r)));
  }

  @GetMapping("/bank-profile")
  public ApiResponse<com.fasterxml.jackson.databind.JsonNode> profile(HttpServletRequest r) {
    return ApiResponse.success(profiles.status(identity.current(r)));
  }

  @PostMapping("/bank-destinations")
  @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
  public ApiResponse<BankDestinations.Destination> register(
      @Valid @RequestBody BankDestinations.Input input,
      @RequestHeader("Idempotency-Key") String key,
      HttpServletRequest r) {
    return ApiResponse.created(
        profiles.register(input, key, identity.current(r), r.getHeader("Authorization")));
  }

  @PostMapping("/bookings/{id}/guest-bank-destinations")
  @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
  public ApiResponse<BankDestinations.Destination> guest(
      @PathVariable UUID id,
      @Valid @RequestBody BankDestinations.Input input,
      @RequestHeader("Idempotency-Key") String key,
      HttpServletRequest r) {
    return ApiResponse.created(
        destinations.register(input, key, identity.current(r), r.getHeader("Authorization"), id));
  }
}
