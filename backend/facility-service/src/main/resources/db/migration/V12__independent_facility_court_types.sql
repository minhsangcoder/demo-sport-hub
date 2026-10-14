-- A court type is a facility-owned classification, independent from the global sport category.
-- Existing courts remain untyped; no inference from names/categories or business-data rewrite.
CREATE TABLE court_types (
 id UUID PRIMARY KEY, facility_id UUID NOT NULL REFERENCES facilities(id),
 name VARCHAR(100) NOT NULL CHECK(length(trim(name))>0), active BOOLEAN NOT NULL,
 version BIGINT NOT NULL DEFAULT 0, created_by UUID NOT NULL,
 created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(), updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
 UNIQUE(id,facility_id)
);
CREATE UNIQUE INDEX uq_court_type_name ON court_types(facility_id,lower(name));
ALTER TABLE courts ADD COLUMN court_type_id UUID;
ALTER TABLE courts ADD CONSTRAINT fk_court_type_facility
 FOREIGN KEY(court_type_id,facility_id) REFERENCES court_types(id,facility_id);
