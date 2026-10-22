-- Arrival order is independent of provider execution order. Raw signed facts remain immutable.
CREATE TABLE movement_event_applications (
 event_id UUID PRIMARY KEY REFERENCES money_movement_events(id),
 applied_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);
CREATE TRIGGER immutable_movement_event_applications BEFORE UPDATE OR DELETE ON movement_event_applications
 FOR EACH ROW EXECUTE FUNCTION protect_payment_evidence();
CREATE INDEX idx_movement_unapplied ON money_movement_events(attempt_id,occurred_at,id);
