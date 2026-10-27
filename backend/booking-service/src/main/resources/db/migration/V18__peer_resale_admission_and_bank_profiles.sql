ALTER TABLE transfer_leases ADD COLUMN protocol VARCHAR(24) NOT NULL DEFAULT 'LEGACY_OWNER'
 CHECK(protocol IN('LEGACY_OWNER','PEER_RESALE_V1'));
CREATE FUNCTION preserve_transfer_protocol() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF NEW.protocol IS DISTINCT FROM OLD.protocol THEN
  RAISE EXCEPTION 'Transfer protocol is immutable' USING ERRCODE='23514';
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER immutable_transfer_protocol BEFORE UPDATE ON transfer_leases
 FOR EACH ROW EXECUTE FUNCTION preserve_transfer_protocol();

CREATE TABLE user_hold_claims (
 id UUID PRIMARY KEY,principal_id UUID NOT NULL,kind VARCHAR(30) NOT NULL
 CHECK(kind IN('ONLINE_BOOKING','PEER_ACQUISITION','LEGACY_ACQUISITION')),
 source_id UUID NOT NULL UNIQUE,court_id UUID NOT NULL,facility_id UUID NOT NULL,
 starts_at TIMESTAMPTZ NOT NULL,ends_at TIMESTAMPTZ NOT NULL CHECK(ends_at>starts_at),
 expires_at TIMESTAMPTZ NOT NULL,phase VARCHAR(30) NOT NULL,
 state VARCHAR(15) NOT NULL CHECK(state IN('ACTIVE','RELEASED','COMPLETED')),
 grandfather BOOLEAN NOT NULL DEFAULT FALSE,
 created_at TIMESTAMPTZ NOT NULL,updated_at TIMESTAMPTZ NOT NULL
);
CREATE UNIQUE INDEX one_online_claim_per_user ON user_hold_claims(principal_id)
 WHERE state='ACTIVE' AND NOT grandfather;
ALTER TABLE user_hold_claims ADD COLUMN protection_eligible BOOLEAN NOT NULL DEFAULT FALSE;
CREATE INDEX claim_expiry ON user_hold_claims(expires_at) WHERE state='ACTIVE';
-- Preserve every old live hold, including users already above the new quota; never penalize it.
INSERT INTO user_hold_claims(id,principal_id,kind,source_id,court_id,facility_id,starts_at,ends_at,
 expires_at,phase,state,grandfather,created_at,updated_at)
 SELECT gen_random_uuid(),r.holder_id,'ONLINE_BOOKING',r.id,r.court_id,r.facility_id,r.starts_at,r.ends_at,
 r.expires_at,'LEGACY_HOLD','ACTIVE',TRUE,r.created_at,clock_timestamp()
 FROM slot_reservations r WHERE r.state='HOLD' AND r.expires_at>clock_timestamp()
 AND r.holder_id IS NOT NULL AND COALESCE(r.quote->>'admissionChannel','ONLINE')<>'COUNTER'
 AND NOT EXISTS(SELECT 1 FROM bookings b WHERE b.reservation_id=r.id AND b.source='OFFLINE_COUNTER');
INSERT INTO user_hold_claims(id,principal_id,kind,source_id,court_id,facility_id,starts_at,ends_at,
 expires_at,phase,state,grandfather,created_at,updated_at)
 SELECT gen_random_uuid(),l.buyer_id,'LEGACY_ACQUISITION',l.acquisition_id,b.court_id,b.facility_id,
 b.starts_at,b.ends_at,l.expires_at,'LEGACY_LOCKED','ACTIVE',TRUE,clock_timestamp(),clock_timestamp()
 FROM transfer_leases l JOIN bookings b ON b.id=l.booking_id
 WHERE l.protocol='LEGACY_OWNER' AND l.state='LOCKED' AND l.expires_at>clock_timestamp()
 AND l.acquisition_id IS NOT NULL AND l.buyer_id IS NOT NULL;
CREATE TABLE booking_abandonment_events (
 source_id UUID PRIMARY KEY,principal_id UUID NOT NULL,court_id UUID NOT NULL,
 starts_at TIMESTAMPTZ NOT NULL,ends_at TIMESTAMPTZ NOT NULL CHECK(ends_at>starts_at),
 kind VARCHAR(30) NOT NULL,reason VARCHAR(40) NOT NULL,effective_at TIMESTAMPTZ NOT NULL,
 recorded_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);
CREATE INDEX abandonment_user_time ON booking_abandonment_events(principal_id,effective_at DESC);
CREATE TABLE booking_admission_successes (
 source_id UUID PRIMARY KEY,principal_id UUID NOT NULL,effective_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX admission_success_user_time ON booking_admission_successes(principal_id,effective_at DESC);
CREATE TABLE booking_admission_versions (
 principal_id UUID PRIMARY KEY,revision UUID NOT NULL,updated_at TIMESTAMPTZ NOT NULL
);
CREATE SEQUENCE booking_admission_position_seq;
ALTER TABLE booking_abandonment_events ADD COLUMN position BIGINT NOT NULL DEFAULT nextval('booking_admission_position_seq');
ALTER TABLE booking_admission_successes ADD COLUMN position BIGINT NOT NULL DEFAULT nextval('booking_admission_position_seq');
CREATE TABLE booking_admission_exemptions (
 source_id UUID PRIMARY KEY REFERENCES user_hold_claims(source_id),
 reason VARCHAR(30) NOT NULL CHECK(reason IN('SYSTEM_ERROR','PAYMENT_FAILURE','FUNDS_APPLIED','FACILITY_CANCEL','SELLER_WITHDRAWAL')),
 recorded_at TIMESTAMPTZ NOT NULL
);
CREATE TRIGGER immutable_admission_exemption BEFORE UPDATE OR DELETE ON booking_admission_exemptions
 FOR EACH ROW EXECUTE FUNCTION immutable_cancellation_decision_evidence();
CREATE TABLE user_admission_restrictions (
 principal_id UUID PRIMARY KEY,hold_blocked_until TIMESTAMPTZ,online_blocked_until TIMESTAMPTZ,
 market_blocked_until TIMESTAMPTZ,market_permanent BOOLEAN NOT NULL DEFAULT FALSE,
 strike_count INTEGER NOT NULL DEFAULT 0 CHECK(strike_count>=0),
 policy_version BIGINT NOT NULL DEFAULT 0 CHECK(policy_version>=0),updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE user_bank_profiles (
 principal_id UUID PRIMARY KEY,active_revision_id UUID,destination_hash VARCHAR(64),
 initialized BOOLEAN NOT NULL DEFAULT FALSE,version BIGINT NOT NULL DEFAULT 0 CHECK(version>=0),
 reconciliation_required BOOLEAN NOT NULL DEFAULT FALSE,updated_at TIMESTAMPTZ NOT NULL,
 CHECK((active_revision_id IS NULL)=(destination_hash IS NULL)),
 CHECK(destination_hash IS NULL OR destination_hash ~ '^[a-f0-9]{64}$'),
 CHECK(active_revision_id IS NULL OR initialized)
);
CREATE TABLE bank_profile_activation_decisions (
 principal_id UUID NOT NULL,idempotency_key VARCHAR(100) NOT NULL,
 fingerprint VARCHAR(64) NOT NULL CHECK(fingerprint ~ '^[a-f0-9]{64}$'),
 revision_id UUID NOT NULL,destination_hash VARCHAR(64) NOT NULL CHECK(destination_hash ~ '^[a-f0-9]{64}$'),
 version BIGINT NOT NULL CHECK(version>0),decision VARCHAR(12) NOT NULL CHECK(decision='ACTIVATED'),
 decided_at TIMESTAMPTZ NOT NULL,PRIMARY KEY(principal_id,idempotency_key),UNIQUE(principal_id,version)
);
CREATE TRIGGER immutable_bank_activation_decision BEFORE UPDATE OR DELETE ON bank_profile_activation_decisions
 FOR EACH ROW EXECUTE FUNCTION immutable_cancellation_decision_evidence();

CREATE TABLE peer_listing_terms (
 listing_id UUID NOT NULL REFERENCES transfer_leases(listing_id),version BIGINT NOT NULL CHECK(version>=0),
 terms JSONB NOT NULL CHECK(terms->>'protocol' IS NOT DISTINCT FROM 'PEER_RESALE_V1'),
 terms_hash VARCHAR(64) NOT NULL CHECK(terms_hash ~ '^[a-f0-9]{64}$'),
 accepted_by UUID NOT NULL,accepted_at TIMESTAMPTZ NOT NULL,PRIMARY KEY(listing_id,version)
);
CREATE TRIGGER immutable_peer_listing_terms BEFORE UPDATE OR DELETE ON peer_listing_terms
 FOR EACH ROW EXECUTE FUNCTION immutable_cancellation_decision_evidence();

CREATE TABLE peer_usage_attempts (
 acquisition_id UUID PRIMARY KEY,listing_id UUID NOT NULL REFERENCES transfer_leases(listing_id),
 booking_id UUID NOT NULL REFERENCES bookings(id),facility_id UUID NOT NULL,court_id UUID NOT NULL,
 seller_id UUID NOT NULL,buyer_id UUID NOT NULL CHECK(buyer_id<>seller_id),
 amount NUMERIC(20,0) NOT NULL CHECK(amount>0),currency VARCHAR(3) NOT NULL CHECK(currency='VND'),
 starts_at TIMESTAMPTZ NOT NULL,ends_at TIMESTAMPTZ NOT NULL CHECK(ends_at>starts_at),
 bank_revision_id UUID NOT NULL,destination_hash VARCHAR(64) NOT NULL,
 refund_policy_hash VARCHAR(64) NOT NULL,terms_hash VARCHAR(64) NOT NULL,
 pay_by TIMESTAMPTZ NOT NULL CHECK(pay_by<=starts_at),
 phase VARCHAR(30) NOT NULL CHECK(phase IN('WAITING_PAYMENT','PAYMENT_DECLARED','DISPUTED','HANDOFF_PENDING','COMPLETED','EXPIRED','ABORTED_A_RETAINS','TIMED_OUT_A_RETAINS')),
 confirmation_method VARCHAR(32) NOT NULL DEFAULT 'NONE'
 CHECK(confirmation_method IN('NONE','BUYER_DECLARED','COUNTERPARTY_ACKNOWLEDGED','OWNER_ADJUDICATED')),
 version BIGINT NOT NULL DEFAULT 0 CHECK(version>=0),
 payment_declared_at TIMESTAMPTZ,seller_acknowledged_at TIMESTAMPTZ,
 created_at TIMESTAMPTZ NOT NULL,updated_at TIMESTAMPTZ NOT NULL,
 CHECK(terms_hash ~ '^[a-f0-9]{64}$' AND destination_hash ~ '^[a-f0-9]{64}$' AND refund_policy_hash ~ '^[a-f0-9]{64}$')
);
CREATE UNIQUE INDEX one_peer_attempt_per_listing ON peer_usage_attempts(listing_id)
 WHERE phase IN('WAITING_PAYMENT','PAYMENT_DECLARED','DISPUTED','HANDOFF_PENDING');
CREATE INDEX peer_usage_deadlines ON peer_usage_attempts(phase,pay_by,starts_at);
CREATE INDEX peer_seller_open ON peer_usage_attempts(seller_id,phase);
CREATE INDEX peer_buyer_open ON peer_usage_attempts(buyer_id,phase);

CREATE TABLE peer_attempt_terms (
 acquisition_id UUID PRIMARY KEY REFERENCES peer_usage_attempts(acquisition_id),
 terms JSONB NOT NULL CHECK(terms->>'protocol' IS NOT DISTINCT FROM 'PEER_RESALE_V1'),
 terms_hash VARCHAR(64) NOT NULL CHECK(terms_hash ~ '^[a-f0-9]{64}$'),accepted_at TIMESTAMPTZ NOT NULL
);
CREATE TRIGGER immutable_peer_attempt_terms BEFORE UPDATE OR DELETE ON peer_attempt_terms
 FOR EACH ROW EXECUTE FUNCTION immutable_cancellation_decision_evidence();

CREATE TABLE peer_usage_evidence_receipts (
 acquisition_id UUID PRIMARY KEY REFERENCES peer_usage_attempts(acquisition_id),buyer_id UUID NOT NULL,
 terms_hash VARCHAR(64) NOT NULL CHECK(terms_hash ~ '^[a-f0-9]{64}$'),
 bank_reference VARCHAR(120) NOT NULL CHECK(length(trim(bank_reference))>0),
 evidence_ids UUID[] NOT NULL CHECK(cardinality(evidence_ids) BETWEEN 1 AND 3),
 fingerprint VARCHAR(64) NOT NULL CHECK(fingerprint ~ '^[a-f0-9]{64}$'),claimed_at TIMESTAMPTZ NOT NULL
);
CREATE TRIGGER immutable_peer_evidence_receipt BEFORE UPDATE OR DELETE ON peer_usage_evidence_receipts
 FOR EACH ROW EXECUTE FUNCTION immutable_cancellation_decision_evidence();

CREATE TABLE peer_usage_decisions (
 acquisition_id UUID PRIMARY KEY REFERENCES peer_usage_attempts(acquisition_id),
 booking_id UUID NOT NULL REFERENCES bookings(id),listing_id UUID NOT NULL REFERENCES transfer_leases(listing_id),
 seller_id UUID NOT NULL,buyer_id UUID NOT NULL,amount NUMERIC(20,0) NOT NULL CHECK(amount>0),
 currency VARCHAR(3) NOT NULL CHECK(currency='VND'),terms_hash VARCHAR(64) NOT NULL,
 decision VARCHAR(30) NOT NULL CHECK(decision IN('COMPLETED','ABORTED_A_RETAINS','TIMED_OUT_A_RETAINS','EXPIRED')),
 confirmation_method VARCHAR(32) NOT NULL,actor_id UUID,
 reason VARCHAR(1000) NOT NULL CHECK(length(trim(reason))>0),
 command_fingerprint VARCHAR(64) NOT NULL CHECK(command_fingerprint ~ '^[a-f0-9]{64}$'),
 booking_version BIGINT NOT NULL,decided_at TIMESTAMPTZ NOT NULL,
 CHECK(decision<>'COMPLETED' OR (actor_id IS NOT NULL AND confirmation_method IN('COUNTERPARTY_ACKNOWLEDGED','OWNER_ADJUDICATED')))
);
CREATE TABLE peer_booking_handoffs (
 booking_id UUID PRIMARY KEY REFERENCES bookings(id),
 acquisition_id UUID NOT NULL UNIQUE REFERENCES peer_usage_decisions(acquisition_id) DEFERRABLE INITIALLY DEFERRED,
 listing_id UUID NOT NULL REFERENCES transfer_leases(listing_id),seller_id UUID NOT NULL,buyer_id UUID NOT NULL,
 amount NUMERIC(20,0) NOT NULL CHECK(amount>0),currency VARCHAR(3) NOT NULL CHECK(currency='VND'),
 terms_hash VARCHAR(64) NOT NULL,confirmation_method VARCHAR(32) NOT NULL,
 booking_version BIGINT NOT NULL,token_version INTEGER NOT NULL,transferred_at TIMESTAMPTZ NOT NULL,
 CHECK(seller_id<>buyer_id AND confirmation_method IN('COUNTERPARTY_ACKNOWLEDGED','OWNER_ADJUDICATED'))
);
CREATE TABLE booking_resale_rights (
 booking_id UUID PRIMARY KEY REFERENCES bookings(id),consumed BOOLEAN NOT NULL DEFAULT FALSE,
 acquisition_id UUID,origin VARCHAR(25) NOT NULL CHECK(origin IN('LEGACY_HISTORY','PEER_HANDOFF')),
 consumed_at TIMESTAMPTZ
);
-- Metadata only: preserve all historical holders, payment receipts and multiple legacy handoffs.
INSERT INTO booking_resale_rights(booking_id,consumed,origin)
 SELECT DISTINCT booking_id,TRUE,'LEGACY_HISTORY' FROM transfer_leases WHERE state='COMPLETED';

CREATE TABLE peer_attendance (
 acquisition_id UUID NOT NULL REFERENCES peer_usage_attempts(acquisition_id),
 party VARCHAR(6) NOT NULL CHECK(party IN('SELLER','BUYER')),actor_id UUID NOT NULL,
 observed_at TIMESTAMPTZ NOT NULL,reason VARCHAR(500) NOT NULL CHECK(length(trim(reason))>0),
 PRIMARY KEY(acquisition_id,party)
);
CREATE TABLE peer_money_cases (
 acquisition_id UUID PRIMARY KEY REFERENCES peer_usage_attempts(acquisition_id),
 facility_id UUID NOT NULL,seller_id UUID NOT NULL,buyer_id UUID NOT NULL,
 state VARCHAR(30) NOT NULL CHECK(state IN('OPEN','RETURN_REQUIRED','RETURN_RECORDED','CLOSED_UNPROVEN','CLOSED_NO_RETURN','CLOSED_RETURNED','CLOSED_MUTUAL')),
 return_amount NUMERIC(20,0) CHECK(return_amount>=0),reason VARCHAR(1000),
 version BIGINT NOT NULL DEFAULT 0,created_at TIMESTAMPTZ NOT NULL,updated_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX peer_money_cases_facility ON peer_money_cases(facility_id,state,created_at);
CREATE INDEX peer_money_cases_seller ON peer_money_cases(seller_id,state);
CREATE INDEX peer_money_cases_buyer ON peer_money_cases(buyer_id,state);
CREATE TABLE peer_late_payment_reports (
 id UUID PRIMARY KEY,acquisition_id UUID NOT NULL REFERENCES peer_usage_attempts(acquisition_id),reporter_id UUID NOT NULL,
 terms_hash VARCHAR(64) NOT NULL CHECK(terms_hash ~ '^[a-f0-9]{64}$'),
 bank_reference VARCHAR(120) NOT NULL CHECK(length(trim(bank_reference))>0 AND bank_reference !~ '[[:cntrl:]]'),
 evidence_ids UUID[] NOT NULL CHECK(cardinality(evidence_ids) BETWEEN 1 AND 3),
 fingerprint VARCHAR(64) NOT NULL CHECK(fingerprint ~ '^[a-f0-9]{64}$'),reported_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX peer_late_reports_attempt ON peer_late_payment_reports(acquisition_id,reported_at,id);
CREATE TRIGGER immutable_peer_late_report BEFORE UPDATE OR DELETE ON peer_late_payment_reports
 FOR EACH ROW EXECUTE FUNCTION immutable_cancellation_decision_evidence();
CREATE TABLE peer_money_no_claims (
 acquisition_id UUID NOT NULL REFERENCES peer_usage_attempts(acquisition_id),actor_id UUID NOT NULL,
 fingerprint VARCHAR(64) NOT NULL CHECK(fingerprint ~ '^[a-f0-9]{64}$'),created_at TIMESTAMPTZ NOT NULL,
 PRIMARY KEY(acquisition_id,actor_id)
);
CREATE TABLE peer_return_obligations (
 return_id UUID PRIMARY KEY,acquisition_id UUID NOT NULL REFERENCES peer_usage_attempts(acquisition_id),
 seller_id UUID NOT NULL,buyer_id UUID NOT NULL,amount NUMERIC(20,0) NOT NULL CHECK(amount>0),
 terms_hash VARCHAR(64) NOT NULL CHECK(terms_hash ~ '^[a-f0-9]{64}$'),
 bank_reference VARCHAR(120) NOT NULL CHECK(length(trim(bank_reference))>0 AND bank_reference !~ '[[:cntrl:]]'),
 evidence_ids UUID[] NOT NULL CHECK(cardinality(evidence_ids) BETWEEN 1 AND 3),
 state VARCHAR(25) NOT NULL CHECK(state IN('AWAITING_BUYER','BUYER_ACKNOWLEDGED','WAIVED_BY_BOTH')),
 acknowledged_by UUID,finalized_at TIMESTAMPTZ,version BIGINT NOT NULL DEFAULT 0 CHECK(version>=0),
 created_at TIMESTAMPTZ NOT NULL,updated_at TIMESTAMPTZ NOT NULL,
 CHECK((state='AWAITING_BUYER')=(finalized_at IS NULL)),
 CHECK((state='BUYER_ACKNOWLEDGED')=(acknowledged_by IS NOT NULL)),
 CHECK(acknowledged_by IS NULL OR acknowledged_by=buyer_id)
);
CREATE INDEX peer_return_parties ON peer_return_obligations(acquisition_id,state);
CREATE INDEX peer_return_pages ON peer_return_obligations(acquisition_id,return_id);
CREATE UNIQUE INDEX peer_return_reference ON peer_return_obligations(acquisition_id,upper(trim(bank_reference)));
CREATE INDEX peer_return_seller ON peer_return_obligations(seller_id) WHERE state='AWAITING_BUYER';
CREATE INDEX peer_return_buyer ON peer_return_obligations(buyer_id) WHERE state='AWAITING_BUYER';
CREATE FUNCTION guard_peer_return_obligation() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE a peer_usage_attempts%ROWTYPE;n INTEGER;
BEGIN
 IF TG_OP='DELETE' THEN RAISE EXCEPTION 'Return history cannot be deleted' USING ERRCODE='23514';END IF;
 SELECT * INTO a FROM peer_usage_attempts WHERE acquisition_id=NEW.acquisition_id;
 IF TG_TABLE_NAME='peer_money_no_claims' THEN
  IF NEW.actor_id NOT IN(a.seller_id,a.buyer_id) THEN RAISE EXCEPTION 'Only counterparties waive their claims' USING ERRCODE='23514';END IF;
  RETURN NEW;
 END IF;
 IF (NEW.seller_id,NEW.buyer_id,NEW.terms_hash) IS DISTINCT FROM(a.seller_id,a.buyer_id,a.terms_hash) THEN
  RAISE EXCEPTION 'Peer return must retain its original counterparties and terms' USING ERRCODE='23514';END IF;
 IF TG_OP='INSERT' THEN
  PERFORM pg_advisory_xact_lock(hashtextextended(NEW.acquisition_id::text,7063));
  IF NEW.state<>'AWAITING_BUYER' OR NEW.version<>0
   OR NEW.amount+COALESCE((SELECT sum(amount) FROM peer_return_obligations WHERE acquisition_id=a.acquisition_id),0)>a.amount THEN
   RAISE EXCEPTION 'Peer return needs buyer acknowledgment and cannot exceed the accepted amount' USING ERRCODE='23514';END IF;
 ELSE
  IF (to_jsonb(NEW)-ARRAY['state','acknowledged_by','finalized_at','version','updated_at']) IS DISTINCT FROM
    (to_jsonb(OLD)-ARRAY['state','acknowledged_by','finalized_at','version','updated_at']) OR OLD.state<>'AWAITING_BUYER'
    OR NEW.state NOT IN('BUYER_ACKNOWLEDGED','WAIVED_BY_BOTH') OR NEW.version<>OLD.version+1 THEN
   RAISE EXCEPTION 'Peer return identity and final acknowledgment are immutable' USING ERRCODE='23514';END IF;
  IF NEW.state='WAIVED_BY_BOTH' THEN
   SELECT count(*) INTO n FROM peer_money_no_claims WHERE acquisition_id=a.acquisition_id AND actor_id IN(a.seller_id,a.buyer_id);
   IF n<>2 THEN RAISE EXCEPTION 'Both counterparties must waive claims' USING ERRCODE='23514';END IF;
  END IF;
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER peer_return_guard BEFORE INSERT OR UPDATE OR DELETE ON peer_return_obligations FOR EACH ROW EXECUTE FUNCTION guard_peer_return_obligation();
CREATE TRIGGER peer_no_claim_guard BEFORE INSERT ON peer_money_no_claims FOR EACH ROW EXECUTE FUNCTION guard_peer_return_obligation();
CREATE TRIGGER immutable_peer_no_claim BEFORE UPDATE OR DELETE ON peer_money_no_claims FOR EACH ROW EXECUTE FUNCTION immutable_cancellation_decision_evidence();
CREATE TABLE market_fraud_strikes (
 acquisition_id UUID PRIMARY KEY REFERENCES peer_usage_decisions(acquisition_id),
 principal_id UUID NOT NULL,actor_id UUID NOT NULL,reason VARCHAR(1000) NOT NULL,
 evidence JSONB NOT NULL,recorded_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX market_strikes_principal ON market_fraud_strikes(principal_id,recorded_at);
CREATE TABLE market_strike_corrections (
 acquisition_id UUID PRIMARY KEY REFERENCES market_fraud_strikes(acquisition_id),
 actor_id UUID NOT NULL,reason VARCHAR(1000) NOT NULL CHECK(length(trim(reason))>0),
 evidence JSONB NOT NULL CHECK(jsonb_typeof(evidence)='array' AND jsonb_array_length(evidence) BETWEEN 1 AND 20),
 recorded_at TIMESTAMPTZ NOT NULL
);
CREATE TABLE peer_usage_commands (
 actor_id UUID NOT NULL,action VARCHAR(40) NOT NULL,idempotency_key VARCHAR(100) NOT NULL,
 fingerprint VARCHAR(64) NOT NULL,result JSONB NOT NULL,created_at TIMESTAMPTZ NOT NULL,
 PRIMARY KEY(actor_id,action,idempotency_key)
);
CREATE TRIGGER immutable_peer_usage_command BEFORE UPDATE OR DELETE ON peer_usage_commands
 FOR EACH ROW EXECUTE FUNCTION immutable_cancellation_decision_evidence();

CREATE FUNCTION assert_peer_handoff(aid UUID) RETURNS VOID LANGUAGE plpgsql AS $$
DECLARE h peer_booking_handoffs%ROWTYPE;d peer_usage_decisions%ROWTYPE;l transfer_leases%ROWTYPE;a peer_usage_attempts%ROWTYPE;
BEGIN
 SELECT * INTO d FROM peer_usage_decisions WHERE acquisition_id=aid;
 SELECT * INTO h FROM peer_booking_handoffs WHERE acquisition_id=aid;
 IF h.acquisition_id IS NULL AND d.decision IS DISTINCT FROM 'COMPLETED' THEN RETURN; END IF;
 IF h.acquisition_id IS NULL OR d.decision IS DISTINCT FROM 'COMPLETED' OR
  (h.booking_id,h.listing_id,h.seller_id,h.buyer_id,h.amount,h.currency,h.terms_hash,h.confirmation_method,h.booking_version,h.transferred_at)
  IS DISTINCT FROM (d.booking_id,d.listing_id,d.seller_id,d.buyer_id,d.amount,d.currency,d.terms_hash,d.confirmation_method,d.booking_version,d.decided_at)
 THEN RAISE EXCEPTION 'Peer handoff needs matching immutable decision' USING ERRCODE='23514'; END IF;
 SELECT * INTO l FROM transfer_leases WHERE listing_id=h.listing_id;
 SELECT * INTO a FROM peer_usage_attempts WHERE acquisition_id=aid;
 IF l.protocol IS DISTINCT FROM 'PEER_RESALE_V1' OR l.state IS DISTINCT FROM 'COMPLETED' OR l.payment_id IS NOT NULL OR
  (l.booking_id,l.seller_id,l.buyer_id,l.acquisition_id,l.price) IS DISTINCT FROM (h.booking_id,h.seller_id,h.buyer_id,h.acquisition_id,h.amount)
  OR NOT EXISTS(SELECT 1 FROM bookings b JOIN slot_reservations r ON r.id=b.reservation_id
    WHERE b.id=h.booking_id AND b.status='CONFIRMED' AND b.customer_id=h.buyer_id AND b.current_holder_id=h.buyer_id
      AND b.facility_id=a.facility_id AND b.court_id=a.court_id AND b.starts_at=a.starts_at AND b.ends_at=a.ends_at
      AND EXISTS(SELECT 1 FROM booking_money_cohorts m WHERE m.booking_id=b.id AND m.money_class='REAL')
      AND NOT EXISTS(SELECT 1 FROM booking_groups g WHERE g.id=b.id)
      AND r.holder_id=h.buyer_id AND b.checkin_token_version=h.token_version AND b.version=h.booking_version)
  OR NOT EXISTS(SELECT 1 FROM booking_resale_rights x WHERE x.booking_id=h.booking_id AND x.consumed AND x.acquisition_id=aid)
  OR NOT EXISTS(SELECT 1 FROM user_hold_claims c WHERE c.source_id=aid AND c.principal_id=h.buyer_id
    AND c.kind='PEER_ACQUISITION' AND c.state='COMPLETED' AND c.court_id=a.court_id AND c.starts_at=a.starts_at AND c.ends_at=a.ends_at)
  OR a.phase IS DISTINCT FROM 'COMPLETED' OR
   (a.booking_id,a.listing_id,a.seller_id,a.buyer_id,a.amount,a.currency,a.terms_hash)
    IS DISTINCT FROM(h.booking_id,h.listing_id,h.seller_id,h.buyer_id,h.amount,h.currency,h.terms_hash)
  OR h.transferred_at>=a.ends_at
  OR (h.confirmation_method='COUNTERPARTY_ACKNOWLEDGED' AND (d.actor_id<>a.seller_id OR h.transferred_at>=a.starts_at))
  OR (h.transferred_at>=a.starts_at AND NOT EXISTS(SELECT 1 FROM peer_attendance p WHERE p.acquisition_id=aid AND p.observed_at<=a.starts_at))
  OR EXISTS(SELECT 1 FROM transfer_leases legacy WHERE legacy.booking_id=h.booking_id AND legacy.protocol='LEGACY_OWNER' AND legacy.state='COMPLETED')
 THEN RAISE EXCEPTION 'Peer ownership, lease, credential and resale right must commit together' USING ERRCODE='23514'; END IF;
END $$;
CREATE FUNCTION check_peer_handoff_commit() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN PERFORM assert_peer_handoff(NEW.acquisition_id);RETURN NULL;END $$;
CREATE CONSTRAINT TRIGGER peer_handoff_commit AFTER INSERT ON peer_booking_handoffs
 DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION check_peer_handoff_commit();
CREATE CONSTRAINT TRIGGER peer_decision_commit AFTER INSERT ON peer_usage_decisions
 DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION check_peer_handoff_commit();

-- Preserve the complete legacy guard; a peer handoff uses its own proof, never a fake payment UUID.
CREATE OR REPLACE FUNCTION check_transfer_handoff_commit() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE lid UUID;h transfer_handoffs%ROWTYPE;l transfer_leases%ROWTYPE;
BEGIN
 lid=NEW.listing_id;
 IF TG_TABLE_NAME='transfer_leases' THEN
  IF NEW.state<>'COMPLETED' OR (TG_OP='UPDATE' AND OLD.state='COMPLETED') THEN RETURN NULL;END IF;
  IF NEW.protocol='PEER_RESALE_V1' THEN
   IF NOT EXISTS(SELECT 1 FROM peer_booking_handoffs WHERE acquisition_id=NEW.acquisition_id) THEN
    RAISE EXCEPTION 'Peer completion requires its handoff' USING ERRCODE='23514';END IF;
   PERFORM assert_peer_handoff(NEW.acquisition_id);RETURN NULL;
  END IF;
 END IF;
 SELECT * INTO h FROM transfer_handoffs WHERE listing_id=lid;
 IF NOT FOUND THEN RAISE EXCEPTION 'New handoff requires a durable receipt' USING ERRCODE='23514';END IF;
 SELECT * INTO l FROM transfer_leases WHERE listing_id=lid;
 IF l.protocol<>'LEGACY_OWNER' OR l.state<>'COMPLETED' OR
  (h.booking_id,h.seller_id,h.acquisition_id,h.buyer_id,h.payment_id,h.amount,h.expires_at,h.deadline)
  IS DISTINCT FROM(l.booking_id,l.seller_id,l.acquisition_id,l.buyer_id,l.payment_id,l.price,l.expires_at,l.deadline)
  OR NOT EXISTS(SELECT 1 FROM bookings b JOIN slot_reservations r ON r.id=b.reservation_id
    WHERE b.id=h.booking_id AND b.customer_id=h.buyer_id AND b.current_holder_id=h.buyer_id AND r.holder_id=h.buyer_id AND b.currency=h.currency)
 THEN RAISE EXCEPTION 'Handoff scope/ownership must commit consistently' USING ERRCODE='23514';END IF;
 RETURN NULL;
END $$;
CREATE CONSTRAINT TRIGGER transfer_initial_completion_commit AFTER INSERT ON transfer_leases
 DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION check_transfer_handoff_commit();

CREATE TRIGGER immutable_peer_decision BEFORE UPDATE OR DELETE ON peer_usage_decisions
 FOR EACH ROW EXECUTE FUNCTION immutable_cancellation_decision_evidence();
CREATE TRIGGER immutable_peer_handoff BEFORE UPDATE OR DELETE ON peer_booking_handoffs
 FOR EACH ROW EXECUTE FUNCTION immutable_cancellation_decision_evidence();
CREATE TRIGGER immutable_bank_activation BEFORE UPDATE OR DELETE ON bank_profile_activation_decisions
 FOR EACH ROW EXECUTE FUNCTION immutable_cancellation_decision_evidence();
CREATE TRIGGER immutable_abandonment BEFORE UPDATE OR DELETE ON booking_abandonment_events
 FOR EACH ROW EXECUTE FUNCTION immutable_cancellation_decision_evidence();
CREATE TRIGGER immutable_admission_success BEFORE UPDATE OR DELETE ON booking_admission_successes
 FOR EACH ROW EXECUTE FUNCTION immutable_cancellation_decision_evidence();
CREATE TRIGGER immutable_market_strike BEFORE UPDATE OR DELETE ON market_fraud_strikes
 FOR EACH ROW EXECUTE FUNCTION immutable_cancellation_decision_evidence();
CREATE TRIGGER immutable_market_strike_correction BEFORE UPDATE OR DELETE ON market_strike_corrections
 FOR EACH ROW EXECUTE FUNCTION immutable_cancellation_decision_evidence();
CREATE TRIGGER immutable_peer_attendance BEFORE UPDATE OR DELETE ON peer_attendance
 FOR EACH ROW EXECUTE FUNCTION immutable_cancellation_decision_evidence();

CREATE FUNCTION preserve_peer_attempt() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF TG_OP='DELETE' THEN RAISE EXCEPTION 'Peer attempts cannot be deleted' USING ERRCODE='23514';END IF;
 IF OLD.phase IN('COMPLETED','EXPIRED','ABORTED_A_RETAINS','TIMED_OUT_A_RETAINS') THEN
  RAISE EXCEPTION 'Terminal peer attempts are immutable' USING ERRCODE='23514';END IF;
 IF (to_jsonb(NEW)-ARRAY['phase','confirmation_method','version','payment_declared_at','seller_acknowledged_at','updated_at'])
  IS DISTINCT FROM (to_jsonb(OLD)-ARRAY['phase','confirmation_method','version','payment_declared_at','seller_acknowledged_at','updated_at'])
  OR NEW.version<>OLD.version+1 THEN
  RAISE EXCEPTION 'Peer terms are immutable and updates require a version' USING ERRCODE='23514';END IF;
 IF NEW.phase<>OLD.phase AND NOT(
  OLD.phase='WAITING_PAYMENT' AND NEW.phase IN('PAYMENT_DECLARED','EXPIRED','ABORTED_A_RETAINS')
  OR OLD.phase='PAYMENT_DECLARED' AND NEW.phase IN('DISPUTED','HANDOFF_PENDING','TIMED_OUT_A_RETAINS','ABORTED_A_RETAINS')
  OR OLD.phase='DISPUTED' AND NEW.phase IN('HANDOFF_PENDING','TIMED_OUT_A_RETAINS','ABORTED_A_RETAINS')
  OR OLD.phase='HANDOFF_PENDING' AND NEW.phase='COMPLETED') THEN
  RAISE EXCEPTION 'Invalid peer phase transition' USING ERRCODE='23514';END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER peer_attempt_identity BEFORE UPDATE OR DELETE ON peer_usage_attempts
 FOR EACH ROW EXECUTE FUNCTION preserve_peer_attempt();
CREATE FUNCTION check_peer_attempt_completion() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF NEW.phase='COMPLETED' THEN
  IF NOT EXISTS(SELECT 1 FROM peer_booking_handoffs WHERE acquisition_id=NEW.acquisition_id) THEN
   RAISE EXCEPTION 'Completed peer attempt needs its handoff' USING ERRCODE='23514';END IF;
  PERFORM assert_peer_handoff(NEW.acquisition_id);
 END IF;
 RETURN NULL;
END $$;
CREATE CONSTRAINT TRIGGER peer_attempt_completion AFTER INSERT OR UPDATE ON peer_usage_attempts
 DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION check_peer_attempt_completion();
CREATE FUNCTION preserve_bank_profile_initialization() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF TG_OP='DELETE' THEN RAISE EXCEPTION 'Bank profile history cannot be reset' USING ERRCODE='23514';END IF;
 IF OLD.initialized AND NOT NEW.initialized OR NEW.version<OLD.version THEN
  RAISE EXCEPTION 'Bank profile initialization and version cannot go backwards' USING ERRCODE='23514';END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER bank_profile_initialization BEFORE UPDATE OR DELETE ON user_bank_profiles
 FOR EACH ROW EXECUTE FUNCTION preserve_bank_profile_initialization();
CREATE FUNCTION preserve_consumed_resale_right() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF TG_OP='DELETE' OR OLD.consumed AND NEW IS DISTINCT FROM OLD THEN
  RAISE EXCEPTION 'Consumed resale rights cannot be reset' USING ERRCODE='23514';END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER consumed_resale_right BEFORE UPDATE OR DELETE ON booking_resale_rights
 FOR EACH ROW EXECUTE FUNCTION preserve_consumed_resale_right();
