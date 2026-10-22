ALTER TABLE transfer_acquisitions ADD COLUMN abort_decision JSONB;
ALTER TABLE transfer_acquisitions ADD CONSTRAINT demo_abort_decision_identity CHECK(abort_decision IS NULL OR (
 jsonb_typeof(abort_decision)='object' AND finance_terms IS NOT NULL AND finance_terms->>'mode' IS NOT DISTINCT FROM 'DEMO' AND
 abort_decision ?& ARRAY['decision','origin','acquisitionId','listingId','bookingId','paymentId','buyerId','sellerId','amount','currency'] AND abort_decision->>'decision'='ABORTED' AND
 abort_decision->>'origin'='ACQUISITION_DECISION' AND abort_decision->>'acquisitionId'=id::text AND
 abort_decision->>'listingId'=listing_id::text AND abort_decision->>'buyerId'=buyer_id::text AND
 (abort_decision->>'amount')::numeric=amount AND abort_decision->>'currency'=currency AND
 (abort_decision->>'paymentId')::uuid IS NOT NULL AND state NOT IN('PENDING_HANDOFF','SUCCESS')));

CREATE OR REPLACE FUNCTION protect_transfer_acquisition() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE l transfer_listings%ROWTYPE;
BEGIN
 IF TG_OP='DELETE' THEN RAISE EXCEPTION 'Transfer acquisition history cannot be deleted' USING ERRCODE='23514'; END IF;
 SELECT * INTO l FROM transfer_listings WHERE id=NEW.listing_id FOR UPDATE;
 IF NEW.buyer_id=l.seller_id OR (NEW.state IN('PENDING','PENDING_HANDOFF','PENDING_AUDIT') AND NEW.expires_at>l.deadline) OR NEW.currency<>l.currency OR NEW.amount>l.original_amount THEN RAISE EXCEPTION 'Transfer acquisition scope is invalid' USING ERRCODE='23514'; END IF;
 IF TG_OP='UPDATE' THEN
  IF (NEW.id,NEW.listing_id,NEW.buyer_id,NEW.amount,NEW.currency,NEW.expires_at) IS DISTINCT FROM(OLD.id,OLD.listing_id,OLD.buyer_id,OLD.amount,OLD.currency,OLD.expires_at) THEN RAISE EXCEPTION 'Transfer acquisition identity/price/deadline is immutable' USING ERRCODE='23514'; END IF;
  IF OLD.payment_payload IS NOT NULL AND (NEW.payment_id,NEW.payment_payload) IS DISTINCT FROM(OLD.payment_id,OLD.payment_payload) THEN RAISE EXCEPTION 'Verified transfer payment is immutable' USING ERRCODE='23514'; END IF;
  IF OLD.abort_decision IS NOT NULL AND NEW.abort_decision IS DISTINCT FROM OLD.abort_decision THEN RAISE EXCEPTION 'Aborted acquisition decision is immutable' USING ERRCODE='23514'; END IF;
  IF NEW.state<>OLD.state AND NOT(
   (OLD.state='PENDING' AND NEW.state IN('PENDING_HANDOFF','CANCELLED','EXPIRED','FAILED','PENDING_AUDIT')) OR
   (OLD.state='PENDING_HANDOFF' AND NEW.state IN('SUCCESS','PENDING_AUDIT')) OR
   (OLD.state='PENDING_AUDIT' AND NEW.state='PENDING_HANDOFF' AND l.retry_workflow='HANDOFF' AND l.last_error LIKE 'TECHNICAL_RETRY_EXHAUSTED:%') OR
   (OLD.state IN('PENDING_HANDOFF','PENDING_AUDIT') AND NEW.state='FAILED' AND NEW.abort_decision IS NOT NULL AND NEW.finance_terms->>'mode'='DEMO')) THEN
   RAISE EXCEPTION 'Invalid transfer acquisition transition' USING ERRCODE='23514'; END IF;
 ELSE
  IF NEW.amount<>l.price THEN RAISE EXCEPTION 'Acquisition price must match its listing' USING ERRCODE='23514'; END IF;
 END IF;
 IF NEW.state IN('PENDING_HANDOFF','SUCCESS') AND (NEW.payment_id IS NULL OR NEW.payment_payload IS NULL) THEN RAISE EXCEPTION 'Verified payment is required for handoff' USING ERRCODE='23514'; END IF;
 RETURN NEW;
END $$;
