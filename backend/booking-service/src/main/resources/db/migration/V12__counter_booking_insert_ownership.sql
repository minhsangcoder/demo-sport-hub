-- Validate only newly inserted counter bookings. Existing legacy rows keep their evidence.
-- This runs at commit so booking, snapshot and reservation can be written in either order.
DO $$ BEGIN
 IF EXISTS(SELECT 1 FROM bookings b JOIN slot_reservations r ON r.id=b.reservation_id
   WHERE r.holder_id IS DISTINCT FROM b.current_holder_id
      OR (b.court_id,b.facility_id,b.starts_at,b.ends_at,b.hold_expires_at)
         IS DISTINCT FROM (r.court_id,r.facility_id,r.starts_at,r.ends_at,r.expires_at)
      OR (b.status='PENDING' AND r.state<>'HOLD')
      OR (b.status IN ('CONFIRMED','CHECKED_IN','COMPLETED') AND r.state<>'BOOKED')
      OR (b.status IN ('CANCELLED','EXPIRED') AND r.state<>'RELEASED'))
 THEN RAISE EXCEPTION 'Counter ownership structural preflight failed; review inconsistent reservation rows before V12'; END IF;
END $$;

CREATE FUNCTION check_new_counter_ownership() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE b bookings%ROWTYPE; g counter_booking_guests%ROWTYPE; r slot_reservations%ROWTYPE;
BEGIN
 IF NEW.source<>'OFFLINE_COUNTER' THEN RETURN NULL; END IF;
 SELECT * INTO b FROM bookings WHERE id=NEW.id;
 IF NOT FOUND THEN RETURN NULL; END IF;
 SELECT * INTO g FROM counter_booking_guests WHERE booking_id=b.id;
 IF NOT FOUND THEN RAISE EXCEPTION 'New counter booking requires a guest snapshot' USING ERRCODE='23514'; END IF;
 SELECT * INTO r FROM slot_reservations WHERE id=b.reservation_id;
 IF g.operator_id IS DISTINCT FROM b.created_by
    OR (g.guest_name,g.guest_phone) IS DISTINCT FROM (b.guest_name,b.guest_phone)
    OR b.customer_id IS DISTINCT FROM g.linked_customer_id
    OR b.current_holder_id IS DISTINCT FROM g.linked_customer_id
    OR r.holder_id IS DISTINCT FROM g.linked_customer_id
 THEN RAISE EXCEPTION 'New counter ownership must match the verified guest link' USING ERRCODE='23514'; END IF;
 RETURN NULL;
END $$;
CREATE CONSTRAINT TRIGGER new_counter_ownership_commit AFTER INSERT ON bookings
 DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION check_new_counter_ownership();
