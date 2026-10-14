-- Fail on inconsistent legacy rows; never repair bookings, deadlines, payments or money implicitly.
DO $$ BEGIN
 IF EXISTS(SELECT 1 FROM bookings b JOIN slot_reservations r ON r.id=b.reservation_id
   WHERE b.facility_id<>r.facility_id OR b.court_id<>r.court_id OR b.starts_at<>r.starts_at OR b.ends_at<>r.ends_at
     OR b.hold_expires_at<>r.expires_at
     OR (b.status='PENDING' AND r.state<>'HOLD')
     OR (b.status IN ('CONFIRMED','CHECKED_IN','COMPLETED') AND r.state<>'BOOKED')
     OR (b.status IN ('CANCELLED','EXPIRED') AND r.state<>'RELEASED'))
 THEN RAISE EXCEPTION 'Booking/reservation preflight failed; reconcile legacy rows before V6'; END IF;
 IF EXISTS(SELECT payment_id FROM bookings WHERE payment_id IS NOT NULL GROUP BY payment_id HAVING count(*)>1)
 THEN RAISE EXCEPTION 'Duplicate booking payment preflight failed; reconcile before V6'; END IF;
END $$;

ALTER TABLE slot_reservations ADD CONSTRAINT uq_reservation_scope UNIQUE(id,court_id,facility_id,starts_at,ends_at);
ALTER TABLE bookings ADD CONSTRAINT fk_booking_reservation_scope
 FOREIGN KEY(reservation_id,court_id,facility_id,starts_at,ends_at)
 REFERENCES slot_reservations(id,court_id,facility_id,starts_at,ends_at);
CREATE UNIQUE INDEX uq_booking_applied_payment ON bookings(payment_id) WHERE payment_id IS NOT NULL;
-- Consumer deduplication, not a provider-side payment obligation or settlement ledger.
CREATE TABLE booking_payment_receipts (
 payment_id UUID PRIMARY KEY, booking_id UUID NOT NULL REFERENCES bookings(id),
 fingerprint VARCHAR(64) NOT NULL, received_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE FUNCTION protect_booking_lifecycle() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF (NEW.reservation_id,NEW.court_id,NEW.facility_id,NEW.starts_at,NEW.ends_at)
     IS DISTINCT FROM (OLD.reservation_id,OLD.court_id,OLD.facility_id,OLD.starts_at,OLD.ends_at)
 THEN RAISE EXCEPTION 'Booking reservation scope is immutable' USING ERRCODE='23514'; END IF;
 IF NEW.status<>OLD.status AND NOT (
   (OLD.status='PENDING' AND NEW.status IN ('CONFIRMED','CANCELLED','EXPIRED')) OR
   (OLD.status='CONFIRMED' AND NEW.status IN ('CHECKED_IN','CANCELLED')) OR
   (OLD.status='CHECKED_IN' AND NEW.status='COMPLETED'))
 THEN RAISE EXCEPTION 'Invalid booking state transition' USING ERRCODE='23514'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER booking_lifecycle_integrity BEFORE UPDATE ON bookings
 FOR EACH ROW EXECUTE FUNCTION protect_booking_lifecycle();

CREATE FUNCTION protect_reservation_lifecycle() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF (NEW.court_id,NEW.facility_id,NEW.starts_at,NEW.ends_at,NEW.quote)
     IS DISTINCT FROM (OLD.court_id,OLD.facility_id,OLD.starts_at,OLD.ends_at,OLD.quote)
 THEN RAISE EXCEPTION 'Reservation scope and quote are immutable' USING ERRCODE='23514'; END IF;
 IF NEW.state<>OLD.state AND NOT (
   (OLD.state='HOLD' AND NEW.state IN ('BOOKED','RELEASED')) OR
   (OLD.state='BOOKED' AND NEW.state='RELEASED'))
 THEN RAISE EXCEPTION 'Invalid reservation transition' USING ERRCODE='23514'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER reservation_lifecycle_integrity BEFORE UPDATE ON slot_reservations
 FOR EACH ROW EXECUTE FUNCTION protect_reservation_lifecycle();

-- Deferred to permit both records to change in either order inside ONE Booking DB transaction.
CREATE FUNCTION check_booking_reservation_pair() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE rid UUID;
BEGIN
 IF TG_TABLE_NAME='bookings' THEN rid=NEW.reservation_id; ELSE rid=NEW.id; END IF;
 IF EXISTS(SELECT 1 FROM bookings b JOIN slot_reservations r ON r.id=b.reservation_id WHERE r.id=rid AND (
   b.hold_expires_at<>r.expires_at OR
   (b.status='PENDING' AND r.state<>'HOLD') OR
   (b.status IN ('CONFIRMED','CHECKED_IN','COMPLETED') AND r.state<>'BOOKED') OR
   (b.status IN ('CANCELLED','EXPIRED') AND r.state<>'RELEASED')))
 THEN RAISE EXCEPTION 'Booking and reservation must commit consistently' USING ERRCODE='23514'; END IF;
 RETURN NULL;
END $$;
CREATE CONSTRAINT TRIGGER booking_reservation_pair AFTER INSERT OR UPDATE ON bookings
 DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION check_booking_reservation_pair();
CREATE CONSTRAINT TRIGGER reservation_booking_pair AFTER INSERT OR UPDATE ON slot_reservations
 DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION check_booking_reservation_pair();

-- State audit includes direct SQL writers. No historical events or actors are invented.
CREATE TABLE reservation_history (
 id BIGSERIAL PRIMARY KEY, reservation_id UUID NOT NULL REFERENCES slot_reservations(id),
 previous_state VARCHAR(20), state VARCHAR(20) NOT NULL, recorded_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);
CREATE FUNCTION audit_reservation_state() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF TG_OP='INSERT' THEN
   INSERT INTO reservation_history(reservation_id,previous_state,state) VALUES(NEW.id,NULL,NEW.state);
 ELSIF NEW.state<>OLD.state THEN
   INSERT INTO reservation_history(reservation_id,previous_state,state) VALUES(NEW.id,OLD.state,NEW.state);
 END IF;
 RETURN NULL;
END $$;
CREATE TRIGGER reservation_state_audit AFTER INSERT OR UPDATE ON slot_reservations
 FOR EACH ROW EXECUTE FUNCTION audit_reservation_state();
CREATE FUNCTION protect_reservation_history() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN RAISE EXCEPTION 'Reservation history is immutable' USING ERRCODE='23514'; END $$;
CREATE TRIGGER immutable_reservation_history BEFORE UPDATE OR DELETE ON reservation_history
 FOR EACH ROW EXECUTE FUNCTION protect_reservation_history();
