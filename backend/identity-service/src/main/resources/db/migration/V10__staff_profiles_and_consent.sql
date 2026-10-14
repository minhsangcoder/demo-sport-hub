-- Identity owns personnel records. facility_id is a logical reference, not a cross-database FK.
CREATE TABLE staff_profiles (
 id UUID PRIMARY KEY DEFAULT uuid_generate_v4(), facility_id UUID NOT NULL,
 full_name VARCHAR(255) NOT NULL CHECK (length(trim(full_name)) >= 2),
 contact_email VARCHAR(255), contact_phone VARCHAR(20), is_active BOOLEAN NOT NULL DEFAULT TRUE,
 binding_id UUID UNIQUE REFERENCES staff_facility_bindings(id), version BIGINT NOT NULL DEFAULT 0 CHECK(version >= 0),
 created_by UUID NOT NULL REFERENCES users(id), created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(), updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
INSERT INTO staff_profiles(facility_id,full_name,contact_email,contact_phone,is_active,binding_id,created_by)
 SELECT b.facility_id,p.full_name,u.email,u.phone,b.is_active,b.id,b.assigned_by
 FROM staff_facility_bindings b JOIN users u ON u.id=b.staff_user_id JOIN user_profiles p ON p.user_id=u.id;
ALTER TABLE staff_facility_bindings ADD COLUMN version BIGINT NOT NULL DEFAULT 0 CHECK(version >= 0);
CREATE TABLE staff_invitations (
 id UUID PRIMARY KEY DEFAULT uuid_generate_v4(), profile_id UUID NOT NULL REFERENCES staff_profiles(id),
 facility_id UUID NOT NULL, target_user_id UUID NOT NULL REFERENCES users(id), invited_by UUID NOT NULL REFERENCES users(id),
 permissions TEXT[] NOT NULL, state VARCHAR(20) NOT NULL DEFAULT 'PENDING'
  CHECK(state IN ('PENDING','ACCEPTED','DECLINED','CANCELLED','CONSUMED')),
 expires_at TIMESTAMPTZ NOT NULL, responded_at TIMESTAMPTZ, created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
 CHECK(expires_at > created_at), CHECK(cardinality(permissions) > 0)
);
CREATE UNIQUE INDEX uq_staff_profile_open_invitation ON staff_invitations(profile_id) WHERE state IN ('PENDING','ACCEPTED');
CREATE INDEX idx_staff_invitation_recipient ON staff_invitations(target_user_id,created_at DESC);
CREATE INDEX idx_staff_profiles_facility ON staff_profiles(facility_id,full_name,id);
ALTER TABLE staff_permissions ADD CONSTRAINT chk_staff_permission_catalog CHECK(permission IN
 ('BOOKING_READ','BOOKING_CREATE_COUNTER','BOOKING_CHECK_IN','BOOKING_COMPLETE','SCHEDULE_READ',
  'SCHEDULE_MANAGE','COURT_STATUS_MANAGE','COURT_MAINTENANCE_MANAGE'));
CREATE FUNCTION validate_staff_profile_binding() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF NEW.binding_id IS NOT NULL AND NOT EXISTS(SELECT 1 FROM staff_facility_bindings WHERE id=NEW.binding_id AND facility_id=NEW.facility_id)
 THEN RAISE EXCEPTION 'Staff profile and binding facility differ' USING ERRCODE='23514'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER staff_profile_binding_scope BEFORE INSERT OR UPDATE ON staff_profiles FOR EACH ROW EXECUTE FUNCTION validate_staff_profile_binding();
CREATE FUNCTION validate_staff_invitation_scope() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF NOT EXISTS(SELECT 1 FROM staff_profiles WHERE id=NEW.profile_id AND facility_id=NEW.facility_id)
 THEN RAISE EXCEPTION 'Staff invitation and profile facility differ' USING ERRCODE='23514'; END IF;
 IF NOT NEW.permissions <@ ARRAY['BOOKING_READ','BOOKING_CREATE_COUNTER','BOOKING_CHECK_IN','BOOKING_COMPLETE','SCHEDULE_READ','SCHEDULE_MANAGE','COURT_STATUS_MANAGE','COURT_MAINTENANCE_MANAGE']::text[]
 THEN RAISE EXCEPTION 'Unsupported Staff permission' USING ERRCODE='23514'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER staff_invitation_scope BEFORE INSERT OR UPDATE ON staff_invitations FOR EACH ROW EXECUTE FUNCTION validate_staff_invitation_scope();
