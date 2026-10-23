package com.sporthub.booking.service;

import static com.sporthub.booking.web.BookingDtos.*;

import com.sporthub.booking.repository.BookingRepository;
import com.sporthub.common.exception.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class BookingIdempotency {
  private final BookingRepository repo;

  public BookingIdempotency(BookingRepository repo) {
    this.repo = repo;
  }

  public UUID idempotent(UUID actor, String operation, String key, String fingerprint) {
    var rows =
        repo.jdbc()
            .queryForList(
                "SELECT fingerprint,resource_id FROM request_idempotency WHERE actor_id=? AND"
                    + " operation=? AND key=?",
                actor,
                operation,
                key);
    if (rows.isEmpty()) return null;
    if (!rows.get(0).get("fingerprint").equals(fingerprint))
      throw new ConflictException("Idempotency key was used with different input");
    return (UUID) rows.get(0).get("resource_id");
  }

  public void remember(
      UUID actor, String operation, String key, String fingerprint, UUID resource) {
    repo.jdbc()
        .update(
            "INSERT INTO request_idempotency(actor_id,operation,key,fingerprint,resource_id)"
                + " VALUES(?,?,?,?,?)",
            actor,
            operation,
            key,
            fingerprint,
            resource);
  }

  public void checkKey(String key) {
    if (key == null || !key.matches("[A-Za-z0-9_.:-]{1,100}"))
      throw new IllegalArgumentException("A valid Idempotency-Key is required");
  }
}
