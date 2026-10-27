package com.sporthub.transfer.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.sporthub.common.dto.ApiResponse;
import com.sporthub.common.money.*;
import com.sporthub.common.security.RemoteIdentity;
import com.sporthub.transfer.service.PeerMoneyCommands;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/transfers/acquisitions/{id}")
public class PeerMoneyController {
  private final PeerMoneyCommands money;
  private final RemoteIdentity identity;

  public PeerMoneyController(PeerMoneyCommands money, RemoteIdentity identity) {
    this.money = money;
    this.identity = identity;
  }

  @GetMapping("/money")
  public ApiResponse<JsonNode> read(
      @PathVariable UUID id,
      @RequestParam(required = false) UUID afterReturn,
      @RequestParam(defaultValue = "50") int size,
      HttpServletRequest request) {
    return ApiResponse.success(money.read(id, afterReturn, size, identity.current(request)));
  }

  @PostMapping("/report-late-payment")
  public ApiResponse<JsonNode> report(
      @PathVariable UUID id,
      @Valid @RequestBody PeerMoneyContract.Report input,
      @RequestHeader("Idempotency-Key") String key,
      HttpServletRequest request) {
    return ApiResponse.success(money.report(id, input, key, identity.current(request)));
  }

  @PostMapping("/returns")
  public ApiResponse<JsonNode> returned(
      @PathVariable UUID id,
      @Valid @RequestBody PeerMoneyContract.ReturnInput input,
      @RequestHeader("Idempotency-Key") String key,
      HttpServletRequest request) {
    return ApiResponse.success(money.returned(id, input, key, identity.current(request)));
  }

  @PostMapping("/returns/{returned}/acknowledge")
  public ApiResponse<JsonNode> acknowledge(
      @PathVariable UUID id,
      @PathVariable UUID returned,
      @Valid @RequestBody PeerResaleContract.Command input,
      @RequestHeader("Idempotency-Key") String key,
      HttpServletRequest request) {
    return ApiResponse.success(
        money.acknowledge(id, returned, input, key, identity.current(request)));
  }

  @PostMapping("/money-no-claim")
  public ApiResponse<JsonNode> noClaim(
      @PathVariable UUID id,
      @Valid @RequestBody PeerMoneyContract.NoClaim input,
      @RequestHeader("Idempotency-Key") String key,
      HttpServletRequest request) {
    return ApiResponse.success(money.noClaim(id, input, key, identity.current(request)));
  }
}
