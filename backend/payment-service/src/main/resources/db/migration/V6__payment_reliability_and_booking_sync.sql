-- Preflight only: inconsistent legacy money/claims require reconciliation, never silent repair.
DO $$ BEGIN
 IF EXISTS(SELECT 1 FROM payment_orders WHERE currency<>'VND' OR NOT (
   (purpose='BOOKING' AND member_id IS NULL AND acquisition_id IS NULL AND seller_id IS NULL) OR
   (purpose='GROUP_CONTRIBUTION' AND member_id IS NOT NULL AND acquisition_id IS NULL AND seller_id IS NULL) OR
   (purpose='TRANSFER' AND member_id IS NULL AND acquisition_id IS NOT NULL AND seller_id IS NOT NULL)))
 THEN RAISE EXCEPTION 'Payment scope/currency preflight failed'; END IF;
 IF EXISTS(SELECT booking_id,purpose,COALESCE(member_id,acquisition_id,'00000000-0000-0000-0000-000000000000'::uuid)
   FROM payment_orders GROUP BY 1,2,3 HAVING count(*) FILTER(WHERE status IN ('PENDING','SUCCESS'))>1 OR count(DISTINCT (amount,currency))>1)
 THEN RAISE EXCEPTION 'Payment obligation preflight failed: reconcile duplicate live/paid orders or inconsistent prices'; END IF;
 IF EXISTS(SELECT 1 FROM payment_requests r JOIN payment_orders p ON p.id=r.payment_id
   WHERE r.payer_id<>p.payer_id OR r.booking_id<>p.booking_id OR r.member_id IS DISTINCT FROM p.member_id OR r.acquisition_id IS DISTINCT FROM p.acquisition_id)
 THEN RAISE EXCEPTION 'Payment request binding preflight failed'; END IF;
END $$;

CREATE TABLE payment_obligations (
 booking_id UUID NOT NULL,purpose VARCHAR(30) NOT NULL,subject_id UUID NOT NULL,
 amount NUMERIC(12,2) NOT NULL CHECK(amount>0),currency VARCHAR(3) NOT NULL,
 current_payment_id UUID UNIQUE REFERENCES payment_orders(id),settled_payment_id UUID UNIQUE REFERENCES payment_orders(id),
 created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
 PRIMARY KEY(booking_id,purpose,subject_id)
);
INSERT INTO payment_obligations(booking_id,purpose,subject_id,amount,currency,current_payment_id,settled_payment_id)
 SELECT DISTINCT ON(booking_id,purpose,COALESCE(member_id,acquisition_id,'00000000-0000-0000-0000-000000000000'::uuid))
 booking_id,purpose,COALESCE(member_id,acquisition_id,'00000000-0000-0000-0000-000000000000'::uuid),amount,currency,id,
 CASE WHEN status='SUCCESS' THEN id ELSE NULL END
 FROM payment_orders ORDER BY booking_id,purpose,COALESCE(member_id,acquisition_id,'00000000-0000-0000-0000-000000000000'::uuid),
 CASE status WHEN 'SUCCESS' THEN 3 WHEN 'PENDING' THEN 2 ELSE 1 END DESC,created_at DESC,id;
CREATE UNIQUE INDEX uq_live_payment_obligation ON payment_orders(booking_id,purpose,
 COALESCE(member_id,acquisition_id,'00000000-0000-0000-0000-000000000000'::uuid)) WHERE status='PENDING';
CREATE FUNCTION require_payment_claim() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE claim payment_obligations%ROWTYPE;
BEGIN
 SELECT * INTO claim FROM payment_obligations WHERE booking_id=NEW.booking_id AND purpose=NEW.purpose
   AND subject_id=COALESCE(NEW.member_id,NEW.acquisition_id,'00000000-0000-0000-0000-000000000000'::uuid) FOR UPDATE;
 IF NOT FOUND OR claim.amount<>NEW.amount OR claim.currency<>NEW.currency OR (NEW.status='PENDING' AND claim.settled_payment_id IS NOT NULL)
 THEN RAISE EXCEPTION 'Payment requires an unsettled matching obligation' USING ERRCODE='23514'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER payment_claim_required BEFORE INSERT ON payment_orders FOR EACH ROW EXECUTE FUNCTION require_payment_claim();

CREATE TABLE payment_callback_reviews (
 id UUID PRIMARY KEY,payment_id UUID NOT NULL REFERENCES payment_orders(id),
 fingerprint VARCHAR(64) NOT NULL,reason VARCHAR(80) NOT NULL,body JSONB NOT NULL,
 state VARCHAR(40) NOT NULL DEFAULT 'REQUIRES_REVIEW',created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
 UNIQUE(payment_id,fingerprint,reason)
);
CREATE TABLE payment_booking_sync (
 payment_id UUID PRIMARY KEY REFERENCES payment_orders(id),event_id UUID REFERENCES event_outbox(event_id),
 outcome VARCHAR(20) NOT NULL CHECK(outcome IN ('SUCCESS','FAILED')),
 state VARCHAR(30) NOT NULL CHECK(state IN ('AWAITING_BOOKING','ACKNOWLEDGED','REQUIRES_REVIEW')),
 attempts INT NOT NULL DEFAULT 0 CHECK(attempts>=0),next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
 last_error_code VARCHAR(80),booking_status VARCHAR(30),checked_at TIMESTAMPTZ,updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_payment_booking_sync_retry ON payment_booking_sync(next_attempt_at) WHERE state='AWAITING_BOOKING';
INSERT INTO payment_booking_sync(payment_id,event_id,outcome,state,last_error_code)
 SELECT p.id,e.event_id,p.status,CASE WHEN e.event_id IS NULL THEN 'REQUIRES_REVIEW' ELSE 'AWAITING_BOOKING' END,
 CASE WHEN e.event_id IS NULL THEN 'LEGACY_OUTBOX_MISSING' ELSE NULL END
 FROM payment_orders p LEFT JOIN LATERAL (
   SELECT event_id FROM event_outbox WHERE body->>'eventType'='payment.completed' AND body->'payload'->>'paymentId'=p.id::text
    AND body->'payload'->>'status'=p.status ORDER BY created_at,event_id LIMIT 1
 ) e ON true WHERE p.status IN ('SUCCESS','FAILED') AND p.purpose IN ('BOOKING','GROUP_CONTRIBUTION');

CREATE FUNCTION protect_payment_order() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF (NEW.id,NEW.booking_id,NEW.payer_id,NEW.member_id,NEW.acquisition_id,NEW.seller_id,NEW.purpose,NEW.amount,NEW.currency,
     NEW.provider,NEW.provider_reference,NEW.idempotency_key,NEW.expires_at)
   IS DISTINCT FROM (OLD.id,OLD.booking_id,OLD.payer_id,OLD.member_id,OLD.acquisition_id,OLD.seller_id,OLD.purpose,OLD.amount,OLD.currency,
     OLD.provider,OLD.provider_reference,OLD.idempotency_key,OLD.expires_at)
 THEN RAISE EXCEPTION 'Payment financial identity is immutable' USING ERRCODE='23514'; END IF;
 IF OLD.status IN ('SUCCESS','FAILED') AND NEW IS DISTINCT FROM OLD
 THEN RAISE EXCEPTION 'Final payment is immutable' USING ERRCODE='23514'; END IF;
 IF NEW.status<>OLD.status AND NOT (
   (OLD.status='PENDING' AND NEW.status IN ('SUCCESS','FAILED','EXPIRED')) OR
   (OLD.status='EXPIRED' AND NEW.status IN ('SUCCESS','FAILED')))
 THEN RAISE EXCEPTION 'Invalid payment transition' USING ERRCODE='23514'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER payment_order_immutable BEFORE UPDATE ON payment_orders FOR EACH ROW EXECUTE FUNCTION protect_payment_order();
CREATE FUNCTION protect_payment_evidence() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN RAISE EXCEPTION 'Payment evidence is immutable' USING ERRCODE='23514'; END $$;
CREATE TRIGGER immutable_provider_callbacks BEFORE UPDATE OR DELETE ON provider_callbacks FOR EACH ROW EXECUTE FUNCTION protect_payment_evidence();
CREATE TRIGGER immutable_payment_audit BEFORE UPDATE OR DELETE ON payment_audit FOR EACH ROW EXECUTE FUNCTION protect_payment_evidence();
CREATE TRIGGER immutable_payment_requests BEFORE UPDATE OR DELETE ON payment_requests FOR EACH ROW EXECUTE FUNCTION protect_payment_evidence();
CREATE FUNCTION protect_payment_event() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF (NEW.event_id,NEW.exchange,NEW.routing_key,NEW.body) IS DISTINCT FROM (OLD.event_id,OLD.exchange,OLD.routing_key,OLD.body)
 THEN RAISE EXCEPTION 'Payment event content is immutable' USING ERRCODE='23514'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER immutable_payment_event BEFORE UPDATE ON event_outbox FOR EACH ROW EXECUTE FUNCTION protect_payment_event();
