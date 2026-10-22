-- Additive financial cohort. Existing escrow/refund review records are never rewritten or dispatched.
CREATE TABLE transfer_fee_policies (
 version BIGINT PRIMARY KEY CHECK(version>0), fee_bps SMALLINT NOT NULL CHECK(fee_bps=200),
 currency VARCHAR(3) NOT NULL CHECK(currency='VND'), rounding VARCHAR(20) NOT NULL CHECK(rounding='HALF_UP'),
 effective_at TIMESTAMPTZ NOT NULL
);
INSERT INTO transfer_fee_policies VALUES(1,200,'VND','HALF_UP',clock_timestamp());

CREATE TABLE seller_payout_accounts (
 id UUID PRIMARY KEY, owner_id UUID NOT NULL, provider VARCHAR(40) NOT NULL,
 provider_account VARCHAR(120) NOT NULL, currency VARCHAR(3) NOT NULL CHECK(currency='VND'),
 money_class VARCHAR(10) NOT NULL CHECK(money_class IN('REAL','DEMO')),
 created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(), UNIQUE(id,owner_id)
);
CREATE TABLE seller_payout_account_revisions (
 id UUID PRIMARY KEY, account_id UUID NOT NULL, owner_id UUID NOT NULL,
 revision INT NOT NULL CHECK(revision>0), provider VARCHAR(40) NOT NULL, provider_account VARCHAR(120) NOT NULL,
 provider_token VARCHAR(200) NOT NULL, masked_label VARCHAR(180) NOT NULL,
 verification_status VARCHAR(20) NOT NULL CHECK(verification_status IN('VERIFIED','UNVERIFIED')),
 consent_reference VARCHAR(200) NOT NULL CHECK(length(trim(consent_reference))>0),verification_proof JSONB NOT NULL,
 created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(), verified_at TIMESTAMPTZ,
 FOREIGN KEY(account_id,owner_id) REFERENCES seller_payout_accounts(id,owner_id),
 UNIQUE(account_id,revision), UNIQUE(provider,provider_account,provider_token),
 CHECK((verification_status='VERIFIED')=(verified_at IS NOT NULL))
);
CREATE TABLE seller_payout_account_revocations (
 revision_id UUID PRIMARY KEY REFERENCES seller_payout_account_revisions(id), actor_id UUID NOT NULL,
 reason VARCHAR(500) NOT NULL CHECK(length(trim(reason))>0), revoked_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);

CREATE TABLE transfer_payment_terms (
 payment_id UUID PRIMARY KEY REFERENCES payment_orders(id), acquisition_id UUID NOT NULL,
 listing_id UUID NOT NULL, booking_id UUID NOT NULL, facility_id UUID NOT NULL, court_id UUID NOT NULL,
 seller_id UUID NOT NULL, buyer_id UUID NOT NULL, policy_version BIGINT NOT NULL REFERENCES transfer_fee_policies(version),
 fee_bps SMALLINT NOT NULL CHECK(fee_bps=200), rounding VARCHAR(20) NOT NULL CHECK(rounding='HALF_UP'),
 amount NUMERIC(20,0) NOT NULL CHECK(amount>0), quoted_fee_amount NUMERIC(20,0) NOT NULL CHECK(quoted_fee_amount>=0),
 quoted_seller_net_amount NUMERIC(20,0) NOT NULL CHECK(quoted_seller_net_amount>0),
 currency VARCHAR(3) NOT NULL CHECK(currency='VND'),
 beneficiary_revision_id UUID NOT NULL REFERENCES seller_payout_account_revisions(id),
 terms_hash VARCHAR(64) NOT NULL CHECK(terms_hash ~ '^[a-f0-9]{64}$'),
 refund_policy JSONB NOT NULL CHECK(jsonb_typeof(refund_policy)='object'),snapshot JSONB NOT NULL CHECK(jsonb_typeof(snapshot)='object'),
 accepted_at TIMESTAMPTZ NOT NULL,
 CHECK(seller_id<>buyer_id), CHECK(amount=quoted_fee_amount+quoted_seller_net_amount),
 CHECK(quoted_fee_amount=round(amount*fee_bps/10000,0)), UNIQUE(payment_id,acquisition_id)
);
CREATE FUNCTION validate_transfer_payment_terms() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE p payment_orders%ROWTYPE; b seller_payout_account_revisions%ROWTYPE;
BEGIN
 SELECT * INTO p FROM payment_orders WHERE id=NEW.payment_id;
 SELECT * INTO b FROM seller_payout_account_revisions WHERE id=NEW.beneficiary_revision_id;
 IF p.purpose IS DISTINCT FROM 'TRANSFER' OR
  (p.booking_id,p.acquisition_id,p.seller_id,p.payer_id,p.amount,p.currency) IS DISTINCT FROM
  (NEW.booking_id,NEW.acquisition_id,NEW.seller_id,NEW.buyer_id,NEW.amount,NEW.currency) OR
  b.owner_id IS DISTINCT FROM NEW.seller_id OR b.provider IS DISTINCT FROM p.provider OR b.verification_status IS DISTINCT FROM 'VERIFIED' OR
  NOT (NEW.snapshot ?& ARRAY['listingId','acquisitionId','bookingId','sellerId','buyerId','amount','quotedFeeAmount','quotedSellerNetAmount','feeBps','beneficiaryRevisionId','termsHash','refundPolicy','deadline','expiresAt','acceptedAt']) OR
  (NEW.snapshot->>'listingId',NEW.snapshot->>'acquisitionId',NEW.snapshot->>'bookingId',NEW.snapshot->>'sellerId',NEW.snapshot->>'buyerId',NEW.snapshot->>'beneficiaryRevisionId',NEW.snapshot->>'termsHash') IS DISTINCT FROM
  (NEW.listing_id::text,NEW.acquisition_id::text,NEW.booking_id::text,NEW.seller_id::text,NEW.buyer_id::text,NEW.beneficiary_revision_id::text,NEW.terms_hash) OR
  (NEW.snapshot->>'amount')::numeric IS DISTINCT FROM NEW.amount OR (NEW.snapshot->>'expiresAt')::timestamptz IS DISTINCT FROM p.expires_at OR
  NEW.snapshot->'refundPolicy' IS DISTINCT FROM NEW.refund_policy
 THEN RAISE EXCEPTION 'Transfer terms do not match their payment or verified seller' USING ERRCODE='23514'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER transfer_payment_terms_binding BEFORE INSERT ON transfer_payment_terms FOR EACH ROW EXECUTE FUNCTION validate_transfer_payment_terms();

CREATE TABLE transfer_settlements (
 id UUID PRIMARY KEY, acquisition_id UUID NOT NULL UNIQUE, payment_id UUID NOT NULL UNIQUE REFERENCES payment_orders(id),
 origin VARCHAR(20) NOT NULL CHECK(origin IN('NEW_TERMS','LEGACY_IMPORT')),
 state VARCHAR(30) NOT NULL CHECK(state IN('WAITING_HANDOFF','ALLOCATED','ABORTED','REVIEW','LEGACY_REVIEW')),
 provider VARCHAR(40) NOT NULL, provider_account VARCHAR(120) NOT NULL,
 money_class VARCHAR(10) NOT NULL CHECK(money_class IN('REAL','DEMO','UNKNOWN')),
 currency VARCHAR(3) NOT NULL CHECK(currency='VND'),
 listing_id UUID,booking_id UUID NOT NULL,facility_id UUID,court_id UUID,seller_id UUID,buyer_id UUID NOT NULL,
 amount NUMERIC(20,0) NOT NULL CHECK(amount>0),quoted_fee_amount NUMERIC(20,0),quoted_seller_net_amount NUMERIC(20,0),
 fee_bps SMALLINT,policy_version BIGINT REFERENCES transfer_fee_policies(version),rounding VARCHAR(20),terms_hash VARCHAR(64),
 beneficiary_revision_id UUID REFERENCES seller_payout_account_revisions(id),
 decision_ref UUID,decision_fingerprint VARCHAR(64),decided_at TIMESTAMPTZ,
 version BIGINT NOT NULL DEFAULT 0 CHECK(version>=0),created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),updated_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
 CHECK(origin<>'NEW_TERMS' OR (listing_id IS NOT NULL AND facility_id IS NOT NULL AND court_id IS NOT NULL AND seller_id IS NOT NULL
   AND quoted_fee_amount IS NOT NULL AND quoted_seller_net_amount IS NOT NULL AND fee_bps IS NOT NULL AND fee_bps=200
   AND policy_version IS NOT NULL AND rounding IS NOT NULL AND rounding='HALF_UP' AND terms_hash IS NOT NULL AND length(terms_hash)=64 AND beneficiary_revision_id IS NOT NULL
   AND quoted_fee_amount=round(amount*fee_bps/10000,0) AND quoted_seller_net_amount>0 AND amount=quoted_fee_amount+quoted_seller_net_amount)),
 CHECK(origin<>'LEGACY_IMPORT' OR state='LEGACY_REVIEW'),
 CHECK(state NOT IN('ALLOCATED','ABORTED') OR (decision_ref IS NOT NULL AND decision_fingerprint IS NOT NULL AND decided_at IS NOT NULL))
);
CREATE FUNCTION validate_transfer_settlement() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE p payment_orders%ROWTYPE;t transfer_payment_terms%ROWTYPE;
BEGIN
 IF TG_OP='UPDATE' THEN
  IF (to_jsonb(NEW)-ARRAY['state','decision_ref','decision_fingerprint','decided_at','version','updated_at']) IS DISTINCT FROM
     (to_jsonb(OLD)-ARRAY['state','decision_ref','decision_fingerprint','decided_at','version','updated_at']) OR NEW.version<>OLD.version+1 OR
     (OLD.decision_ref IS NOT NULL AND (NEW.decision_ref,NEW.decision_fingerprint,NEW.decided_at) IS DISTINCT FROM (OLD.decision_ref,OLD.decision_fingerprint,OLD.decided_at)) OR
     (OLD.state IN('ALLOCATED','ABORTED','LEGACY_REVIEW') AND NEW.state<>OLD.state)
  THEN RAISE EXCEPTION 'Settlement identity and terminal decision are immutable' USING ERRCODE='23514'; END IF;
 END IF;
 SELECT * INTO p FROM payment_orders WHERE id=NEW.payment_id;
 IF p.status IS DISTINCT FROM 'SUCCESS' OR p.purpose IS DISTINCT FROM 'TRANSFER' OR
  (p.acquisition_id,p.booking_id,p.payer_id,p.amount,p.currency,p.provider) IS DISTINCT FROM
  (NEW.acquisition_id,NEW.booking_id,NEW.buyer_id,NEW.amount,NEW.currency,NEW.provider)
 THEN RAISE EXCEPTION 'Settlement requires a verified matching transfer collection' USING ERRCODE='23514'; END IF;
 IF NEW.origin='NEW_TERMS' THEN
  SELECT * INTO t FROM transfer_payment_terms WHERE payment_id=NEW.payment_id;
  IF NOT FOUND OR (t.acquisition_id,t.listing_id,t.booking_id,t.facility_id,t.court_id,t.seller_id,t.buyer_id,t.amount,t.quoted_fee_amount,t.quoted_seller_net_amount,t.fee_bps,t.policy_version,t.rounding,t.terms_hash,t.beneficiary_revision_id)
    IS DISTINCT FROM (NEW.acquisition_id,NEW.listing_id,NEW.booking_id,NEW.facility_id,NEW.court_id,NEW.seller_id,NEW.buyer_id,NEW.amount,NEW.quoted_fee_amount,NEW.quoted_seller_net_amount,NEW.fee_bps,NEW.policy_version,NEW.rounding,NEW.terms_hash,NEW.beneficiary_revision_id) OR
    NOT EXISTS(SELECT 1 FROM payment_obligations o WHERE o.settled_payment_id=NEW.payment_id)
  THEN RAISE EXCEPTION 'Settlement requires canonical immutable terms' USING ERRCODE='23514'; END IF;
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER settlement_binding BEFORE INSERT OR UPDATE ON transfer_settlements FOR EACH ROW EXECUTE FUNCTION validate_transfer_settlement();

CREATE TABLE facility_refund_requests (
 id UUID PRIMARY KEY,cancellation_id UUID NOT NULL UNIQUE,booking_id UUID NOT NULL,facility_id UUID NOT NULL,court_id UUID NOT NULL,
 booking_version BIGINT NOT NULL,ownership_version BIGINT NOT NULL,policy_version BIGINT NOT NULL CHECK(policy_version>0),
 policy_hash VARCHAR(64) NOT NULL CHECK(policy_hash ~ '^[a-f0-9]{64}$'),rate_bps SMALLINT NOT NULL CHECK(rate_bps BETWEEN 0 AND 10000),
 base_amount NUMERIC(20,0) NOT NULL CHECK(base_amount>=0),target_amount NUMERIC(20,0) NOT NULL CHECK(target_amount>=0 AND target_amount<=base_amount),
 currency VARCHAR(3) NOT NULL CHECK(currency='VND'),rounding VARCHAR(20) NOT NULL CHECK(rounding='HALF_UP'),
 money_class VARCHAR(10) NOT NULL CHECK(money_class IN('REAL','DEMO','UNKNOWN')),
 reason VARCHAR(500) NOT NULL CHECK(length(trim(reason))>0),requested_by UUID NOT NULL,authorized_by UUID,
 decision_proof JSONB NOT NULL,authorization_proof JSONB,
 state VARCHAR(40) NOT NULL CHECK(state IN('AWAITING_AUTHORIZATION','FUNDING_PENDING','READY','PROCESSING','SUCCEEDED','REVIEW','NO_REFUND')),
 version BIGINT NOT NULL DEFAULT 0 CHECK(version>=0),created_at TIMESTAMPTZ NOT NULL,authorized_at TIMESTAMPTZ,updated_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
 CHECK(target_amount=round(base_amount*rate_bps/10000,0)), CHECK((authorized_by IS NULL)=(authorized_at IS NULL))
);
CREATE TABLE facility_refund_allocations (
 id UUID PRIMARY KEY,request_id UUID NOT NULL REFERENCES facility_refund_requests(id),payment_id UUID NOT NULL REFERENCES payment_orders(id),
 payer_id UUID NOT NULL,base_amount NUMERIC(20,0) NOT NULL CHECK(base_amount>0),amount NUMERIC(20,0) NOT NULL CHECK(amount>=0 AND amount<=base_amount),
 evidence_hash VARCHAR(64) NOT NULL,UNIQUE(request_id,payment_id)
);
CREATE TABLE facility_funding_intents (
 id UUID PRIMARY KEY,facility_id UUID NOT NULL,request_id UUID REFERENCES facility_refund_requests(id),actor_id UUID NOT NULL,
 amount NUMERIC(20,0) NOT NULL CHECK(amount>0),currency VARCHAR(3) NOT NULL CHECK(currency='VND'),
 money_class VARCHAR(10) NOT NULL CHECK(money_class IN('REAL','DEMO')),provider VARCHAR(40) NOT NULL,provider_account VARCHAR(120) NOT NULL,
 source_kind VARCHAR(30) NOT NULL CHECK(source_kind IN('COURT_ALLOCATION','TOP_UP')),source_ref VARCHAR(200) NOT NULL,source_payment_id UUID UNIQUE REFERENCES payment_orders(id),
 state VARCHAR(20) NOT NULL CHECK(state IN('PENDING','VERIFIED','REVIEW')),proof JSONB,created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),verified_at TIMESTAMPTZ,
 UNIQUE(provider,provider_account,source_ref),CHECK(state<>'VERIFIED' OR (proof IS NOT NULL AND verified_at IS NOT NULL)),
 CHECK((source_kind='COURT_ALLOCATION')=(source_payment_id IS NOT NULL))
);
CREATE TABLE financial_funding_buckets (
 id UUID PRIMARY KEY,scope_kind VARCHAR(30) NOT NULL CHECK(scope_kind IN('TRANSFER_COLLECTION','FACILITY_FUNDS')),
 payment_id UUID UNIQUE REFERENCES payment_orders(id),funding_intent_id UUID UNIQUE REFERENCES facility_funding_intents(id),facility_id UUID,
 provider VARCHAR(40) NOT NULL,provider_account VARCHAR(120) NOT NULL,currency VARCHAR(3) NOT NULL CHECK(currency='VND'),
 money_class VARCHAR(10) NOT NULL CHECK(money_class IN('REAL','DEMO','UNKNOWN')),
 CHECK((scope_kind='TRANSFER_COLLECTION' AND payment_id IS NOT NULL AND funding_intent_id IS NULL AND facility_id IS NULL) OR
  (scope_kind='FACILITY_FUNDS' AND payment_id IS NULL AND funding_intent_id IS NOT NULL AND facility_id IS NOT NULL))
);

CREATE TABLE money_movement_orders (
 id UUID PRIMARY KEY,kind VARCHAR(30) NOT NULL CHECK(kind IN('SELLER_PAYOUT','BUYER_REFUND','FACILITY_POLICY_REFUND','RECOVERY')),
 business_key VARCHAR(200) NOT NULL UNIQUE,payment_id UUID NOT NULL REFERENCES payment_orders(id),
 settlement_id UUID REFERENCES transfer_settlements(id),refund_allocation_id UUID UNIQUE REFERENCES facility_refund_allocations(id),
 payee_id UUID NOT NULL,beneficiary_revision_id UUID REFERENCES seller_payout_account_revisions(id),
 amount NUMERIC(20,0) NOT NULL CHECK(amount>0),currency VARCHAR(3) NOT NULL CHECK(currency='VND'),
 money_class VARCHAR(10) NOT NULL CHECK(money_class IN('REAL','DEMO','UNKNOWN')),provider VARCHAR(40) NOT NULL,provider_account VARCHAR(120) NOT NULL,
 state VARCHAR(30) NOT NULL CHECK(state IN('NEW','FUNDING_PENDING','READY','SUBMITTING','PROCESSING','SUCCEEDED','FAILED_RETRYABLE','FAILED_FINAL','UNKNOWN','REVIEW','RETURNED')),
 executed_total NUMERIC(20,0) NOT NULL DEFAULT 0 CHECK(executed_total>=0),returned_total NUMERIC(20,0) NOT NULL DEFAULT 0 CHECK(returned_total>=0),
 version BIGINT NOT NULL DEFAULT 0 CHECK(version>=0),next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
 proof JSONB NOT NULL,created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),updated_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
 CHECK(returned_total<=executed_total AND executed_total-returned_total<=amount),
 CHECK((kind='SELLER_PAYOUT' AND settlement_id IS NOT NULL AND beneficiary_revision_id IS NOT NULL AND refund_allocation_id IS NULL) OR
  (kind='BUYER_REFUND' AND beneficiary_revision_id IS NULL AND refund_allocation_id IS NULL) OR
  (kind='FACILITY_POLICY_REFUND' AND refund_allocation_id IS NOT NULL AND beneficiary_revision_id IS NULL) OR kind='RECOVERY')
);
CREATE UNIQUE INDEX uq_seller_payout_entitlement ON money_movement_orders(settlement_id) WHERE kind='SELLER_PAYOUT';
CREATE FUNCTION validate_money_movement() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE p payment_orders%ROWTYPE;s transfer_settlements%ROWTYPE;a facility_refund_allocations%ROWTYPE;total NUMERIC;
BEGIN
 IF TG_OP='UPDATE' AND ((to_jsonb(NEW)-ARRAY['state','executed_total','returned_total','version','next_attempt_at','updated_at']) IS DISTINCT FROM
   (to_jsonb(OLD)-ARRAY['state','executed_total','returned_total','version','next_attempt_at','updated_at']) OR NEW.version<>OLD.version+1 OR
   NEW.executed_total<OLD.executed_total OR NEW.returned_total<OLD.returned_total)
 THEN RAISE EXCEPTION 'Money movement entitlement is immutable' USING ERRCODE='23514'; END IF;
 SELECT * INTO p FROM payment_orders WHERE id=NEW.payment_id FOR UPDATE;
 IF p.status IS DISTINCT FROM 'SUCCESS' OR p.currency<>NEW.currency OR p.provider<>NEW.provider THEN
  RAISE EXCEPTION 'Movement requires verified matching received money' USING ERRCODE='23514'; END IF;
 IF NEW.kind='SELLER_PAYOUT' THEN
  SELECT * INTO s FROM transfer_settlements WHERE id=NEW.settlement_id;
  IF s.state IS DISTINCT FROM 'ALLOCATED' OR s.origin IS DISTINCT FROM 'NEW_TERMS' OR s.payment_id<>NEW.payment_id OR
    (s.seller_id,s.quoted_seller_net_amount,s.beneficiary_revision_id,s.money_class,s.provider_account) IS DISTINCT FROM
    (NEW.payee_id,NEW.amount,NEW.beneficiary_revision_id,NEW.money_class,NEW.provider_account)
  THEN RAISE EXCEPTION 'Payout differs from the verified seller entitlement' USING ERRCODE='23514'; END IF;
 ELSIF NEW.kind IN('BUYER_REFUND','FACILITY_POLICY_REFUND') THEN
  IF NEW.payee_id<>p.payer_id THEN RAISE EXCEPTION 'Refund must return to original payer' USING ERRCODE='23514'; END IF;
  SELECT COALESCE(sum(amount),0) INTO total FROM money_movement_orders WHERE payment_id=NEW.payment_id AND kind IN('BUYER_REFUND','FACILITY_POLICY_REFUND') AND id<>NEW.id;
  IF total+NEW.amount>p.amount THEN RAISE EXCEPTION 'Refund exceeds shared received-payment cap' USING ERRCODE='23514'; END IF;
  IF NEW.kind='BUYER_REFUND' THEN
   IF NEW.settlement_id IS NOT NULL THEN
    SELECT * INTO s FROM transfer_settlements WHERE id=NEW.settlement_id;
    IF s.state IS DISTINCT FROM 'ABORTED' OR s.payment_id IS DISTINCT FROM NEW.payment_id OR NEW.amount<>p.amount OR
       NEW.proof->>'acquisitionId' IS DISTINCT FROM s.acquisition_id::text OR NEW.proof->>'decisionFingerprint' IS DISTINCT FROM s.decision_fingerprint THEN
     RAISE EXCEPTION 'Technical refund needs the matching abort decision and full original price' USING ERRCODE='23514'; END IF;
   ELSIF NOT EXISTS(SELECT 1 FROM payment_obligations c WHERE c.booking_id=p.booking_id AND c.purpose=p.purpose AND c.subject_id=p.acquisition_id
      AND c.settled_payment_id IS NOT NULL AND c.settled_payment_id<>p.id AND c.settled_payment_id::text=NEW.proof->>'canonicalPaymentId'
      AND NEW.proof->>'decision'='NON_CANONICAL_COLLECTION' AND NEW.amount=p.amount) THEN
    RAISE EXCEPTION 'Noncanonical refund needs verified canonical binding' USING ERRCODE='23514';
   END IF;
  END IF;
  IF NEW.kind='FACILITY_POLICY_REFUND' THEN
   SELECT * INTO a FROM facility_refund_allocations WHERE id=NEW.refund_allocation_id;
   IF (a.payment_id,a.payer_id,a.amount) IS DISTINCT FROM (NEW.payment_id,NEW.payee_id,NEW.amount) THEN
    RAISE EXCEPTION 'Policy movement differs from its allocation' USING ERRCODE='23514'; END IF;
  END IF;
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER movement_entitlement_guard BEFORE INSERT OR UPDATE ON money_movement_orders FOR EACH ROW EXECUTE FUNCTION validate_money_movement();

CREATE TABLE money_movement_attempts (
 id UUID PRIMARY KEY,order_id UUID NOT NULL REFERENCES money_movement_orders(id),attempt_no INT NOT NULL CHECK(attempt_no>0),
 amount NUMERIC(20,0) NOT NULL CHECK(amount>0),beneficiary_revision_id UUID REFERENCES seller_payout_account_revisions(id),
 funding_bucket_id UUID NOT NULL REFERENCES financial_funding_buckets(id),
 provider VARCHAR(40) NOT NULL,provider_account VARCHAR(120) NOT NULL,region VARCHAR(40) NOT NULL,
 idempotency_key VARCHAR(150) NOT NULL,request_fingerprint VARCHAR(64) NOT NULL CHECK(length(request_fingerprint)=64),
 request_body JSONB NOT NULL,key_expires_at TIMESTAMPTZ NOT NULL,
 provider_reference VARCHAR(200),state VARCHAR(30) NOT NULL CHECK(state IN('SUBMITTING','PROCESSING','UNKNOWN','SUCCEEDED','FAILED_NO_OUTFLOW','REVIEW')),
 debit_amount NUMERIC(20,0) NOT NULL DEFAULT 0,final_amount NUMERIC(20,0) NOT NULL DEFAULT 0,returned_amount NUMERIC(20,0) NOT NULL DEFAULT 0,
 lease_until TIMESTAMPTZ NOT NULL,next_query_at TIMESTAMPTZ NOT NULL,submit_count INT NOT NULL DEFAULT 0 CHECK(submit_count>=0),
 last_error_code VARCHAR(80),version BIGINT NOT NULL DEFAULT 0,created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),submitted_at TIMESTAMPTZ,final_at TIMESTAMPTZ,
 UNIQUE(order_id,attempt_no),UNIQUE(provider,provider_account,region,idempotency_key),UNIQUE(provider,provider_account,provider_reference),
 CHECK(key_expires_at>created_at),CHECK(debit_amount BETWEEN 0 AND amount AND final_amount BETWEEN 0 AND amount AND returned_amount BETWEEN 0 AND final_amount),
 CHECK(state<>'SUCCEEDED' OR (final_amount=amount AND final_at IS NOT NULL))
);
CREATE UNIQUE INDEX uq_active_movement_attempt ON money_movement_attempts(order_id) WHERE state IN('SUBMITTING','PROCESSING','UNKNOWN','REVIEW');
CREATE TABLE financial_funding_reservations (
 attempt_id UUID PRIMARY KEY REFERENCES money_movement_attempts(id),bucket_id UUID NOT NULL REFERENCES financial_funding_buckets(id),
 original_amount NUMERIC(20,0) NOT NULL CHECK(original_amount>0),remaining_amount NUMERIC(20,0) NOT NULL CHECK(remaining_amount>=0 AND remaining_amount<=original_amount),
 version BIGINT NOT NULL DEFAULT 0,created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);
CREATE TABLE money_movement_events (
 id UUID PRIMARY KEY,provider VARCHAR(40) NOT NULL,provider_account VARCHAR(120) NOT NULL,event_id VARCHAR(200) NOT NULL,
 attempt_id UUID REFERENCES money_movement_attempts(id),order_id UUID REFERENCES money_movement_orders(id),
 source VARCHAR(20) NOT NULL CHECK(source IN('WEBHOOK','QUERY','STATEMENT','SUBMIT')),
 fingerprint VARCHAR(64) NOT NULL CHECK(length(fingerprint)=64),event_kind VARCHAR(30) NOT NULL,
 amount NUMERIC(20,0) NOT NULL CHECK(amount>=0),currency VARCHAR(3) NOT NULL CHECK(currency='VND'),
 counterparty_reference VARCHAR(200) NOT NULL,proof JSONB NOT NULL,occurred_at TIMESTAMPTZ NOT NULL,received_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
 UNIQUE(provider,provider_account,event_id)
);
CREATE TABLE movement_command_outbox (
 attempt_id UUID PRIMARY KEY REFERENCES money_movement_attempts(id),request_fingerprint VARCHAR(64) NOT NULL,
 created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),dispatched_at TIMESTAMPTZ
);
CREATE FUNCTION validate_movement_attempt() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE o money_movement_orders%ROWTYPE;b financial_funding_buckets%ROWTYPE;r facility_refund_requests%ROWTYPE;destination seller_payout_account_revisions%ROWTYPE;
BEGIN
 IF TG_OP='DELETE' THEN RAISE EXCEPTION 'Movement attempts cannot be deleted' USING ERRCODE='23514'; END IF;
 IF TG_OP='UPDATE' AND ((to_jsonb(NEW)-ARRAY['provider_reference','state','debit_amount','final_amount','returned_amount','lease_until','next_query_at','submit_count','last_error_code','version','submitted_at','final_at']) IS DISTINCT FROM
   (to_jsonb(OLD)-ARRAY['provider_reference','state','debit_amount','final_amount','returned_amount','lease_until','next_query_at','submit_count','last_error_code','version','submitted_at','final_at']) OR NEW.version<>OLD.version+1 OR
   NEW.debit_amount<OLD.debit_amount OR NEW.final_amount<OLD.final_amount OR NEW.returned_amount<OLD.returned_amount OR
   (OLD.provider_reference IS NOT NULL AND NEW.provider_reference IS DISTINCT FROM OLD.provider_reference) OR
   (OLD.state IN('SUCCEEDED','FAILED_NO_OUTFLOW') AND NEW.state<>OLD.state))
 THEN RAISE EXCEPTION 'Movement attempt identity and final evidence are immutable' USING ERRCODE='23514'; END IF;
 SELECT * INTO o FROM money_movement_orders WHERE id=NEW.order_id FOR UPDATE;
 SELECT * INTO b FROM financial_funding_buckets WHERE id=NEW.funding_bucket_id;
 IF (NEW.provider,NEW.provider_account) IS DISTINCT FROM (o.provider,o.provider_account) OR
    (b.provider,b.provider_account,b.currency,b.money_class) IS DISTINCT FROM (o.provider,o.provider_account,o.currency,o.money_class) OR
    NEW.amount>o.amount OR NEW.state='FAILED_NO_OUTFLOW' AND (NEW.debit_amount>0 OR NEW.final_amount>0)
 THEN RAISE EXCEPTION 'Movement attempt scope differs from its order/funding' USING ERRCODE='23514'; END IF;
 IF o.kind IN('SELLER_PAYOUT','BUYER_REFUND') AND (b.scope_kind<>'TRANSFER_COLLECTION' OR b.payment_id<>o.payment_id) THEN
  RAISE EXCEPTION 'Transfer movement cannot spend another payment bucket' USING ERRCODE='23514'; END IF;
 IF o.kind='FACILITY_POLICY_REFUND' THEN
  SELECT f.* INTO r FROM facility_refund_requests f JOIN facility_refund_allocations a ON a.request_id=f.id WHERE a.id=o.refund_allocation_id;
  IF b.scope_kind IS DISTINCT FROM 'FACILITY_FUNDS' OR b.facility_id IS DISTINCT FROM r.facility_id THEN
   RAISE EXCEPTION 'Policy refund requires its own facility funds' USING ERRCODE='23514'; END IF;
 END IF;
 IF o.kind='SELLER_PAYOUT' THEN
  SELECT * INTO destination FROM seller_payout_account_revisions WHERE id=NEW.beneficiary_revision_id;
  IF destination.owner_id IS DISTINCT FROM o.payee_id OR destination.verification_status IS DISTINCT FROM 'VERIFIED' THEN
   RAISE EXCEPTION 'Payout destination must remain verified and owned by the seller' USING ERRCODE='23514'; END IF;
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER movement_attempt_scope BEFORE INSERT OR UPDATE OR DELETE ON money_movement_attempts FOR EACH ROW EXECUTE FUNCTION validate_movement_attempt();
CREATE FUNCTION check_movement_projection() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE oid UUID;o money_movement_orders%ROWTYPE;paid NUMERIC;returned NUMERIC;active NUMERIC;
BEGIN
 IF TG_TABLE_NAME='money_movement_orders' THEN oid=NEW.id;ELSE oid=NEW.order_id;END IF;
 SELECT * INTO o FROM money_movement_orders WHERE id=oid;
 SELECT COALESCE(sum(final_amount),0),COALESCE(sum(returned_amount),0),COALESCE(sum(amount-final_amount) FILTER(WHERE state IN('SUBMITTING','PROCESSING','UNKNOWN','REVIEW')),0)
 INTO paid,returned,active FROM money_movement_attempts WHERE order_id=oid;
 IF paid<>o.executed_total OR returned<>o.returned_total OR paid-returned+active>o.amount THEN
  RAISE EXCEPTION 'Movement projection exceeds its immutable entitlement' USING ERRCODE='23514'; END IF;
 RETURN NULL;
END $$;
CREATE CONSTRAINT TRIGGER movement_order_projection AFTER INSERT OR UPDATE ON money_movement_orders DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION check_movement_projection();
CREATE CONSTRAINT TRIGGER movement_attempt_projection AFTER INSERT OR UPDATE ON money_movement_attempts DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION check_movement_projection();
CREATE FUNCTION guard_facility_refund_request() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF TG_OP='DELETE' THEN RAISE EXCEPTION 'Facility refund entitlement cannot be deleted' USING ERRCODE='23514'; END IF;
 IF (to_jsonb(NEW)-ARRAY['state','authorized_by','authorized_at','authorization_proof','version','updated_at']) IS DISTINCT FROM
    (to_jsonb(OLD)-ARRAY['state','authorized_by','authorized_at','authorization_proof','version','updated_at']) OR NEW.version<>OLD.version+1 OR
    (OLD.authorized_by IS NOT NULL AND (NEW.authorized_by,NEW.authorized_at,NEW.authorization_proof) IS DISTINCT FROM (OLD.authorized_by,OLD.authorized_at,OLD.authorization_proof)) THEN
  RAISE EXCEPTION 'Facility refund identity and authorization are immutable' USING ERRCODE='23514'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER facility_refund_request_identity BEFORE UPDATE OR DELETE ON facility_refund_requests FOR EACH ROW EXECUTE FUNCTION guard_facility_refund_request();
CREATE FUNCTION validate_refund_allocations() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE rid UUID;r facility_refund_requests%ROWTYPE;total_base NUMERIC;total_refund NUMERIC;
BEGIN
 IF TG_TABLE_NAME='facility_refund_requests' THEN rid=NEW.id; ELSE rid=NEW.request_id; END IF;
 SELECT * INTO r FROM facility_refund_requests WHERE id=rid;
 SELECT COALESCE(sum(base_amount),0),COALESCE(sum(amount),0) INTO total_base,total_refund FROM facility_refund_allocations WHERE request_id=rid;
 IF total_base<>r.base_amount OR total_refund<>r.target_amount OR EXISTS(
  SELECT 1 FROM facility_refund_allocations a JOIN payment_orders p ON p.id=a.payment_id WHERE a.request_id=rid AND
   (p.status<>'SUCCESS' OR p.payer_id<>a.payer_id OR p.amount<a.base_amount OR p.currency<>r.currency OR p.booking_id<>r.booking_id)) THEN
  RAISE EXCEPTION 'Refund allocations must exactly bind original received funds and target' USING ERRCODE='23514'; END IF;
 RETURN NULL;
END $$;
CREATE CONSTRAINT TRIGGER refund_request_allocations AFTER INSERT ON facility_refund_requests DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION validate_refund_allocations();
CREATE CONSTRAINT TRIGGER refund_allocation_totals AFTER INSERT ON facility_refund_allocations DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION validate_refund_allocations();
CREATE INDEX idx_movement_due ON money_movement_orders(next_attempt_at,id) WHERE state IN('NEW','FUNDING_PENDING','READY','FAILED_RETRYABLE');
CREATE INDEX idx_movement_query ON money_movement_attempts(next_query_at,id) WHERE state IN('SUBMITTING','PROCESSING','UNKNOWN');
CREATE INDEX idx_movement_payment ON money_movement_orders(payment_id,kind);

CREATE TABLE financial_accounts (
 id UUID PRIMARY KEY,kind VARCHAR(50) NOT NULL CHECK(kind IN('PROVIDER_CLEARING','PROVIDER_AVAILABLE','OUTGOING_IN_TRANSIT','BUYER_ESCROW','SELLER_PAYABLE','PAYOUT_IN_FLIGHT','BUYER_REFUND_PAYABLE','REFUND_IN_FLIGHT','TRANSFER_FEE_REVENUE','FEE_REVERSAL','PROVIDER_FEE_EXPENSE','RECOVERY_RECEIVABLE','SUSPENSE','FACILITY_REFUND_RECEIVABLE','FACILITY_REFUND_PAYABLE','FACILITY_AVAILABLE','FACILITY_FUNDING_CLEARING')),
 scope_id UUID,provider VARCHAR(40) NOT NULL,provider_account VARCHAR(120) NOT NULL,
 currency VARCHAR(3) NOT NULL CHECK(currency='VND'),money_class VARCHAR(10) NOT NULL CHECK(money_class IN('REAL','DEMO','UNKNOWN')),
 UNIQUE NULLS NOT DISTINCT(kind,scope_id,provider,provider_account,currency,money_class)
);
CREATE TABLE financial_journals (
 id UUID PRIMARY KEY,effect_type VARCHAR(60) NOT NULL,effect_ref VARCHAR(200) NOT NULL,
 payment_id UUID REFERENCES payment_orders(id),settlement_id UUID REFERENCES transfer_settlements(id),movement_id UUID REFERENCES money_movement_orders(id),
 currency VARCHAR(3) NOT NULL CHECK(currency='VND'),money_class VARCHAR(10) NOT NULL CHECK(money_class IN('REAL','DEMO','UNKNOWN')),
 effective_at TIMESTAMPTZ NOT NULL,recorded_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
 reversal_of UUID REFERENCES financial_journals(id),evidence JSONB NOT NULL,transaction_id XID8 NOT NULL DEFAULT pg_current_xact_id(),
 UNIQUE(effect_type,effect_ref)
);
CREATE TABLE financial_postings (
 journal_id UUID NOT NULL REFERENCES financial_journals(id),line_no INT NOT NULL CHECK(line_no>0),
 account_id UUID NOT NULL REFERENCES financial_accounts(id),side CHAR(1) NOT NULL CHECK(side IN('D','C')),
 amount NUMERIC(20,0) NOT NULL CHECK(amount>0),PRIMARY KEY(journal_id,line_no)
);
CREATE FUNCTION check_financial_journal() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE jid UUID; header financial_journals%ROWTYPE;n BIGINT;total NUMERIC;
BEGIN
 IF TG_TABLE_NAME='financial_journals' THEN jid=NEW.id; ELSE jid=NEW.journal_id; END IF;
 SELECT * INTO header FROM financial_journals WHERE id=jid;
 SELECT count(*),COALESCE(sum(CASE side WHEN 'D' THEN amount ELSE -amount END),0) INTO n,total FROM financial_postings WHERE journal_id=jid;
 IF n<2 OR total<>0 OR EXISTS(SELECT 1 FROM financial_postings p JOIN financial_accounts a ON a.id=p.account_id WHERE p.journal_id=jid AND
  (a.currency,a.money_class) IS DISTINCT FROM (header.currency,header.money_class))
 THEN RAISE EXCEPTION 'Journal must balance positive postings in one currency/money class' USING ERRCODE='23514'; END IF;
 RETURN NULL;
END $$;
CREATE CONSTRAINT TRIGGER financial_header_balance AFTER INSERT ON financial_journals DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION check_financial_journal();
CREATE CONSTRAINT TRIGGER financial_lines_balance AFTER INSERT ON financial_postings DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION check_financial_journal();
CREATE FUNCTION guard_new_journal_postings() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF NOT EXISTS(SELECT 1 FROM financial_journals WHERE id=NEW.journal_id AND transaction_id=pg_current_xact_id()) THEN
  RAISE EXCEPTION 'Cannot append postings to a committed journal' USING ERRCODE='23514'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER financial_postings_same_transaction BEFORE INSERT ON financial_postings FOR EACH ROW EXECUTE FUNCTION guard_new_journal_postings();
CREATE INDEX idx_financial_journal_time ON financial_journals(effective_at,id);
CREATE INDEX idx_financial_postings_account ON financial_postings(account_id,journal_id);
CREATE FUNCTION guard_funding_reservation() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE a money_movement_attempts%ROWTYPE;b financial_funding_buckets%ROWTYPE;available NUMERIC;reserved NUMERIC;
BEGIN
 IF TG_OP='DELETE' THEN RAISE EXCEPTION 'Funding reservation evidence cannot be deleted' USING ERRCODE='23514'; END IF;
 IF TG_OP='UPDATE' AND ((NEW.attempt_id,NEW.bucket_id,NEW.original_amount,NEW.created_at) IS DISTINCT FROM (OLD.attempt_id,OLD.bucket_id,OLD.original_amount,OLD.created_at) OR
  NEW.remaining_amount>OLD.remaining_amount OR NEW.version<>OLD.version+1) THEN
  RAISE EXCEPTION 'Funding reservation identity is immutable' USING ERRCODE='23514'; END IF;
 SELECT * INTO b FROM financial_funding_buckets WHERE id=NEW.bucket_id FOR UPDATE;
 SELECT * INTO a FROM money_movement_attempts WHERE id=NEW.attempt_id;
 IF a.funding_bucket_id IS DISTINCT FROM NEW.bucket_id OR a.amount IS DISTINCT FROM NEW.original_amount THEN
  RAISE EXCEPTION 'Reservation differs from its attempt' USING ERRCODE='23514'; END IF;
 SELECT COALESCE(sum(CASE p.side WHEN 'D' THEN p.amount ELSE -p.amount END),0) INTO available FROM financial_postings p JOIN financial_accounts c ON c.id=p.account_id
  WHERE c.kind='PROVIDER_AVAILABLE' AND c.scope_id=NEW.bucket_id AND (c.provider,c.provider_account,c.currency,c.money_class)=(b.provider,b.provider_account,b.currency,b.money_class);
 SELECT COALESCE(sum(remaining_amount),0) INTO reserved FROM financial_funding_reservations WHERE bucket_id=NEW.bucket_id AND attempt_id<>NEW.attempt_id;
 IF reserved+NEW.remaining_amount>available THEN RAISE EXCEPTION 'Funding bucket cannot be over-reserved' USING ERRCODE='23514'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER funding_reservation_guard BEFORE INSERT OR UPDATE OR DELETE ON financial_funding_reservations FOR EACH ROW EXECUTE FUNCTION guard_funding_reservation();
CREATE FUNCTION check_available_funding() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE c financial_accounts%ROWTYPE;available NUMERIC;reserved NUMERIC;
BEGIN
 SELECT * INTO c FROM financial_accounts WHERE id=NEW.account_id;
 IF c.kind<>'PROVIDER_AVAILABLE' THEN RETURN NULL; END IF;
 PERFORM 1 FROM financial_funding_buckets WHERE id=c.scope_id FOR UPDATE;
 SELECT COALESCE(sum(CASE side WHEN 'D' THEN amount ELSE -amount END),0) INTO available FROM financial_postings WHERE account_id=c.id;
 SELECT COALESCE(sum(remaining_amount),0) INTO reserved FROM financial_funding_reservations WHERE bucket_id=c.scope_id;
 IF available<reserved THEN RAISE EXCEPTION 'Available funds cannot fall below unresolved reservations' USING ERRCODE='23514'; END IF;
 RETURN NULL;
END $$;
CREATE CONSTRAINT TRIGGER available_funding_guard AFTER INSERT ON financial_postings DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION check_available_funding();

CREATE TABLE financial_reconciliation_cases (
 id UUID PRIMARY KEY,case_key VARCHAR(200) NOT NULL UNIQUE,category VARCHAR(50) NOT NULL,
 payment_id UUID REFERENCES payment_orders(id),settlement_id UUID REFERENCES transfer_settlements(id),movement_id UUID REFERENCES money_movement_orders(id),
 opened_by UUID,reason VARCHAR(500) NOT NULL,created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);
CREATE TABLE financial_case_actions (
 id UUID PRIMARY KEY,case_id UUID NOT NULL REFERENCES financial_reconciliation_cases(id),actor_id UUID NOT NULL,
 action VARCHAR(40) NOT NULL CHECK(action IN('NOTE','PROPOSE_RESOLUTION','EXECUTE_RESOLUTION','DISPUTE','CHARGEBACK','EVIDENCE')),
 resolution_key VARCHAR(200) UNIQUE,effect_type VARCHAR(60),proof JSONB NOT NULL,created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
 CHECK(action NOT IN('PROPOSE_RESOLUTION','EXECUTE_RESOLUTION') OR (resolution_key IS NOT NULL AND effect_type IS NOT NULL))
);
CREATE TABLE financial_approvals (
 action_id UUID NOT NULL REFERENCES financial_case_actions(id),actor_id UUID NOT NULL,proof JSONB NOT NULL,
 created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),PRIMARY KEY(action_id,actor_id)
);
CREATE FUNCTION guard_financial_approval() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF NOT EXISTS(SELECT 1 FROM financial_case_actions WHERE id=NEW.action_id AND action='PROPOSE_RESOLUTION' AND actor_id<>NEW.actor_id) THEN
  RAISE EXCEPTION 'Financial exception needs a distinct approving actor' USING ERRCODE='23514'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER financial_dual_control BEFORE INSERT ON financial_approvals FOR EACH ROW EXECUTE FUNCTION guard_financial_approval();

-- Existing immutable-evidence function is deliberately reused; no old trigger is weakened.
DO $$ DECLARE name TEXT; BEGIN
 FOREACH name IN ARRAY ARRAY['transfer_fee_policies','seller_payout_accounts','seller_payout_account_revisions','seller_payout_account_revocations',
  'transfer_payment_terms','financial_accounts','financial_journals','financial_postings','money_movement_events','financial_reconciliation_cases','financial_case_actions','financial_approvals','facility_refund_allocations','financial_funding_buckets'] LOOP
  EXECUTE format('CREATE TRIGGER %I BEFORE UPDATE OR DELETE ON %I FOR EACH ROW EXECUTE FUNCTION protect_payment_evidence()', 'immutable_'||name,name);
 END LOOP;
END $$;
