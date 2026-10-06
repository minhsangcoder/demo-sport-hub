-- Existing names stay intact until the applicant explicitly selects current administrative codes.
UPDATE owner_applications
SET initial_facility = initial_facility - 'district'
WHERE initial_facility ? 'district';

-- Remove the retired field from historical submissions, preserving every other snapshot field.
ALTER TABLE owner_application_submissions DISABLE TRIGGER owner_submission_immutable;
UPDATE owner_application_submissions
SET facility_snapshot = facility_snapshot #- '{facility,district}'
WHERE facility_snapshot #> '{facility,district}' IS NOT NULL;
ALTER TABLE owner_application_submissions ENABLE TRIGGER owner_submission_immutable;
