-- Reservation authority remains in Booking. No FK into Facility/Schedule databases.
CREATE TABLE configuration_change_guards (
 operation_id UUID PRIMARY KEY, facility_id UUID NOT NULL,
 source VARCHAR(20) NOT NULL CHECK(source IN ('facility','schedule')),
 state VARCHAR(20) NOT NULL CHECK(state IN ('PREPARED','RELEASED')),
 created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(), released_at TIMESTAMPTZ
);
CREATE UNIQUE INDEX uq_prepared_configuration_guard ON configuration_change_guards(facility_id)
 WHERE state='PREPARED';
