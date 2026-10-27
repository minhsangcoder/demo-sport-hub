-- Old rules keep their original unit and snapshots. Owners explicitly publish hourly prices.
ALTER TABLE pricing_rules
 ADD COLUMN pricing_unit VARCHAR(20) NOT NULL DEFAULT 'LEGACY_SLOT',
 ADD COLUMN price_per_hour NUMERIC(12,0),
 ADD CONSTRAINT pricing_unit_valid CHECK (
  pricing_unit='LEGACY_SLOT' AND price_per_hour IS NULL
  OR pricing_unit='HOURLY' AND price_per_hour IS NOT NULL AND price_per_hour>0
 );

CREATE TABLE facility_booking_policies (
 facility_id UUID PRIMARY KEY,
 enabled BOOLEAN NOT NULL DEFAULT FALSE,
 version BIGINT NOT NULL DEFAULT 0 CHECK(version>=0),
 selection_step_minutes INTEGER NOT NULL DEFAULT 30 CHECK(selection_step_minutes=30),
 minimum_minutes INTEGER NOT NULL DEFAULT 60 CHECK(minimum_minutes=60),
 maximum_minutes INTEGER NOT NULL DEFAULT 240 CHECK(maximum_minutes=240),
 updated_by UUID NOT NULL,
 updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE facility_booking_policy_versions (
 facility_id UUID NOT NULL REFERENCES facility_booking_policies(facility_id),
 version BIGINT NOT NULL CHECK(version>=0),
 snapshot JSONB NOT NULL,
 actor_id UUID NOT NULL,
 idempotency_key VARCHAR(100),
 fingerprint VARCHAR(64),
 recorded_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
 PRIMARY KEY(facility_id,version),
 UNIQUE(actor_id,idempotency_key),
 CHECK((idempotency_key IS NULL)=(fingerprint IS NULL))
);
CREATE TRIGGER immutable_booking_policy_version BEFORE UPDATE OR DELETE
 ON facility_booking_policy_versions FOR EACH ROW EXECUTE FUNCTION protect_price_revision();

CREATE INDEX hourly_rules_lookup ON pricing_rules(facility_id,is_active,day_of_week,specific_date)
 WHERE pricing_unit='HOURLY';
