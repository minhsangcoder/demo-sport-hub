package com.sporthub.booking.service;

import com.sporthub.booking.repository.BookingRepository;
import com.sporthub.booking.web.BookingDtos.Booking;
import com.sporthub.common.exception.ConflictException;
import com.sporthub.common.security.RemoteIdentity.Caller;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
public class BookingRefundEligibility {
  private final BookingRepository repo;

  public BookingRefundEligibility(BookingRepository repo) {
    this.repo = repo;
  }

  public void require(Booking booking, Caller actor) {
    if (Boolean.TRUE.equals(repo.jdbc().queryForObject("""
SELECT EXISTS(SELECT 1 FROM transfer_handoffs WHERE booking_id=?)
    OR EXISTS(SELECT 1 FROM peer_booking_handoffs WHERE booking_id=?)
""", Boolean.class, booking.id(), booking.id())))
      throw new ConflictException("Lượt đặt nhận qua chuyển nhượng không áp dụng hoàn tiền khi tự hủy.");
    if (actor.hasRole("CUSTOMER") && Objects.equals(booking.currentHolderId(), actor.id())
        && (!BookingMoneyCohorts.isReal(booking) || !Boolean.TRUE.equals(repo.jdbc().queryForObject(
            "SELECT EXISTS(SELECT 1 FROM booking_groups WHERE id=? AND owner_id=?)",
            Boolean.class, booking.id(), actor.id()))))
      throw new ConflictException("Không áp dụng hoàn tiền cho người đặt ban đầu khi tự hủy. Bạn có thể đăng chuyển nhượng lượt đặt.");
  }
}
