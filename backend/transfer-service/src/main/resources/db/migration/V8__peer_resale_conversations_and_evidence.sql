ALTER TABLE transfer_listings ADD COLUMN protocol VARCHAR(24) NOT NULL DEFAULT 'LEGACY_OWNER'
 CHECK(protocol IN('LEGACY_OWNER','PEER_RESALE_V1'));
CREATE TABLE peer_listing_operations (
 actor_id UUID NOT NULL, listing_id UUID NOT NULL REFERENCES transfer_listings(id),
 action VARCHAR(24) NOT NULL CHECK(action IN('publish','withdraw')),
 idempotency_key VARCHAR(160) NOT NULL, fingerprint CHAR(64) NOT NULL,
 command JSONB NOT NULL, state VARCHAR(12) NOT NULL DEFAULT 'PENDING' CHECK(state IN('PENDING','COMPLETED','REJECTED')),
 result JSONB, created_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ NOT NULL,
 PRIMARY KEY(actor_id,action,idempotency_key), CHECK((state='PENDING')=(result IS NULL))
);
CREATE INDEX pending_peer_listing_operations ON peer_listing_operations(updated_at) WHERE state='PENDING';
CREATE FUNCTION protect_peer_listing_operation() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF TG_OP='DELETE' OR OLD.state<>'PENDING' OR NEW.state NOT IN('PENDING','COMPLETED','REJECTED') OR
  (to_jsonb(NEW)-ARRAY['state','result','updated_at']) IS DISTINCT FROM(to_jsonb(OLD)-ARRAY['state','result','updated_at']) THEN
  RAISE EXCEPTION 'Peer listing intent and terminal result are immutable' USING ERRCODE='23514';END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER immutable_peer_listing_operation BEFORE UPDATE OR DELETE ON peer_listing_operations
 FOR EACH ROW EXECUTE FUNCTION protect_peer_listing_operation();
ALTER TABLE transfer_acquisitions
 ADD COLUMN protocol VARCHAR(24) NOT NULL DEFAULT 'LEGACY_OWNER' CHECK(protocol IN('LEGACY_OWNER','PEER_RESALE_V1')),
 ADD COLUMN conversation_id UUID,
 ADD COLUMN peer_projection JSONB;

-- Retain the exact old validators, scoped to their existing transaction cohort.
DROP TRIGGER listing_finance_terms_guard ON transfer_listings;
CREATE TRIGGER listing_finance_terms_guard BEFORE INSERT OR UPDATE ON transfer_listings
 FOR EACH ROW WHEN(NEW.protocol='LEGACY_OWNER') EXECUTE FUNCTION guard_transfer_listing_finance_terms();
DROP TRIGGER acquisition_finance_terms_guard ON transfer_acquisitions;
CREATE TRIGGER acquisition_finance_terms_guard BEFORE INSERT OR UPDATE ON transfer_acquisitions
 FOR EACH ROW WHEN(NEW.protocol='LEGACY_OWNER') EXECUTE FUNCTION guard_transfer_acquisition_finance_terms();
DROP TRIGGER protected_transfer_acquisition ON transfer_acquisitions;
CREATE TRIGGER protected_transfer_acquisition BEFORE INSERT OR UPDATE ON transfer_acquisitions
 FOR EACH ROW WHEN(NEW.protocol='LEGACY_OWNER') EXECUTE FUNCTION protect_transfer_acquisition();
CREATE TRIGGER protected_transfer_acquisition_delete BEFORE DELETE ON transfer_acquisitions
 FOR EACH ROW EXECUTE FUNCTION protect_transfer_acquisition();

CREATE FUNCTION guard_peer_transfer_listing() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF TG_OP='UPDATE' AND NEW.protocol IS DISTINCT FROM OLD.protocol THEN
  RAISE EXCEPTION 'Transfer protocol cannot be changed' USING ERRCODE='23514';END IF;
 IF NEW.protocol<>'PEER_RESALE_V1' THEN RETURN NEW;END IF;
 IF NEW.currency<>'VND' OR NEW.price<>trunc(NEW.price) OR NEW.finance_terms IS NULL OR
  NEW.finance_terms->>'protocol' IS DISTINCT FROM 'PEER_RESALE_V1' OR
  NOT(NEW.finance_terms ?& ARRAY['bookingId','facilityId','courtId','sellerId','bankDestinationRevisionId','destinationHash','refundPolicy','termsHash','amount','currency','deadline']) OR
  NEW.finance_terms ?| ARRAY['accountNumber','apiKey','checksumKey','feeBps','quotedFeeAmount','quotedSellerNetAmount','receiverOwnerId'] OR
  (NEW.finance_terms->>'bookingId',NEW.finance_terms->>'facilityId',NEW.finance_terms->>'courtId',NEW.finance_terms->>'sellerId',NEW.finance_terms->>'currency')
   IS DISTINCT FROM(NEW.booking_id::text,NEW.facility_id::text,NEW.court_id::text,NEW.seller_id::text,NEW.currency) OR
  (NEW.finance_terms->>'amount')::numeric IS DISTINCT FROM NEW.price OR
  (NEW.finance_terms->>'deadline')::timestamptz IS DISTINCT FROM NEW.deadline OR
  NEW.finance_terms->>'termsHash' !~ '^[a-f0-9]{64}$' OR NEW.finance_terms->>'destinationHash' !~ '^[a-f0-9]{64}$' THEN
  RAISE EXCEPTION 'Peer listing requires fee-free accepted seller terms' USING ERRCODE='23514';END IF;
 IF TG_OP='UPDATE' AND NEW.finance_terms IS DISTINCT FROM OLD.finance_terms AND
  (OLD.state<>'ACTIVE' OR OLD.workflow<>'READY' OR NEW.state<>'ACTIVE' OR OLD.active_acquisition_id IS NOT NULL) THEN
  RAISE EXCEPTION 'Locked peer listing terms are immutable' USING ERRCODE='23514';END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER peer_listing_terms BEFORE INSERT OR UPDATE ON transfer_listings
 FOR EACH ROW EXECUTE FUNCTION guard_peer_transfer_listing();

CREATE TABLE transfer_conversations (
 id UUID PRIMARY KEY,listing_id UUID NOT NULL REFERENCES transfer_listings(id),
 seller_id UUID NOT NULL,buyer_id UUID NOT NULL CHECK(seller_id<>buyer_id),created_at TIMESTAMPTZ NOT NULL,
 UNIQUE(listing_id,seller_id,buyer_id)
);
ALTER TABLE transfer_acquisitions ADD CONSTRAINT acquisition_conversation
 FOREIGN KEY(conversation_id) REFERENCES transfer_conversations(id);
CREATE TABLE transfer_messages (
 cursor BIGSERIAL UNIQUE,id UUID PRIMARY KEY,
 conversation_id UUID NOT NULL REFERENCES transfer_conversations(id),sender_id UUID NOT NULL,
 text VARCHAR(2000) NOT NULL CHECK(length(trim(text))>0),
 idempotency_key VARCHAR(100) NOT NULL,fingerprint VARCHAR(64) NOT NULL,created_at TIMESTAMPTZ NOT NULL,
 UNIQUE(conversation_id,sender_id,idempotency_key)
);
CREATE INDEX transfer_messages_cursor ON transfer_messages(conversation_id,cursor);
CREATE INDEX transfer_messages_sender_time ON transfer_messages(conversation_id,sender_id,created_at DESC);
CREATE TABLE transfer_evidence (
 id UUID PRIMARY KEY,acquisition_id UUID NOT NULL REFERENCES transfer_acquisitions(id),uploader_id UUID NOT NULL,
 object_key VARCHAR(300) NOT NULL UNIQUE,sha256 VARCHAR(64) NOT NULL CHECK(sha256 ~ '^[a-f0-9]{64}$'),
 content_type VARCHAR(20) NOT NULL CHECK(content_type IN('image/jpeg','image/png')),
 byte_size BIGINT NOT NULL CHECK(byte_size>0 AND byte_size<=5242880),
 state VARCHAR(10) NOT NULL CHECK(state IN('STAGED','READY','PURGING')),
 cleanup_after TIMESTAMPTZ,CHECK((state='PURGING')=(cleanup_after IS NOT NULL)),
 idempotency_key VARCHAR(100) NOT NULL,fingerprint VARCHAR(64) NOT NULL,created_at TIMESTAMPTZ NOT NULL,
 UNIQUE(uploader_id,idempotency_key)
);
CREATE INDEX transfer_evidence_user_time ON transfer_evidence(uploader_id,created_at);
CREATE INDEX transfer_evidence_staged_cleanup ON transfer_evidence(created_at,id) WHERE state IN('STAGED','PURGING');
CREATE TABLE peer_payment_claims (
 acquisition_id UUID PRIMARY KEY REFERENCES transfer_acquisitions(id),buyer_id UUID NOT NULL,
 bank_reference VARCHAR(120) NOT NULL CHECK(length(trim(bank_reference))>0),
 evidence_ids UUID[] NOT NULL CHECK(cardinality(evidence_ids) BETWEEN 1 AND 3),
 fingerprint VARCHAR(64) NOT NULL,created_at TIMESTAMPTZ NOT NULL
);
CREATE TABLE peer_seller_acknowledgments (
 acquisition_id UUID PRIMARY KEY REFERENCES transfer_acquisitions(id),seller_id UUID NOT NULL,
 terms_hash VARCHAR(64) NOT NULL,fingerprint VARCHAR(64) NOT NULL,created_at TIMESTAMPTZ NOT NULL
);
CREATE TABLE peer_transfer_commands (
 actor_id UUID NOT NULL,acquisition_id UUID NOT NULL REFERENCES transfer_acquisitions(id),
 action VARCHAR(40) NOT NULL,idempotency_key VARCHAR(100) NOT NULL,fingerprint VARCHAR(64) NOT NULL,
 command JSONB NOT NULL,result JSONB,state VARCHAR(12) NOT NULL CHECK(state IN('PENDING','COMPLETED','REJECTED')),
 created_at TIMESTAMPTZ NOT NULL,updated_at TIMESTAMPTZ NOT NULL,
 PRIMARY KEY(actor_id,action,idempotency_key)
);
CREATE INDEX peer_transfer_commands_pending ON peer_transfer_commands(state,updated_at) WHERE state='PENDING';
CREATE FUNCTION guard_peer_transfer_command() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF TG_OP='DELETE' OR (NEW.actor_id,NEW.acquisition_id,NEW.action,NEW.idempotency_key,NEW.fingerprint,NEW.command,NEW.created_at)
 IS DISTINCT FROM(OLD.actor_id,OLD.acquisition_id,OLD.action,OLD.idempotency_key,OLD.fingerprint,OLD.command,OLD.created_at)
 OR (OLD.state IN('COMPLETED','REJECTED') AND (NEW.state,NEW.result) IS DISTINCT FROM(OLD.state,OLD.result))
 OR (NEW.state<>'PENDING' AND NEW.result IS NULL) THEN
 RAISE EXCEPTION 'Peer command identity and terminal result are immutable' USING ERRCODE='23514';END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER peer_transfer_command_identity BEFORE UPDATE OR DELETE ON peer_transfer_commands
 FOR EACH ROW EXECUTE FUNCTION guard_peer_transfer_command();
CREATE TABLE peer_case_evidence (
 acquisition_id UUID NOT NULL REFERENCES transfer_acquisitions(id),
 evidence_id UUID NOT NULL REFERENCES transfer_evidence(id),actor_id UUID NOT NULL,created_at TIMESTAMPTZ NOT NULL,
 PRIMARY KEY(acquisition_id,evidence_id)
);

CREATE TABLE peer_return_declarations (
 id UUID PRIMARY KEY,acquisition_id UUID NOT NULL REFERENCES transfer_acquisitions(id),seller_id UUID NOT NULL,
 amount NUMERIC(20,0) NOT NULL CHECK(amount>0),bank_reference VARCHAR(120) NOT NULL CHECK(length(trim(bank_reference))>0 AND bank_reference !~ '[[:cntrl:]]'),
 evidence_ids UUID[] NOT NULL CHECK(cardinality(evidence_ids) BETWEEN 1 AND 3),
 terms_hash VARCHAR(64) NOT NULL CHECK(terms_hash ~ '^[a-f0-9]{64}$'),
 fingerprint VARCHAR(64) NOT NULL CHECK(fingerprint ~ '^[a-f0-9]{64}$'),created_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX peer_returns_attempt ON peer_return_declarations(acquisition_id,created_at,id);
CREATE TABLE peer_return_acknowledgments (
 return_id UUID PRIMARY KEY REFERENCES peer_return_declarations(id),buyer_id UUID NOT NULL,
 fingerprint VARCHAR(64) NOT NULL CHECK(fingerprint ~ '^[a-f0-9]{64}$'),created_at TIMESTAMPTZ NOT NULL
);
CREATE TABLE peer_money_no_claims (
 acquisition_id UUID NOT NULL REFERENCES transfer_acquisitions(id),actor_id UUID NOT NULL,
 fingerprint VARCHAR(64) NOT NULL CHECK(fingerprint ~ '^[a-f0-9]{64}$'),created_at TIMESTAMPTZ NOT NULL,
 PRIMARY KEY(acquisition_id,actor_id)
);
CREATE FUNCTION guard_peer_money_history() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE a transfer_acquisitions%ROWTYPE;l transfer_listings%ROWTYPE;r peer_return_declarations%ROWTYPE;n INTEGER;
BEGIN
 IF TG_TABLE_NAME='peer_return_acknowledgments' THEN
  SELECT * INTO r FROM peer_return_declarations WHERE id=NEW.return_id;
  SELECT * INTO a FROM transfer_acquisitions WHERE id=r.acquisition_id;
  IF a.id IS NULL OR NEW.buyer_id IS DISTINCT FROM a.buyer_id THEN
   RAISE EXCEPTION 'Only the buyer acknowledges the seller return' USING ERRCODE='23514';END IF;
 ELSE
  SELECT * INTO a FROM transfer_acquisitions WHERE id=NEW.acquisition_id;
  SELECT * INTO l FROM transfer_listings WHERE id=a.listing_id;
  IF a.protocol IS DISTINCT FROM 'PEER_RESALE_V1' THEN RAISE EXCEPTION 'Peer money history needs peer terms' USING ERRCODE='23514';END IF;
  IF TG_TABLE_NAME='peer_money_no_claims' THEN
   IF NEW.actor_id NOT IN(a.buyer_id,l.seller_id) THEN RAISE EXCEPTION 'Only counterparties waive their claims' USING ERRCODE='23514';END IF;
  ELSE
   PERFORM pg_advisory_xact_lock(hashtextextended(NEW.acquisition_id::text,7063));
   SELECT count(DISTINCT id) INTO n FROM transfer_evidence WHERE id=ANY(NEW.evidence_ids)
    AND acquisition_id=a.id AND uploader_id=l.seller_id AND state='READY';
   IF NEW.seller_id IS DISTINCT FROM l.seller_id OR NEW.terms_hash IS DISTINCT FROM a.finance_terms->>'termsHash'
    OR n<>cardinality(NEW.evidence_ids) OR NEW.amount+COALESCE((SELECT sum(amount) FROM peer_return_declarations WHERE acquisition_id=a.id),0)>a.amount THEN
    RAISE EXCEPTION 'A seller return needs matching terms and private ready evidence within the peer amount' USING ERRCODE='23514';END IF;
  END IF;
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER peer_return_actor BEFORE INSERT ON peer_return_declarations FOR EACH ROW EXECUTE FUNCTION guard_peer_money_history();
CREATE TRIGGER peer_return_buyer BEFORE INSERT ON peer_return_acknowledgments FOR EACH ROW EXECUTE FUNCTION guard_peer_money_history();
CREATE TRIGGER peer_no_claim_actor BEFORE INSERT ON peer_money_no_claims FOR EACH ROW EXECUTE FUNCTION guard_peer_money_history();
CREATE TRIGGER immutable_peer_return BEFORE UPDATE OR DELETE ON peer_return_declarations FOR EACH ROW EXECUTE FUNCTION protect_transfer_financial_evidence();
CREATE TRIGGER immutable_peer_return_ack BEFORE UPDATE OR DELETE ON peer_return_acknowledgments FOR EACH ROW EXECUTE FUNCTION protect_transfer_financial_evidence();
CREATE TRIGGER immutable_peer_no_claim BEFORE UPDATE OR DELETE ON peer_money_no_claims FOR EACH ROW EXECUTE FUNCTION protect_transfer_financial_evidence();

CREATE FUNCTION guard_peer_acquisition() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE l transfer_listings%ROWTYPE;c transfer_conversations%ROWTYPE;
BEGIN
 IF TG_OP='UPDATE' AND NEW.protocol IS DISTINCT FROM OLD.protocol THEN
  RAISE EXCEPTION 'Acquisition protocol is immutable' USING ERRCODE='23514';END IF;
 SELECT * INTO l FROM transfer_listings WHERE id=NEW.listing_id;
 IF NEW.protocol IS DISTINCT FROM l.protocol THEN RAISE EXCEPTION 'Acquisition must match listing protocol' USING ERRCODE='23514';END IF;
 IF NEW.protocol<>'PEER_RESALE_V1' THEN RETURN NEW;END IF;
 IF NEW.payment_id IS NOT NULL OR NEW.payment_payload IS NOT NULL OR NEW.buyer_id=l.seller_id
  OR (TG_OP='INSERT' AND (NEW.amount<>l.price OR NEW.expires_at>l.deadline))
  OR NEW.currency<>l.currency OR NEW.conversation_id IS NULL THEN
  RAISE EXCEPTION 'Peer acquisition cannot use a native payment or another price/participant' USING ERRCODE='23514';END IF;
 SELECT * INTO c FROM transfer_conversations WHERE id=NEW.conversation_id;
 IF (c.listing_id,c.seller_id,c.buyer_id) IS DISTINCT FROM(NEW.listing_id,l.seller_id,NEW.buyer_id) OR
  NEW.finance_terms IS NULL OR
  (NEW.finance_terms->>'listingId',NEW.finance_terms->>'acquisitionId',NEW.finance_terms->>'buyerId')
   IS DISTINCT FROM(NEW.listing_id::text,NEW.id::text,NEW.buyer_id::text) OR
  (NEW.finance_terms->>'expiresAt')::timestamptz IS DISTINCT FROM NEW.expires_at OR
  NEW.finance_terms->>'termsHash' !~ '^[a-f0-9]{64}$' THEN
  RAISE EXCEPTION 'Peer acquisition terms must match its private conversation and accepted listing' USING ERRCODE='23514';END IF;
 IF TG_OP='INSERT' AND (
  NEW.finance_terms->>'sellerTermsHash' IS DISTINCT FROM l.finance_terms->>'termsHash' OR
  (NEW.finance_terms->>'listingVersion')::bigint IS DISTINCT FROM l.version OR
  (NEW.finance_terms-ARRAY['listingId','listingVersion','acquisitionId','buyerId','expiresAt','acceptedAt','sellerTermsHash','termsHash'])
   IS DISTINCT FROM(l.finance_terms-'termsHash')) THEN
  RAISE EXCEPTION 'New peer acquisition must match current listing terms' USING ERRCODE='23514';END IF;
 IF TG_OP='UPDATE' AND
  (NEW.id,NEW.listing_id,NEW.buyer_id,NEW.amount,NEW.currency,NEW.expires_at,NEW.finance_terms,NEW.conversation_id)
   IS DISTINCT FROM(OLD.id,OLD.listing_id,OLD.buyer_id,OLD.amount,OLD.currency,OLD.expires_at,OLD.finance_terms,OLD.conversation_id) THEN
  RAISE EXCEPTION 'Peer acquisition identity and terms are immutable' USING ERRCODE='23514';END IF;
 IF NEW.state IN('PENDING_HANDOFF','SUCCESS') AND
  (NEW.peer_projection->>'id' IS DISTINCT FROM NEW.id::text OR NEW.peer_projection->>'phase' IS DISTINCT FROM 'COMPLETED') THEN
  RAISE EXCEPTION 'Peer handoff requires its authoritative projection' USING ERRCODE='23514';END IF;
 IF TG_OP='UPDATE' AND NEW.state<>OLD.state AND NOT(
  OLD.state='PENDING' AND NEW.state IN('PENDING_HANDOFF','CANCELLED','EXPIRED','FAILED','PENDING_AUDIT')
  OR OLD.state='PENDING_HANDOFF' AND NEW.state IN('SUCCESS','PENDING_AUDIT')) THEN
  RAISE EXCEPTION 'Invalid peer acquisition transition' USING ERRCODE='23514';END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER peer_acquisition_guard BEFORE INSERT OR UPDATE ON transfer_acquisitions
 FOR EACH ROW EXECUTE FUNCTION guard_peer_acquisition();

CREATE FUNCTION protect_transfer_evidence_metadata() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF TG_OP='DELETE' THEN
  IF OLD.state='STAGED' AND NOT EXISTS(SELECT 1 FROM peer_case_evidence WHERE evidence_id=OLD.id)
   AND NOT EXISTS(SELECT 1 FROM peer_payment_claims WHERE OLD.id=ANY(evidence_ids)) THEN RETURN OLD;END IF;
  RAISE EXCEPTION 'Referenced evidence cannot be deleted' USING ERRCODE='23514';
 END IF;
 IF OLD.state='READY' OR NEW.state NOT IN('READY','PURGING') OR
  (OLD.state='PURGING' AND (NEW.state<>'PURGING' OR NEW.cleanup_after<=OLD.cleanup_after)) OR
  (to_jsonb(NEW)-ARRAY['state','cleanup_after']) IS DISTINCT FROM(to_jsonb(OLD)-ARRAY['state','cleanup_after']) THEN
  RAISE EXCEPTION 'Evidence identity is immutable' USING ERRCODE='23514';END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER transfer_evidence_identity BEFORE UPDATE OR DELETE ON transfer_evidence
 FOR EACH ROW EXECUTE FUNCTION protect_transfer_evidence_metadata();
CREATE TRIGGER immutable_transfer_message BEFORE UPDATE OR DELETE ON transfer_messages
 FOR EACH ROW EXECUTE FUNCTION protect_transfer_financial_evidence();
CREATE TRIGGER immutable_peer_payment_claim BEFORE UPDATE OR DELETE ON peer_payment_claims
 FOR EACH ROW EXECUTE FUNCTION protect_transfer_financial_evidence();
CREATE TRIGGER immutable_peer_seller_ack BEFORE UPDATE OR DELETE ON peer_seller_acknowledgments
 FOR EACH ROW EXECUTE FUNCTION protect_transfer_financial_evidence();
CREATE TRIGGER immutable_peer_case_evidence BEFORE UPDATE OR DELETE ON peer_case_evidence
 FOR EACH ROW EXECUTE FUNCTION protect_transfer_financial_evidence();

CREATE FUNCTION validate_peer_payment_evidence() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE a transfer_acquisitions%ROWTYPE;n INTEGER;
BEGIN
 SELECT * INTO a FROM transfer_acquisitions WHERE id=NEW.acquisition_id;
 SELECT count(DISTINCT id) INTO n FROM transfer_evidence
  WHERE id=ANY(NEW.evidence_ids) AND acquisition_id=NEW.acquisition_id AND uploader_id=NEW.buyer_id AND state='READY';
 IF a.protocol IS DISTINCT FROM 'PEER_RESALE_V1' OR a.buyer_id IS DISTINCT FROM NEW.buyer_id
  OR n<>cardinality(NEW.evidence_ids) OR NEW.created_at>=a.expires_at OR NEW.bank_reference ~ '[[:cntrl:]]' THEN
  RAISE EXCEPTION 'Payment claim needs ready evidence of this buyer and attempt before expiry' USING ERRCODE='23514';END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER peer_claim_ready_evidence BEFORE INSERT ON peer_payment_claims
 FOR EACH ROW EXECUTE FUNCTION validate_peer_payment_evidence();
CREATE FUNCTION validate_peer_message_sender() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF NOT EXISTS(SELECT 1 FROM transfer_conversations c WHERE c.id=NEW.conversation_id
  AND NEW.sender_id IN(c.seller_id,c.buyer_id)) THEN
  RAISE EXCEPTION 'Message sender is outside the conversation' USING ERRCODE='23514';END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER peer_message_participant BEFORE INSERT ON transfer_messages
 FOR EACH ROW EXECUTE FUNCTION validate_peer_message_sender();
