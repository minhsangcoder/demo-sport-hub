-- Allow anonymous walk-in counter bookings with null customer and holder identity
ALTER TABLE bookings ALTER COLUMN customer_id DROP NOT NULL;
ALTER TABLE bookings ALTER COLUMN current_holder_id DROP NOT NULL;
ALTER TABLE slot_reservations ALTER COLUMN holder_id DROP NOT NULL;
ALTER TABLE booking_code_registry ALTER COLUMN holder_id DROP NOT NULL;

-- Invariant: booking customer_id and current_holder_id must be both null or both non-null
ALTER TABLE bookings ADD CONSTRAINT chk_booking_owner_pair
  CHECK ((customer_id IS NULL AND current_holder_id IS NULL) OR (customer_id IS NOT NULL AND current_holder_id IS NOT NULL));

-- Invariant: online bookings must always have an account owner
ALTER TABLE bookings ADD CONSTRAINT chk_booking_online_has_owner
  CHECK (source <> 'ONLINE' OR (customer_id IS NOT NULL AND current_holder_id IS NOT NULL));

-- Support null customer IDs for walk-in counter bookings while preserving snapshot integrity
CREATE OR REPLACE FUNCTION protect_counter_guest() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF TG_OP<>'INSERT' THEN RAISE EXCEPTION 'Counter guest snapshots are immutable' USING ERRCODE='23514'; END IF;
 IF NOT EXISTS(SELECT 1 FROM bookings b WHERE b.id=NEW.booking_id AND b.source='OFFLINE_COUNTER'
   AND b.created_by=NEW.operator_id AND b.guest_name=NEW.guest_name AND b.guest_phone=NEW.guest_phone
   AND (b.customer_id IS NOT DISTINCT FROM NEW.linked_customer_id
        OR (NEW.linked_customer_id IS NULL AND b.customer_id = b.created_by)))
 THEN RAISE EXCEPTION 'Counter guest snapshot scope differs' USING ERRCODE='23514'; END IF;
 RETURN NEW;
END $$;

-- Support null holder comparison during operational updates; rotate code only on account-to-account transfer handoff
CREATE OR REPLACE FUNCTION protect_operational_booking_fields() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF (NEW.booking_code <> OLD.booking_code AND NOT (
       OLD.current_holder_id IS NOT NULL AND NEW.current_holder_id IS NOT NULL
       AND NEW.current_holder_id <> OLD.current_holder_id
       AND NEW.status = 'CONFIRMED' AND OLD.status = 'CONFIRMED'
       AND NEW.checkin_token_version > OLD.checkin_token_version))
    OR (NEW.source, NEW.created_by, NEW.guest_name, NEW.guest_phone)
       IS DISTINCT FROM (OLD.source, OLD.created_by, OLD.guest_name, OLD.guest_phone)
 THEN
    RAISE EXCEPTION 'Booking origin/contact snapshot immutable; code rotates only with ownership handoff' USING ERRCODE='23514';
 END IF;
 RETURN NEW;
END $$;

-- Enforce deferred booking and reservation pair integrity with null-safe holder comparison and guest snapshot requirements
CREATE OR REPLACE FUNCTION check_booking_reservation_pair() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE
  rid UUID;
  res_holder UUID;
BEGIN
 IF TG_TABLE_NAME='bookings' THEN
   rid := NEW.reservation_id;
 ELSE
   rid := NEW.id;
   IF NOT EXISTS(SELECT 1 FROM bookings WHERE reservation_id=rid) THEN
     SELECT holder_id INTO res_holder FROM slot_reservations WHERE id=rid;
     IF res_holder IS NULL THEN
       RAISE EXCEPTION 'Unattached reservation must have a holder' USING ERRCODE='23514';
     END IF;
   END IF;
 END IF;
 IF EXISTS(SELECT 1 FROM bookings b JOIN slot_reservations r ON r.id=b.reservation_id WHERE r.id=rid AND (
   b.hold_expires_at<>r.expires_at OR
   (b.status='PENDING' AND r.state<>'HOLD') OR
   (b.status IN ('CONFIRMED','CHECKED_IN','COMPLETED') AND r.state<>'BOOKED') OR
   (b.status IN ('CANCELLED','EXPIRED') AND r.state<>'RELEASED') OR
   r.holder_id IS DISTINCT FROM b.current_holder_id OR
   (b.current_holder_id IS NULL AND (
     b.source<>'OFFLINE_COUNTER' OR
     NOT EXISTS(SELECT 1 FROM counter_booking_guests g WHERE g.booking_id=b.id
       AND g.operator_id=b.created_by AND g.linked_customer_id IS NULL
       AND g.guest_name=b.guest_name AND g.guest_phone=b.guest_phone)
   ))))
 THEN RAISE EXCEPTION 'Booking and reservation must commit consistently' USING ERRCODE='23514'; END IF;
 RETURN NULL;
END $$;

-- Enforce non-null owner matching group owner in group integrity checks
CREATE OR REPLACE FUNCTION check_group_integrity(gid UUID) RETURNS void LANGUAGE plpgsql AS $$
DECLARE g booking_groups%ROWTYPE; b bookings%ROWTYPE; members INT; owners INT; due NUMERIC; paid NUMERIC;
BEGIN
 SELECT * INTO g FROM booking_groups WHERE id=gid;
 IF NOT FOUND THEN RETURN; END IF;
 SELECT * INTO b FROM bookings WHERE id=gid;
 SELECT count(*),count(*) FILTER(WHERE user_id=g.owner_id),COALESCE(sum(amount_due),0),COALESCE(sum(amount_paid),0)
 INTO members,owners,due,paid FROM group_members WHERE group_id=gid AND active;
 IF b.currency<>'VND' OR b.amount<>TRUNC(b.amount) OR b.current_holder_id IS NULL OR g.owner_id IS DISTINCT FROM b.current_holder_id
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

DROP TRIGGER IF EXISTS booking_group_integrity_commit ON bookings;
CREATE CONSTRAINT TRIGGER booking_group_integrity_commit AFTER INSERT OR UPDATE ON bookings
 DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION check_group_commit();
