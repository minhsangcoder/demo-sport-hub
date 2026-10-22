package com.sporthub.facility.service;

import com.sporthub.common.exception.ConflictException;
import com.sporthub.common.exception.ResourceNotFoundException;
import com.sporthub.common.security.RemoteIdentity.Caller;
import com.sporthub.common.security.Signatures;
import jakarta.validation.constraints.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class FacilityTransferFeePolicyService {
    public record Policy(boolean configured, UUID id, long version, Integer feeBps,
                         String currency, String rounding, String termsHash, Instant createdAt) {}
    public record Input(@NotNull @DecimalMin("0") @DecimalMax("10000") BigDecimal feeBps,
                        @NotNull @PositiveOrZero Long version) {}

    private final FacilityService facilities;
    private final JdbcTemplate jdbc;

    public FacilityTransferFeePolicyService(FacilityService facilities, JdbcTemplate jdbc) {
        this.facilities = facilities;
        this.jdbc = jdbc;
    }

    public Policy own(UUID facilityId, Caller actor) {
        actor.requireRole("OWNER");
        facilities.ownedEntity(facilityId, actor);
        return current(facilityId);
    }

    public Policy publicPolicy(UUID facilityId) {
        facilities.publicDetail(facilityId);
        return current(facilityId);
    }

    public Policy current(UUID facilityId) {
        return jdbc.query("""
                SELECT p.* FROM facility_transfer_fee_policy_heads h
                JOIN facility_transfer_fee_policy_versions p ON p.facility_id=h.facility_id AND p.version=h.policy_version
                WHERE h.facility_id=?
                """, (r, n) -> new Policy(true, r.getObject("id", UUID.class), r.getLong("version"),
                r.getInt("transfer_fee_bps"), r.getString("currency"), r.getString("rounding"),
                r.getString("terms_hash"), r.getTimestamp("created_at").toInstant()), facilityId)
                .stream().findFirst().orElse(new Policy(false, null, 0, null, null, null, null, null));
    }

    public Policy version(UUID facilityId, long version) {
        return jdbc.query("SELECT * FROM facility_transfer_fee_policy_versions WHERE facility_id=? AND version=?",
                (r, n) -> new Policy(true, r.getObject("id", UUID.class), r.getLong("version"),
                        r.getInt("transfer_fee_bps"), r.getString("currency"), r.getString("rounding"),
                        r.getString("terms_hash"), r.getTimestamp("created_at").toInstant()), facilityId, version)
                .stream().findFirst().orElseThrow(() -> new ResourceNotFoundException("Transfer fee policy version not found"));
    }

    @Transactional
    public Policy update(UUID facilityId, Input input, Caller actor) {
        actor.requireRole("OWNER");
        facilities.mutableOwnedEntity(facilityId, actor);
        if (input == null || input.feeBps() == null || input.version() == null || input.version() < 0) {
            throw new IllegalArgumentException("Transfer fee rate and current policy version are required");
        }
        final int rate;
        try { rate = input.feeBps().intValueExact(); }
        catch (ArithmeticException ex) { throw new IllegalArgumentException("Transfer fee rate must be whole basis points"); }
        if (rate < 0 || rate > 10000) throw new IllegalArgumentException("Transfer fee rate must be between 0 and 10000 basis points");
        var before = current(facilityId);
        if (before.version() != input.version()) throw new ConflictException("Transfer fee policy changed; reload and try again");
        long nextVersion = before.version() + 1;
        var hash = Signatures.sha256(facilityId + "|" + nextVersion + "|" + rate + "|VND|HALF_UP|FACILITY_TRANSFER_FEE");
        jdbc.update("""
                INSERT INTO facility_transfer_fee_policy_versions(id,facility_id,version,transfer_fee_bps,currency,rounding,terms_hash,created_by)
                VALUES(?,?,?,?,'VND','HALF_UP',?,?)
                """, UUID.randomUUID(), facilityId, nextVersion, rate, hash, actor.id());
        if (before.configured()) {
            int changed = jdbc.update("UPDATE facility_transfer_fee_policy_heads SET policy_version=? WHERE facility_id=? AND policy_version=?",
                    nextVersion, facilityId, before.version());
            if (changed != 1) throw new ConflictException("Transfer fee policy changed; reload and try again");
        } else {
            jdbc.update("INSERT INTO facility_transfer_fee_policy_heads(facility_id,policy_version) VALUES(?,?)", facilityId, nextVersion);
        }
        jdbc.update("""
                INSERT INTO facility_audit(id,facility_id,actor_id,action,details)
                VALUES(?,?,?,'TRANSFER_FEE_POLICY_UPDATED',jsonb_build_object('previousVersion',?::bigint,'version',?::bigint,'feeBps',?::int)::text)
                """, UUID.randomUUID(), facilityId, actor.id(), before.version(), nextVersion, rate);
        return current(facilityId);
    }
}
