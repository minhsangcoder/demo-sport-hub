-- Isolated simulated provider state. These rows are never evidence of real bank transfers.
CREATE TABLE beneficiary_registration_requests (
 owner_id UUID NOT NULL,idempotency_key VARCHAR(100) NOT NULL,fingerprint VARCHAR(64) NOT NULL,
 revision_id UUID NOT NULL REFERENCES seller_payout_account_revisions(id),PRIMARY KEY(owner_id,idempotency_key)
);
CREATE TRIGGER immutable_beneficiary_requests BEFORE UPDATE OR DELETE ON beneficiary_registration_requests FOR EACH ROW EXECUTE FUNCTION protect_payment_evidence();
CREATE TABLE demo_provider_movement_commands (
 attempt_id UUID PRIMARY KEY,order_id UUID NOT NULL,provider_account VARCHAR(120) NOT NULL,region VARCHAR(40) NOT NULL,
 idempotency_key VARCHAR(150) NOT NULL,request_fingerprint VARCHAR(64) NOT NULL,request_body JSONB NOT NULL,
 result JSONB NOT NULL,created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
 UNIQUE(provider_account,region,idempotency_key)
);
CREATE TABLE demo_provider_movement_events (
 event_id VARCHAR(200) PRIMARY KEY,attempt_id UUID NOT NULL REFERENCES demo_provider_movement_commands(attempt_id),
 body JSONB NOT NULL,occurred_at TIMESTAMPTZ NOT NULL
);
CREATE TRIGGER immutable_demo_provider_commands BEFORE UPDATE OR DELETE ON demo_provider_movement_commands FOR EACH ROW EXECUTE FUNCTION protect_payment_evidence();
CREATE TRIGGER immutable_demo_provider_events BEFORE UPDATE OR DELETE ON demo_provider_movement_events FOR EACH ROW EXECUTE FUNCTION protect_payment_evidence();
