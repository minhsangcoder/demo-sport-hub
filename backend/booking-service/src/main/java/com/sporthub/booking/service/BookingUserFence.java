package com.sporthub.booking.service;

import com.sporthub.booking.repository.BookingRepository;
import com.sporthub.booking.web.BookingDtos.Booking;
import java.util.*;
import org.springframework.stereotype.Component;

/** Personal-bank responsibility producers acquire every user fence before any court fence. */
@Component
public class BookingUserFence {
  private final BookingRepository repo;

  public BookingUserFence(BookingRepository repo) {
    this.repo = repo;
  }

  public void lock(UUID... users) {
    lock(repo, users);
  }

  public static void lock(BookingRepository repo, UUID... users) {
    Arrays.stream(users)
        .filter(Objects::nonNull)
        .distinct()
        .sorted(Comparator.comparing(UUID::toString))
        .forEach(repo::lock);
  }

  public static UUID counterCustomer(BookingRepository repo, Booking booking) {
    if (!booking.source().equals("OFFLINE_COUNTER")) return null;
    var rows =
        repo.jdbc()
            .queryForList(
                "SELECT linked_customer_id FROM counter_booking_guests WHERE booking_id=?",
                booking.id());
    return rows.isEmpty() ? null : (UUID) rows.getFirst().get("linked_customer_id");
  }

  public static void money(BookingRepository repo, Booking booking, UUID payer) {
    lock(repo, booking.currentHolderId(), payer, counterCustomer(repo, booking));
  }
}
