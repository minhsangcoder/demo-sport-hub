-- Preserve the existing policy boundary. This adds no payout, settlement or refund execution.
DO $$ BEGIN
 IF EXISTS(SELECT 1 FROM transfer_escrow e JOIN payment_orders p ON p.id=e.payment_id WHERE
     p.purpose<>'TRANSFER' OR p.status<>'SUCCESS'
     OR (e.acquisition_id,e.payer_id,e.seller_id,e.amount,e.currency)
        IS DISTINCT FROM(p.acquisition_id,p.payer_id,p.seller_id,p.amount,p.currency))
    OR EXISTS(SELECT 1 FROM payment_orders p LEFT JOIN transfer_escrow e ON e.payment_id=p.id WHERE p.purpose='TRANSFER' AND p.status='SUCCESS' AND e.payment_id IS NULL)
 THEN RAISE EXCEPTION 'Transfer escrow preflight failed; reconcile legacy evidence before V8'; END IF;
END $$;
CREATE FUNCTION protect_transfer_escrow() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF TG_OP='DELETE' THEN RAISE EXCEPTION 'Transfer escrow cannot be deleted' USING ERRCODE='23514'; END IF;
 IF TG_OP='UPDATE' THEN
  IF (NEW.payment_id,NEW.acquisition_id,NEW.payer_id,NEW.seller_id,NEW.amount,NEW.currency,NEW.created_at)
     IS DISTINCT FROM(OLD.payment_id,OLD.acquisition_id,OLD.payer_id,OLD.seller_id,OLD.amount,OLD.currency,OLD.created_at)
  THEN RAISE EXCEPTION 'Transfer escrow financial identity is immutable' USING ERRCODE='23514'; END IF;
  IF OLD.state='REFUND_REVIEW' AND NEW.state<>'REFUND_REVIEW'
  THEN RAISE EXCEPTION 'Escrow review requires an approved resolution' USING ERRCODE='23514'; END IF;
 END IF;
 IF NOT EXISTS(SELECT 1 FROM payment_orders p WHERE p.id=NEW.payment_id AND p.purpose='TRANSFER' AND p.status='SUCCESS'
     AND (p.acquisition_id,p.payer_id,p.seller_id,p.amount,p.currency)=(NEW.acquisition_id,NEW.payer_id,NEW.seller_id,NEW.amount,NEW.currency))
 THEN RAISE EXCEPTION 'Escrow must reference its verified transfer payment' USING ERRCODE='23514'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER protected_transfer_escrow BEFORE INSERT OR UPDATE OR DELETE ON transfer_escrow
 FOR EACH ROW EXECUTE FUNCTION protect_transfer_escrow();
