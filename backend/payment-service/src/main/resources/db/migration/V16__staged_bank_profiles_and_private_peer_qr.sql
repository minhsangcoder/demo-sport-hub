ALTER TABLE bank_destination_requests
 ADD COLUMN state VARCHAR(12) NOT NULL DEFAULT 'LEGACY' CHECK(state IN('LEGACY','STAGED','ACTIVATED','REJECTED')),
 ADD COLUMN activation_version BIGINT,
 ADD COLUMN expected_profile_version BIGINT,
 ADD COLUMN history_present BOOLEAN NOT NULL DEFAULT FALSE,
 ADD COLUMN decision JSONB,
 ADD CONSTRAINT bank_request_activation CHECK(state<>'ACTIVATED' OR (activation_version>0 AND decision IS NOT NULL));
CREATE INDEX bank_destination_principal_time ON customer_bank_destination_revisions(principal_id,created_at DESC)
 WHERE principal_id IS NOT NULL;
CREATE INDEX bank_destination_request_revision ON bank_destination_requests(revision_id,state);

-- The image contains recipient data and is encrypted with the existing purpose-bound vault.
CREATE TABLE peer_qr_instructions (
 acquisition_id UUID PRIMARY KEY,terms_hash VARCHAR(64) NOT NULL CHECK(terms_hash ~ '^[a-f0-9]{64}$'),
 qr_reference BIGINT GENERATED ALWAYS AS IDENTITY (MAXVALUE 999999999999999999) UNIQUE CHECK(qr_reference>0),
 image_ciphertext TEXT,content_type VARCHAR(20) NOT NULL DEFAULT 'image/png' CHECK(content_type='image/png'),
 expires_at TIMESTAMPTZ NOT NULL,created_at TIMESTAMPTZ NOT NULL
);
CREATE FUNCTION guard_peer_qr_instruction() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF (NEW.acquisition_id,NEW.terms_hash,NEW.qr_reference,NEW.content_type,NEW.expires_at,NEW.created_at)
 IS DISTINCT FROM (OLD.acquisition_id,OLD.terms_hash,OLD.qr_reference,OLD.content_type,OLD.expires_at,OLD.created_at)
 OR OLD.image_ciphertext IS NOT NULL OR NEW.image_ciphertext IS NULL THEN
 RAISE EXCEPTION 'Peer QR binding and completed image are immutable' USING ERRCODE='23514';END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER peer_qr_instruction_identity BEFORE UPDATE ON peer_qr_instructions
 FOR EACH ROW EXECUTE FUNCTION guard_peer_qr_instruction();
CREATE FUNCTION guard_bank_destination_request() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF TG_OP='DELETE' OR
  (NEW.actor_id,NEW.idempotency_key,NEW.fingerprint,NEW.revision_id,NEW.expected_profile_version,NEW.history_present)
   IS DISTINCT FROM(OLD.actor_id,OLD.idempotency_key,OLD.fingerprint,OLD.revision_id,OLD.expected_profile_version,OLD.history_present) OR
  OLD.state IN('ACTIVATED','REJECTED') OR
  NEW.state NOT IN('ACTIVATED','REJECTED') THEN
  RAISE EXCEPTION 'Bank request identity and terminal result are immutable' USING ERRCODE='23514';END IF;
 RETURN NEW;
END $$;
DROP TRIGGER immutable_bank_request ON bank_destination_requests;
CREATE TRIGGER bank_destination_request_identity BEFORE UPDATE OR DELETE ON bank_destination_requests
 FOR EACH ROW EXECUTE FUNCTION guard_bank_destination_request();
