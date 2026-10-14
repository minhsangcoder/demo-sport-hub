-- UC-5.4 operational cancellation before payment; no Staff refund or paid cancellation authority.
ALTER TABLE staff_permissions DROP CONSTRAINT chk_staff_permission_catalog;
ALTER TABLE staff_permissions ADD CONSTRAINT chk_staff_permission_catalog CHECK(permission IN
 ('BOOKING_READ','BOOKING_CREATE_COUNTER','BOOKING_CHECK_IN','BOOKING_COMPLETE','SCHEDULE_READ',
  'SCHEDULE_MANAGE','COURT_STATUS_MANAGE','COURT_MAINTENANCE_MANAGE','BOOKING_CANCEL_PENDING'));
CREATE OR REPLACE FUNCTION validate_staff_invitation_scope() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF NOT EXISTS(SELECT 1 FROM staff_profiles WHERE id=NEW.profile_id AND facility_id=NEW.facility_id)
 THEN RAISE EXCEPTION 'Staff invitation and profile facility differ' USING ERRCODE='23514'; END IF;
 IF NOT NEW.permissions <@ ARRAY['BOOKING_READ','BOOKING_CREATE_COUNTER','BOOKING_CHECK_IN','BOOKING_COMPLETE','SCHEDULE_READ','SCHEDULE_MANAGE','COURT_STATUS_MANAGE','COURT_MAINTENANCE_MANAGE','BOOKING_CANCEL_PENDING']::text[]
 THEN RAISE EXCEPTION 'Unsupported Staff permission' USING ERRCODE='23514'; END IF;
 RETURN NEW;
END $$;
