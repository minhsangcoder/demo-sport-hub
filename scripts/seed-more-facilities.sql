BEGIN;

-- 1. Insert facilities
INSERT INTO facilities (id, owner_id, name, phone, address_line, province, ward, timezone, status, province_code, ward_code, contact_email, created_at, updated_at)
VALUES 
    (
        '22222222-2222-2222-2222-222222222222', 
        'c2222222-2222-2222-2222-222222222222', 
        'Cụm Sân Cầu Lông Ba Đình', 
        '+84988123456', 
        'Số 12 phố Đội Cấn', 
        'Thành phố Hà Nội', 
        'Phường Ngọc Hà', 
        'Asia/Ho_Chi_Minh', 
        'ACTIVE', 
        '01', 
        '00004', 
        'owner2@sporthub.local',
        NOW(), 
        NOW()
    ),
    (
        '33333333-3333-3333-3333-333333333333', 
        'c3333333-3333-3333-3333-333333333333', 
        'CLB Pickleball & Tennis Tây Hồ', 
        '+84977654321', 
        'Số 68 đường Lạc Long Quân', 
        'Thành phố Hà Nội', 
        'Phường Xuân La', 
        'Asia/Ho_Chi_Minh', 
        'ACTIVE', 
        '01', 
        '00064', 
        'owner3@sporthub.local',
        NOW(), 
        NOW()
    )
ON CONFLICT (id) DO UPDATE
SET name = EXCLUDED.name,
    status = 'ACTIVE',
    updated_at = NOW();

-- 2. Insert amenities
INSERT INTO facility_amenities (facility_id, name)
VALUES 
    ('22222222-2222-2222-2222-222222222222', 'Bãi đỗ xe'),
    ('22222222-2222-2222-2222-222222222222', 'Điều hòa'),
    ('22222222-2222-2222-2222-222222222222', 'Căng tin nước uống'),
    ('33333333-3333-3333-3333-333333333333', 'Bãi đỗ xe ô tô'),
    ('33333333-3333-3333-3333-333333333333', 'Phòng thay đồ & Tắm nóng lạnh'),
    ('33333333-3333-3333-3333-333333333333', 'Cho thuê vợt bóng')
ON CONFLICT DO NOTHING;

-- 3. Insert Courts
INSERT INTO courts (id, facility_id, sport_category_id, code, name, enabled, version)
VALUES
    ('22222222-2222-2222-2222-000000000001', '22222222-2222-2222-2222-222222222222', '6f213de7-7682-4920-bcfb-6d464758147a', 'BD-01', 'Sân Cầu Lông 1', true, 1),
    ('22222222-2222-2222-2222-000000000002', '22222222-2222-2222-2222-222222222222', '6f213de7-7682-4920-bcfb-6d464758147a', 'BD-02', 'Sân Cầu Lông 2', true, 1),
    ('33333333-3333-3333-3333-000000000001', '33333333-3333-3333-3333-333333333333', '0a1fb58e-1012-4e87-aaf8-ce643f291b43', 'TH-PB01', 'Sân Pickleball VIP 1', true, 1),
    ('33333333-3333-3333-3333-000000000002', '33333333-3333-3333-3333-333333333333', '0a1fb58e-1012-4e87-aaf8-ce643f291b43', 'TH-PB02', 'Sân Pickleball VIP 2', true, 1),
    ('33333333-3333-3333-3333-000000000003', '33333333-3333-3333-3333-333333333333', '975fddf3-49bf-46cb-95b6-106e4e9665e5', 'TH-TN01', 'Sân Tennis Mặt Cứng 1', true, 1)
ON CONFLICT (id) DO NOTHING;

COMMIT;
