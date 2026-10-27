package com.sporthub.transfer.web;

import com.sporthub.common.dto.ApiResponse;
import com.sporthub.common.money.PeerResaleContract.Evidence;
import com.sporthub.common.security.*;
import com.sporthub.transfer.service.PeerEvidence;
import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/transfers")
public class PeerEvidenceController {
  private final PeerEvidence evidence;
  private final RemoteIdentity identity;
  private final String secret;
  private final com.fasterxml.jackson.databind.ObjectMapper json;

  public PeerEvidenceController(
      PeerEvidence evidence,
      RemoteIdentity identity,
      @Value("${SERVICE_CALL_SECRET:}") String secret,
      com.fasterxml.jackson.databind.ObjectMapper json) {
    this.evidence = evidence;
    this.identity = identity;
    this.secret = secret;
    this.json = json;
  }

  @PostMapping(
      value = "/acquisitions/{id}/evidence",
      consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ApiResponse<Evidence> upload(
      @PathVariable UUID id,
      @RequestPart("file") MultipartFile file,
      @RequestHeader("Idempotency-Key") String key,
      HttpServletRequest request) {
    return ApiResponse.success(evidence.upload(id, file, key, identity.current(request)));
  }

  @GetMapping("/acquisitions/{id}/evidence")
  public ApiResponse<List<Evidence>> list(@PathVariable UUID id, HttpServletRequest request) {
    return ApiResponse.success(
        evidence.list(id, identity.current(request), request.getHeader("Authorization")));
  }

  @GetMapping("/evidence/{id}")
  public ResponseEntity<byte[]> image(@PathVariable UUID id, HttpServletRequest request) {
    var image = evidence.image(id, identity.current(request), request.getHeader("Authorization"));
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(image.contentType()))
        .cacheControl(CacheControl.noStore().cachePrivate())
        .header("X-Content-Type-Options", "nosniff")
        .header("Referrer-Policy", "no-referrer")
        .body(image.bytes());
  }

  @PostMapping("/internal/peer/{id}/evidence-references")
  public ApiResponse<List<Evidence>> references(
      @PathVariable UUID id, @RequestBody String body, HttpServletRequest request)
      throws Exception {
    ServiceCalls.verify(
        secret,
        "POST",
        request.getRequestURI(),
        request.getHeader("X-Service-Time"),
        body,
        request.getHeader("X-Service-Signature"));
    var values = json.readTree(body).path("ids");
    if (!values.isArray() || values.size() > 20)
      throw new IllegalArgumentException("Chỉ đối chiếu tối đa 20 chứng cứ mỗi lần.");
    var ids = new ArrayList<UUID>();
    for (var value : values) ids.add(UUID.fromString(value.asText()));
    return ApiResponse.success(evidence.references(id, ids));
  }
}
