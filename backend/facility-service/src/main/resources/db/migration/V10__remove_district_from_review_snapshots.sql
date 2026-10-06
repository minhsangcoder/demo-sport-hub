-- Remove the retired field without inventing administrative codes for historical addresses.
-- Flyway runs this in one transaction; snapshot immutability resumes before commit.
ALTER TABLE facility_reviews DISABLE TRIGGER facility_review_snapshot_immutable;
UPDATE facility_reviews
SET snapshot = snapshot #- '{facility,district}'
WHERE snapshot #> '{facility,district}' IS NOT NULL;
ALTER TABLE facility_reviews ENABLE TRIGGER facility_review_snapshot_immutable;
