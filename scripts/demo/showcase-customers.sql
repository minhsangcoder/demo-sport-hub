-- Local/demo fixtures share the existing customer's DEMO_PASSWORD hash.
-- Existing accounts and profiles are preserved; no password is embedded here.
BEGIN;
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM users WHERE email = 'customer@sporthub.local'
        AND status = 'ACTIVE' AND password_hash IS NOT NULL
    ) THEN
        RAISE EXCEPTION 'Start the local/demo identity seed first';
    END IF;
END $$;
WITH fixtures(email, full_name, date_of_birth) AS (
    VALUES
        ('demo.anh@sporthub.local', 'Nguyễn Minh Anh (Demo)', DATE '1998-05-12'),
        ('demo.huy@sporthub.local', 'Trần Quốc Huy (Demo)', DATE '1995-09-20'),
        ('demo.linh@sporthub.local', 'Lê Thùy Linh (Demo)', DATE '1997-03-08'),
        ('demo.trang@sporthub.local', 'Phạm Thu Trang (Demo)', DATE '1999-11-15'),
        ('demo.quang@sporthub.local', 'Vũ Minh Quang (Demo)', DATE '1996-07-04'),
        ('demo.mai@sporthub.local', 'Đỗ Ngọc Mai (Demo)', DATE '2000-02-18')
), created AS (
    INSERT INTO users (email, password_hash, status, email_verified)
    SELECT f.email, seed.password_hash, 'ACTIVE', TRUE
    FROM fixtures f CROSS JOIN users seed
    WHERE seed.email = 'customer@sporthub.local'
    ON CONFLICT DO NOTHING
    RETURNING id, email
), profiles AS (
    INSERT INTO user_profiles (user_id, full_name, date_of_birth)
    SELECT c.id, f.full_name, f.date_of_birth
    FROM created c JOIN fixtures f USING (email)
    RETURNING user_id
)
INSERT INTO user_roles (user_id, role)
SELECT id, 'CUSTOMER' FROM created;
COMMIT;
