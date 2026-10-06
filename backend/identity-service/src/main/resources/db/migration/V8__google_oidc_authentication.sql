ALTER TABLE users ALTER COLUMN password_hash DROP NOT NULL;

ALTER TABLE users
    ADD COLUMN provider VARCHAR(20) NOT NULL DEFAULT 'LOCAL',
    ADD COLUMN google_sub VARCHAR(255);

ALTER TABLE users
    ADD CONSTRAINT chk_users_auth_provider CHECK (
        (provider = 'LOCAL' AND google_sub IS NULL)
        OR (provider = 'GOOGLE' AND google_sub IS NOT NULL)
    );

CREATE UNIQUE INDEX uq_users_google_sub ON users (google_sub) WHERE google_sub IS NOT NULL;
