CREATE TABLE demo_transfer_payment_terms (
 payment_id UUID PRIMARY KEY REFERENCES payment_orders(id),acquisition_id UUID NOT NULL,facility_id UUID NOT NULL,
 snapshot JSONB NOT NULL CHECK(jsonb_typeof(snapshot)='object' AND snapshot->>'mode' IS NOT DISTINCT FROM 'DEMO'),
 created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);
CREATE INDEX demo_transfer_terms_facility ON demo_transfer_payment_terms(facility_id,acquisition_id);
CREATE FUNCTION guard_demo_transfer_payment_terms() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE p payment_orders%ROWTYPE;
BEGIN
 IF TG_OP<>'INSERT' THEN RAISE EXCEPTION 'DEMO payment terms are immutable' USING ERRCODE='23514'; END IF;
 SELECT * INTO p FROM payment_orders WHERE id=NEW.payment_id;
 IF p.provider<>'DEMO' OR p.purpose<>'TRANSFER' OR
  (NEW.acquisition_id,NEW.facility_id::text,NEW.snapshot->>'bookingId',NEW.snapshot->>'buyerId',NEW.snapshot->>'sellerId') IS DISTINCT FROM
  (p.acquisition_id,NEW.snapshot->>'facilityId',p.booking_id::text,p.payer_id::text,p.seller_id::text) OR
  (NEW.snapshot->>'amount')::numeric IS DISTINCT FROM p.amount OR NEW.snapshot->>'currency' IS DISTINCT FROM p.currency OR
  (NEW.snapshot->>'expiresAt')::timestamptz IS DISTINCT FROM p.expires_at THEN RAISE EXCEPTION 'DEMO acquisition and payment differ' USING ERRCODE='23514'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER demo_transfer_payment_identity BEFORE INSERT OR UPDATE OR DELETE ON demo_transfer_payment_terms FOR EACH ROW EXECUTE FUNCTION guard_demo_transfer_payment_terms();
