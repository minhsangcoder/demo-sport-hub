-- All references below belong to the Booking database. Payment IDs are logical references.
ALTER TABLE booking_groups ADD COLUMN invite_version BIGINT NOT NULL DEFAULT 0 CHECK(invite_version>=0);

CREATE FUNCTION check_group_integrity(gid UUID) RETURNS void LANGUAGE plpgsql AS $$
DECLARE g booking_groups%ROWTYPE; b bookings%ROWTYPE; members INT; owners INT; due NUMERIC; paid NUMERIC;
BEGIN
 SELECT * INTO g FROM booking_groups WHERE id=gid;
 IF NOT FOUND THEN RETURN; END IF;
 SELECT * INTO b FROM bookings WHERE id=gid;
 SELECT count(*),count(*) FILTER(WHERE user_id=g.owner_id),COALESCE(sum(amount_due),0),COALESCE(sum(amount_paid),0)
 INTO members,owners,due,paid FROM group_members WHERE group_id=gid AND active;
 IF b.currency<>'VND' OR b.amount<>TRUNC(b.amount) OR g.owner_id<>b.current_holder_id
    OR owners<>1 OR members>g.max_members OR g.deadline<>b.hold_expires_at
    OR (g.allocations_locked AND due<>b.amount)
    OR (NOT g.allocations_locked AND (due<>0 OR paid<>0))
 THEN RAISE EXCEPTION 'Group allocation integrity failed; reconcile legacy rows before V7' USING ERRCODE='23514'; END IF;
 IF EXISTS(SELECT 1 FROM group_members m WHERE m.group_id=gid AND (
     m.amount_paid<>TRUNC(m.amount_paid)
     OR m.amount_paid<>COALESCE((SELECT sum(c.amount) FROM group_contributions c WHERE c.member_id=m.id),0)
     OR (NOT m.active AND (m.amount_due<>0 OR m.amount_paid<>0 OR m.payment_requested_at IS NOT NULL))
     OR (m.amount_paid>0 AND (m.amount_paid<>m.amount_due OR m.payment_state NOT IN ('PAID','PAID_BY_OWNER')))
     OR (m.amount_paid=0 AND m.payment_state IN ('PAID','PAID_BY_OWNER'))))
 OR EXISTS(SELECT 1 FROM group_contributions c JOIN group_members m ON m.id=c.member_id
     WHERE (c.group_id=gid OR m.group_id=gid) AND (
         c.group_id<>m.group_id OR NOT m.active OR c.amount<>m.amount_due OR c.amount<>TRUNC(c.amount) OR c.paid_at>=g.deadline
         OR c.payer_id NOT IN(m.user_id,g.owner_id)
         OR m.payment_state<>CASE WHEN c.payer_id=m.user_id THEN 'PAID' ELSE 'PAID_BY_OWNER' END))
 OR EXISTS(SELECT member_id FROM group_contributions WHERE group_id=gid GROUP BY member_id HAVING count(*)>1)
 THEN RAISE EXCEPTION 'Group contribution integrity failed; reconcile legacy rows before V7' USING ERRCODE='23514'; END IF;
 IF (g.state='GROUP_PENDING' AND (b.status<>'PENDING' OR paid=b.amount))
    OR (g.state='CONFIRMED' AND (b.status NOT IN('CONFIRMED','CHECKED_IN','COMPLETED') OR paid<>b.amount OR NOT g.allocations_locked))
    OR (g.state='GROUP_CANCELLED' AND b.status<>'CANCELLED')
    OR (g.state='GROUP_EXPIRED' AND b.status<>'EXPIRED')
 THEN RAISE EXCEPTION 'Group and booking must commit consistently; reconcile legacy rows before V7' USING ERRCODE='23514'; END IF;
END $$;

-- Preflight never repairs, rounds, refunds or deletes financial records.
DO $$ DECLARE gid UUID; BEGIN
 FOR gid IN SELECT id FROM booking_groups LOOP PERFORM check_group_integrity(gid); END LOOP;
END $$;

ALTER TABLE group_members ADD CONSTRAINT uq_group_member_scope UNIQUE(id,group_id);
ALTER TABLE group_contributions ADD CONSTRAINT fk_contribution_member_group
 FOREIGN KEY(member_id,group_id) REFERENCES group_members(id,group_id);
ALTER TABLE group_contributions ADD CONSTRAINT whole_vnd_contribution CHECK(amount=TRUNC(amount));
ALTER TABLE group_members ADD CONSTRAINT whole_vnd_paid CHECK(amount_paid=TRUNC(amount_paid));
CREATE UNIQUE INDEX uq_group_member_contribution ON group_contributions(member_id);

CREATE FUNCTION protect_group_identity() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF TG_OP='DELETE' THEN RAISE EXCEPTION 'Group history cannot be deleted' USING ERRCODE='23514'; END IF;
 IF (NEW.id,NEW.owner_id,NEW.deadline) IS DISTINCT FROM (OLD.id,OLD.owner_id,OLD.deadline)
    OR NEW.invite_version<OLD.invite_version
 THEN RAISE EXCEPTION 'Group ownership, deadline and invitation generation are protected' USING ERRCODE='23514'; END IF;
 IF NEW.state<>OLD.state AND NOT(OLD.state='GROUP_PENDING' AND NEW.state IN('CONFIRMED','GROUP_CANCELLED','GROUP_EXPIRED'))
 THEN RAISE EXCEPTION 'Invalid group state transition' USING ERRCODE='23514'; END IF;
 IF NEW.state='GROUP_CANCELLED' AND OLD.state<>'GROUP_CANCELLED'
    AND EXISTS(SELECT 1 FROM group_members WHERE group_id=OLD.id AND (amount_paid>0 OR payment_requested_at IS NOT NULL))
 THEN RAISE EXCEPTION 'Paid group cancellation requires a resolution policy' USING ERRCODE='23514'; END IF;
 IF NEW.allocations_locked IS DISTINCT FROM OLD.allocations_locked
    AND EXISTS(SELECT 1 FROM group_members WHERE group_id=OLD.id AND (amount_paid>0 OR payment_requested_at IS NOT NULL))
 THEN RAISE EXCEPTION 'Payment started; allocation changes require a resolution policy' USING ERRCODE='23514'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER protected_group_identity BEFORE UPDATE OR DELETE ON booking_groups
 FOR EACH ROW EXECUTE FUNCTION protect_group_identity();

CREATE FUNCTION protect_group_member() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF TG_OP='DELETE' THEN RAISE EXCEPTION 'Group member history cannot be deleted' USING ERRCODE='23514'; END IF;
 -- Serialize all writers, including direct SQL, before evaluating capacity and sum invariants.
 PERFORM 1 FROM booking_groups WHERE id=NEW.group_id FOR UPDATE;
 IF TG_OP='UPDATE' THEN
   IF (NEW.id,NEW.group_id,NEW.user_id) IS DISTINCT FROM (OLD.id,OLD.group_id,OLD.user_id)
   THEN RAISE EXCEPTION 'Group member identity is immutable' USING ERRCODE='23514'; END IF;
   IF OLD.amount_paid>0 AND (NEW.active,NEW.amount_due,NEW.amount_paid,NEW.payment_state)
      IS DISTINCT FROM (OLD.active,OLD.amount_due,OLD.amount_paid,OLD.payment_state)
   THEN RAISE EXCEPTION 'Paid group contributions are immutable' USING ERRCODE='23514'; END IF;
   IF OLD.payment_requested_at IS NOT NULL AND NEW.payment_requested_at IS DISTINCT FROM OLD.payment_requested_at
   THEN RAISE EXCEPTION 'Payment intent cannot be cleared without a resolution protocol' USING ERRCODE='23514'; END IF;
   IF (NEW.active,NEW.amount_due) IS DISTINCT FROM (OLD.active,OLD.amount_due)
      AND EXISTS(SELECT 1 FROM group_members WHERE group_id=OLD.group_id AND (amount_paid>0 OR payment_requested_at IS NOT NULL))
   THEN RAISE EXCEPTION 'Payment started; membership and allocations are frozen' USING ERRCODE='23514'; END IF;
   IF NEW.active AND NOT OLD.active AND EXISTS(SELECT 1 FROM booking_groups WHERE id=OLD.group_id AND (allocations_locked OR state<>'GROUP_PENDING'))
   THEN RAISE EXCEPTION 'Group membership is finalized' USING ERRCODE='23514'; END IF;
 ELSE
   IF EXISTS(SELECT 1 FROM booking_groups WHERE id=NEW.group_id AND (allocations_locked OR state<>'GROUP_PENDING'))
   THEN RAISE EXCEPTION 'Group membership is finalized' USING ERRCODE='23514'; END IF;
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER protected_group_member BEFORE INSERT OR UPDATE OR DELETE ON group_members
 FOR EACH ROW EXECUTE FUNCTION protect_group_member();

CREATE FUNCTION protect_group_contribution() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE g booking_groups%ROWTYPE;
BEGIN
 IF TG_OP<>'INSERT' THEN RAISE EXCEPTION 'Group financial receipts are immutable' USING ERRCODE='23514'; END IF;
 SELECT * INTO g FROM booking_groups WHERE id=NEW.group_id FOR UPDATE;
 IF g.state<>'GROUP_PENDING' OR NOT g.allocations_locked OR NEW.paid_at>=g.deadline
    OR EXISTS(SELECT 1 FROM bookings WHERE id=NEW.group_id AND status<>'PENDING')
 THEN RAISE EXCEPTION 'Group contribution window is closed' USING ERRCODE='23514'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER protected_group_contribution BEFORE INSERT OR UPDATE OR DELETE ON group_contributions
 FOR EACH ROW EXECUTE FUNCTION protect_group_contribution();

-- A member receipt and its projection, or group/booking state, can change in either order
-- within ONE local transaction. The committed sum must always match actual receipts.
CREATE FUNCTION check_group_commit() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF TG_TABLE_NAME IN('bookings','booking_groups') THEN PERFORM check_group_integrity(NEW.id);
 ELSE PERFORM check_group_integrity(NEW.group_id); END IF;
 RETURN NULL;
END $$;
CREATE CONSTRAINT TRIGGER group_integrity_commit AFTER INSERT OR UPDATE ON booking_groups
 DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION check_group_commit();
CREATE CONSTRAINT TRIGGER member_integrity_commit AFTER INSERT OR UPDATE ON group_members
 DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION check_group_commit();
CREATE CONSTRAINT TRIGGER contribution_integrity_commit AFTER INSERT ON group_contributions
 DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION check_group_commit();
CREATE CONSTRAINT TRIGGER booking_group_integrity_commit AFTER UPDATE ON bookings
 DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION check_group_commit();
