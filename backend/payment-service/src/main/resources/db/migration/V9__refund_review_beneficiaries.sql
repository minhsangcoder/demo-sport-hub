-- Preserve evidence; a BLOCKED_RULE record is never an executed refund.
DO $$ BEGIN
 IF EXISTS(SELECT 1 FROM refunds r JOIN payment_orders p ON p.id=r.payment_id
   WHERE p.status<>'SUCCESS' OR r.state<>'BLOCKED_RULE' OR r.amount IS NOT NULL OR r.policy_reference IS NOT NULL)
 THEN RAISE EXCEPTION 'Refund review preflight failed; reconcile unsupported legacy outcomes before V9'; END IF;
END $$;
ALTER TABLE refunds ADD COLUMN beneficiary_id UUID;
UPDATE refunds r SET beneficiary_id=p.payer_id FROM payment_orders p WHERE p.id=r.payment_id;
ALTER TABLE refunds ALTER COLUMN beneficiary_id SET NOT NULL;
ALTER TABLE payment_orders ADD CONSTRAINT uq_payment_original_payer UNIQUE(id,payer_id);
ALTER TABLE refunds ADD CONSTRAINT fk_refund_original_payer FOREIGN KEY(payment_id,beneficiary_id) REFERENCES payment_orders(id,payer_id);
ALTER TABLE refunds ADD CONSTRAINT refund_review_has_no_executed_money CHECK(state='BLOCKED_RULE' AND amount IS NULL AND policy_reference IS NULL);
CREATE TABLE refund_review_notes (
 id UUID PRIMARY KEY, refund_id UUID NOT NULL REFERENCES refunds(id), actor_id UUID NOT NULL,
 note VARCHAR(2000) NOT NULL CHECK(length(btrim(note))>=20), idempotency_key VARCHAR(100) NOT NULL,
 created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(), UNIQUE(actor_id,idempotency_key)
);
CREATE FUNCTION preserve_refund_review_records() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN RAISE EXCEPTION 'Refund review evidence is immutable' USING ERRCODE='23514'; END $$;
CREATE TRIGGER immutable_refund_review BEFORE UPDATE OR DELETE ON refunds FOR EACH ROW EXECUTE FUNCTION preserve_refund_review_records();
CREATE TRIGGER immutable_refund_review_notes BEFORE UPDATE OR DELETE ON refund_review_notes FOR EACH ROW EXECUTE FUNCTION preserve_refund_review_records();

CREATE FUNCTION validate_refund_review_source() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN IF NOT EXISTS(SELECT 1 FROM payment_orders WHERE id=NEW.payment_id AND status='SUCCESS' AND payer_id=NEW.beneficiary_id)
 THEN RAISE EXCEPTION 'Refund review requires its original successful payer' USING ERRCODE='23514'; END IF; RETURN NEW; END $$;
CREATE TRIGGER valid_refund_review_source BEFORE INSERT ON refunds FOR EACH ROW EXECUTE FUNCTION validate_refund_review_source();
