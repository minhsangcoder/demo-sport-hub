CREATE TABLE facility_refund_policy_versions (
 id UUID PRIMARY KEY,
 facility_id UUID NOT NULL REFERENCES facilities(id),
 version BIGINT NOT NULL CHECK(version > 0),
 refund_rate_bps SMALLINT NOT NULL CHECK(refund_rate_bps BETWEEN 0 AND 10000),
 currency VARCHAR(3) NOT NULL CHECK(currency = 'VND'),
 rounding VARCHAR(20) NOT NULL CHECK(rounding = 'HALF_UP'),
 terms_hash VARCHAR(64) NOT NULL CHECK(length(terms_hash) = 64),
 created_by UUID NOT NULL,
 created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
 UNIQUE(facility_id,version)
);

CREATE TABLE facility_refund_policy_heads (
 facility_id UUID PRIMARY KEY REFERENCES facilities(id),
 policy_version BIGINT NOT NULL,
 FOREIGN KEY(facility_id,policy_version) REFERENCES facility_refund_policy_versions(facility_id,version)
);

CREATE FUNCTION protect_facility_refund_policy_version() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 RAISE EXCEPTION 'Refund policy versions are immutable' USING ERRCODE='23514';
END $$;
CREATE TRIGGER immutable_facility_refund_policy_version
 BEFORE UPDATE OR DELETE ON facility_refund_policy_versions
 FOR EACH ROW EXECUTE FUNCTION protect_facility_refund_policy_version();

CREATE FUNCTION protect_facility_refund_policy_head() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF TG_OP = 'DELETE' THEN
  RAISE EXCEPTION 'Refund policy history cannot be reset' USING ERRCODE='23514';
 ELSIF TG_OP = 'INSERT' THEN
  IF NEW.policy_version <> 1 THEN
   RAISE EXCEPTION 'First refund policy version must be one' USING ERRCODE='23514';
  END IF;
 ELSIF NEW.facility_id <> OLD.facility_id OR NEW.policy_version <> OLD.policy_version + 1 THEN
  RAISE EXCEPTION 'Refund policy versions must advance monotonically' USING ERRCODE='23514';
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER monotonic_facility_refund_policy_head
 BEFORE INSERT OR UPDATE OR DELETE ON facility_refund_policy_heads
 FOR EACH ROW EXECUTE FUNCTION protect_facility_refund_policy_head();

-- No default rate or historical policy is inferred; each Owner explicitly configures it.
