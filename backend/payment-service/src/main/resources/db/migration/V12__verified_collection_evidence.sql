-- Only newly verified callbacks populate this table. Historical SUCCESS/outbox rows are not backfilled.
CREATE TABLE verified_payment_collections (
 payment_id UUID PRIMARY KEY REFERENCES payment_orders(id),booking_id UUID NOT NULL,payer_id UUID NOT NULL,member_id UUID,acquisition_id UUID,
 purpose VARCHAR(30) NOT NULL CHECK(purpose IN('BOOKING','GROUP_CONTRIBUTION','TRANSFER')),
 amount NUMERIC(12,2) NOT NULL CHECK(amount>0),currency VARCHAR(3) NOT NULL CHECK(currency='VND'),paid_at TIMESTAMPTZ NOT NULL,
 provider VARCHAR(40) NOT NULL,provider_account VARCHAR(120) NOT NULL,money_class VARCHAR(10) NOT NULL CHECK(money_class IN('REAL','DEMO','UNKNOWN')),
 provider_transaction_id VARCHAR(120) NOT NULL,callback_fingerprint VARCHAR(64) NOT NULL,outcome_fingerprint VARCHAR(64) NOT NULL,
 member_text VARCHAR(50) NOT NULL,canonical BOOLEAN NOT NULL,funds_available_at TIMESTAMPTZ,
 event_id UUID NOT NULL REFERENCES event_outbox(event_id),proof JSONB NOT NULL,recorded_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
 CHECK(provider<>'DEMO' OR money_class IN('DEMO','UNKNOWN'))
);
CREATE FUNCTION validate_verified_collection() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE p payment_orders%ROWTYPE;
BEGIN
 SELECT * INTO p FROM payment_orders WHERE id=NEW.payment_id;
 IF p.status IS DISTINCT FROM 'SUCCESS' OR
  (p.booking_id,p.payer_id,p.member_id,p.acquisition_id,p.purpose,p.amount,p.currency,p.paid_at,p.provider) IS DISTINCT FROM
  (NEW.booking_id,NEW.payer_id,NEW.member_id,NEW.acquisition_id,NEW.purpose,NEW.amount,NEW.currency,NEW.paid_at,NEW.provider) OR
  NOT EXISTS(SELECT 1 FROM provider_callbacks c WHERE c.provider=NEW.provider AND c.transaction_id=NEW.provider_transaction_id AND c.payment_id=NEW.payment_id AND c.fingerprint=NEW.callback_fingerprint AND c.body->>'status'='SUCCESS') OR
  NEW.canonical IS DISTINCT FROM EXISTS(SELECT 1 FROM payment_obligations o WHERE o.settled_payment_id=NEW.payment_id) THEN
  RAISE EXCEPTION 'Collection must bind a verified callback and canonical obligation decision' USING ERRCODE='23514'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER verified_collection_binding BEFORE INSERT ON verified_payment_collections FOR EACH ROW EXECUTE FUNCTION validate_verified_collection();
CREATE TRIGGER immutable_verified_collections BEFORE UPDATE OR DELETE ON verified_payment_collections FOR EACH ROW EXECUTE FUNCTION protect_payment_evidence();
CREATE FUNCTION protect_settled_payment_obligation() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF (NEW.booking_id,NEW.purpose,NEW.subject_id,NEW.amount,NEW.currency) IS DISTINCT FROM (OLD.booking_id,OLD.purpose,OLD.subject_id,OLD.amount,OLD.currency) OR
  (OLD.settled_payment_id IS NOT NULL AND NEW.settled_payment_id IS DISTINCT FROM OLD.settled_payment_id) OR
  (NEW.settled_payment_id IS NOT NULL AND NEW.current_payment_id IS DISTINCT FROM NEW.settled_payment_id) THEN
  RAISE EXCEPTION 'Canonical payment obligation identity is immutable' USING ERRCODE='23514'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER settled_obligation_identity BEFORE UPDATE ON payment_obligations FOR EACH ROW EXECUTE FUNCTION protect_settled_payment_obligation();
CREATE INDEX idx_verified_collection_booking ON verified_payment_collections(booking_id,purpose);
CREATE INDEX idx_verified_collection_paid ON verified_payment_collections(paid_at,payment_id);
