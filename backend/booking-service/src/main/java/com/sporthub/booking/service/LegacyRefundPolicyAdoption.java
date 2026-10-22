package com.sporthub.booking.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.sporthub.booking.repository.BookingRepository;
import com.sporthub.booking.web.BookingDtos.Booking;
import com.sporthub.common.exception.*;
import com.sporthub.common.security.RemoteIdentity.Caller;
import jakarta.validation.constraints.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;

@Service
public class LegacyRefundPolicyAdoption {
    public record Input(@Min(0) long expectedBookingVersion,@NotBlank String policyHash,
                        @NotBlank @Size(max=500) String reason,@Size(max=500) String guestConsentReference) {}
    public record Offer(UUID id,UUID bookingId,long bookingVersion,JsonNode policy,List<UUID> requiredUsers,
                        List<UUID> consentedUsers,String reason,Instant expiresAt,boolean adopted) {}
    public record Candidate(UUID bookingId,String status,String amount,List<String> reasons) {}
    private final BookingRepository repo;
    private final BookingDependencies dependencies;
    private final BookingRefundPolicies policies;
    private final BookingService bookings;
    private final Clock clock;
    public LegacyRefundPolicyAdoption(BookingRepository repo,BookingDependencies dependencies,BookingRefundPolicies policies,BookingService bookings,Clock clock){
        this.repo=repo;this.dependencies=dependencies;this.policies=policies;this.bookings=bookings;this.clock=clock;
    }
    @Transactional(readOnly=true)
    public List<Candidate> preflight(UUID facility,Caller caller,String token){
        caller.requireRole("OWNER");dependencies.ownerFacility(facility,caller,token);
        return repo.list("SELECT b.* FROM bookings b WHERE facility_id=? AND NOT EXISTS(SELECT 1 FROM booking_refund_policy_snapshots p WHERE p.booking_id=b.id) ORDER BY starts_at,id LIMIT 1000",facility)
                .stream().map(b->new Candidate(b.id(),b.status(),b.amount().toPlainString(),reasons(b))).toList();
    }
    private List<String> reasons(Booking b){
        var result=new ArrayList<String>();result.add("MISSING_REFUND_POLICY");
        if(!b.currency().equals("VND")||b.amount().stripTrailingZeros().scale()>0)result.add("UNSUPPORTED_MONEY_PRECISION_REVIEW");
        if(!Objects.equals(b.currentHolderId(),bookings.projectEffective(b).currentHolderId()))result.add("COUNTER_OWNERSHIP_REVIEW");
        if(!Set.of("PENDING","CONFIRMED").contains(b.status())||!b.startsAt().isAfter(clock.instant()))result.add("CANCELLATION_WINDOW_CLOSED");
        if(repo.jdbc().queryForObject("SELECT count(*) FROM transfer_leases WHERE booking_id=? AND state IN ('ACTIVE','LOCKED')",Integer.class,b.id())>0)result.add("TRANSFER_DECISION_REQUIRED");
        return result;
    }
    @Transactional
    public Offer propose(UUID id,Input input,Caller caller,String token){
        caller.requireRole("OWNER");var b=repo.find(id);dependencies.ownerFacility(b.facilityId(),caller,token);repo.lock(b.courtId());b=repo.find(id);
        if(exists(id))throw new ConflictException("Booking already has an immutable refund policy");
        if(input.expectedBookingVersion()!=b.version()||reasons(b).size()>1)throw new ConflictException("Legacy booking needs reconciliation or a fresh adoption offer");
        var policy=policies.current(b.facilityId());
        if(!policy.path("termsHash").asText().equals(input.policyHash()))throw new ConflictException("Review the current policy before proposing adoption");
        var required=requiredUsers(b);String guest=input.guestConsentReference();
        if(required.isEmpty()&&(guest==null||guest.isBlank()))throw new IllegalArgumentException("Record the walk-in customer's explicit consent reference");
        UUID offer=UUID.randomUUID();Instant now=clock.instant(),expires=now.plusSeconds(900);if(expires.isAfter(b.startsAt()))expires=b.startsAt();
        repo.jdbc().update("INSERT INTO booking_refund_policy_adoption_offers(id,booking_id,booking_version,proposed_by,policy,required_users,reason,guest_consent_reference,created_at,expires_at) VALUES(?,?,?,?,?::jsonb,?::jsonb,?,?,?,?)",
                offer,id,b.version(),caller.id(),repo.write(policy),repo.write(required),input.reason(),required.isEmpty()?guest:null,Timestamp.from(now),Timestamp.from(expires));
        if(required.isEmpty())adopt(b,policy,caller.id(),input.reason()+"; guest consent: "+guest);
        return load(offer);
    }
    public Offer view(UUID id,Caller caller,String token){
        var offer=load(id);var b=repo.find(offer.bookingId());
        if(!offer.requiredUsers().contains(caller.id())){caller.requireRole("OWNER");dependencies.ownerFacility(b.facilityId(),caller,token);}
        return offer;
    }
    @Transactional
    public Offer consent(UUID id,Caller caller){
        caller.requireRole("CUSTOMER");var offer=load(id);var b=repo.find(offer.bookingId());repo.lock(b.courtId());b=repo.find(b.id());offer=load(id);
        if(!offer.requiredUsers().contains(caller.id()))throw new ForbiddenException("Only the affected customer can accept these terms");
        if(offer.adopted())return offer;
        if(exists(b.id()))throw new ConflictException("A different policy adoption has already completed");
        if(!offer.expiresAt().isAfter(clock.instant())||b.version()!=offer.bookingVersion()||reasons(b).size()>1||!requiredUsers(b).equals(offer.requiredUsers()))throw new ConflictException("Adoption offer is stale; request a new offer");
        repo.jdbc().update("INSERT INTO booking_refund_policy_adoption_consents(offer_id,user_id,consented_at) VALUES(?,?,?) ON CONFLICT DO NOTHING",id,caller.id(),Timestamp.from(clock.instant()));
        offer=load(id);if(offer.consentedUsers().containsAll(offer.requiredUsers()))adopt(b,offer.policy(),caller.id(),offer.reason()+"; consent offer: "+id);
        return load(id);
    }
    private List<UUID> requiredUsers(Booking b){
        var ids=new TreeSet<UUID>();if(b.currentHolderId()!=null)ids.add(b.currentHolderId());
        ids.addAll(repo.jdbc().queryForList("SELECT user_id FROM group_members WHERE group_id=? AND active UNION SELECT payer_id FROM group_contributions WHERE group_id=?",UUID.class,b.id(),b.id()));return List.copyOf(ids);
    }
    private void adopt(Booking b,JsonNode p,UUID actor,String reason){
        repo.jdbc().update("INSERT INTO booking_refund_policy_snapshots(booking_id,facility_id,policy_id,policy_version,refund_rate_bps,currency,rounding,terms_hash,origin,accepted_by,accepted_at,adoption_reason) VALUES(?,?,?,?,?,?,'HALF_UP',?,'LEGACY_ADOPTION',?,?,?)",
                b.id(),b.facilityId(),UUID.fromString(p.path("id").asText()),p.path("version").longValue(),p.path("rateBps").intValue(),p.path("currency").asText(),p.path("termsHash").asText(),actor,Timestamp.from(clock.instant()),reason.substring(0,Math.min(500,reason.length())));
        repo.history(b.id(),actor,"REFUND_POLICY_LEGACY_ADOPTED",Map.of("policyHash",p.path("termsHash").asText(),"reason",reason));
    }
    private boolean exists(UUID id){return repo.jdbc().queryForObject("SELECT count(*) FROM booking_refund_policy_snapshots WHERE booking_id=?",Integer.class,id)>0;}
    private Offer load(UUID id){
        return repo.jdbc().query("SELECT * FROM booking_refund_policy_adoption_offers WHERE id=?",(r,n)->{
            var users=new ArrayList<UUID>();repo.read(r.getString("required_users")).forEach(u->users.add(UUID.fromString(u.asText())));
            var consented=repo.jdbc().queryForList("SELECT user_id FROM booking_refund_policy_adoption_consents WHERE offer_id=? ORDER BY user_id",UUID.class,id);
            UUID booking=r.getObject("booking_id",UUID.class);var policy=repo.read(r.getString("policy"));boolean adopted=repo.jdbc().queryForObject("SELECT count(*) FROM booking_refund_policy_snapshots WHERE booking_id=? AND terms_hash=?",Integer.class,booking,policy.path("termsHash").asText())>0;
            return new Offer(id,booking,r.getLong("booking_version"),policy,users,consented,r.getString("reason"),r.getTimestamp("expires_at").toInstant(),adopted);
        },id).stream().findFirst().orElseThrow(()->new ResourceNotFoundException("Adoption offer not found"));
    }
}
