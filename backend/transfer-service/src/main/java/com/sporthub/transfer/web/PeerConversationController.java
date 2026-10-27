package com.sporthub.transfer.web;

import com.sporthub.common.dto.ApiResponse;
import com.sporthub.common.money.PeerResaleContract.*;
import com.sporthub.common.security.RemoteIdentity;
import com.sporthub.transfer.service.PeerConversations;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/transfers/conversations/{id}/messages")
public class PeerConversationController {
  private final PeerConversations conversations;
  private final RemoteIdentity identity;

  public PeerConversationController(PeerConversations conversations, RemoteIdentity identity) {
    this.conversations = conversations;
    this.identity = identity;
  }

  @GetMapping
  public ApiResponse<List<Message>> messages(
      @PathVariable UUID id,
      @RequestParam(required = false) Long after,
      @RequestParam(required = false) Long before,
      @RequestParam(defaultValue = "50") int size,
      HttpServletRequest request) {
    return ApiResponse.success(conversations.messages(id, after, before, size, identity.current(request)));
  }

  @PostMapping
  public ApiResponse<Message> send(
      @PathVariable UUID id,
      @Valid @RequestBody MessageInput input,
      @RequestHeader("Idempotency-Key") String key,
      HttpServletRequest request) {
    return ApiResponse.success(conversations.send(id, input, key, identity.current(request)));
  }
}
