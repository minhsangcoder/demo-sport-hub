-- Original collection payer and refund beneficiary are different after a completed direct resale.
-- Historical allocations keep their original columns and NULL peer metadata.
ALTER TABLE facility_refund_allocations
 ADD COLUMN beneficiary_id UUID,
 ADD COLUMN peer_acquisition_id UUID REFERENCES peer_booking_handoffs(acquisition_id),
 ADD CONSTRAINT peer_refund_beneficiary_pair CHECK((beneficiary_id IS NULL)=(peer_acquisition_id IS NULL));
CREATE INDEX peer_refund_funding ON facility_refund_allocations(peer_acquisition_id,payment_id)
 WHERE peer_acquisition_id IS NOT NULL;

CREATE FUNCTION guard_peer_refund_funding() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE h peer_booking_handoffs%ROWTYPE;e facility_refund_entitlements%ROWTYPE;
 f booking_real_collections%ROWTYPE;o facility_disbursement_obligations%ROWTYPE;b bookings%ROWTYPE;
BEGIN
 IF NEW.peer_acquisition_id IS NULL THEN RETURN NEW;END IF;
 SELECT * INTO h FROM peer_booking_handoffs WHERE acquisition_id=NEW.peer_acquisition_id;
 SELECT * INTO e FROM facility_refund_entitlements WHERE id=NEW.entitlement_id;
 SELECT * INTO b FROM bookings WHERE id=h.booking_id;
 SELECT * INTO f FROM booking_real_collections WHERE payment_id=NEW.payment_id;
 IF h.acquisition_id IS NULL OR e.reason IS DISTINCT FROM 'POLICY_CANCELLATION'
  OR e.acquisition_id IS DISTINCT FROM h.acquisition_id OR e.booking_id IS DISTINCT FROM h.booking_id
  OR e.facility_id IS DISTINCT FROM b.facility_id OR b.status IS DISTINCT FROM 'CANCELLED'
  OR b.current_holder_id IS DISTINCT FROM h.buyer_id OR NEW.beneficiary_id IS DISTINCT FROM h.buyer_id
  OR e.base_amount IS DISTINCT FROM h.amount OR NEW.base_amount IS DISTINCT FROM e.base_amount OR NEW.amount IS DISTINCT FROM e.target_amount
  OR f.booking_id IS DISTINCT FROM h.booking_id OR f.purpose IS DISTINCT FROM 'BOOKING'
  OR f.payment_id IS DISTINCT FROM b.payment_id OR f.payer_id IS DISTINCT FROM NEW.payer_id OR NEW.guest_booking_id IS NOT NULL
  OR f.amount<NEW.base_amount OR NOT EXISTS(SELECT 1 FROM booking_cancellation_refund_quotes q
   WHERE q.id=e.quote_id AND q.booking_id=e.booking_id AND q.acquisition_id=h.acquisition_id
    AND q.base_amount=e.base_amount AND q.target_amount=e.target_amount AND q.rate_bps=e.rate_bps
    AND q.policy_hash=e.policy_hash AND q.policy_version=e.policy_version) THEN
  RAISE EXCEPTION 'Peer cancellation must refund B from original verified court funding, never from a dispute or fictitious P collection' USING ERRCODE='23514';
 END IF;
 IF NEW.amount>0 THEN
  SELECT * INTO o FROM facility_disbursement_obligations WHERE id=NEW.obligation_id;
  IF o.id IS NULL OR o.payee_id IS DISTINCT FROM h.buyer_id OR o.guest_booking_id IS NOT NULL
   OR o.kind IS DISTINCT FROM 'POLICY_REFUND' OR o.payment_id IS DISTINCT FROM NEW.payment_id
   OR o.booking_id IS DISTINCT FROM h.booking_id OR o.facility_id IS DISTINCT FROM e.facility_id
   OR o.receiver_owner_id IS DISTINCT FROM f.receiver_owner_id OR o.amount IS DISTINCT FROM NEW.amount THEN
   RAISE EXCEPTION 'Peer cancellation payout beneficiary must be B' USING ERRCODE='23514';END IF;
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER peer_refund_funding_guard BEFORE INSERT ON facility_refund_allocations
 FOR EACH ROW EXECUTE FUNCTION guard_peer_refund_funding();
-- The V17 real_refund_capacity trigger still sums every allocation against the same actual receipt.
