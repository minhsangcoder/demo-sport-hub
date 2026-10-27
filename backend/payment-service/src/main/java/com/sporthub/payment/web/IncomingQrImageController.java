package com.sporthub.payment.web;

import com.sporthub.common.dto.ApiResponse;
import com.sporthub.common.security.RemoteIdentity;
import com.sporthub.payment.service.IncomingQrImages;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;

@RestController
public class IncomingQrImageController {
  private final IncomingQrImages images;
  private final RemoteIdentity identity;

  public IncomingQrImageController(IncomingQrImages images, RemoteIdentity identity) {
    this.images = images;
    this.identity = identity;
  }

  @GetMapping("/api/v1/payments/{id}/qr-image")
  public ApiResponse<String> image(@PathVariable UUID id, HttpServletRequest request) {
    return ApiResponse.success(
        images.image(id, identity.current(request), request.getHeader("Authorization")));
  }
}
