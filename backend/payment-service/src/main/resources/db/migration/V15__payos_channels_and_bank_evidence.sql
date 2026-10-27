-- Additive REAL protocol. Existing DEMO/legacy money is deliberately not backfilled.
CREATE TABLE payos_channels (
 id UUID PRIMARY KEY,facility_id UUID NOT NULL,owner_id UUID NOT NULL,
 status VARCHAR(20) NOT NULL CHECK(status IN('PREPARING','READY','DISABLED')),
 version BIGINT NOT NULL CHECK(version>0),current_revision_id UUID,
 native_channel_hash VARCHAR(64) NOT NULL UNIQUE,
 created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);
CREATE UNIQUE INDEX payos_one_active_facility ON payos_channels(facility_id) WHERE status<>'DISABLED';
CREATE TABLE payos_channel_revisions (
 id UUID PRIMARY KEY,channel_id UUID NOT NULL REFERENCES payos_channels(id),revision BIGINT NOT NULL CHECK(revision>0),
 credentials_ciphertext TEXT NOT NULL,bank_bin VARCHAR(6),account_ciphertext TEXT,masked_account VARCHAR(24),account_name VARCHAR(150),
 receiver_hash VARCHAR(64),verified_at TIMESTAMPTZ,created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
 UNIQUE(channel_id,revision),UNIQUE(channel_id,id),CHECK(bank_bin IS NULL OR bank_bin ~ '^[0-9]{6}$'),
 CHECK((verified_at IS NULL)=(receiver_hash IS NULL)),
 CHECK(verified_at IS NULL OR (bank_bin IS NOT NULL AND account_ciphertext IS NOT NULL AND masked_account IS NOT NULL))
);
ALTER TABLE payos_channels ADD CONSTRAINT payos_current_revision_fk FOREIGN KEY(id,current_revision_id) REFERENCES payos_channel_revisions(channel_id,id);
CREATE SEQUENCE payos_order_codes MINVALUE 1 MAXVALUE 78364164095 NO CYCLE;
CREATE TABLE payos_qr_sessions (
 payment_id UUID PRIMARY KEY REFERENCES payment_orders(id),channel_id UUID NOT NULL REFERENCES payos_channels(id),
 receiver_revision_id UUID NOT NULL,facility_id UUID NOT NULL,receiver_owner_id UUID NOT NULL,
 order_code BIGINT NOT NULL DEFAULT nextval('payos_order_codes') UNIQUE CHECK(order_code BETWEEN 1 AND 78364164095),
 description VARCHAR(9) NOT NULL UNIQUE CHECK(description ~ '^[A-Z0-9]{9}$'),transfer_terms JSONB,counter_operator BOOLEAN NOT NULL DEFAULT FALSE,
 state VARCHAR(25) NOT NULL CHECK(state IN('INITIALIZING','CREATE_UNKNOWN','READY','PARTIAL','VERIFIED','EXPIRED','REVIEW')),
 native_link_id VARCHAR(120),qr_code TEXT,checkout_url TEXT,
 amount_received NUMERIC(20,0) NOT NULL DEFAULT 0 CHECK(amount_received>=0),
 claim_id UUID,claim_until TIMESTAMPTZ,attempts INT NOT NULL DEFAULT 0 CHECK(attempts>=0),
 next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),last_error_code VARCHAR(80),last_refresh_at TIMESTAMPTZ,
 created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),updated_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
 FOREIGN KEY(channel_id,receiver_revision_id) REFERENCES payos_channel_revisions(channel_id,id),
 CHECK((claim_id IS NULL)=(claim_until IS NULL)),UNIQUE(channel_id,native_link_id)
);
CREATE INDEX payos_session_recovery ON payos_qr_sessions(next_attempt_at,channel_id) WHERE state<>'VERIFIED';
CREATE TABLE payos_bank_observations (
 id UUID PRIMARY KEY,channel_id UUID NOT NULL REFERENCES payos_channels(id),payment_id UUID REFERENCES payment_orders(id),
 native_reference VARCHAR(120) NOT NULL,order_code BIGINT NOT NULL,native_link_id VARCHAR(120),
 fingerprint VARCHAR(64) NOT NULL,raw_ciphertext TEXT NOT NULL,
 received_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),UNIQUE(channel_id,native_reference,fingerprint)
);
CREATE TABLE payos_observation_processing (
 observation_id UUID PRIMARY KEY REFERENCES payos_bank_observations(id),
 state VARCHAR(20) NOT NULL CHECK(state IN('OBSERVED','VERIFIED','QUARANTINED','IGNORED')),
 reason VARCHAR(80),processed_at TIMESTAMPTZ
);
CREATE TABLE payos_bank_credits (
 id UUID PRIMARY KEY,channel_id UUID NOT NULL REFERENCES payos_channels(id),payment_id UUID NOT NULL REFERENCES payos_qr_sessions(payment_id),
 native_reference VARCHAR(120) NOT NULL,amount NUMERIC(20,0) NOT NULL CHECK(amount>0),currency VARCHAR(3) NOT NULL CHECK(currency='VND'),
 bank_time TIMESTAMPTZ NOT NULL,verified_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
 fingerprint VARCHAR(64) NOT NULL,proof_ciphertext TEXT NOT NULL,UNIQUE(channel_id,native_reference)
);
CREATE INDEX payos_credit_payment ON payos_bank_credits(payment_id,bank_time,id);
CREATE TABLE real_payment_collections (
 payment_id UUID PRIMARY KEY REFERENCES payment_orders(id),channel_id UUID NOT NULL REFERENCES payos_channels(id),
 receiver_revision_id UUID NOT NULL,facility_id UUID NOT NULL,receiver_owner_id UUID NOT NULL,
 applied_amount NUMERIC(20,0) NOT NULL CHECK(applied_amount>=0),collected_amount NUMERIC(20,0) NOT NULL CHECK(collected_amount>=applied_amount),
 paid_at TIMESTAMPTZ NOT NULL,verified_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
 fingerprint VARCHAR(64) NOT NULL,canonical BOOLEAN NOT NULL,event_id UUID NOT NULL REFERENCES event_outbox(event_id),
 FOREIGN KEY(channel_id,receiver_revision_id) REFERENCES payos_channel_revisions(channel_id,id)
);
CREATE TABLE real_unapplied_funds (
 id UUID PRIMARY KEY,payment_id UUID NOT NULL REFERENCES payment_orders(id),source_key VARCHAR(160) NOT NULL UNIQUE,
 reason VARCHAR(40) NOT NULL CHECK(reason IN('OVERPAYMENT','NON_CANONICAL','EXPIRED_PARTIAL','LATE_COLLECTION')),
 amount NUMERIC(20,0) NOT NULL CHECK(amount>0),created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),event_id UUID NOT NULL REFERENCES event_outbox(event_id)
);
CREATE TABLE customer_bank_destination_revisions (
 id UUID PRIMARY KEY,principal_id UUID,guest_booking_id UUID,bank_bin VARCHAR(6) NOT NULL CHECK(bank_bin ~ '^[0-9]{6}$'),
 bank_name VARCHAR(150) NOT NULL,account_ciphertext TEXT NOT NULL,masked_account VARCHAR(24) NOT NULL,
 account_name VARCHAR(100) NOT NULL DEFAULT '',destination_hash VARCHAR(64) NOT NULL,
 consent_reference VARCHAR(200) NOT NULL CHECK(length(trim(consent_reference))>0),created_by UUID NOT NULL,
 created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),CHECK((principal_id IS NULL)<>(guest_booking_id IS NULL))
);
CREATE TABLE bank_destination_requests (
 actor_id UUID NOT NULL,idempotency_key VARCHAR(100) NOT NULL,fingerprint VARCHAR(64) NOT NULL,
 revision_id UUID NOT NULL REFERENCES customer_bank_destination_revisions(id),PRIMARY KEY(actor_id,idempotency_key)
);
CREATE TABLE payos_channel_audit (
 id UUID PRIMARY KEY,channel_id UUID NOT NULL REFERENCES payos_channels(id),actor_id UUID NOT NULL,
 action VARCHAR(40) NOT NULL,version BIGINT NOT NULL,created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);
CREATE TABLE payos_channel_commands (
 actor_id UUID NOT NULL,operation VARCHAR(20) NOT NULL,idempotency_key VARCHAR(100) NOT NULL,
 fingerprint VARCHAR(64) NOT NULL,channel_id UUID NOT NULL REFERENCES payos_channels(id),revision_id UUID NOT NULL REFERENCES payos_channel_revisions(id),
 PRIMARY KEY(actor_id,operation,idempotency_key)
);
CREATE FUNCTION protect_payos_revision() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF TG_OP='DELETE' OR (NEW.id,NEW.channel_id,NEW.revision,NEW.credentials_ciphertext,NEW.created_at) IS DISTINCT FROM
 (OLD.id,OLD.channel_id,OLD.revision,OLD.credentials_ciphertext,OLD.created_at) OR OLD.verified_at IS NOT NULL THEN
 RAISE EXCEPTION 'payOS revision is immutable; append a new revision' USING ERRCODE='23514'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER payos_revision_identity BEFORE UPDATE OR DELETE ON payos_channel_revisions FOR EACH ROW EXECUTE FUNCTION protect_payos_revision();
CREATE FUNCTION protect_payos_session_identity() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF TG_OP='DELETE' OR (NEW.payment_id,NEW.channel_id,NEW.receiver_revision_id,NEW.facility_id,NEW.receiver_owner_id,NEW.order_code,NEW.description,NEW.transfer_terms,NEW.counter_operator,NEW.created_at)
 IS DISTINCT FROM (OLD.payment_id,OLD.channel_id,OLD.receiver_revision_id,OLD.facility_id,OLD.receiver_owner_id,OLD.order_code,OLD.description,OLD.transfer_terms,OLD.counter_operator,OLD.created_at)
 OR (OLD.native_link_id IS NOT NULL AND NEW.native_link_id IS DISTINCT FROM OLD.native_link_id) THEN
 RAISE EXCEPTION 'payOS order identity is immutable' USING ERRCODE='23514'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER payos_session_identity BEFORE UPDATE OR DELETE ON payos_qr_sessions FOR EACH ROW EXECUTE FUNCTION protect_payos_session_identity();
CREATE TRIGGER immutable_payos_observation BEFORE UPDATE OR DELETE ON payos_bank_observations FOR EACH ROW EXECUTE FUNCTION protect_payment_evidence();
CREATE TRIGGER immutable_payos_credit BEFORE UPDATE OR DELETE ON payos_bank_credits FOR EACH ROW EXECUTE FUNCTION protect_payment_evidence();
CREATE TRIGGER immutable_real_collection BEFORE UPDATE OR DELETE ON real_payment_collections FOR EACH ROW EXECUTE FUNCTION protect_payment_evidence();
CREATE TRIGGER immutable_real_unapplied BEFORE UPDATE OR DELETE ON real_unapplied_funds FOR EACH ROW EXECUTE FUNCTION protect_payment_evidence();
CREATE TRIGGER immutable_bank_destination BEFORE UPDATE OR DELETE ON customer_bank_destination_revisions FOR EACH ROW EXECUTE FUNCTION protect_payment_evidence();
CREATE TRIGGER immutable_bank_request BEFORE UPDATE OR DELETE ON bank_destination_requests FOR EACH ROW EXECUTE FUNCTION protect_payment_evidence();
CREATE TRIGGER immutable_channel_audit BEFORE UPDATE OR DELETE ON payos_channel_audit FOR EACH ROW EXECUTE FUNCTION protect_payment_evidence();
CREATE TRIGGER immutable_channel_command BEFORE UPDATE OR DELETE ON payos_channel_commands FOR EACH ROW EXECUTE FUNCTION protect_payment_evidence();
