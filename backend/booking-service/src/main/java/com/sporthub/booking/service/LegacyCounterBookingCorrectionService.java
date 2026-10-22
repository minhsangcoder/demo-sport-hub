package com.sporthub.booking.service;

import com.sporthub.booking.repository.BookingRepository;
import com.sporthub.booking.web.BookingDtos.Booking;
import com.sporthub.common.exception.ResourceNotFoundException;
import com.sporthub.common.security.Signatures;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

/** Explicitly invoked maintenance operations; never runs during application startup. */
@Service
public class LegacyCounterBookingCorrectionService {
    public record Candidate(UUID bookingId, long version, UUID courtId, UUID facilityId,
        UUID expectedOperatorId, int checkinTokenVersion, String category, String reason,
        String databaseIdentity, String evidenceFingerprint) {}
    public record PreflightResult(String targetDatabase, List<Candidate> candidates,
        long totalExamined, long eligibleCount, long reviewCount, long excludedCount) {}
    public record CorrectionReport(UUID bookingId, boolean success, String status, String message) {}
    public record CorrectionSummary(String targetDatabase, UUID operatorId, List<CorrectionReport> reports,
        int correctedCount, int skippedCount) {}

    private final BookingRepository repo;
    private final PlatformTransactionManager txManager;

    public LegacyCounterBookingCorrectionService(BookingRepository repo, PlatformTransactionManager txManager) {
        this.repo = repo;
        this.txManager = txManager;
    }

    public PreflightResult preflight(String targetDatabase) {
        var tx = new TransactionTemplate(txManager);
        tx.setReadOnly(true);
        tx.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        return tx.execute(status -> {
            validateTargetDatabase(targetDatabase);
            var rows = repo.list("SELECT * FROM bookings WHERE source='OFFLINE_COUNTER' ORDER BY starts_at DESC,id DESC");
            var candidates = rows.stream().map(this::classify).toList();
            return new PreflightResult(targetDatabase, candidates, candidates.size(),
                candidates.stream().filter(c -> c.category().equals("CONFIRMED_GUEST_LEGACY")).count(),
                candidates.stream().filter(c -> c.category().equals("REVIEW_REQUIRED")).count(),
                candidates.stream().filter(c -> c.category().equals("EXCLUDED")).count());
        });
    }

    public Candidate classify(Booking b) {
        String reason = classificationReason(b);
        String category = switch (reason) {
            case "SAFE_LEGACY_GUEST_FALLBACK" -> "CONFIRMED_GUEST_LEGACY";
            case "NOT_COUNTER_BOOKING", "ALREADY_NULL_OWNER", "LEGITIMATE_LINKED_CUSTOMER" -> "EXCLUDED";
            default -> "REVIEW_REQUIRED";
        };
        return new Candidate(b.id(), b.version(), b.courtId(), b.facilityId(), b.createdBy(),
            b.checkinTokenVersion(), category, reason, databaseIdentity(), evidenceFingerprint(b.id()));
    }

    private String classificationReason(Booking b) {
        if (!"OFFLINE_COUNTER".equals(b.source())) return "NOT_COUNTER_BOOKING";
        if (b.customerId() == null && b.currentHolderId() == null) return "ALREADY_NULL_OWNER";
        if (count("SELECT count(*) FROM booking_groups WHERE id=?", b.id()) > 0) return "GROUP_BOOKING";
        if (count("SELECT count(*) FROM transfer_leases WHERE booking_id=?", b.id()) > 0
            || count("SELECT count(*) FROM transfer_handoffs WHERE booking_id=?", b.id()) > 0) return "TRANSFER_EVIDENCE_EXISTS";
        // Booking has no financial-resolution protocol. Review annotations do not settle a discrepancy.
        if (count("SELECT count(*) FROM payment_reconciliation WHERE booking_id=?", b.id()) > 0) return "UNRESOLVED_RECONCILIATION";
        var guests = repo.jdbc().queryForList("SELECT * FROM counter_booking_guests WHERE booking_id=?", b.id());
        if (guests.size() != 1) return "MISSING_GUEST_SNAPSHOT";
        var guest = guests.getFirst();
        if (guest.get("linked_customer_id") != null) return "LEGITIMATE_LINKED_CUSTOMER";
        if (!Objects.equals(guest.get("operator_id"), b.createdBy())) return "OPERATOR_MISMATCH";
        if (b.guestName() == null || b.guestName().isBlank() || b.guestPhone() == null
            || !b.guestPhone().matches("\\+?[0-9]{8,15}")
            || !Objects.equals(guest.get("guest_name"), b.guestName())
            || !Objects.equals(guest.get("guest_phone"), b.guestPhone())) return "CONTACT_MISMATCH";
        if (!Objects.equals(b.customerId(), b.createdBy()) || !Objects.equals(b.currentHolderId(), b.createdBy())) return "HOLDER_MISMATCH";
        var reservations = repo.jdbc().queryForList("""
            SELECT r.* FROM slot_reservations r JOIN bookings b ON b.reservation_id=r.id WHERE b.id=?
            AND r.court_id=b.court_id AND r.facility_id=b.facility_id
            AND r.starts_at=b.starts_at AND r.ends_at=b.ends_at AND r.expires_at=b.hold_expires_at
            """, b.id());
        if (reservations.size() != 1) return "RESERVATION_SCOPE_MISMATCH";
        var reservation = reservations.getFirst();
        if (!Objects.equals(reservation.get("holder_id"), b.createdBy())) return "HOLDER_MISMATCH";
        String expectedState = switch (b.status()) {
            case "PENDING" -> "HOLD";
            case "CONFIRMED", "CHECKED_IN", "COMPLETED" -> "BOOKED";
            case "CANCELLED", "EXPIRED" -> "RELEASED";
            default -> "INVALID";
        };
        if (!expectedState.equals(reservation.get("state"))) return "RESERVATION_STATE_MISMATCH";
        if ((b.paymentId() == null) != (b.paidAt() == null)) return "INCOMPLETE_PAYMENT_EVIDENCE";
        if (Set.of("CONFIRMED", "CHECKED_IN", "COMPLETED").contains(b.status()) && b.paymentId() == null) return "MISSING_PAYMENT_EVIDENCE";
        if (b.paymentId() == null) {
            if (count("SELECT count(*) FROM booking_payment_receipts WHERE booking_id=?", b.id()) > 0) return "UNAPPLIED_PAYMENT_EVIDENCE";
        } else {
            if (Set.of("PENDING", "EXPIRED").contains(b.status()) || !b.paidAt().isBefore(b.holdExpiresAt())) return "INCONSISTENT_PAYMENT_EVIDENCE";
            var receipts = repo.jdbc().queryForList("SELECT fingerprint FROM booking_payment_receipts WHERE payment_id=? AND booking_id=?", b.paymentId(), b.id());
            if (receipts.size() != 1) return "MISSING_PAYMENT_RECEIPT";
            var proofs = repo.jdbc().queryForList("""
                SELECT actor_id,details->>'effectiveAt' AS effective_at FROM booking_history
                WHERE booking_id=? AND action='CONFIRMED' AND details->>'paymentId'=?
                """, b.id(), b.paymentId().toString());
            if (proofs.size() != 1 || !Objects.equals(proofs.getFirst().get("actor_id"), b.createdBy())) return "MISSING_PAYMENT_AUDIT";
            try {
                Instant effective = Instant.parse((String) proofs.getFirst().get("effective_at"));
                if (Duration.between(effective, b.paidAt()).abs().compareTo(Duration.ofNanos(1000)) > 0) return "PAYMENT_TIMESTAMP_MISMATCH";
                String expected = Signatures.sha256(b.id()+"|"+b.createdBy()+"||BOOKING|SUCCESS|"+
                    b.amount().stripTrailingZeros().toPlainString()+"|"+b.currency()+"|"+effective);
                if (!expected.equals(receipts.getFirst().get("fingerprint"))) return "PAYMENT_FINGERPRINT_MISMATCH";
            } catch (RuntimeException invalid) { return "INCOMPLETE_PAYMENT_EVIDENCE"; }
            if (count("SELECT count(*) FROM booking_payment_receipts WHERE booking_id=? AND payment_id<>?", b.id(), b.paymentId()) > 0) return "UNAPPLIED_PAYMENT_EVIDENCE";
        }
        return "SAFE_LEGACY_GUEST_FALLBACK";
    }

    public CorrectionSummary correct(String targetDatabase, UUID operatorId, List<Candidate> approvedManifest) {
        validateTargetDatabase(targetDatabase);
        if (operatorId == null) throw new IllegalArgumentException("Actual operator ID performing the correction is required");
        if (approvedManifest == null) throw new IllegalArgumentException("An explicitly approved manifest is required");
        var reports = new ArrayList<CorrectionReport>();
        for (var candidate : approvedManifest) reports.add(correctSingle(targetDatabase, candidate, operatorId));
        int corrected = (int) reports.stream().filter(CorrectionReport::success).count();
        return new CorrectionSummary(targetDatabase, operatorId, reports, corrected, reports.size()-corrected);
    }

    private CorrectionReport correctSingle(String targetDatabase, Candidate candidate, UUID operatorId) {
        if (candidate == null || candidate.bookingId() == null) throw new IllegalArgumentException("Invalid manifest item");
        return new TransactionTemplate(txManager).execute(status -> {
            validateTargetDatabase(targetDatabase);
            Booking initial;
            try { initial = repo.find(candidate.bookingId()); }
            catch (ResourceNotFoundException missing) { return skipped(candidate, "SKIPPED_NOT_FOUND"); }
            // Lock the immutable court read from the database, never the supplied manifest court.
            repo.lock(initial.courtId());
            var current = repo.find(initial.id());
            if (current.customerId() == null && current.currentHolderId() == null) return skipped(candidate, "SKIPPED_ALREADY_CORRECTED");
            if (current.version() != candidate.version()) return skipped(candidate, "SKIPPED_VERSION_MISMATCH");
            var fresh = classify(current);
            if (!"CONFIRMED_GUEST_LEGACY".equals(fresh.category()) || !fresh.equals(candidate)) return skipped(candidate, "SKIPPED_CHANGED");
            int reservationChanged = repo.jdbc().update("""
                UPDATE slot_reservations SET holder_id=NULL
                WHERE id=(SELECT reservation_id FROM bookings WHERE id=?) AND holder_id=?
                """, current.id(), current.createdBy());
            int changed = repo.jdbc().update("""
                UPDATE bookings SET customer_id=NULL,current_holder_id=NULL,version=version+1,checkin_token_version=checkin_token_version+1
                WHERE id=? AND version=? AND customer_id=? AND current_holder_id=? AND checkin_token_version=?
                """, current.id(), candidate.version(), current.customerId(), current.currentHolderId(), candidate.checkinTokenVersion());
            if (reservationChanged != 1 || changed != 1) {
                status.setRollbackOnly();
                return skipped(candidate, "SKIPPED_CHANGED");
            }
            Map<String,Object> audit = new LinkedHashMap<>();
            audit.put("previousCustomerId", current.customerId());
            audit.put("previousCurrentHolderId", current.currentHolderId());
            audit.put("newCustomerId", null);
            audit.put("newCurrentHolderId", null);
            audit.put("correctionOperatorId", operatorId);
            audit.put("preflightReason", candidate.reason());
            audit.put("evidenceFingerprint", candidate.evidenceFingerprint());
            audit.put("changeSource", "LEGACY_DATA_CORRECTION");
            repo.history(current.id(), operatorId, "LEGACY_OWNERSHIP_CORRECTED", audit);
            return new CorrectionReport(current.id(), true, "CORRECTED", "Owners cleared; original evidence retained");
        });
    }

    private CorrectionReport skipped(Candidate c, String reason) {
        return new CorrectionReport(c.bookingId(), false, reason, "No ownership data changed");
    }

    private int count(String sql, Object... args) { return repo.jdbc().queryForObject(sql, Integer.class, args); }

    private void validateTargetDatabase(String targetDatabase) {
        if (targetDatabase == null || targetDatabase.isBlank()) throw new IllegalArgumentException("Explicit target database JDBC URL is required");
        if (!targetDatabase.startsWith("jdbc:postgresql:") || targetDatabase.matches("(?i).*[?&](password|user)=.*"))
            throw new IllegalArgumentException("Supply an explicit PostgreSQL JDBC URL without credentials");
        String actual = repo.jdbc().execute((ConnectionCallback<String>) c -> c.getMetaData().getURL());
        if (!targetDatabase.equals(actual)) throw new IllegalArgumentException("Target database does not match the connected datasource");
    }

    private String databaseIdentity() {
        return repo.jdbc().execute((ConnectionCallback<String>) c -> c.getMetaData().getURL()+"|"+c.getCatalog()+"|"+c.getSchema());
    }

    private String evidenceFingerprint(UUID id) {
        String evidence = repo.jdbc().queryForObject("""
            SELECT jsonb_build_object('booking',to_jsonb(b),'reservation',to_jsonb(r),
              'guest',(SELECT to_jsonb(g) FROM counter_booking_guests g WHERE g.booking_id=b.id),
              'groups',(SELECT jsonb_agg(to_jsonb(g) ORDER BY g.id) FROM booking_groups g WHERE g.id=b.id),
              'leases',(SELECT jsonb_agg(to_jsonb(l) ORDER BY l.listing_id) FROM transfer_leases l WHERE l.booking_id=b.id),
              'handoffs',(SELECT jsonb_agg(to_jsonb(h) ORDER BY h.listing_id) FROM transfer_handoffs h WHERE h.booking_id=b.id),
              'reconciliation',(SELECT jsonb_agg(to_jsonb(p) ORDER BY p.id) FROM payment_reconciliation p WHERE p.booking_id=b.id),
              'receipts',(SELECT jsonb_agg(to_jsonb(p) ORDER BY p.payment_id) FROM booking_payment_receipts p WHERE p.booking_id=b.id),
              'history',(SELECT jsonb_agg(to_jsonb(h) ORDER BY h.id) FROM booking_history h WHERE h.booking_id=b.id),
              'registry',(SELECT jsonb_agg(to_jsonb(c) ORDER BY c.code) FROM booking_code_registry c WHERE c.booking_id=b.id))::text
            FROM bookings b JOIN slot_reservations r ON r.id=b.reservation_id WHERE b.id=?
            """, String.class, id);
        return Signatures.sha256(evidence);
    }
}
