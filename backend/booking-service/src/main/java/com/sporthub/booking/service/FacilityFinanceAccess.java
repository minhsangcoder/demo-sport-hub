package com.sporthub.booking.service;

import com.sporthub.booking.repository.BookingRepository;
import com.sporthub.common.exception.*;
import com.sporthub.common.security.RemoteIdentity.Caller;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class FacilityFinanceAccess {
  private final BookingRepository repo;
  private final BookingDependencies dependencies;

  public FacilityFinanceAccess(BookingRepository repo, BookingDependencies dependencies) {
    this.repo = repo;
    this.dependencies = dependencies;
  }

  public Map<String, Object> obligation(UUID id, boolean lock) {
    var rows =
        repo.jdbc()
            .queryForList(
                "SELECT * FROM facility_disbursement_obligations WHERE id=?"
                    + (lock ? " FOR UPDATE" : ""),
                id);
    if (rows.isEmpty()) throw new ResourceNotFoundException("Không tìm thấy khoản phải trả.");
    return rows.getFirst();
  }

  public Map<String, Object> operation(UUID id, boolean lock) {
    var rows =
        repo.jdbc()
            .queryForList(
                "SELECT * FROM facility_disbursement_operations WHERE id=?"
                    + (lock ? " FOR UPDATE" : ""),
                id);
    if (rows.isEmpty()) throw new ResourceNotFoundException("Không tìm thấy lệnh chuyển khoản.");
    return rows.getFirst();
  }

  public void operator(Map<String, Object> obligation, Caller actor, String token) {
    if (actor.hasRole("ADMIN") && !actor.hasRole("OWNER") && !actor.hasRole("STAFF"))
      throw new ForbiddenException("Quản trị viên chỉ được giám sát.");
    dependencies.facilityPermission(
        (UUID) obligation.get("facility_id"),
        actor,
        token,
        "SELLER_PAYOUT".equals(obligation.get("kind")) ? "TRANSFER_PAYOUT" : "BOOKING_REFUND");
  }

  public void owner(Map<String, Object> obligation, Caller actor, String token) {
    actor.requireRole("OWNER");
    if (!actor.id().equals(obligation.get("receiver_owner_id")))
      throw new ForbiddenException("Chỉ Owner nhận tiền của cơ sở được tiếp nhận đối soát.");
    operator(obligation, actor, token);
  }

  public void recipient(Map<String, Object> obligation, Caller actor, String token) {
    if (obligation.get("guest_booking_id") != null) {
      operator(obligation, actor, token);
      return;
    }
    actor.requireRole("CUSTOMER");
    if (!actor.id().equals(obligation.get("payee_id")))
      throw new ForbiddenException("Chỉ người được nhận tự chọn tài khoản của mình.");
  }

  public void audit(
      UUID obligation, UUID operation, Caller actor, String action, String reason, Object details) {
    repo.jdbc()
        .update(
            "INSERT INTO"
                + " facility_finance_audit(id,obligation_id,operation_id,actor_id,action,reason,details)"
                + " VALUES(?,?,?,?,?,?,?::jsonb)",
            UUID.randomUUID(),
            obligation,
            operation,
            actor.id(),
            action,
            reason,
            repo.write(details));
  }

  public static void key(String key) {
    if (key == null || !key.matches("[A-Za-z0-9_.:-]{1,100}"))
      throw new IllegalArgumentException("Cần mã yêu cầu hợp lệ.");
  }

  public static void version(Map<String, Object> row, long version) {
    if (((Number) row.get("version")).longValue() != version)
      throw new ConflictException("Dữ liệu đã thay đổi. Tải lại trước khi thực hiện.");
  }
}
