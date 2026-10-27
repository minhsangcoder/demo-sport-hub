CREATE TABLE payment_admission_failures (
 kind VARCHAR(24) NOT NULL CHECK(kind IN('NATIVE_PAYMENT','PEER_ACQUISITION')),
 source_id UUID NOT NULL,booking_id UUID NOT NULL,principal_id UUID NOT NULL,
 event_id UUID NOT NULL UNIQUE,failed_at TIMESTAMPTZ NOT NULL,
 PRIMARY KEY(kind,source_id)
);
CREATE FUNCTION immutable_payment_admission_failure() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN RAISE EXCEPTION 'Technical admission evidence is immutable' USING ERRCODE='23514'; END $$;
CREATE TRIGGER preserve_payment_admission_failure BEFORE UPDATE OR DELETE ON payment_admission_failures
 FOR EACH ROW EXECUTE FUNCTION immutable_payment_admission_failure();
