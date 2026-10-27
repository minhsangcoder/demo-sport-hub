-- Display projection only. Booking remains the admission authority.
CREATE TABLE user_market_restrictions (
 user_id UUID PRIMARY KEY REFERENCES users(id),version BIGINT NOT NULL CHECK(version>0),
 strike_count INTEGER NOT NULL CHECK(strike_count>=0),permanent BOOLEAN NOT NULL,
 blocked_until TIMESTAMPTZ,fingerprint VARCHAR(64) NOT NULL,updated_at TIMESTAMPTZ NOT NULL,
 CHECK(permanent=(strike_count>=3)),CHECK((strike_count=0)=(blocked_until IS NULL))
);
