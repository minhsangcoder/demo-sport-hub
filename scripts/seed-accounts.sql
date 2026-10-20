-- Seed/Sync script for all test accounts with role-based access
-- Default password for all accounts: SportHub123@
-- BCrypt hash: $2a$10$H0QPpp/d3C.UM0ytYfyMxOo27Pwz9p7d1KC5LPwfC9MXd79YY3/NK

BEGIN;

-- 1. Create or update users
WITH account_data (email, full_name, primary_role) AS (
    VALUES
        ('admin@sporthub.local', 'Quản trị demo', 'ADMIN'),
        ('owner@sporthub.local', 'Chủ cơ sở demo', 'OWNER'),
        ('owner2@sporthub.local', 'Hoàng Văn Nam (Chủ Sân Ba Đình)', 'OWNER'),
        ('owner3@sporthub.local', 'Trần Thị Mai Phương (Chủ Sân Tây Hồ)', 'OWNER'),
        ('owner4@sporthub.local', 'Lê Hoàng Long (Chủ Cơ Sở Mới)', 'OWNER'),
        ('staff@sporthub.local', 'Nhân viên demo', 'STAFF'),
        ('staff.badinh@sporthub.local', 'Nguyễn Văn Tuấn (Lễ tân Ba Đình)', 'STAFF'),
        ('staff.tayho@sporthub.local', 'Lê Thu Hà (Lễ tân Tây Hồ)', 'STAFF'),
        ('customer@sporthub.local', 'Khách hàng demo', 'CUSTOMER'),
        ('customer2@sporthub.local', 'Khách hàng thứ hai', 'CUSTOMER'),
        ('demo.anh@sporthub.local', 'Nguyễn Minh Anh (Demo)', 'CUSTOMER'),
        ('demo.huy@sporthub.local', 'Trần Quốc Huy (Demo)', 'CUSTOMER'),
        ('demo.linh@sporthub.local', 'Lê Thùy Linh (Demo)', 'CUSTOMER'),
        ('demo.trang@sporthub.local', 'Phạm Thu Trang (Demo)', 'CUSTOMER'),
        ('demo.quang@sporthub.local', 'Vũ Minh Quang (Demo)', 'CUSTOMER'),
        ('demo.mai@sporthub.local', 'Đỗ Ngọc Mai (Demo)', 'CUSTOMER')
)
INSERT INTO users (id, email, password_hash, status, email_verified)
SELECT gen_random_uuid(), a.email, '$2a$10$H0QPpp/d3C.UM0ytYfyMxOo27Pwz9p7d1KC5LPwfC9MXd79YY3/NK', 'ACTIVE', true
FROM account_data a
ON CONFLICT (email) DO UPDATE 
SET password_hash = '$2a$10$H0QPpp/d3C.UM0ytYfyMxOo27Pwz9p7d1KC5LPwfC9MXd79YY3/NK',
    status = 'ACTIVE',
    email_verified = true;

-- 2. Create or update user profiles
INSERT INTO user_profiles (user_id, full_name)
SELECT u.id, d.full_name
FROM users u
JOIN (
    VALUES
        ('admin@sporthub.local', 'Quản trị demo'),
        ('owner@sporthub.local', 'Chủ cơ sở demo'),
        ('owner2@sporthub.local', 'Hoàng Văn Nam (Chủ Sân Ba Đình)'),
        ('owner3@sporthub.local', 'Trần Thị Mai Phương (Chủ Sân Tây Hồ)'),
        ('owner4@sporthub.local', 'Lê Hoàng Long (Chủ Cơ Sở Mới)'),
        ('staff@sporthub.local', 'Nhân viên demo'),
        ('staff.badinh@sporthub.local', 'Nguyễn Văn Tuấn (Lễ tân Ba Đình)'),
        ('staff.tayho@sporthub.local', 'Lê Thu Hà (Lễ tân Tây Hồ)'),
        ('customer@sporthub.local', 'Khách hàng demo'),
        ('customer2@sporthub.local', 'Khách hàng thứ hai'),
        ('demo.anh@sporthub.local', 'Nguyễn Minh Anh (Demo)'),
        ('demo.huy@sporthub.local', 'Trần Quốc Huy (Demo)'),
        ('demo.linh@sporthub.local', 'Lê Thùy Linh (Demo)'),
        ('demo.trang@sporthub.local', 'Phạm Thu Trang (Demo)'),
        ('demo.quang@sporthub.local', 'Vũ Minh Quang (Demo)'),
        ('demo.mai@sporthub.local', 'Đỗ Ngọc Mai (Demo)')
) AS d(email, full_name) ON u.email = d.email
ON CONFLICT (user_id) DO UPDATE
SET full_name = EXCLUDED.full_name;

-- 3. Assign base CUSTOMER role (Staff is not customer)
INSERT INTO user_roles (user_id, role)
SELECT id, 'CUSTOMER' FROM users 
WHERE email IN (
    'admin@sporthub.local', 'owner@sporthub.local', 'owner2@sporthub.local', 'owner3@sporthub.local', 'owner4@sporthub.local',
    'customer@sporthub.local', 'customer2@sporthub.local', 'demo.anh@sporthub.local',
    'demo.huy@sporthub.local', 'demo.linh@sporthub.local', 'demo.trang@sporthub.local',
    'demo.quang@sporthub.local', 'demo.mai@sporthub.local'
)
ON CONFLICT DO NOTHING;

-- Remove CUSTOMER role from staff if it exists
DELETE FROM user_roles 
WHERE user_id IN (SELECT id FROM users WHERE email IN ('staff@sporthub.local', 'staff.badinh@sporthub.local', 'staff.tayho@sporthub.local')) 
  AND role = 'CUSTOMER';

-- 4. Assign specific roles
INSERT INTO user_roles (user_id, role)
SELECT id, 'ADMIN' FROM users WHERE email = 'admin@sporthub.local'
ON CONFLICT DO NOTHING;

INSERT INTO user_roles (user_id, role)
SELECT id, 'OWNER' FROM users WHERE email IN ('owner@sporthub.local', 'owner2@sporthub.local', 'owner3@sporthub.local', 'owner4@sporthub.local')
ON CONFLICT DO NOTHING;

INSERT INTO user_roles (user_id, role)
SELECT id, 'STAFF' FROM users WHERE email IN ('staff@sporthub.local', 'staff.badinh@sporthub.local', 'staff.tayho@sporthub.local')
ON CONFLICT DO NOTHING;

-- 5. Bind Staff to facilities if any facility exists
DO $$
DECLARE
    v_staff_id UUID;
    v_owner_id UUID;
    v_binding_id UUID;
    v_fac RECORD;
BEGIN
    SELECT id INTO v_staff_id FROM users WHERE email = 'staff@sporthub.local';
    SELECT id INTO v_owner_id FROM users WHERE email = 'owner@sporthub.local';

    IF v_staff_id IS NOT NULL AND v_owner_id IS NOT NULL THEN
        -- Find existing facilities in facility_db or default UUIDs
        FOR v_fac IN 
            SELECT id FROM (VALUES 
                ('ef99a092-90f5-49f8-b802-07b6053e2e7a'::uuid),
                ('5fa5ca3a-c6a8-4aa1-9020-4305b5964eba'::uuid)
            ) AS f(id)
        LOOP
            IF NOT EXISTS (SELECT 1 FROM staff_facility_bindings WHERE staff_user_id = v_staff_id AND facility_id = v_fac.id) THEN
                v_binding_id := gen_random_uuid();

                INSERT INTO staff_facility_bindings (id, staff_user_id, facility_id, assigned_by, is_active)
                VALUES (v_binding_id, v_staff_id, v_fac.id, v_owner_id, true);

                INSERT INTO staff_profiles (facility_id, full_name, contact_email, is_active, binding_id, created_by)
                VALUES (v_fac.id, 'Nhân viên Demo', 'staff@sporthub.local', true, v_binding_id, v_owner_id);

                INSERT INTO staff_permissions (binding_id, permission)
                VALUES 
                    (v_binding_id, 'BOOKING_READ'),
                    (v_binding_id, 'BOOKING_CREATE_COUNTER'),
                    (v_binding_id, 'BOOKING_PAYMENT_COUNTER'),
                    (v_binding_id, 'BOOKING_CHECK_IN'),
                    (v_binding_id, 'SCHEDULE_READ'),
                    (v_binding_id, 'SCHEDULE_MANAGE'),
                    (v_binding_id, 'COURT_STATUS_MANAGE'),
                    (v_binding_id, 'COURT_MAINTENANCE_MANAGE');
            END IF;
        END LOOP;
    END IF;
END $$;

COMMIT;
