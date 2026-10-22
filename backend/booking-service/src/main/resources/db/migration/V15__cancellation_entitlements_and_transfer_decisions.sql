CREATE TABLE transfer_acquisition_decisions (
 acquisition_id UUID PRIMARY KEY,listing_id UUID NOT NULL,booking_id UUID NOT NULL REFERENCES bookings(id),
 decision VARCHAR(20) NOT NULL CHECK(decision IN('COMPLETED','ABORTED')),
 command_fingerprint VARCHAR(64) NOT NULL CHECK(command_fingerprint ~ '^[a-f0-9]{64}$'),
 payment_id UUID,seller_id UUID NOT NULL,buyer_id UUID NOT NULL,amount NUMERIC(20,0),currency VARCHAR(3),
 reason VARCHAR(500) NOT NULL,decided_at TIMESTAMPTZ NOT NULL,booking_version BIGINT NOT NULL CHECK(booking_version>=0),
 CHECK(decision<>'COMPLETED' OR (payment_id IS NOT NULL AND amount IS NOT NULL AND amount>0 AND currency IS NOT NULL AND currency='VND'))
);
CREATE INDEX idx_acquisition_decision_booking ON transfer_acquisition_decisions(booking_id,decided_at);
CREATE FUNCTION check_transfer_acquisition_fence() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE aid UUID;d transfer_acquisition_decisions%ROWTYPE;h transfer_handoffs%ROWTYPE;
BEGIN
 aid=NEW.acquisition_id;IF aid IS NULL THEN RETURN NULL;END IF;
 SELECT * INTO d FROM transfer_acquisition_decisions WHERE acquisition_id=aid;
 SELECT * INTO h FROM transfer_handoffs WHERE acquisition_id=aid;
 IF d.decision='ABORTED' AND (h.acquisition_id IS NOT NULL OR EXISTS(SELECT 1 FROM transfer_leases WHERE acquisition_id=aid AND state IN('LOCKED','COMPLETED'))) THEN
  RAISE EXCEPTION 'Aborted acquisition cannot lock or handoff' USING ERRCODE='23514'; END IF;
 IF h.acquisition_id IS NOT NULL AND (TG_TABLE_NAME='transfer_handoffs' OR d.acquisition_id IS NOT NULL) AND (d.decision IS DISTINCT FROM 'COMPLETED' OR
  (d.listing_id,d.booking_id,d.seller_id,d.buyer_id,d.payment_id,d.amount,d.currency,d.command_fingerprint,d.decided_at) IS DISTINCT FROM
  (h.listing_id,h.booking_id,h.seller_id,h.buyer_id,h.payment_id,h.amount,h.currency,h.command_fingerprint,h.transferred_at)) THEN
  RAISE EXCEPTION 'New handoff requires matching immutable completed decision' USING ERRCODE='23514'; END IF;
 IF TG_TABLE_NAME='transfer_acquisition_decisions' AND d.decision='COMPLETED' AND h.acquisition_id IS NULL THEN
  RAISE EXCEPTION 'Completed acquisition decision requires its handoff' USING ERRCODE='23514'; END IF;
 RETURN NULL;
END $$;
CREATE CONSTRAINT TRIGGER acquisition_decision_commit AFTER INSERT ON transfer_acquisition_decisions DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION check_transfer_acquisition_fence();
CREATE CONSTRAINT TRIGGER acquisition_handoff_commit AFTER INSERT ON transfer_handoffs DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION check_transfer_acquisition_fence();
CREATE CONSTRAINT TRIGGER acquisition_lease_fence AFTER INSERT OR UPDATE ON transfer_leases DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION check_transfer_acquisition_fence();
CREATE TABLE booking_cancellation_refund_quotes (
 id UUID PRIMARY KEY,booking_id UUID NOT NULL REFERENCES bookings(id),booking_version BIGINT NOT NULL,ownership_version BIGINT NOT NULL,
 policy_version BIGINT NOT NULL CHECK(policy_version>0),policy_hash VARCHAR(64) NOT NULL,
 base_amount NUMERIC(20,0) NOT NULL CHECK(base_amount>=0),rate_bps SMALLINT NOT NULL CHECK(rate_bps BETWEEN 0 AND 10000),
 target_amount NUMERIC(20,0) NOT NULL CHECK(target_amount>=0 AND target_amount<=base_amount),
 currency VARCHAR(3) NOT NULL CHECK(currency='VND'),rounding VARCHAR(20) NOT NULL CHECK(rounding='HALF_UP'),
 acquisition_id UUID,allocations JSONB NOT NULL CHECK(jsonb_typeof(allocations)='array'),
 basis_fingerprint VARCHAR(64) NOT NULL CHECK(basis_fingerprint ~ '^[a-f0-9]{64}$'),
 requested_by UUID NOT NULL,created_at TIMESTAMPTZ NOT NULL,expires_at TIMESTAMPTZ NOT NULL CHECK(expires_at>created_at),
 CHECK(target_amount=round(base_amount*rate_bps/10000,0))
);
CREATE TABLE booking_cancellation_refund_requests (
 id UUID PRIMARY KEY,booking_id UUID NOT NULL UNIQUE REFERENCES bookings(id),quote_id UUID NOT NULL UNIQUE REFERENCES booking_cancellation_refund_quotes(id),
 facility_id UUID NOT NULL,court_id UUID NOT NULL,booking_version BIGINT NOT NULL,ownership_version BIGINT NOT NULL,
 policy_version BIGINT NOT NULL,policy_hash VARCHAR(64) NOT NULL,base_amount NUMERIC(20,0) NOT NULL CHECK(base_amount>=0),
 rate_bps SMALLINT NOT NULL CHECK(rate_bps BETWEEN 0 AND 10000),target_amount NUMERIC(20,0) NOT NULL CHECK(target_amount>=0 AND target_amount<=base_amount),
 currency VARCHAR(3) NOT NULL CHECK(currency='VND'),rounding VARCHAR(20) NOT NULL CHECK(rounding='HALF_UP'),
 acquisition_id UUID,allocations JSONB NOT NULL CHECK(jsonb_typeof(allocations)='array'),
 requested_by UUID NOT NULL,reason VARCHAR(500) NOT NULL CHECK(length(trim(reason))>0),
 authorized_by UUID,authorized_at TIMESTAMPTZ,
 state VARCHAR(40) NOT NULL CHECK(state IN('AWAITING_FACILITY_AUTHORIZATION','AUTHORIZED','PUBLISHED','NO_REFUND','REVIEW')),
 idempotency_key VARCHAR(100) NOT NULL,version BIGINT NOT NULL DEFAULT 0 CHECK(version>=0),created_at TIMESTAMPTZ NOT NULL,updated_at TIMESTAMPTZ NOT NULL,
 UNIQUE(requested_by,idempotency_key),CHECK(target_amount=round(base_amount*rate_bps/10000,0)),CHECK((authorized_by IS NULL)=(authorized_at IS NULL))
);
CREATE FUNCTION guard_cancellation_entitlement() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE b bookings%ROWTYPE;q booking_cancellation_refund_quotes%ROWTYPE;p booking_refund_policy_snapshots%ROWTYPE;
BEGIN
 IF TG_OP='DELETE' THEN RAISE EXCEPTION 'Cancellation entitlement cannot be deleted' USING ERRCODE='23514'; END IF;
 IF TG_OP='UPDATE' THEN
  IF (to_jsonb(NEW)-ARRAY['state','authorized_by','authorized_at','version','updated_at']) IS DISTINCT FROM
     (to_jsonb(OLD)-ARRAY['state','authorized_by','authorized_at','version','updated_at']) OR NEW.version<>OLD.version+1 OR
     (OLD.authorized_by IS NOT NULL AND (NEW.authorized_by,NEW.authorized_at) IS DISTINCT FROM (OLD.authorized_by,OLD.authorized_at))
  THEN RAISE EXCEPTION 'Cancellation entitlement is immutable' USING ERRCODE='23514'; END IF;
 END IF;
 SELECT * INTO b FROM bookings WHERE id=NEW.booking_id;
 SELECT * INTO q FROM booking_cancellation_refund_quotes WHERE id=NEW.quote_id;
 SELECT * INTO p FROM booking_refund_policy_snapshots WHERE booking_id=NEW.booking_id;
 IF NOT FOUND OR b.status IS DISTINCT FROM 'CANCELLED' OR
   (NEW.facility_id,NEW.court_id,NEW.booking_version,NEW.ownership_version) IS DISTINCT FROM (b.facility_id,b.court_id,b.version,b.checkin_token_version::bigint) OR
   (q.booking_id,q.policy_version,q.policy_hash,q.base_amount,q.rate_bps,q.target_amount,q.currency,q.rounding,q.acquisition_id,q.allocations) IS DISTINCT FROM
   (NEW.booking_id,NEW.policy_version,NEW.policy_hash,NEW.base_amount,NEW.rate_bps,NEW.target_amount,NEW.currency,NEW.rounding,NEW.acquisition_id,NEW.allocations) OR
   (p.policy_version,p.terms_hash,p.refund_rate_bps,p.currency,p.rounding) IS DISTINCT FROM (NEW.policy_version,NEW.policy_hash,NEW.rate_bps,NEW.currency,NEW.rounding)
 THEN RAISE EXCEPTION 'Cancellation entitlement must match the committed booking, quote and policy' USING ERRCODE='23514'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER cancellation_entitlement_integrity BEFORE INSERT OR UPDATE OR DELETE ON booking_cancellation_refund_requests FOR EACH ROW EXECUTE FUNCTION guard_cancellation_entitlement();
CREATE FUNCTION immutable_cancellation_decision_evidence() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN RAISE EXCEPTION 'Cancellation/transfer decision evidence is immutable' USING ERRCODE='23514'; END $$;
CREATE TRIGGER immutable_transfer_acquisition_decision BEFORE UPDATE OR DELETE ON transfer_acquisition_decisions FOR EACH ROW EXECUTE FUNCTION immutable_cancellation_decision_evidence();
CREATE TRIGGER immutable_cancellation_quote BEFORE UPDATE OR DELETE ON booking_cancellation_refund_quotes FOR EACH ROW EXECUTE FUNCTION immutable_cancellation_decision_evidence();
