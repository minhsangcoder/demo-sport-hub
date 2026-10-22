-- Historical bookings are deliberately left without a snapshot for explicit legacy reconciliation.
CREATE TABLE booking_refund_policy_snapshots (
 booking_id UUID PRIMARY KEY REFERENCES bookings(id),
 facility_id UUID NOT NULL,
 policy_id UUID NOT NULL,
 policy_version BIGINT NOT NULL CHECK(policy_version > 0),
 refund_rate_bps SMALLINT NOT NULL CHECK(refund_rate_bps BETWEEN 0 AND 10000),
 currency VARCHAR(3) NOT NULL CHECK(currency = 'VND'),
 rounding VARCHAR(20) NOT NULL CHECK(rounding = 'HALF_UP'),
 terms_hash VARCHAR(64) NOT NULL CHECK(length(terms_hash) = 64),
 origin VARCHAR(30) NOT NULL CHECK(origin IN ('BOOKING_CREATED','LEGACY_ADOPTION')),
 accepted_by UUID NOT NULL,
 accepted_at TIMESTAMPTZ NOT NULL,
 adoption_reason VARCHAR(500),
 CHECK((origin='BOOKING_CREATED' AND adoption_reason IS NULL) OR
       (origin='LEGACY_ADOPTION' AND adoption_reason IS NOT NULL AND length(trim(adoption_reason))>0))
);

CREATE FUNCTION protect_booking_refund_policy_snapshot() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE b bookings%ROWTYPE;
BEGIN
 IF TG_OP <> 'INSERT' THEN
  RAISE EXCEPTION 'Booking refund policy snapshots are immutable' USING ERRCODE='23514';
 END IF;
 SELECT * INTO b FROM bookings WHERE id=NEW.booking_id;
 IF NOT FOUND OR NEW.facility_id<>b.facility_id OR NEW.currency<>b.currency THEN
  RAISE EXCEPTION 'Refund snapshot booking scope differs' USING ERRCODE='23514';
 END IF;
 IF NEW.origin='BOOKING_CREATED' AND (b.status<>'PENDING' OR NEW.accepted_by<>b.created_by OR
    b.price_snapshot->'refundPolicy'->>'termsHash' IS DISTINCT FROM NEW.terms_hash) THEN
  RAISE EXCEPTION 'New booking refund policy must match its accepted quote' USING ERRCODE='23514';
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER immutable_booking_refund_policy_snapshot BEFORE INSERT OR UPDATE OR DELETE
 ON booking_refund_policy_snapshots FOR EACH ROW EXECUTE FUNCTION protect_booking_refund_policy_snapshot();
