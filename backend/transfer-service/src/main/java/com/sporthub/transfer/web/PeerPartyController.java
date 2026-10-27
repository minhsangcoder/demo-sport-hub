package com.sporthub.transfer.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.sporthub.common.dto.ApiResponse;
import com.sporthub.common.money.PeerResaleContract.*;
import com.sporthub.common.security.RemoteIdentity;
import com.sporthub.transfer.service.PeerBookingClient;
import com.sporthub.transfer.service.PeerEvidenceAccess;
import com.sporthub.transfer.service.PeerPartyCommands;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/transfers/acquisitions/{id}")
public class PeerPartyController {
  private final PeerPartyCommands commands;
  private final RemoteIdentity identity;
  private final PeerBookingClient booking;
  private final PeerEvidenceAccess access;

  public PeerPartyController(
      PeerPartyCommands commands,
      RemoteIdentity identity,
      PeerBookingClient booking,
      PeerEvidenceAccess access) {
    this.commands = commands;
    this.identity = identity;
    this.booking = booking;
    this.access = access;
  }

  @GetMapping("/case")
  public ApiResponse<JsonNode> detail(@PathVariable UUID id, HttpServletRequest request) {
    var actor = identity.current(request);
    access.participant(id, actor);
    return ApiResponse.success(booking.party(id, "case", actor.id(), null, null, null, null));
  }

  @GetMapping("/declaration")
  public ApiResponse<JsonNode> declaration(@PathVariable UUID id, HttpServletRequest request) {
    var actor = identity.current(request);
    access.participant(id, actor);
    return ApiResponse.success(
        booking.party(id, "declaration", actor.id(), null, null, null, null));
  }

  @PostMapping("/declare-payment")
  public ApiResponse<JsonNode> declare(
      @PathVariable UUID id,
      @Valid @RequestBody Declaration input,
      @RequestHeader("Idempotency-Key") String key,
      HttpServletRequest request) {
    return ApiResponse.success(commands.declare(id, input, key, identity.current(request)));
  }

  @PostMapping("/confirm-receipt")
  public ApiResponse<JsonNode> confirm(
      @PathVariable UUID id,
      @Valid @RequestBody Command input,
      @RequestHeader("Idempotency-Key") String key,
      HttpServletRequest request) {
    return ApiResponse.success(commands.confirm(id, input, key, identity.current(request)));
  }

  @PostMapping("/release")
  public ApiResponse<JsonNode> cancel(
      @PathVariable UUID id,
      @Valid @RequestBody Command input,
      @RequestHeader("Idempotency-Key") String key,
      HttpServletRequest request) {
    return ApiResponse.success(commands.cancel(id, input, key, identity.current(request)));
  }

  @PostMapping("/deny-receipt")
  public ApiResponse<JsonNode> deny(
      @PathVariable UUID id,
      @Valid @RequestBody Command input,
      @RequestHeader("Idempotency-Key") String key,
      HttpServletRequest request) {
    return ApiResponse.success(commands.deny(id, input, key, identity.current(request)));
  }

  @PostMapping("/open-dispute")
  public ApiResponse<JsonNode> dispute(
      @PathVariable UUID id,
      @Valid @RequestBody Command input,
      @RequestHeader("Idempotency-Key") String key,
      HttpServletRequest request) {
    return ApiResponse.success(commands.dispute(id, input, key, identity.current(request)));
  }
}
