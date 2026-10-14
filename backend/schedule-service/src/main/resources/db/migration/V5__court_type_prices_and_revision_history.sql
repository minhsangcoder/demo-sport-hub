-- Logical Facility reference, deliberately no cross-service FK.
ALTER TABLE pricing_rules ADD COLUMN court_type_id UUID;
-- Keep legacy court_type text intact; it cannot safely be mapped to Facility UUIDs by this migration.
CREATE TABLE pricing_rule_versions (
 rule_id UUID NOT NULL REFERENCES pricing_rules(id), version BIGINT NOT NULL,
 snapshot JSONB NOT NULL, actor_id UUID, recorded_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
 PRIMARY KEY(rule_id,version)
);
INSERT INTO pricing_rule_versions(rule_id,version,actor_id,snapshot)
 SELECT id,version,COALESCE(updated_by,created_by),jsonb_build_object(
  'id',id,'facilityId',facility_id,'courtId',court_id,'sportCategoryId',sport_category_id,
  'dayOfWeek',day_of_week,'specificDate',specific_date,'startsAt',start_time,'endsAt',end_time,
  'pricePerSlot',price_per_slot,'priority',priority,'label',label,'effectiveFrom',effective_from,
  'effectiveTo',effective_to,'currency',currency,'active',is_active,'version',version,
  'courtTypeId',court_type_id,'legacyCourtType',court_type)
 FROM pricing_rules;
CREATE FUNCTION protect_price_revision() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN RAISE EXCEPTION 'Price revisions are immutable'; END $$;
CREATE TRIGGER immutable_price_revision BEFORE UPDATE OR DELETE ON pricing_rule_versions
 FOR EACH ROW EXECUTE FUNCTION protect_price_revision();
