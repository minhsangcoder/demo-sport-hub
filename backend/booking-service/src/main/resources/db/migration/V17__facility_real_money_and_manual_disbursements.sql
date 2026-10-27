-- No historical cohort, account or money evidence is inferred from existing statuses.
ALTER TABLE counter_booking_guests DROP CONSTRAINT counter_booking_guests_payment_capability_check;
ALTER TABLE counter_booking_guests ADD CONSTRAINT counter_booking_guests_payment_capability_check CHECK(payment_capability IN('DEMO_ONLY','BANK_QR'));
CREATE TABLE booking_money_cohorts (
 booking_id UUID PRIMARY KEY REFERENCES bookings(id),facility_id UUID NOT NULL,receiver_owner_id UUID NOT NULL,
 channel_id UUID NOT NULL,receiver_revision_id UUID NOT NULL,money_class VARCHAR(10) NOT NULL CHECK(money_class='REAL'),
 created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);
CREATE TABLE booking_real_collections (
 payment_id UUID PRIMARY KEY,booking_id UUID NOT NULL REFERENCES booking_money_cohorts(booking_id),payer_id UUID NOT NULL,
 member_id UUID,acquisition_id UUID,purpose VARCHAR(30) NOT NULL CHECK(purpose IN('BOOKING','GROUP_CONTRIBUTION','TRANSFER')),
 amount NUMERIC(20,0) NOT NULL CHECK(amount>0),collected_amount NUMERIC(20,0) NOT NULL CHECK(collected_amount>=amount),currency VARCHAR(3) NOT NULL CHECK(currency='VND'),paid_at TIMESTAMPTZ NOT NULL,
 channel_id UUID NOT NULL,receiver_revision_id UUID NOT NULL,receiver_owner_id UUID NOT NULL,
 proof_id UUID NOT NULL,proof_hash VARCHAR(64) NOT NULL CHECK(proof_hash ~ '^[a-f0-9]{64}$'),source_event_id UUID NOT NULL,fingerprint VARCHAR(64) NOT NULL,transfer_terms JSONB,
 CHECK((purpose='TRANSFER')=(acquisition_id IS NOT NULL)),CHECK((purpose='GROUP_CONTRIBUTION')=(member_id IS NOT NULL)),
 CHECK((purpose='TRANSFER')=(transfer_terms IS NOT NULL))
);
CREATE TABLE booking_real_bank_credits (
 credit_id UUID PRIMARY KEY,payment_id UUID NOT NULL,booking_id UUID NOT NULL REFERENCES booking_money_cohorts(booking_id),
 payer_id UUID NOT NULL,amount NUMERIC(20,0) NOT NULL CHECK(amount>0),bank_time TIMESTAMPTZ NOT NULL,
 channel_id UUID NOT NULL,receiver_owner_id UUID NOT NULL,proof_hash VARCHAR(64) NOT NULL,source_event_id UUID NOT NULL,
 recorded_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);
CREATE INDEX booking_real_credit_payment ON booking_real_bank_credits(payment_id);
CREATE TABLE booking_real_unapplied_funds (
 source_key VARCHAR(180) PRIMARY KEY,payment_id UUID NOT NULL,booking_id UUID NOT NULL REFERENCES booking_money_cohorts(booking_id),
 payer_id UUID NOT NULL,receiver_owner_id UUID NOT NULL,acquisition_id UUID,reason VARCHAR(40) NOT NULL,
 amount NUMERIC(20,0) NOT NULL CHECK(amount>0),source_event_id UUID NOT NULL UNIQUE,proof_hash VARCHAR(64) NOT NULL,
 recorded_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);
CREATE TABLE facility_disbursement_obligations (
 id UUID PRIMARY KEY,source_key VARCHAR(180) NOT NULL UNIQUE,booking_id UUID NOT NULL REFERENCES booking_money_cohorts(booking_id),
 facility_id UUID NOT NULL,receiver_owner_id UUID NOT NULL,payment_id UUID NOT NULL,
 kind VARCHAR(30) NOT NULL CHECK(kind IN('SELLER_PAYOUT','POLICY_REFUND','TECHNICAL_REFUND')),
 payee_id UUID,guest_booking_id UUID REFERENCES counter_booking_guests(booking_id),
 amount NUMERIC(20,0) NOT NULL CHECK(amount>0),destination_revision_id UUID,destination_hash VARCHAR(64),
 state VARCHAR(30) NOT NULL CHECK(state IN('AWAITING_BANK_DETAILS','UNPAID','PREPARED','REVIEW','MANUALLY_RECORDED')),
 version BIGINT NOT NULL DEFAULT 1 CHECK(version>0),created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
 CHECK((payee_id IS NULL)<>(guest_booking_id IS NULL)),
 CHECK((destination_revision_id IS NULL)=(destination_hash IS NULL)),
 CHECK(state<>'AWAITING_BANK_DETAILS' OR destination_revision_id IS NULL),
 CHECK(state NOT IN('PREPARED','MANUALLY_RECORDED') OR destination_revision_id IS NOT NULL)
);
CREATE TABLE facility_transfer_settlements (
 acquisition_id UUID PRIMARY KEY REFERENCES transfer_acquisition_decisions(acquisition_id),payment_id UUID NOT NULL UNIQUE,
 booking_id UUID NOT NULL REFERENCES booking_money_cohorts(booking_id),facility_id UUID NOT NULL,court_id UUID NOT NULL,
 receiver_owner_id UUID NOT NULL,seller_id UUID NOT NULL,buyer_id UUID NOT NULL CHECK(seller_id<>buyer_id),
 amount NUMERIC(20,0) NOT NULL CHECK(amount>0),fee_bps SMALLINT NOT NULL CHECK(fee_bps BETWEEN 0 AND 10000),
 policy_version BIGINT NOT NULL CHECK(policy_version>0),fee_amount NUMERIC(20,0) NOT NULL CHECK(fee_amount>=0),
 seller_net_amount NUMERIC(20,0) NOT NULL CHECK(seller_net_amount>=0),terms_hash VARCHAR(64) NOT NULL,terms JSONB NOT NULL,
 handoff_at TIMESTAMPTZ NOT NULL,payout_obligation_id UUID UNIQUE REFERENCES facility_disbursement_obligations(id),
 CHECK(amount=fee_amount+seller_net_amount),CHECK(fee_amount=round(amount*fee_bps/10000,0)),
 CHECK((seller_net_amount=0)=(payout_obligation_id IS NULL)),
 CHECK(terms->>'mode' IS NOT DISTINCT FROM 'REAL' AND terms->>'protocol' IS NOT DISTINCT FROM 'FACILITY_REAL_V1')
);
CREATE TABLE facility_refund_entitlements (
 id UUID PRIMARY KEY,source_key VARCHAR(180) NOT NULL UNIQUE,booking_id UUID NOT NULL REFERENCES booking_money_cohorts(booking_id),
 facility_id UUID NOT NULL,quote_id UUID REFERENCES booking_cancellation_refund_quotes(id),acquisition_id UUID,
 reason VARCHAR(40) NOT NULL CHECK(reason IN('POLICY_CANCELLATION','TECHNICAL_TRANSFER_FAILURE','OVERPAYMENT','NON_CANONICAL','EXPIRED_PARTIAL','LATE_COLLECTION','UNAPPLIED_COLLECTION')),
 base_amount NUMERIC(20,0) NOT NULL CHECK(base_amount>=0),rate_bps SMALLINT NOT NULL CHECK(rate_bps BETWEEN 0 AND 10000),
 target_amount NUMERIC(20,0) NOT NULL CHECK(target_amount>=0 AND target_amount<=base_amount),
 policy_version BIGINT,policy_hash VARCHAR(64),requested_by UUID NOT NULL,created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
 CHECK(target_amount=round(base_amount*rate_bps/10000,0)),
 CHECK(reason='POLICY_CANCELLATION' OR rate_bps=10000),
 CHECK(reason<>'POLICY_CANCELLATION' OR (quote_id IS NOT NULL AND policy_version>0 AND policy_hash IS NOT NULL))
);
CREATE TABLE facility_refund_allocations (
 id UUID PRIMARY KEY,entitlement_id UUID NOT NULL REFERENCES facility_refund_entitlements(id),payment_id UUID NOT NULL,
 base_amount NUMERIC(20,0) NOT NULL CHECK(base_amount>0),amount NUMERIC(20,0) NOT NULL CHECK(amount>=0 AND amount<=base_amount),
 payer_id UUID,guest_booking_id UUID REFERENCES counter_booking_guests(booking_id),
 obligation_id UUID UNIQUE REFERENCES facility_disbursement_obligations(id),
 UNIQUE(entitlement_id,payment_id),CHECK((payer_id IS NULL)<>(guest_booking_id IS NULL)),CHECK((amount=0)=(obligation_id IS NULL))
);
CREATE SEQUENCE facility_disbursement_codes MINVALUE 1 NO CYCLE;
CREATE TABLE facility_disbursement_operations (
 id UUID PRIMARY KEY,obligation_id UUID NOT NULL REFERENCES facility_disbursement_obligations(id),
 version BIGINT NOT NULL DEFAULT 1 CHECK(version>0),claimed_by UUID NOT NULL,destination_revision_id UUID NOT NULL,destination_hash VARCHAR(64) NOT NULL,
 amount NUMERIC(20,0) NOT NULL CHECK(amount>0),reference_code BIGINT NOT NULL DEFAULT nextval('facility_disbursement_codes') UNIQUE,
 description VARCHAR(25) NOT NULL UNIQUE CHECK(description ~ '^[A-Z0-9 ]{1,25}$'),
 state VARCHAR(25) NOT NULL CHECK(state IN('PREPARED','REVIEW','MANUALLY_RECORDED','RELEASED')),
 created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);
CREATE UNIQUE INDEX one_facility_disbursement_claim ON facility_disbursement_operations(obligation_id) WHERE state<>'RELEASED';
CREATE TABLE facility_disbursement_receipts (
 id UUID PRIMARY KEY,operation_id UUID NOT NULL UNIQUE REFERENCES facility_disbursement_operations(id),
 obligation_id UUID NOT NULL UNIQUE REFERENCES facility_disbursement_obligations(id),source_scope UUID NOT NULL,
 bank_reference VARCHAR(120) NOT NULL CHECK(length(trim(bank_reference))>0),normalized_reference VARCHAR(120) NOT NULL,
 amount NUMERIC(20,0) NOT NULL CHECK(amount>0),destination_revision_id UUID NOT NULL,destination_hash VARCHAR(64) NOT NULL,
 confirmation_method VARCHAR(25) NOT NULL CHECK(confirmation_method='MANUALLY_RECORDED'),
 confirmed_at TIMESTAMPTZ NOT NULL,performed_by UUID NOT NULL,UNIQUE(source_scope,normalized_reference)
);
CREATE TABLE facility_finance_commands (
 actor_id UUID NOT NULL,operation VARCHAR(40) NOT NULL,idempotency_key VARCHAR(100) NOT NULL,
 fingerprint VARCHAR(64) NOT NULL,result JSONB NOT NULL,created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
 PRIMARY KEY(actor_id,operation,idempotency_key)
);
CREATE TABLE facility_finance_audit (
 id UUID PRIMARY KEY,obligation_id UUID NOT NULL REFERENCES facility_disbursement_obligations(id),operation_id UUID REFERENCES facility_disbursement_operations(id),
 actor_id UUID NOT NULL,action VARCHAR(40) NOT NULL,reason VARCHAR(500) NOT NULL,details JSONB NOT NULL,
 created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);
CREATE INDEX facility_money_list ON facility_disbursement_obligations(facility_id,created_at,id);
CREATE INDEX real_settlement_facility ON facility_transfer_settlements(facility_id,handoff_at,acquisition_id);
CREATE FUNCTION preserve_facility_money() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN RAISE EXCEPTION 'Facility money evidence is append only' USING ERRCODE='23514'; END $$;
CREATE TRIGGER immutable_money_cohort BEFORE UPDATE OR DELETE ON booking_money_cohorts FOR EACH ROW EXECUTE FUNCTION preserve_facility_money();
CREATE TRIGGER immutable_real_collection BEFORE UPDATE OR DELETE ON booking_real_collections FOR EACH ROW EXECUTE FUNCTION preserve_facility_money();
CREATE TRIGGER immutable_bank_credit BEFORE UPDATE OR DELETE ON booking_real_bank_credits FOR EACH ROW EXECUTE FUNCTION preserve_facility_money();
CREATE TRIGGER immutable_unapplied_funds BEFORE UPDATE OR DELETE ON booking_real_unapplied_funds FOR EACH ROW EXECUTE FUNCTION preserve_facility_money();
CREATE TRIGGER immutable_facility_settlement BEFORE UPDATE OR DELETE ON facility_transfer_settlements FOR EACH ROW EXECUTE FUNCTION preserve_facility_money();
CREATE TRIGGER immutable_refund_entitlement BEFORE UPDATE OR DELETE ON facility_refund_entitlements FOR EACH ROW EXECUTE FUNCTION preserve_facility_money();
CREATE TRIGGER immutable_refund_allocation BEFORE UPDATE OR DELETE ON facility_refund_allocations FOR EACH ROW EXECUTE FUNCTION preserve_facility_money();
CREATE TRIGGER immutable_disbursement_receipt BEFORE UPDATE OR DELETE ON facility_disbursement_receipts FOR EACH ROW EXECUTE FUNCTION preserve_facility_money();
CREATE TRIGGER immutable_finance_command BEFORE UPDATE OR DELETE ON facility_finance_commands FOR EACH ROW EXECUTE FUNCTION preserve_facility_money();
CREATE TRIGGER immutable_finance_audit BEFORE UPDATE OR DELETE ON facility_finance_audit FOR EACH ROW EXECUTE FUNCTION preserve_facility_money();
CREATE FUNCTION guard_facility_money_mutation() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF TG_OP='DELETE' OR (to_jsonb(NEW)-ARRAY['state','version','claimed_by','destination_revision_id','destination_hash']) IS DISTINCT FROM
 (to_jsonb(OLD)-ARRAY['state','version','claimed_by','destination_revision_id','destination_hash']) OR
 OLD.state='MANUALLY_RECORDED' OR NEW.version<>OLD.version+1 THEN
 RAISE EXCEPTION 'Facility obligation identity or final receipt is immutable' USING ERRCODE='23514'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER facility_obligation_guard BEFORE UPDATE OR DELETE ON facility_disbursement_obligations FOR EACH ROW EXECUTE FUNCTION guard_facility_money_mutation();
CREATE TRIGGER facility_operation_guard BEFORE UPDATE OR DELETE ON facility_disbursement_operations FOR EACH ROW EXECUTE FUNCTION guard_facility_money_mutation();
CREATE FUNCTION guard_facility_refund_capacity() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE actual NUMERIC;promised NUMERIC;
BEGIN
 PERFORM pg_advisory_xact_lock(hashtextextended(NEW.payment_id::text,7062));
 SELECT COALESCE(sum(amount),0) INTO actual FROM booking_real_bank_credits WHERE payment_id=NEW.payment_id;
 SELECT greatest(actual,COALESCE(max(collected_amount),0)) INTO actual FROM booking_real_collections WHERE payment_id=NEW.payment_id;
 SELECT COALESCE(sum(amount),0) INTO promised FROM facility_refund_allocations WHERE payment_id=NEW.payment_id;
 IF promised+NEW.amount>actual THEN RAISE EXCEPTION 'Refunds exceed actual verified collection' USING ERRCODE='23514'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER real_refund_capacity BEFORE INSERT ON facility_refund_allocations FOR EACH ROW EXECUTE FUNCTION guard_facility_refund_capacity();
CREATE FUNCTION validate_facility_receipt() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE o facility_disbursement_operations%ROWTYPE;b facility_disbursement_obligations%ROWTYPE;
BEGIN
 SELECT * INTO o FROM facility_disbursement_operations WHERE id=NEW.operation_id FOR UPDATE;
 SELECT * INTO b FROM facility_disbursement_obligations WHERE id=o.obligation_id FOR UPDATE;
 IF (NEW.obligation_id,NEW.source_scope,NEW.amount,NEW.destination_revision_id,NEW.destination_hash,NEW.performed_by) IS DISTINCT FROM
 (b.id,b.receiver_owner_id,o.amount,o.destination_revision_id,o.destination_hash,o.claimed_by) OR
 o.amount<>b.amount OR o.destination_revision_id IS DISTINCT FROM b.destination_revision_id OR o.state='RELEASED' THEN
 RAISE EXCEPTION 'Manual receipt differs from claimed obligation' USING ERRCODE='23514'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER facility_receipt_binding BEFORE INSERT ON facility_disbursement_receipts FOR EACH ROW EXECUTE FUNCTION validate_facility_receipt();
CREATE FUNCTION validate_real_transfer_settlement() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE d transfer_acquisition_decisions%ROWTYPE;c booking_real_collections%ROWTYPE;o facility_disbursement_obligations%ROWTYPE;
BEGIN
 SELECT * INTO d FROM transfer_acquisition_decisions WHERE acquisition_id=NEW.acquisition_id;
 SELECT * INTO c FROM booking_real_collections WHERE payment_id=NEW.payment_id;
 IF d.decision IS DISTINCT FROM 'COMPLETED' OR c.purpose IS DISTINCT FROM 'TRANSFER' OR
 (d.booking_id,d.payment_id,d.seller_id,d.buyer_id,d.amount,d.decided_at) IS DISTINCT FROM
 (NEW.booking_id,NEW.payment_id,NEW.seller_id,NEW.buyer_id,NEW.amount,NEW.handoff_at) OR
 (c.booking_id,c.acquisition_id,c.payer_id,c.amount,c.transfer_terms->>'termsHash') IS DISTINCT FROM
 (NEW.booking_id,NEW.acquisition_id,NEW.buyer_id,NEW.amount,NEW.terms_hash) THEN
 RAISE EXCEPTION 'REAL settlement requires matching bank collection and completed handoff' USING ERRCODE='23514'; END IF;
 IF NEW.seller_net_amount>0 THEN
  SELECT * INTO o FROM facility_disbursement_obligations WHERE id=NEW.payout_obligation_id;
  IF (o.booking_id,o.facility_id,o.receiver_owner_id,o.payment_id,o.kind,o.payee_id,o.amount,o.destination_revision_id::text,o.destination_hash) IS DISTINCT FROM
  (NEW.booking_id,NEW.facility_id,NEW.receiver_owner_id,NEW.payment_id,'SELLER_PAYOUT'::varchar,NEW.seller_id,NEW.seller_net_amount,NEW.terms->>'sellerDestinationRevisionId',NEW.terms->>'sellerDestinationHash') THEN
   RAISE EXCEPTION 'Seller payable differs from the accepted facility terms' USING ERRCODE='23514'; END IF;
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER real_transfer_settlement_binding BEFORE INSERT ON facility_transfer_settlements FOR EACH ROW EXECUTE FUNCTION validate_real_transfer_settlement();
CREATE OR REPLACE FUNCTION protect_group_identity() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE policy_cancel BOOLEAN;
BEGIN
 IF TG_OP='DELETE' THEN RAISE EXCEPTION 'Group history cannot be deleted' USING ERRCODE='23514'; END IF;
 IF (NEW.id,NEW.owner_id,NEW.deadline) IS DISTINCT FROM (OLD.id,OLD.owner_id,OLD.deadline) OR NEW.invite_version<OLD.invite_version THEN
  RAISE EXCEPTION 'Group ownership, deadline and invitation generation are protected' USING ERRCODE='23514'; END IF;
 policy_cancel:=NEW.state='GROUP_CANCELLED' AND EXISTS(SELECT 1 FROM bookings b WHERE b.id=OLD.id AND b.status='CANCELLED') AND
  (EXISTS(SELECT 1 FROM demo_refund_requests r WHERE r.booking_id=OLD.id AND r.reason='POLICY_CANCELLATION') OR
   EXISTS(SELECT 1 FROM facility_refund_entitlements r WHERE r.booking_id=OLD.id AND r.reason='POLICY_CANCELLATION'));
 IF NEW.state<>OLD.state AND NOT(OLD.state='GROUP_PENDING' AND NEW.state IN('CONFIRMED','GROUP_CANCELLED','GROUP_EXPIRED') OR OLD.state='CONFIRMED' AND policy_cancel) THEN
  RAISE EXCEPTION 'Invalid group state transition' USING ERRCODE='23514'; END IF;
 IF NEW.state='GROUP_CANCELLED' AND OLD.state<>'GROUP_CANCELLED'
  AND EXISTS(SELECT 1 FROM group_members WHERE group_id=OLD.id AND (amount_paid>0 OR payment_requested_at IS NOT NULL)) AND NOT policy_cancel THEN
  RAISE EXCEPTION 'Paid group cancellation requires a resolution policy' USING ERRCODE='23514'; END IF;
 IF NEW.allocations_locked IS DISTINCT FROM OLD.allocations_locked
  AND EXISTS(SELECT 1 FROM group_members WHERE group_id=OLD.id AND (amount_paid>0 OR payment_requested_at IS NOT NULL)) THEN
  RAISE EXCEPTION 'Payment started; allocation changes require a resolution policy' USING ERRCODE='23514'; END IF;
 RETURN NEW;
END $$;
