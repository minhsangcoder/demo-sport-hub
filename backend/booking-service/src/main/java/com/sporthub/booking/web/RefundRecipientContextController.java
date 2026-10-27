package com.sporthub.booking.web;

import com.sporthub.booking.repository.BookingRepository;
import com.sporthub.booking.service.BookingDependencies;
import com.sporthub.common.dto.ApiResponse;
import com.sporthub.common.exception.ForbiddenException;
import com.sporthub.common.security.RemoteIdentity;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;

@RestController
public class RefundRecipientContextController {
  public record Context(UUID bookingId, UUID facilityId, boolean guest) {}

  private final BookingRepository repo;
  private final BookingDependencies dependencies;
  private final RemoteIdentity identity;

  public RefundRecipientContextController(
      BookingRepository repo, BookingDependencies dependencies, RemoteIdentity identity) {
    this.repo = repo;
    this.dependencies = dependencies;
    this.identity = identity;
  }

  @GetMapping("/api/v1/bookings/{id}/refund-recipient-context")
  public ApiResponse<Context> guest(@PathVariable UUID id, HttpServletRequest request) {
    var booking = repo.find(id);
    dependencies.facilityPermission(
        booking.facilityId(),
        identity.current(request),
        request.getHeader("Authorization"),
        "BOOKING_REFUND");
    boolean guest =
        booking.source().equals("OFFLINE_COUNTER")
            && booking.currentHolderId() == null
            && repo.jdbc()
                    .queryForObject(
                        "SELECT count(*) FROM counter_booking_guests WHERE booking_id=? AND"
                            + " linked_customer_id IS NULL",
                        Integer.class,
                        id)
                == 1;
    if (!guest) throw new ForbiddenException("Khách có tài khoản cần tự khai tài khoản nhận tiền.");
    return ApiResponse.success(new Context(id, booking.facilityId(), true));
  }
}
