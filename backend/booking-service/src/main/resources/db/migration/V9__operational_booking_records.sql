-- New operational identifiers and local records; never rewrite price/payment/transfer history.
ALTER TABLE bookings ADD COLUMN booking_code VARCHAR(10) NOT NULL
 DEFAULT upper(substr(replace(uuid_generate_v4()::text,'-',''),1,10));
ALTER TABLE bookings ADD CONSTRAINT uq_booking_code UNIQUE(booking_code);
ALTER TABLE bookings ADD CONSTRAINT chk_booking_code CHECK(booking_code ~ '^[0-9A-F]{10}$');
-- Retired check-in aliases must never be reassigned to another booking.
CREATE TABLE booking_code_registry (
 code VARCHAR(10) PRIMARY KEY, booking_id UUID NOT NULL REFERENCES bookings(id),
 holder_id UUID NOT NULL, token_version INT NOT NULL, issued_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);
INSERT INTO booking_code_registry(code,booking_id,holder_id,token_version)
 SELECT booking_code,id,current_holder_id,checkin_token_version FROM bookings;
CREATE FUNCTION record_booking_code() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF TG_OP='INSERT' OR NEW.booking_code<>OLD.booking_code THEN
  INSERT INTO booking_code_registry(code,booking_id,holder_id,token_version)
   VALUES(NEW.booking_code,NEW.id,NEW.current_holder_id,NEW.checkin_token_version);
 END IF; RETURN NULL;
END $$;
CREATE TRIGGER booking_code_issue AFTER INSERT OR UPDATE ON bookings FOR EACH ROW EXECUTE FUNCTION record_booking_code();
CREATE TABLE counter_booking_guests (
 booking_id UUID PRIMARY KEY REFERENCES bookings(id), operator_id UUID NOT NULL,
 linked_customer_id UUID, guest_name VARCHAR(180) NOT NULL CHECK(length(trim(guest_name))>0),
 guest_phone VARCHAR(30) NOT NULL CHECK(guest_phone ~ '^\+?[0-9]{8,15}$'),
 payment_capability VARCHAR(30) NOT NULL DEFAULT 'DEMO_ONLY' CHECK(payment_capability='DEMO_ONLY'),
 created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);
-- User IDs are Identity logical references, not foreign keys across service databases.
CREATE FUNCTION protect_counter_guest() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF TG_OP<>'INSERT' THEN RAISE EXCEPTION 'Counter guest snapshots are immutable' USING ERRCODE='23514'; END IF;
 IF NOT EXISTS(SELECT 1 FROM bookings b WHERE b.id=NEW.booking_id AND b.source='OFFLINE_COUNTER'
   AND b.created_by=NEW.operator_id AND b.guest_name=NEW.guest_name AND b.guest_phone=NEW.guest_phone
   AND (NEW.linked_customer_id IS NULL OR b.customer_id=NEW.linked_customer_id))
 THEN RAISE EXCEPTION 'Counter guest snapshot scope differs' USING ERRCODE='23514'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER counter_guest_integrity BEFORE INSERT OR UPDATE OR DELETE ON counter_booking_guests
 FOR EACH ROW EXECUTE FUNCTION protect_counter_guest();
CREATE FUNCTION protect_operational_booking_fields() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF (NEW.booking_code<>OLD.booking_code AND NOT (NEW.current_holder_id<>OLD.current_holder_id
   AND NEW.status='CONFIRMED' AND OLD.status='CONFIRMED' AND NEW.checkin_token_version>OLD.checkin_token_version)) OR (NEW.source,NEW.created_by,NEW.guest_name,NEW.guest_phone)
  IS DISTINCT FROM (OLD.source,OLD.created_by,OLD.guest_name,OLD.guest_phone)
 THEN RAISE EXCEPTION 'Booking origin/contact snapshot immutable; code rotates only with ownership handoff' USING ERRCODE='23514'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER operational_booking_fields_immutable BEFORE UPDATE ON bookings
 FOR EACH ROW EXECUTE FUNCTION protect_operational_booking_fields();

CREATE TABLE booking_status_audit (
 id BIGSERIAL PRIMARY KEY, booking_id UUID NOT NULL REFERENCES bookings(id),
 previous_status VARCHAR(30), status VARCHAR(30) NOT NULL, actor_id UUID,
 change_source VARCHAR(60) NOT NULL, booking_version BIGINT NOT NULL,
 recorded_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);
CREATE FUNCTION record_booking_status() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF TG_OP='INSERT' OR NEW.status<>OLD.status THEN
  INSERT INTO booking_status_audit(booking_id,previous_status,status,actor_id,change_source,booking_version)
   VALUES(NEW.id,CASE WHEN TG_OP='INSERT' THEN NULL ELSE OLD.status END,NEW.status,
    nullif(current_setting('sporthub.booking_actor',true),'')::uuid,
    coalesce(nullif(current_setting('sporthub.booking_change_source',true),''),'DATABASE'),NEW.version);
 END IF; RETURN NULL;
END $$;
CREATE TRIGGER booking_status_record AFTER INSERT OR UPDATE ON bookings FOR EACH ROW EXECUTE FUNCTION record_booking_status();
CREATE FUNCTION protect_operational_audit() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN RAISE EXCEPTION 'Operational audit is append-only' USING ERRCODE='23514'; END $$;
CREATE TRIGGER booking_status_audit_immutable BEFORE UPDATE OR DELETE ON booking_status_audit FOR EACH ROW EXECUTE FUNCTION protect_operational_audit();
CREATE TRIGGER booking_code_registry_immutable BEFORE UPDATE OR DELETE ON booking_code_registry FOR EACH ROW EXECUTE FUNCTION protect_operational_audit();
CREATE TRIGGER booking_history_immutable BEFORE UPDATE OR DELETE ON booking_history FOR EACH ROW EXECUTE FUNCTION protect_operational_audit();

-- Review annotations do not resolve money, mutate receipts, or mark a financial discrepancy settled.
CREATE TABLE booking_reconciliation_reviews (
 id UUID PRIMARY KEY, reconciliation_id UUID NOT NULL REFERENCES payment_reconciliation(id),
 actor_id UUID NOT NULL, note VARCHAR(1000) NOT NULL CHECK(length(trim(note))>0),
 idempotency_key VARCHAR(100) NOT NULL, fingerprint VARCHAR(64) NOT NULL,
 created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
 UNIQUE(reconciliation_id,actor_id,idempotency_key)
);
CREATE TRIGGER reconciliation_reviews_immutable BEFORE UPDATE OR DELETE ON booking_reconciliation_reviews
 FOR EACH ROW EXECUTE FUNCTION protect_operational_audit();
