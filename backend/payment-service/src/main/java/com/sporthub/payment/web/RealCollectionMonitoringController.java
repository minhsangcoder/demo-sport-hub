package com.sporthub.payment.web;

import com.sporthub.common.dto.ApiResponse;
import com.sporthub.common.security.RemoteIdentity;
import com.sporthub.common.time.ReportWindow;
import com.sporthub.payment.repository.PaymentRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/payments/real-collections")
public class RealCollectionMonitoringController {
  private final PaymentRepository repo;
  private final RemoteIdentity identity;
  private final Clock clock;

  public RealCollectionMonitoringController(
      PaymentRepository repo, RemoteIdentity identity, Clock clock) {
    this.repo = repo;
    this.identity = identity;
    this.clock = clock;
  }

  @GetMapping
  @Transactional(readOnly = true)
  public ApiResponse<Map<String, Object>> read(
      @RequestParam(required = false) Instant from,
      @RequestParam(required = false) Instant to,
      HttpServletRequest request) {
    identity.current(request).requireRole("ADMIN");
    var window = ReportWindow.of(from, to, "Asia/Ho_Chi_Minh", clock.instant());
    var start = Timestamp.from(window.from());
    var end = Timestamp.from(window.to());
    var credits =
        repo.jdbc()
            .queryForList(
                "SELECT"
                    + " c.id,c.payment_id,s.facility_id,s.receiver_owner_id,c.amount,c.bank_time,c.verified_at"
                    + " FROM payos_bank_credits c JOIN payos_qr_sessions s ON"
                    + " s.payment_id=c.payment_id WHERE c.bank_time>=? AND c.bank_time<? ORDER BY"
                    + " c.bank_time DESC,c.id LIMIT 200",
                start,
                end);
    var collections =
        repo.jdbc()
            .queryForList(
                "SELECT payment_id,facility_id,applied_amount,collected_amount,paid_at,canonical"
                    + " FROM real_payment_collections WHERE paid_at>=? AND paid_at<? ORDER BY"
                    + " paid_at DESC,payment_id LIMIT 200",
                start,
                end);
    var sessions =
        repo.jdbc()
            .queryForList(
                "SELECT payment_id,facility_id,state,amount_received,last_error_code,created_at"
                    + " FROM payos_qr_sessions WHERE created_at<? AND (created_at>=? OR"
                    + " state='REVIEW') ORDER BY created_at DESC,payment_id LIMIT 200",
                end,
                start);
    return ApiResponse.success(
        Map.of(
            "mode",
            "REAL",
            "from",
            window.from(),
            "to",
            window.to(),
            "readOnly",
            true,
            "bankCredits",
            credits,
            "collections",
            collections,
            "sessions",
            sessions,
            "confirmationMethod",
            "BANK_VERIFIED",
            "limit",
            200));
  }
}
