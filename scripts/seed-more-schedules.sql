BEGIN;

-- Operating hours for Facility 2 (Ba Dinh) & Facility 3 (Tay Ho)
-- Days: 1 (Monday) through 7 (Sunday)
DO $$
DECLARE
    v_fac2 UUID := '22222222-2222-2222-2222-222222222222';
    v_fac3 UUID := '33333333-3333-3333-3333-333333333333';
    d smallint;
BEGIN
    FOR d IN 1..7 LOOP
        IF NOT EXISTS (SELECT 1 FROM operating_hours WHERE facility_id = v_fac2 AND day_of_week = d) THEN
            INSERT INTO operating_hours (facility_id, day_of_week, open_time, close_time, slot_duration_minutes, is_active)
            VALUES (v_fac2, d, '06:00:00', '22:00:00', 60, true);
        END IF;

        IF NOT EXISTS (SELECT 1 FROM pricing_rules WHERE facility_id = v_fac2 AND day_of_week = d) THEN
            INSERT INTO pricing_rules (facility_id, day_of_week, start_time, end_time, price_per_slot, is_active, label, currency, priority)
            VALUES (v_fac2, d, '06:00:00', '22:00:00', 120000, true, 'Giá chuẩn cầu lông', 'VND', 0);
        END IF;

        IF NOT EXISTS (SELECT 1 FROM operating_hours WHERE facility_id = v_fac3 AND day_of_week = d) THEN
            INSERT INTO operating_hours (facility_id, day_of_week, open_time, close_time, slot_duration_minutes, is_active)
            VALUES (v_fac3, d, '06:00:00', '22:00:00', 60, true);
        END IF;

        IF NOT EXISTS (SELECT 1 FROM pricing_rules WHERE facility_id = v_fac3 AND day_of_week = d) THEN
            INSERT INTO pricing_rules (facility_id, day_of_week, start_time, end_time, price_per_slot, is_active, label, currency, priority)
            VALUES (v_fac3, d, '06:00:00', '22:00:00', 160000, true, 'Giá chuẩn Pickleball/Tennis', 'VND', 0);
        END IF;
    END LOOP;
END $$;

COMMIT;
