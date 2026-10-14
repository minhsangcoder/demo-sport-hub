-- All FKs are local to Booking. Acquisition/payment UUIDs are logical service references.
DO $$ BEGIN
 IF EXISTS(SELECT 1 FROM transfer_leases l JOIN bookings b ON b.id=l.booking_id WHERE
    l.price>b.amount OR l.price<>TRUNC(l.price) OR b.currency<>'VND' OR l.deadline>b.starts_at
    OR (l.state IN('LOCKED','COMPLETED') AND (l.acquisition_id IS NULL OR l.buyer_id IS NULL OR l.buyer_id=l.seller_id OR l.expires_at IS NULL OR l.expires_at>l.deadline))
    OR (l.state='COMPLETED' AND l.payment_id IS NULL))
 THEN RAISE EXCEPTION 'Transfer lease preflight failed; reconcile legacy rows before V8'; END IF;
 IF EXISTS(SELECT acquisition_id FROM transfer_leases WHERE acquisition_id IS NOT NULL GROUP BY acquisition_id HAVING count(*)>1)
 THEN RAISE EXCEPTION 'Duplicate transfer acquisition preflight failed'; END IF;
END $$;
CREATE UNIQUE INDEX uq_booking_transfer_acquisition ON transfer_leases(acquisition_id) WHERE acquisition_id IS NOT NULL;
ALTER TABLE transfer_leases ADD CONSTRAINT whole_vnd_transfer_lease CHECK(price=TRUNC(price));
CREATE TABLE transfer_handoffs (
 listing_id UUID PRIMARY KEY REFERENCES transfer_leases(listing_id), booking_id UUID NOT NULL REFERENCES bookings(id),
 seller_id UUID NOT NULL,acquisition_id UUID NOT NULL UNIQUE,buyer_id UUID NOT NULL,payment_id UUID NOT NULL UNIQUE,
 amount NUMERIC(12,2) NOT NULL CHECK(amount>0 AND amount=TRUNC(amount)),currency VARCHAR(3) NOT NULL CHECK(currency='VND'),
 paid_at TIMESTAMPTZ NOT NULL,expires_at TIMESTAMPTZ NOT NULL,deadline TIMESTAMPTZ NOT NULL,
 command_fingerprint VARCHAR(64) NOT NULL CHECK(command_fingerprint ~ '^[0-9a-f]{64}$'),transferred_at TIMESTAMPTZ NOT NULL,
 CHECK(seller_id<>buyer_id AND paid_at<expires_at AND expires_at<=deadline)
);
-- Legacy COMPLETED leases retain their facts; paid_at/fingerprint are not fabricated.
CREATE FUNCTION protect_transfer_handoff() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN RAISE EXCEPTION 'Transfer handoff receipts are immutable' USING ERRCODE='23514'; END $$;
CREATE TRIGGER immutable_transfer_handoff BEFORE UPDATE OR DELETE ON transfer_handoffs
 FOR EACH ROW EXECUTE FUNCTION protect_transfer_handoff();
CREATE FUNCTION protect_transfer_lease() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF TG_OP='DELETE' THEN RAISE EXCEPTION 'Transfer lease history cannot be deleted' USING ERRCODE='23514'; END IF;
 IF (NEW.listing_id,NEW.booking_id,NEW.seller_id) IS DISTINCT FROM(OLD.listing_id,OLD.booking_id,OLD.seller_id)
 THEN RAISE EXCEPTION 'Transfer lease identity is immutable' USING ERRCODE='23514'; END IF;
 IF OLD.state='COMPLETED' AND (NEW.state,NEW.price,NEW.deadline,NEW.acquisition_id,NEW.buyer_id,NEW.expires_at,NEW.payment_id)
    IS DISTINCT FROM(OLD.state,OLD.price,OLD.deadline,OLD.acquisition_id,OLD.buyer_id,OLD.expires_at,OLD.payment_id)
 THEN RAISE EXCEPTION 'Completed transfer lease is immutable' USING ERRCODE='23514'; END IF;
 IF NEW.state<>OLD.state AND NOT(
   (OLD.state='ACTIVE' AND NEW.state IN('LOCKED','RELEASED')) OR
   (OLD.state='LOCKED' AND NEW.state IN('ACTIVE','RELEASED','COMPLETED')))
 THEN RAISE EXCEPTION 'Invalid transfer lease transition' USING ERRCODE='23514'; END IF;
 IF (NEW.price,NEW.deadline) IS DISTINCT FROM(OLD.price,OLD.deadline) AND (OLD.state<>'ACTIVE' OR NEW.state<>'ACTIVE')
 THEN RAISE EXCEPTION 'Locked transfer price/deadline cannot change' USING ERRCODE='23514'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER protected_transfer_lease BEFORE UPDATE OR DELETE ON transfer_leases
 FOR EACH ROW EXECUTE FUNCTION protect_transfer_lease();
CREATE FUNCTION check_transfer_handoff_commit() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE lid UUID; h transfer_handoffs%ROWTYPE; l transfer_leases%ROWTYPE;
BEGIN
 IF TG_TABLE_NAME='transfer_handoffs' THEN lid=NEW.listing_id;
 ELSE
   lid=NEW.listing_id;
   IF NEW.state<>'COMPLETED' OR OLD.state='COMPLETED' THEN RETURN NULL; END IF;
 END IF;
 SELECT * INTO h FROM transfer_handoffs WHERE listing_id=lid;
 IF NOT FOUND THEN RAISE EXCEPTION 'New handoff requires a durable receipt' USING ERRCODE='23514'; END IF;
 SELECT * INTO l FROM transfer_leases WHERE listing_id=lid;
 IF l.state<>'COMPLETED' OR (h.booking_id,h.seller_id,h.acquisition_id,h.buyer_id,h.payment_id,h.amount,h.expires_at,h.deadline)
    IS DISTINCT FROM(l.booking_id,l.seller_id,l.acquisition_id,l.buyer_id,l.payment_id,l.price,l.expires_at,l.deadline)
    OR NOT EXISTS(SELECT 1 FROM bookings b JOIN slot_reservations r ON r.id=b.reservation_id
      WHERE b.id=h.booking_id AND b.customer_id=h.buyer_id AND b.current_holder_id=h.buyer_id AND r.holder_id=h.buyer_id AND b.currency=h.currency)
 THEN RAISE EXCEPTION 'Handoff scope/ownership must commit consistently' USING ERRCODE='23514'; END IF;
 RETURN NULL;
END $$;
CREATE CONSTRAINT TRIGGER handoff_receipt_commit AFTER INSERT ON transfer_handoffs
 DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION check_transfer_handoff_commit();
CREATE CONSTRAINT TRIGGER transfer_completion_commit AFTER UPDATE ON transfer_leases
 DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION check_transfer_handoff_commit();
