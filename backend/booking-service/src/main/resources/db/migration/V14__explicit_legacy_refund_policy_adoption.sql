-- Offers and individual consents are separate append-only evidence. No legacy row is adopted on startup.
CREATE TABLE booking_refund_policy_adoption_offers (
 id UUID PRIMARY KEY,
 booking_id UUID NOT NULL REFERENCES bookings(id),
 booking_version BIGINT NOT NULL CHECK(booking_version>=0),
 proposed_by UUID NOT NULL,
 policy JSONB NOT NULL CHECK(jsonb_typeof(policy)='object'),
 required_users JSONB NOT NULL CHECK(jsonb_typeof(required_users)='array'),
 reason VARCHAR(500) NOT NULL CHECK(length(trim(reason))>0),
 guest_consent_reference VARCHAR(500),
 created_at TIMESTAMPTZ NOT NULL,
 expires_at TIMESTAMPTZ NOT NULL CHECK(expires_at>created_at),
 CHECK(jsonb_array_length(required_users)>0 OR COALESCE(length(trim(guest_consent_reference)),0)>0)
);
CREATE TABLE booking_refund_policy_adoption_consents (
 offer_id UUID NOT NULL REFERENCES booking_refund_policy_adoption_offers(id),
 user_id UUID NOT NULL,
 consented_at TIMESTAMPTZ NOT NULL,
 PRIMARY KEY(offer_id,user_id)
);
CREATE FUNCTION immutable_refund_policy_adoption_evidence() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN RAISE EXCEPTION 'Policy adoption evidence is append-only' USING ERRCODE='23514'; END $$;
CREATE TRIGGER immutable_policy_adoption_offer BEFORE UPDATE OR DELETE ON booking_refund_policy_adoption_offers
 FOR EACH ROW EXECUTE FUNCTION immutable_refund_policy_adoption_evidence();
CREATE TRIGGER immutable_policy_adoption_consent BEFORE UPDATE OR DELETE ON booking_refund_policy_adoption_consents
 FOR EACH ROW EXECUTE FUNCTION immutable_refund_policy_adoption_evidence();
