package com.sporthub.common.schedule;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Logical service references only: these IDs are not cross-database foreign keys. */
public final class ConfigurationGuardProtocol {
    private ConfigurationGuardProtocol() {}
    public record Command(UUID operationId, UUID facilityId, String source) {}
    public record Reservation(UUID reservationId, UUID bookingId, UUID courtId,
                              Instant startsAt, Instant endsAt, String state) {}
    public record Prepared(UUID operationId, List<Reservation> reservations) {}
}
