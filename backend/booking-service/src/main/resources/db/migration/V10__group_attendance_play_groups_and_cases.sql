-- Attendance starts here; never invent attendance for historical whole-booking scans.
CREATE TABLE group_member_attendance (
 member_id UUID PRIMARY KEY, group_id UUID NOT NULL REFERENCES booking_groups(id),
 checked_in_at TIMESTAMPTZ NOT NULL, checked_in_by UUID NOT NULL,
 method VARCHAR(20) NOT NULL CHECK(method IN('MEMBER_QR','MANUAL_MEMBER')),
 reason VARCHAR(500), CHECK(method<>'MANUAL_MEMBER' OR (reason IS NOT NULL AND length(btrim(reason))>=20)), FOREIGN KEY(member_id,group_id) REFERENCES group_members(id,group_id)
);
CREATE FUNCTION validate_member_attendance() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF NOT EXISTS(SELECT 1 FROM group_members m JOIN booking_groups g ON g.id=m.group_id JOIN bookings b ON b.id=g.id
   WHERE m.id=NEW.member_id AND m.group_id=NEW.group_id AND m.active AND g.state='CONFIRMED' AND b.status='CHECKED_IN')
 THEN RAISE EXCEPTION 'Attendance requires an active member and checked-in confirmed group' USING ERRCODE='23514'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER valid_member_attendance BEFORE INSERT ON group_member_attendance FOR EACH ROW EXECUTE FUNCTION validate_member_attendance();

-- A social play group is separate from immutable booking participants/contributions.
CREATE TABLE play_groups (
 id UUID PRIMARY KEY, origin_group_id UUID NOT NULL UNIQUE REFERENCES booking_groups(id),
 owner_id UUID NOT NULL, name VARCHAR(180) NOT NULL,
 state VARCHAR(20) NOT NULL DEFAULT 'OPEN' CHECK(state IN('OPEN','KEPT','DISSOLVED')),
 version BIGINT NOT NULL DEFAULT 0, created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(), kept_at TIMESTAMPTZ, dissolved_at TIMESTAMPTZ
);
CREATE TABLE play_group_bookings (
 booking_group_id UUID PRIMARY KEY REFERENCES booking_groups(id), play_group_id UUID NOT NULL REFERENCES play_groups(id)
);
CREATE TABLE play_group_members (
 play_group_id UUID NOT NULL REFERENCES play_groups(id), user_id UUID NOT NULL, display_name VARCHAR(180) NOT NULL,
 active BOOLEAN NOT NULL DEFAULT TRUE, joined_at TIMESTAMPTZ NOT NULL DEFAULT NOW(), left_at TIMESTAMPTZ,
 PRIMARY KEY(play_group_id,user_id)
);
CREATE TABLE play_group_messages (
 id BIGSERIAL PRIMARY KEY, play_group_id UUID NOT NULL REFERENCES play_groups(id), author_id UUID NOT NULL,
 author_name VARCHAR(180) NOT NULL, body VARCHAR(2000) NOT NULL CHECK(length(btrim(body))>0), idempotency_key VARCHAR(100) NOT NULL,
 created_at TIMESTAMPTZ NOT NULL, UNIQUE(author_id,idempotency_key),
 FOREIGN KEY(play_group_id,author_id) REFERENCES play_group_members(play_group_id,user_id)
);
CREATE INDEX idx_group_messages_page ON play_group_messages(play_group_id,id DESC);
CREATE TABLE play_group_audit (
 id BIGSERIAL PRIMARY KEY, play_group_id UUID NOT NULL REFERENCES play_groups(id), actor_id UUID NOT NULL,
 action VARCHAR(50) NOT NULL, details JSONB NOT NULL, occurred_at TIMESTAMPTZ NOT NULL
);
-- Current facts only: no prior keep/dissolve decisions, messages or new financial members.
INSERT INTO play_groups(id,origin_group_id,owner_id,name) SELECT id,id,owner_id,name FROM booking_groups;
INSERT INTO play_group_bookings SELECT id,id FROM booking_groups;
INSERT INTO play_group_members(play_group_id,user_id,display_name)
 SELECT group_id,user_id,display_name FROM group_members WHERE active;

CREATE TABLE booking_complaint_cases (
 id UUID PRIMARY KEY, booking_id UUID NOT NULL REFERENCES bookings(id), opened_by UUID NOT NULL, reported_by UUID,
 category VARCHAR(30) NOT NULL CHECK(category IN('FACILITY_QUALITY','PAYMENT_REVIEW','TRANSFER_REVIEW')),
 subject VARCHAR(200) NOT NULL CHECK(length(btrim(subject))>0), description VARCHAR(10000) NOT NULL,
 evidence VARCHAR(5000), payment_id UUID, listing_id UUID, reference_snapshot JSONB NOT NULL,
 state VARCHAR(30) NOT NULL DEFAULT 'OPEN' CHECK(state IN('OPEN','IN_REVIEW','INFORMATION_REQUIRED','DECISION_RECORDED')),
 version BIGINT NOT NULL DEFAULT 0, idempotency_key VARCHAR(100) NOT NULL, fingerprint VARCHAR(64) NOT NULL,
 created_at TIMESTAMPTZ NOT NULL, UNIQUE(opened_by,idempotency_key)
);
CREATE INDEX idx_complaint_state ON booking_complaint_cases(state,created_at DESC);
CREATE TABLE booking_complaint_history (
 id BIGSERIAL PRIMARY KEY, case_id UUID NOT NULL REFERENCES booking_complaint_cases(id), actor_id UUID NOT NULL,
 action VARCHAR(30) NOT NULL, previous_state VARCHAR(30), state VARCHAR(30) NOT NULL,
 note VARCHAR(2000) NOT NULL, occurred_at TIMESTAMPTZ NOT NULL
);
CREATE FUNCTION preserve_phase11_evidence() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN RAISE EXCEPTION 'Phase 11 evidence is append-only' USING ERRCODE='23514'; END $$;
CREATE TRIGGER immutable_attendance BEFORE UPDATE OR DELETE ON group_member_attendance FOR EACH ROW EXECUTE FUNCTION preserve_phase11_evidence();
CREATE TRIGGER immutable_group_messages BEFORE UPDATE OR DELETE ON play_group_messages FOR EACH ROW EXECUTE FUNCTION preserve_phase11_evidence();
CREATE TRIGGER immutable_play_group_audit BEFORE UPDATE OR DELETE ON play_group_audit FOR EACH ROW EXECUTE FUNCTION preserve_phase11_evidence();
CREATE TRIGGER immutable_complaint_history BEFORE UPDATE OR DELETE ON booking_complaint_history FOR EACH ROW EXECUTE FUNCTION preserve_phase11_evidence();
CREATE FUNCTION preserve_complaint_references() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF TG_OP='DELETE' OR (NEW.id,NEW.booking_id,NEW.opened_by,NEW.reported_by,NEW.category,NEW.subject,NEW.description,NEW.evidence,NEW.payment_id,NEW.listing_id,NEW.reference_snapshot,NEW.idempotency_key,NEW.fingerprint,NEW.created_at)
   IS DISTINCT FROM (OLD.id,OLD.booking_id,OLD.opened_by,OLD.reported_by,OLD.category,OLD.subject,OLD.description,OLD.evidence,OLD.payment_id,OLD.listing_id,OLD.reference_snapshot,OLD.idempotency_key,OLD.fingerprint,OLD.created_at)
 THEN RAISE EXCEPTION 'Complaint original references are immutable' USING ERRCODE='23514'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER immutable_complaint_origin BEFORE UPDATE OR DELETE ON booking_complaint_cases FOR EACH ROW EXECUTE FUNCTION preserve_complaint_references();

CREATE FUNCTION preserve_play_group_identity() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN IF TG_OP='DELETE' OR (NEW.id,NEW.origin_group_id,NEW.owner_id,NEW.created_at) IS DISTINCT FROM (OLD.id,OLD.origin_group_id,OLD.owner_id,OLD.created_at)
 THEN RAISE EXCEPTION 'Play group origin is immutable' USING ERRCODE='23514'; END IF; RETURN NEW; END $$;
CREATE TRIGGER immutable_play_group_origin BEFORE UPDATE OR DELETE ON play_groups FOR EACH ROW EXECUTE FUNCTION preserve_play_group_identity();
