-- Listing/acquisition references below are local; Booking/Payment IDs remain logical references.
ALTER TABLE transfer_listings ADD COLUMN retry_workflow VARCHAR(30)
 CHECK(retry_workflow IS NULL OR retry_workflow IN('REGISTER','EDIT','LOCK','UNLOCK','WITHDRAW','HANDOFF'));
DO $$ BEGIN
 IF EXISTS(SELECT 1 FROM transfer_listings WHERE original_amount<=0 OR price<>TRUNC(price) OR currency<>'VND' OR starts_at>=ends_at)
    OR EXISTS(SELECT 1 FROM transfer_acquisitions a JOIN transfer_listings l ON l.id=a.listing_id
       WHERE a.amount<>TRUNC(a.amount) OR a.currency<>l.currency OR a.amount>l.original_amount OR (a.state IN('PENDING','PENDING_HANDOFF','PENDING_AUDIT') AND a.expires_at>l.deadline) OR a.buyer_id=l.seller_id)
    OR EXISTS(SELECT 1 FROM transfer_listings l LEFT JOIN transfer_acquisitions a ON a.id=l.active_acquisition_id
       WHERE l.active_acquisition_id IS NOT NULL AND (a.id IS NULL OR a.listing_id<>l.id))
    OR EXISTS(SELECT listing_id FROM transfer_acquisitions WHERE state IN('PENDING','PENDING_HANDOFF','PENDING_AUDIT') GROUP BY listing_id HAVING count(*)>1)
 THEN RAISE EXCEPTION 'Transfer saga preflight failed; reconcile legacy rows before V3'; END IF;
END $$;
ALTER TABLE transfer_listings ADD CONSTRAINT transfer_listing_money_time
 CHECK(original_amount>0 AND price=TRUNC(price) AND currency='VND' AND starts_at<ends_at);
ALTER TABLE transfer_acquisitions ADD CONSTRAINT transfer_acquisition_money CHECK(amount=TRUNC(amount) AND currency='VND');
ALTER TABLE transfer_acquisitions ADD CONSTRAINT uq_acquisition_listing UNIQUE(id,listing_id);
ALTER TABLE transfer_listings ADD CONSTRAINT fk_active_acquisition_listing
 FOREIGN KEY(active_acquisition_id,id) REFERENCES transfer_acquisitions(id,listing_id) DEFERRABLE INITIALLY DEFERRED;
CREATE UNIQUE INDEX one_unresolved_acquisition_per_listing ON transfer_acquisitions(listing_id)
 WHERE state IN('PENDING','PENDING_HANDOFF','PENDING_AUDIT');
CREATE TABLE transfer_payment_receipts (
 payment_id UUID PRIMARY KEY,acquisition_id UUID NOT NULL REFERENCES transfer_acquisitions(id),fingerprint VARCHAR(64) NOT NULL,
 payload JSONB NOT NULL,received_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
-- Unknown references are evidence for manual reconciliation, never accepted acquisitions.
CREATE TABLE transfer_orphan_payments (
 payment_id UUID NOT NULL,acquisition_id UUID NOT NULL,booking_id UUID NOT NULL,payer_id UUID NOT NULL,
 fingerprint VARCHAR(64) NOT NULL,payload JSONB NOT NULL,reason VARCHAR(60) NOT NULL,
 received_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),PRIMARY KEY(payment_id,fingerprint)
);
CREATE FUNCTION protect_transfer_financial_evidence() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN RAISE EXCEPTION 'Transfer evidence is immutable' USING ERRCODE='23514'; END $$;
CREATE TRIGGER immutable_transfer_receipt BEFORE UPDATE OR DELETE ON transfer_payment_receipts
 FOR EACH ROW EXECUTE FUNCTION protect_transfer_financial_evidence();
CREATE TRIGGER immutable_transfer_orphan BEFORE UPDATE OR DELETE ON transfer_orphan_payments
 FOR EACH ROW EXECUTE FUNCTION protect_transfer_financial_evidence();
CREATE TRIGGER immutable_transfer_audit BEFORE UPDATE OR DELETE ON transfer_audit
 FOR EACH ROW EXECUTE FUNCTION protect_transfer_financial_evidence();
CREATE FUNCTION protect_transfer_listing() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF TG_OP='DELETE' THEN RAISE EXCEPTION 'Transfer listing history cannot be deleted' USING ERRCODE='23514'; END IF;
 IF (NEW.id,NEW.booking_id,NEW.seller_id,NEW.facility_id,NEW.court_id,NEW.snapshot,NEW.original_amount,NEW.currency,NEW.starts_at,NEW.ends_at)
    IS DISTINCT FROM(OLD.id,OLD.booking_id,OLD.seller_id,OLD.facility_id,OLD.court_id,OLD.snapshot,OLD.original_amount,OLD.currency,OLD.starts_at,OLD.ends_at)
 THEN RAISE EXCEPTION 'Transfer listing identity/snapshot is immutable' USING ERRCODE='23514'; END IF;
 IF (NEW.price,NEW.deadline) IS DISTINCT FROM(OLD.price,OLD.deadline) AND (OLD.state<>'ACTIVE' OR OLD.workflow<>'READY' OR NEW.state<>'ACTIVE')
 THEN RAISE EXCEPTION 'Only unlocked ready listings can change price/deadline' USING ERRCODE='23514'; END IF;
 IF NEW.state<>OLD.state AND NOT(
   (OLD.state='ACTIVE' AND NEW.state IN('LOCKED','WITHDRAWN','EXPIRED')) OR
   (OLD.state='LOCKED' AND NEW.state IN('ACTIVE','COMPLETED','EXPIRED','PENDING_AUDIT')) OR
   (OLD.state='PENDING_AUDIT' AND NEW.state='LOCKED' AND OLD.retry_workflow='HANDOFF' AND OLD.last_error LIKE 'TECHNICAL_RETRY_EXHAUSTED:%'))
 THEN RAISE EXCEPTION 'Invalid transfer listing transition' USING ERRCODE='23514'; END IF;
 IF OLD.state='COMPLETED' AND (NEW.state,NEW.active_acquisition_id,NEW.price,NEW.deadline)
    IS DISTINCT FROM(OLD.state,OLD.active_acquisition_id,OLD.price,OLD.deadline)
 THEN RAISE EXCEPTION 'Completed transfer is immutable' USING ERRCODE='23514'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER protected_transfer_listing BEFORE UPDATE OR DELETE ON transfer_listings
 FOR EACH ROW EXECUTE FUNCTION protect_transfer_listing();
CREATE FUNCTION protect_transfer_acquisition() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE l transfer_listings%ROWTYPE;
BEGIN
 IF TG_OP='DELETE' THEN RAISE EXCEPTION 'Transfer acquisition history cannot be deleted' USING ERRCODE='23514'; END IF;
 SELECT * INTO l FROM transfer_listings WHERE id=NEW.listing_id FOR UPDATE;
 IF NEW.buyer_id=l.seller_id OR (NEW.state IN('PENDING','PENDING_HANDOFF','PENDING_AUDIT') AND NEW.expires_at>l.deadline) OR NEW.currency<>l.currency OR NEW.amount>l.original_amount
 THEN RAISE EXCEPTION 'Transfer acquisition scope is invalid' USING ERRCODE='23514'; END IF;
 IF TG_OP='UPDATE' THEN
  IF (NEW.id,NEW.listing_id,NEW.buyer_id,NEW.amount,NEW.currency,NEW.expires_at)
     IS DISTINCT FROM(OLD.id,OLD.listing_id,OLD.buyer_id,OLD.amount,OLD.currency,OLD.expires_at)
  THEN RAISE EXCEPTION 'Transfer acquisition identity/price/deadline is immutable' USING ERRCODE='23514'; END IF;
  IF OLD.payment_payload IS NOT NULL AND (NEW.payment_id,NEW.payment_payload) IS DISTINCT FROM(OLD.payment_id,OLD.payment_payload)
  THEN RAISE EXCEPTION 'Verified transfer payment is immutable' USING ERRCODE='23514'; END IF;
  IF NEW.state<>OLD.state AND NOT(
    (OLD.state='PENDING' AND NEW.state IN('PENDING_HANDOFF','CANCELLED','EXPIRED','FAILED','PENDING_AUDIT')) OR
    (OLD.state='PENDING_HANDOFF' AND NEW.state IN('SUCCESS','PENDING_AUDIT')) OR
    (OLD.state='PENDING_AUDIT' AND NEW.state='PENDING_HANDOFF' AND l.retry_workflow='HANDOFF' AND l.last_error LIKE 'TECHNICAL_RETRY_EXHAUSTED:%'))
  THEN RAISE EXCEPTION 'Invalid transfer acquisition transition' USING ERRCODE='23514'; END IF;
 ELSE
  IF NEW.amount<>l.price THEN RAISE EXCEPTION 'Acquisition price must match its listing' USING ERRCODE='23514'; END IF;
 END IF;
 IF NEW.state IN('PENDING_HANDOFF','SUCCESS') AND (NEW.payment_id IS NULL OR NEW.payment_payload IS NULL)
 THEN RAISE EXCEPTION 'Verified payment is required for handoff' USING ERRCODE='23514'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER protected_transfer_acquisition BEFORE INSERT OR UPDATE OR DELETE ON transfer_acquisitions
 FOR EACH ROW EXECUTE FUNCTION protect_transfer_acquisition();
