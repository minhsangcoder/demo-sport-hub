package com.sporthub.payment.web;

import com.sporthub.common.dto.ApiResponse;
import com.sporthub.common.security.RemoteIdentity;
import com.sporthub.payment.service.PeerQrInstructions;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/payments/peer-acquisitions/{id}")
public class PeerQrController {
  private final PeerQrInstructions instructions;
  private final RemoteIdentity identity;

  public PeerQrController(PeerQrInstructions instructions, RemoteIdentity identity) {
    this.instructions = instructions;
    this.identity = identity;
  }

  @GetMapping("/instruction")
  public ApiResponse<PeerQrInstructions.Instruction> instruction(
      @PathVariable UUID id, HttpServletRequest request) {
    return ApiResponse.success(instructions.instruction(id, identity.current(request)));
  }

  @GetMapping(value = "/qr", produces = MediaType.IMAGE_PNG_VALUE)
  public ResponseEntity<byte[]> image(@PathVariable UUID id, HttpServletRequest request) {
    return ResponseEntity.ok()
        .contentType(MediaType.IMAGE_PNG)
        .cacheControl(CacheControl.noStore().cachePrivate())
        .header("Referrer-Policy", "no-referrer")
        .header("X-Content-Type-Options", "nosniff")
        .body(instructions.image(id, identity.current(request)));
  }
}
