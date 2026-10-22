package com.sporthub.booking.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sporthub.booking.repository.BookingRepository;
import com.sporthub.booking.web.BookingDtos.Booking;
import com.sporthub.common.exception.ConflictException;
import com.sporthub.common.security.Signatures;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.UUID;

@Service
public class BookingRefundPolicies {
    private final BookingDependencies dependencies;
    private final BookingRepository repo;
    private final Clock clock;

    public BookingRefundPolicies(BookingDependencies dependencies, BookingRepository repo, Clock clock) {
        this.dependencies = dependencies; this.repo = repo; this.clock = clock;
    }

    public JsonNode attachToQuote(JsonNode quote, UUID facilityId) {
        var policy = validate(dependencies.refundPolicy(facilityId), facilityId);
        if (!quote.path("currency").asText().equals(policy.path("currency").asText())) {
            throw new ConflictException("Facility refund policy does not support the quoted currency");
        }
        ObjectNode snapshot = quote.deepCopy();
        snapshot.set("refundPolicy", policy.deepCopy());
        return snapshot;
    }
    public JsonNode current(UUID facilityId) { return validate(dependencies.refundPolicy(facilityId),facilityId); }

    public JsonNode fromAcceptedQuote(JsonNode quote, UUID facilityId) {
        if (!quote.has("refundPolicy")) {
            throw new ConflictException("Hold has no refund policy; create a new hold after the Owner configures the policy");
        }
        return validate(quote.get("refundPolicy"), facilityId);
    }

    public void requireExpectedPolicy(JsonNode quote, UUID facilityId, String expectedHash) {
        var policy = fromAcceptedQuote(quote, facilityId);
        if (expectedHash == null || !expectedHash.equals(policy.path("termsHash").asText())) {
            throw new ConflictException("Refund policy changed; review the facility policy before counter booking");
        }
    }

    public void store(Booking booking, JsonNode policy, UUID acceptedBy) {
        validate(policy, booking.facilityId());
        repo.jdbc().update("""
                INSERT INTO booking_refund_policy_snapshots(booking_id,facility_id,policy_id,policy_version,
                  refund_rate_bps,currency,rounding,terms_hash,origin,accepted_by,accepted_at)
                VALUES(?,?,?,?,?,?,?,?,'BOOKING_CREATED',?,?)
                """, booking.id(), booking.facilityId(), UUID.fromString(policy.path("id").asText()),
                policy.path("version").longValue(), policy.path("rateBps").intValue(), policy.path("currency").asText(),
                policy.path("rounding").asText(), policy.path("termsHash").asText(), acceptedBy, Timestamp.from(clock.instant()));
    }

    private JsonNode validate(JsonNode policy, UUID facilityId) {
        if (policy == null || !policy.path("configured").isBoolean()) throw unavailable();
        if (!policy.path("configured").booleanValue()) {
            throw new ConflictException("The Owner must configure the facility refund policy before accepting new bookings");
        }
        try {
            UUID.fromString(policy.path("id").asText());
            if (!policy.path("version").isIntegralNumber() || !policy.path("version").canConvertToLong() || policy.path("version").longValue() <= 0
                    || !policy.path("rateBps").isIntegralNumber() || !policy.path("rateBps").canConvertToInt()) throw unavailable();
            int rate = policy.path("rateBps").intValue();
            if (rate < 0 || rate > 10000 || !policy.path("currency").asText().equals("VND") || !policy.path("rounding").asText().equals("HALF_UP")) throw unavailable();
            String expected = Signatures.sha256(facilityId + "|" + policy.path("version").longValue() + "|" + rate + "|VND|HALF_UP|FACILITY_POLICY_CANCELLATION");
            if (!expected.equals(policy.path("termsHash").asText())) throw unavailable();
            return policy;
        } catch (IllegalArgumentException ex) { throw unavailable(); }
    }

    private ResponseStatusException unavailable() {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Facility refund policy could not be verified");
    }
}
