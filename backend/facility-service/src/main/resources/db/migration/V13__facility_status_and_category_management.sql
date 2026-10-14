ALTER TABLE sport_categories ADD COLUMN description VARCHAR(2000), ADD COLUMN icon VARCHAR(80), ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
-- Abort on conflicting legacy names; never silently rename or merge referenced categories.
CREATE UNIQUE INDEX uq_sport_category_canonical_name ON sport_categories
 (lower(regexp_replace(btrim(normalize(name,NFC)), '[[:space:]]+', ' ', 'g')));
CREATE TABLE sport_category_audit (
 id UUID PRIMARY KEY, category_id UUID NOT NULL REFERENCES sport_categories(id), actor_id UUID NOT NULL,
 action VARCHAR(50) NOT NULL, before_value JSONB, after_value JSONB NOT NULL, occurred_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE TABLE facility_status_decisions (
 id UUID PRIMARY KEY, facility_id UUID NOT NULL REFERENCES facilities(id), actor_id UUID NOT NULL,
 action VARCHAR(20) NOT NULL CHECK(action IN ('SUSPEND','RESTORE')), reason VARCHAR(2000) NOT NULL CHECK(length(btrim(reason))>=20),
 before_status VARCHAR(30) NOT NULL, after_status VARCHAR(30) NOT NULL,
 facility_version BIGINT NOT NULL, impact JSONB NOT NULL, occurred_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE FUNCTION preserve_facility_management_history() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN RAISE EXCEPTION 'Management history is immutable'; END $$;
CREATE TRIGGER immutable_category_audit BEFORE UPDATE OR DELETE ON sport_category_audit
 FOR EACH ROW EXECUTE FUNCTION preserve_facility_management_history();
CREATE TRIGGER immutable_facility_status_decisions BEFORE UPDATE OR DELETE ON facility_status_decisions
 FOR EACH ROW EXECUTE FUNCTION preserve_facility_management_history();
CREATE INDEX idx_facility_status_history ON facility_status_decisions(facility_id,occurred_at);
