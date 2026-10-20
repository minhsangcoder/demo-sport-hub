-- Update staff permission catalog: remove BOOKING_COMPLETE and BOOKING_CANCEL_PENDING, add BOOKING_PAYMENT_COUNTER
DELETE FROM staff_permissions WHERE permission IN ('BOOKING_COMPLETE', 'BOOKING_CANCEL_PENDING');
UPDATE staff_invitations SET permissions = array_remove(array_remove(permissions, 'BOOKING_COMPLETE'), 'BOOKING_CANCEL_PENDING');

ALTER TABLE staff_permissions DROP CONSTRAINT chk_staff_permission_catalog;
ALTER TABLE staff_permissions ADD CONSTRAINT chk_staff_permission_catalog CHECK(permission IN
 ('BOOKING_READ','BOOKING_CREATE_COUNTER','BOOKING_PAYMENT_COUNTER','BOOKING_CHECK_IN','SCHEDULE_READ',
  'SCHEDULE_MANAGE','COURT_STATUS_MANAGE','COURT_MAINTENANCE_MANAGE'));

CREATE OR REPLACE FUNCTION validate_staff_invitation_scope() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF NOT EXISTS(SELECT 1 FROM staff_profiles WHERE id=NEW.profile_id AND facility_id=NEW.facility_id)
 THEN RAISE EXCEPTION 'Staff invitation and profile facility differ' USING ERRCODE='23514'; END IF;
 IF NOT NEW.permissions <@ ARRAY['BOOKING_READ','BOOKING_CREATE_COUNTER','BOOKING_PAYMENT_COUNTER','BOOKING_CHECK_IN','SCHEDULE_READ','SCHEDULE_MANAGE','COURT_STATUS_MANAGE','COURT_MAINTENANCE_MANAGE']::text[]
 THEN RAISE EXCEPTION 'Unsupported Staff permission' USING ERRCODE='23514'; END IF;
 RETURN NEW;
END $$;

-- Grant BOOKING_PAYMENT_COUNTER to active staff bindings that already have counter creation
INSERT INTO staff_permissions (binding_id, permission)
SELECT b.id, 'BOOKING_PAYMENT_COUNTER'
FROM staff_facility_bindings b
WHERE b.is_active = true
ON CONFLICT (binding_id, permission) DO NOTHING;
