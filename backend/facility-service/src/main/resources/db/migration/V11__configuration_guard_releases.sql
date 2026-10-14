CREATE TABLE configuration_guard_releases (
 operation_id UUID PRIMARY KEY, facility_id UUID NOT NULL,
 created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
