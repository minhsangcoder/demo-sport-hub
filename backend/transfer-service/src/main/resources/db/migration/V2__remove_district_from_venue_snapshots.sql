-- Legacy listings keep their original venue names; unknown administrative codes are not guessed.
UPDATE transfer_listings
SET snapshot = snapshot - 'district'
WHERE snapshot ? 'district';
