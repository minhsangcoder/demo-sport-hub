-- Application simulation only. No changes to existing payments, policies or refund reviews.
CREATE TABLE booking_demo_collections (
 payment_id UUID PRIMARY KEY,booking_id UUID NOT NULL REFERENCES bookings(id),payer_id UUID NOT NULL,
 member_id UUID,acquisition_id UUID,purpose VARCHAR(30) NOT NULL CHECK(purpose IN('BOOKING','GROUP_CONTRIBUTION','TRANSFER')),
 amount NUMERIC(20,0) NOT NULL CHECK(amount>0),currency VARCHAR(3) NOT NULL CHECK(currency='VND'),paid_at TIMESTAMPTZ NOT NULL,
 source_event_id UUID NOT NULL, fingerprint VARCHAR(64) NOT NULL CHECK(fingerprint ~ '^[a-f0-9]{64}$'),
 transfer_terms JSONB,recorded_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
 CHECK((purpose='TRANSFER')=(acquisition_id IS NOT NULL)),CHECK((purpose='GROUP_CONTRIBUTION')=(member_id IS NOT NULL)),
 CHECK((purpose='TRANSFER')=(transfer_terms IS NOT NULL))
);
CREATE TABLE demo_transfer_settlements (
 acquisition_id UUID PRIMARY KEY REFERENCES transfer_acquisition_decisions(acquisition_id),payment_id UUID NOT NULL UNIQUE,
 booking_id UUID NOT NULL REFERENCES bookings(id),facility_id UUID NOT NULL,court_id UUID NOT NULL,
 receiver_owner_id UUID NOT NULL,seller_id UUID NOT NULL,buyer_id UUID NOT NULL CHECK(buyer_id<>seller_id),
 amount NUMERIC(20,0) NOT NULL CHECK(amount>0),fee_bps SMALLINT NOT NULL CHECK(fee_bps BETWEEN 0 AND 10000),
 policy_version BIGINT NOT NULL CHECK(policy_version>0),fee_amount NUMERIC(20,0) NOT NULL CHECK(fee_amount>=0),
 seller_net_amount NUMERIC(20,0) NOT NULL CHECK(seller_net_amount>=0),terms_hash VARCHAR(64) NOT NULL,
 terms JSONB NOT NULL CHECK(jsonb_typeof(terms)='object' AND terms->>'mode' IS NOT DISTINCT FROM 'DEMO'),
 handoff_at TIMESTAMPTZ NOT NULL,paid_at TIMESTAMPTZ,performed_by UUID,
 payout_state VARCHAR(20) NOT NULL CHECK(payout_state IN('UNPAID','FAILED','PAID','NOT_REQUIRED')),
 version BIGINT NOT NULL DEFAULT 0 CHECK(version>=0),
 CHECK(amount=fee_amount+seller_net_amount),CHECK(fee_amount=round(amount*fee_bps/10000,0)),
 CHECK((payout_state='NOT_REQUIRED')=(seller_net_amount=0)),CHECK((payout_state='PAID')=(paid_at IS NOT NULL)),
 CHECK((paid_at IS NULL)=(performed_by IS NULL))
);
CREATE INDEX demo_settlement_facility ON demo_transfer_settlements(facility_id,handoff_at,acquisition_id);
CREATE TABLE demo_refund_requests (
 id UUID PRIMARY KEY,source_key VARCHAR(160) NOT NULL UNIQUE,booking_id UUID NOT NULL REFERENCES bookings(id),
 facility_id UUID NOT NULL,court_id UUID NOT NULL,quote_id UUID REFERENCES booking_cancellation_refund_quotes(id),
 acquisition_id UUID,reason VARCHAR(40) NOT NULL CHECK(reason IN('POLICY_CANCELLATION','TECHNICAL_TRANSFER_FAILURE')),
 base_amount NUMERIC(20,0) NOT NULL CHECK(base_amount>=0),rate_bps SMALLINT NOT NULL CHECK(rate_bps BETWEEN 0 AND 10000),
 target_amount NUMERIC(20,0) NOT NULL CHECK(target_amount>=0 AND target_amount<=base_amount),
 policy_version BIGINT NOT NULL CHECK(policy_version>0),policy_hash VARCHAR(64) NOT NULL,
 allocations JSONB NOT NULL CHECK(jsonb_typeof(allocations)='array'),requested_by UUID NOT NULL,
 state VARCHAR(20) NOT NULL CHECK(state IN('PENDING','FAILED','REFUNDED','NO_REFUND')),
 created_at TIMESTAMPTZ NOT NULL,refunded_at TIMESTAMPTZ,performed_by UUID,version BIGINT NOT NULL DEFAULT 0 CHECK(version>=0),
 CHECK(target_amount=round(base_amount*rate_bps/10000,0)),CHECK((state='NO_REFUND')=(target_amount=0)),
 CHECK((state='REFUNDED')=(refunded_at IS NOT NULL)),CHECK((refunded_at IS NULL)=(performed_by IS NULL)),
 CHECK(reason<>'POLICY_CANCELLATION' OR quote_id IS NOT NULL),
 CHECK(reason<>'TECHNICAL_TRANSFER_FAILURE' OR (rate_bps=10000 AND acquisition_id IS NOT NULL))
);
CREATE INDEX demo_refund_facility ON demo_refund_requests(facility_id,created_at,id);
CREATE TABLE demo_money_events (
 id UUID PRIMARY KEY,kind VARCHAR(30) NOT NULL CHECK(kind IN('SELLER_PAYOUT','POLICY_REFUND','TECHNICAL_REFUND')),
 subject_id UUID NOT NULL,payment_id UUID NOT NULL,booking_id UUID NOT NULL REFERENCES bookings(id),facility_id UUID NOT NULL,
 payee_id UUID,guest_booking_id UUID REFERENCES counter_booking_guests(booking_id),
 amount NUMERIC(20,0) NOT NULL CHECK(amount>0),currency VARCHAR(3) NOT NULL CHECK(currency='VND'),
 outcome VARCHAR(10) NOT NULL CHECK(outcome IN('SUCCESS','FAILED')),performed_by UUID NOT NULL,
 performed_at TIMESTAMPTZ NOT NULL,command_key VARCHAR(100) NOT NULL,
 CHECK((payee_id IS NULL)=(guest_booking_id IS NOT NULL)),CHECK(kind<>'SELLER_PAYOUT' OR guest_booking_id IS NULL)
);
CREATE UNIQUE INDEX demo_one_success ON demo_money_events(kind,subject_id,payment_id) WHERE outcome='SUCCESS';
CREATE UNIQUE INDEX demo_one_seller_payment ON demo_money_events(subject_id) WHERE kind='SELLER_PAYOUT' AND outcome='SUCCESS';
CREATE INDEX demo_events_facility ON demo_money_events(facility_id,performed_at,id);
CREATE TABLE demo_finance_commands (
 actor_id UUID NOT NULL,operation VARCHAR(30) NOT NULL,idempotency_key VARCHAR(100) NOT NULL,
 fingerprint VARCHAR(64) NOT NULL,result JSONB NOT NULL,created_at TIMESTAMPTZ NOT NULL,
 PRIMARY KEY(actor_id,operation,idempotency_key)
);

CREATE FUNCTION guard_demo_financial_identity() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF TG_OP='DELETE' OR
  (to_jsonb(NEW)-ARRAY['payout_state','state','paid_at','refunded_at','performed_by','version']) IS DISTINCT FROM
  (to_jsonb(OLD)-ARRAY['payout_state','state','paid_at','refunded_at','performed_by','version']) OR
  NEW.version<>OLD.version+1 THEN RAISE EXCEPTION 'DEMO financial identity is immutable' USING ERRCODE='23514'; END IF;
 IF (to_jsonb(OLD)->>'payout_state' IN('PAID','NOT_REQUIRED') OR to_jsonb(OLD)->>'state' IN('REFUNDED','NO_REFUND')) THEN
  RAISE EXCEPTION 'Successful DEMO execution is final' USING ERRCODE='23514'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER demo_settlement_identity BEFORE UPDATE OR DELETE ON demo_transfer_settlements FOR EACH ROW EXECUTE FUNCTION guard_demo_financial_identity();
CREATE TRIGGER demo_refund_identity BEFORE UPDATE OR DELETE ON demo_refund_requests FOR EACH ROW EXECUTE FUNCTION guard_demo_financial_identity();
CREATE FUNCTION immutable_demo_money_evidence() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN RAISE EXCEPTION 'DEMO money evidence is append only' USING ERRCODE='23514'; END $$;
CREATE TRIGGER demo_collection_immutable BEFORE UPDATE OR DELETE ON booking_demo_collections FOR EACH ROW EXECUTE FUNCTION immutable_demo_money_evidence();
CREATE TRIGGER demo_event_immutable BEFORE UPDATE OR DELETE ON demo_money_events FOR EACH ROW EXECUTE FUNCTION immutable_demo_money_evidence();
CREATE TRIGGER demo_command_immutable BEFORE UPDATE OR DELETE ON demo_finance_commands FOR EACH ROW EXECUTE FUNCTION immutable_demo_money_evidence();

CREATE FUNCTION validate_demo_refund_allocations() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE a JSONB;total_base NUMERIC:=0;total_refund NUMERIC:=0;existing NUMERIC;payment UUID;
BEGIN
 IF TG_OP='UPDATE' THEN RETURN NEW; END IF;
 IF (SELECT count(*) FROM jsonb_array_elements(NEW.allocations))<>(SELECT count(DISTINCT x->>'paymentId') FROM jsonb_array_elements(NEW.allocations) x) THEN
  RAISE EXCEPTION 'Duplicate DEMO refund allocation' USING ERRCODE='23514'; END IF;
 FOR a IN SELECT x FROM jsonb_array_elements(NEW.allocations) x ORDER BY x->>'paymentId' LOOP
  payment:=(a->>'paymentId')::uuid;
  IF NOT(a ?& ARRAY['paymentId','payerId','baseAmount','refundAmount']) OR payment IS NULL OR (a->>'payerId')::uuid IS NULL OR a->>'baseAmount' IS NULL OR a->>'refundAmount' IS NULL OR
   (a->>'baseAmount')::numeric<=0 OR (a->>'baseAmount')::numeric<>trunc((a->>'baseAmount')::numeric) OR
   (a->>'refundAmount')::numeric<0 OR (a->>'refundAmount')::numeric> (a->>'baseAmount')::numeric OR
   (a->>'refundAmount')::numeric<>trunc((a->>'refundAmount')::numeric) THEN
   RAISE EXCEPTION 'Invalid DEMO refund allocation' USING ERRCODE='23514'; END IF;
  PERFORM pg_advisory_xact_lock(hashtextextended(payment::text,7061));
  SELECT COALESCE(sum((x->>'refundAmount')::numeric),0) INTO existing FROM demo_refund_requests r CROSS JOIN LATERAL jsonb_array_elements(r.allocations) x WHERE x->>'paymentId'=payment::text;
  IF existing+(a->>'refundAmount')::numeric>(a->>'baseAmount')::numeric THEN RAISE EXCEPTION 'DEMO refunds exceed original payment' USING ERRCODE='23514'; END IF;
  total_base:=total_base+(a->>'baseAmount')::numeric;total_refund:=total_refund+(a->>'refundAmount')::numeric;
 END LOOP;
 IF total_base<>NEW.base_amount OR total_refund<>NEW.target_amount THEN RAISE EXCEPTION 'DEMO allocation totals differ' USING ERRCODE='23514'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER demo_refund_allocation_integrity BEFORE INSERT ON demo_refund_requests FOR EACH ROW EXECUTE FUNCTION validate_demo_refund_allocations();

CREATE FUNCTION validate_demo_business_evidence() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE b bookings%ROWTYPE;q booking_cancellation_refund_quotes%ROWTYPE;d transfer_acquisition_decisions%ROWTYPE;
BEGIN
 SELECT * INTO b FROM bookings WHERE id=NEW.booking_id;
 IF (NEW.facility_id,NEW.court_id) IS DISTINCT FROM (b.facility_id,b.court_id) THEN RAISE EXCEPTION 'DEMO facility differs from booking' USING ERRCODE='23514'; END IF;
 IF TG_TABLE_NAME='demo_transfer_settlements' THEN
  SELECT * INTO d FROM transfer_acquisition_decisions WHERE acquisition_id=NEW.acquisition_id;
  IF NOT FOUND OR d.decision<>'COMPLETED' OR
   (NEW.payment_id,NEW.booking_id,NEW.seller_id,NEW.buyer_id,NEW.amount,NEW.handoff_at) IS DISTINCT FROM
   (d.payment_id,d.booking_id,d.seller_id,d.buyer_id,d.amount,d.decided_at) OR
   (NEW.terms->>'receiverOwnerId')::uuid IS DISTINCT FROM NEW.receiver_owner_id OR
   (NEW.terms->>'feeBps')::int IS DISTINCT FROM NEW.fee_bps OR
   NEW.terms->>'termsHash' IS DISTINCT FROM NEW.terms_hash THEN RAISE EXCEPTION 'DEMO settlement requires the committed handoff' USING ERRCODE='23514'; END IF;
 ELSE
  IF NEW.reason='POLICY_CANCELLATION' THEN
   SELECT * INTO q FROM booking_cancellation_refund_quotes WHERE id=NEW.quote_id;
   IF NOT FOUND OR b.status<>'CANCELLED' OR q.booking_id<>b.id OR q.booking_version+1<>b.version OR q.ownership_version+1<>b.checkin_token_version OR
    (NEW.base_amount,NEW.rate_bps,NEW.target_amount,NEW.policy_version,NEW.policy_hash,NEW.allocations) IS DISTINCT FROM
    (q.base_amount,q.rate_bps,q.target_amount,q.policy_version,q.policy_hash,q.allocations) THEN RAISE EXCEPTION 'DEMO refund requires the accepted cancellation quote' USING ERRCODE='23514'; END IF;
  ELSE
   SELECT * INTO d FROM transfer_acquisition_decisions WHERE acquisition_id=NEW.acquisition_id;
   IF NOT FOUND OR d.decision<>'ABORTED' OR d.booking_id<>b.id OR NEW.base_amount<>d.amount OR
    jsonb_array_length(NEW.allocations)<>1 OR NEW.allocations->0->>'paymentId' IS DISTINCT FROM d.payment_id::text OR
    NEW.allocations->0->>'payerId' IS DISTINCT FROM d.buyer_id::text THEN RAISE EXCEPTION 'Technical refund requires an aborted acquisition' USING ERRCODE='23514'; END IF;
  END IF;
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER demo_settlement_decision BEFORE INSERT ON demo_transfer_settlements FOR EACH ROW EXECUTE FUNCTION validate_demo_business_evidence();
CREATE TRIGGER demo_refund_decision BEFORE INSERT ON demo_refund_requests FOR EACH ROW EXECUTE FUNCTION validate_demo_business_evidence();

CREATE FUNCTION validate_demo_execution_binding() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE s demo_transfer_settlements%ROWTYPE;r demo_refund_requests%ROWTYPE;a JSONB;guest BOOLEAN;
BEGIN
 IF NEW.kind='SELLER_PAYOUT' THEN
  SELECT * INTO s FROM demo_transfer_settlements WHERE acquisition_id=NEW.subject_id;
  IF NOT FOUND OR s.payout_state NOT IN('UNPAID','FAILED') OR
   (NEW.payment_id,NEW.booking_id,NEW.facility_id,NEW.payee_id,NEW.amount) IS DISTINCT FROM
   (s.payment_id,s.booking_id,s.facility_id,s.seller_id,s.seller_net_amount) OR NEW.guest_booking_id IS NOT NULL THEN
   RAISE EXCEPTION 'DEMO seller execution differs from its payable' USING ERRCODE='23514'; END IF;
 ELSE
  SELECT * INTO r FROM demo_refund_requests WHERE id=NEW.subject_id;
  IF NOT FOUND OR r.state NOT IN('PENDING','FAILED') OR (NEW.booking_id,NEW.facility_id) IS DISTINCT FROM(r.booking_id,r.facility_id) OR
   (NEW.kind='POLICY_REFUND')<>(r.reason='POLICY_CANCELLATION') THEN RAISE EXCEPTION 'DEMO refund execution differs from its request' USING ERRCODE='23514'; END IF;
  SELECT x INTO a FROM jsonb_array_elements(r.allocations) x WHERE x->>'paymentId'=NEW.payment_id::text;
  guest:=r.acquisition_id IS NULL AND EXISTS(SELECT 1 FROM counter_booking_guests g WHERE g.booking_id=r.booking_id AND g.linked_customer_id IS NULL);
  IF a IS NULL OR NEW.amount IS DISTINCT FROM (a->>'refundAmount')::numeric OR
   (guest AND (NEW.payee_id IS NOT NULL OR NEW.guest_booking_id IS DISTINCT FROM r.booking_id)) OR
   (NOT guest AND (NEW.guest_booking_id IS NOT NULL OR NEW.payee_id IS DISTINCT FROM (a->>'payerId')::uuid)) THEN
   RAISE EXCEPTION 'DEMO refund payee or allocation differs' USING ERRCODE='23514'; END IF;
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER demo_execution_identity BEFORE INSERT ON demo_money_events FOR EACH ROW EXECUTE FUNCTION validate_demo_execution_binding();

-- Permit paid group cancellation only after the complete policy protocol has committed its entitlement.
-- Member/payment/allocation protections are retained, including for partially paid groups.
CREATE OR REPLACE FUNCTION protect_group_identity() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE policy_cancel BOOLEAN;
BEGIN
 IF TG_OP='DELETE' THEN RAISE EXCEPTION 'Group history cannot be deleted' USING ERRCODE='23514'; END IF;
 IF (NEW.id,NEW.owner_id,NEW.deadline) IS DISTINCT FROM (OLD.id,OLD.owner_id,OLD.deadline) OR NEW.invite_version<OLD.invite_version THEN
  RAISE EXCEPTION 'Group ownership, deadline and invitation generation are protected' USING ERRCODE='23514'; END IF;
 policy_cancel:=NEW.state='GROUP_CANCELLED' AND EXISTS(
  SELECT 1 FROM demo_refund_requests r JOIN bookings b ON b.id=r.booking_id WHERE r.booking_id=OLD.id AND r.reason='POLICY_CANCELLATION' AND b.status='CANCELLED');
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
