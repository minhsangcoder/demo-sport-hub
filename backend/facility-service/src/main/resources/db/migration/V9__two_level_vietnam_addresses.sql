ALTER TABLE facilities
  DROP COLUMN district,
  ADD COLUMN province_code VARCHAR(2),
  ADD COLUMN ward_code VARCHAR(5);

ALTER TABLE facilities
  ADD CONSTRAINT facilities_admin_codes_paired
  CHECK ((province_code IS NULL) = (ward_code IS NULL)),
  ADD CONSTRAINT facilities_province_code_format
  CHECK (province_code IS NULL OR province_code ~ '^[0-9]{2}$'),
  ADD CONSTRAINT facilities_ward_code_format
  CHECK (ward_code IS NULL OR ward_code ~ '^[0-9]{5}$');

CREATE INDEX idx_facility_province_code ON facilities(province_code);
