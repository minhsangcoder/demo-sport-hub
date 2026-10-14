-- Booking/member identifiers remain logical references across service databases.
-- Fail rather than rounding or rewriting a legacy financial obligation.
DO $$ BEGIN
 IF EXISTS(SELECT 1 FROM payment_orders WHERE purpose='GROUP_CONTRIBUTION' AND (currency<>'VND' OR amount<>TRUNC(amount)))
    OR EXISTS(SELECT 1 FROM payment_obligations WHERE purpose='GROUP_CONTRIBUTION' AND (currency<>'VND' OR amount<>TRUNC(amount)))
 THEN RAISE EXCEPTION 'Group payment precision preflight failed; reconcile legacy records before V7'; END IF;
END $$;
ALTER TABLE payment_orders ADD CONSTRAINT whole_vnd_group_order
 CHECK(purpose<>'GROUP_CONTRIBUTION' OR (currency='VND' AND amount=TRUNC(amount)));
ALTER TABLE payment_obligations ADD CONSTRAINT whole_vnd_group_obligation
 CHECK(purpose<>'GROUP_CONTRIBUTION' OR (currency='VND' AND amount=TRUNC(amount)));
