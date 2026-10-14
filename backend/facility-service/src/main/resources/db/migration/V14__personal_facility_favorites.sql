CREATE TABLE facility_favorites (
 user_id UUID NOT NULL, facility_id UUID NOT NULL REFERENCES facilities(id),
 added_at TIMESTAMPTZ NOT NULL DEFAULT NOW(), PRIMARY KEY(user_id,facility_id)
);
-- user_id is an Identity logical reference, never a cross-database FK.
CREATE INDEX idx_personal_favorites ON facility_favorites(user_id,added_at DESC,facility_id);
