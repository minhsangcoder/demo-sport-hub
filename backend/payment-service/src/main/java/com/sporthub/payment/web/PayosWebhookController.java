package com.sporthub.payment.web;

import com.sporthub.payment.service.PayosWebhooks;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
public class PayosWebhookController {
  private final PayosWebhooks service;

  public PayosWebhookController(PayosWebhooks service) {
    this.service = service;
  }

  @PostMapping("/api/v1/payments/providers/payos/{channelId}/webhook")
  public Map<String, String> receive(@PathVariable UUID channelId, @RequestBody String body) {
    service.receive(channelId, body);
    return Map.of("code", "00", "desc", "success");
  }
}
