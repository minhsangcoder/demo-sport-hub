BEGIN;

-- 1. Insert new owner users and demo staff
INSERT INTO users (id, email, password_hash, status, email_verified, created_at, updated_at)
VALUES 
    ('c2222222-2222-2222-2222-222222222222', 'owner2@sporthub.local', '$2a$10$H0QPpp/d3C.UM0ytYfyMxOo27Pwz9p7d1KC5LPwfC9MXd79YY3/NK', 'ACTIVE', true, NOW(), NOW()),
    ('c2222222-2222-2222-2222-333333333333', 'staff.badinh@sporthub.local', '$2a$10$H0QPpp/d3C.UM0ytYfyMxOo27Pwz9p7d1KC5LPwfC9MXd79YY3/NK', 'ACTIVE', true, NOW(), NOW()),
    ('c3333333-3333-3333-3333-333333333333', 'owner3@sporthub.local', '$2a$10$H0QPpp/d3C.UM0ytYfyMxOo27Pwz9p7d1KC5LPwfC9MXd79YY3/NK', 'ACTIVE', true, NOW(), NOW()),
    ('c3333333-3333-3333-3333-444444444444', 'staff.tayho@sporthub.local', '$2a$10$H0QPpp/d3C.UM0ytYfyMxOo27Pwz9p7d1KC5LPwfC9MXd79YY3/NK', 'ACTIVE', true, NOW(), NOW()),
    ('c4444444-4444-4444-4444-444444444444', 'owner4@sporthub.local', '$2a$10$H0QPpp/d3C.UM0ytYfyMxOo27Pwz9p7d1KC5LPwfC9MXd79YY3/NK', 'ACTIVE', true, NOW(), NOW())
ON CONFLICT (email) DO UPDATE 
SET password_hash = EXCLUDED.password_hash,
    status = 'ACTIVE',
    email_verified = true;

-- 2. User profiles
INSERT INTO user_profiles (user_id, full_name, created_at, updated_at)
VALUES
    ('c2222222-2222-2222-2222-222222222222', 'Hoàng Văn Nam (Chủ Sân Ba Đình)', NOW(), NOW()),
    ('c2222222-2222-2222-2222-333333333333', 'Nguyễn Văn Tuấn (Lễ tân Ba Đình)', NOW(), NOW()),
    ('c3333333-3333-3333-3333-333333333333', 'Trần Thị Mai Phương (Chủ Sân Tây Hồ)', NOW(), NOW()),
    ('c3333333-3333-3333-3333-444444444444', 'Lê Thu Hà (Lễ tân Tây Hồ)', NOW(), NOW()),
    ('c4444444-4444-4444-4444-444444444444', 'Lê Hoàng Long (Chủ Cơ Sở Mới)', NOW(), NOW())
ON CONFLICT (user_id) DO UPDATE
SET full_name = EXCLUDED.full_name;

-- 3. Roles
-- Owners get OWNER and CUSTOMER
INSERT INTO user_roles (user_id, role)
VALUES
    ('c2222222-2222-2222-2222-222222222222', 'OWNER'),
    ('c2222222-2222-2222-2222-222222222222', 'CUSTOMER'),
    ('c3333333-3333-3333-3333-333333333333', 'OWNER'),
    ('c3333333-3333-3333-3333-333333333333', 'CUSTOMER'),
    ('c4444444-4444-4444-4444-444444444444', 'OWNER'),
    ('c4444444-4444-4444-4444-444444444444', 'CUSTOMER'),
    ('c2222222-2222-2222-2222-333333333333', 'STAFF'),
    ('c3333333-3333-3333-3333-444444444444', 'STAFF')
ON CONFLICT DO NOTHING;

-- 4. Staff bindings for Facility 2 and Facility 3
-- Ba Dinh Staff binding
INSERT INTO staff_facility_bindings (id, staff_user_id, facility_id, assigned_by, is_active, version, created_at, updated_at)
VALUES ('b2222222-2222-2222-2222-222222222222', 'c2222222-2222-2222-2222-333333333333', '22222222-2222-2222-2222-222222222222', 'c2222222-2222-2222-2222-222222222222', true, 1, NOW(), NOW())
ON CONFLICT (staff_user_id, facility_id) DO UPDATE SET is_active = true;

INSERT INTO staff_profiles (id, facility_id, full_name, contact_email, is_active, binding_id, created_by, version, created_at, updated_at)
VALUES ('e2222222-2222-2222-2222-222222222222', '22222222-2222-2222-2222-222222222222', 'Nguyễn Văn Tuấn', 'staff.badinh@sporthub.local', true, 'b2222222-2222-2222-2222-222222222222', 'c2222222-2222-2222-2222-222222222222', 1, NOW(), NOW())
ON CONFLICT (id) DO NOTHING;

DELETE FROM staff_permissions WHERE binding_id = 'b2222222-2222-2222-2222-222222222222';
INSERT INTO staff_permissions (binding_id, permission)
VALUES 
    ('b2222222-2222-2222-2222-222222222222', 'BOOKING_READ'),
    ('b2222222-2222-2222-2222-222222222222', 'BOOKING_CREATE_COUNTER'),
    ('b2222222-2222-2222-2222-222222222222', 'BOOKING_PAYMENT_COUNTER'),
    ('b2222222-2222-2222-2222-222222222222', 'BOOKING_CHECK_IN'),
    ('b2222222-2222-2222-2222-222222222222', 'SCHEDULE_READ'),
    ('b2222222-2222-2222-2222-222222222222', 'SCHEDULE_MANAGE');

-- Tay Ho Staff binding
INSERT INTO staff_facility_bindings (id, staff_user_id, facility_id, assigned_by, is_active, version, created_at, updated_at)
VALUES ('b3333333-3333-3333-3333-333333333333', 'c3333333-3333-3333-3333-444444444444', '33333333-3333-3333-3333-333333333333', 'c3333333-3333-3333-3333-333333333333', true, 1, NOW(), NOW())
ON CONFLICT (staff_user_id, facility_id) DO UPDATE SET is_active = true;

INSERT INTO staff_profiles (id, facility_id, full_name, contact_email, is_active, binding_id, created_by, version, created_at, updated_at)
VALUES ('e3333333-3333-3333-3333-333333333333', '33333333-3333-3333-3333-333333333333', 'Lê Thu Hà', 'staff.tayho@sporthub.local', true, 'b3333333-3333-3333-3333-333333333333', 'c3333333-3333-3333-3333-333333333333', 1, NOW(), NOW())
ON CONFLICT (id) DO NOTHING;

DELETE FROM staff_permissions WHERE binding_id = 'b3333333-3333-3333-3333-333333333333';
INSERT INTO staff_permissions (binding_id, permission)
VALUES 
    ('b3333333-3333-3333-3333-333333333333', 'BOOKING_READ'),
    ('b3333333-3333-3333-3333-333333333333', 'BOOKING_CREATE_COUNTER'),
    ('b3333333-3333-3333-3333-333333333333', 'BOOKING_PAYMENT_COUNTER'),
    ('b3333333-3333-3333-3333-333333333333', 'BOOKING_CHECK_IN'),
    ('b3333333-3333-3333-3333-333333333333', 'SCHEDULE_READ'),
    ('b3333333-3333-3333-3333-333333333333', 'SCHEDULE_MANAGE');

COMMIT;
