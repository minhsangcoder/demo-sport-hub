-- Add DEMO terms without rewriting any accepted legacy snapshot or applied migration.
CREATE OR REPLACE FUNCTION guard_transfer_listing_finance_terms() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF TG_OP='UPDATE' AND NEW.finance_terms IS DISTINCT FROM OLD.finance_terms AND
  (OLD.state<>'ACTIVE' OR OLD.workflow<>'READY' OR NEW.state<>'ACTIVE' OR OLD.active_acquisition_id IS NOT NULL OR NEW.finance_terms IS NULL) THEN
  RAISE EXCEPTION 'Only an unlocked listing may accept new financial terms' USING ERRCODE='23514'; END IF;
 IF NEW.finance_terms IS NULL THEN RETURN NEW; END IF;
 IF jsonb_typeof(NEW.finance_terms)<>'object' OR
  NOT (NEW.finance_terms ?& ARRAY['bookingId','facilityId','courtId','sellerId','policyVersion','feeBps','amount','quotedFeeAmount','quotedSellerNetAmount','currency','rounding','termsHash','refundPolicy','deadline']) OR
  (NEW.finance_terms->>'bookingId',NEW.finance_terms->>'facilityId',NEW.finance_terms->>'courtId',NEW.finance_terms->>'sellerId',NEW.finance_terms->>'currency') IS DISTINCT FROM
  (NEW.booking_id::text,NEW.facility_id::text,NEW.court_id::text,NEW.seller_id::text,NEW.currency) OR
  (NEW.finance_terms->>'amount')::numeric IS DISTINCT FROM NEW.price OR (NEW.finance_terms->>'deadline')::timestamptz IS DISTINCT FROM NEW.deadline OR
  NEW.finance_terms->>'rounding' IS DISTINCT FROM 'HALF_UP' THEN
  RAISE EXCEPTION 'Listing financial identity does not match accepted terms' USING ERRCODE='23514'; END IF;
 IF NEW.finance_terms->>'mode'='DEMO' THEN
  IF NOT (NEW.finance_terms ?& ARRAY['receiverFacilityId','receiverOwnerId','receiverName','feePolicyHash','provider','moneyClass']) OR
   NEW.finance_terms->>'receiverFacilityId' IS DISTINCT FROM NEW.facility_id::text OR
   (NEW.finance_terms->>'receiverOwnerId')::uuid IS NULL OR length(trim(NEW.finance_terms->>'receiverName'))=0 OR
   NEW.finance_terms->>'provider' IS DISTINCT FROM 'DEMO' OR NEW.finance_terms->>'moneyClass' IS DISTINCT FROM 'DEMO' OR
   (NEW.finance_terms->>'policyVersion')::bigint<1 OR (NEW.finance_terms->>'feeBps')::integer NOT BETWEEN 0 AND 10000 OR
   (NEW.finance_terms->>'quotedFeeAmount')::numeric IS DISTINCT FROM round(NEW.price*(NEW.finance_terms->>'feeBps')::integer/10000,0) OR
   (NEW.finance_terms->>'quotedSellerNetAmount')::numeric IS DISTINCT FROM NEW.price-(NEW.finance_terms->>'quotedFeeAmount')::numeric THEN
   RAISE EXCEPTION 'Invalid facility DEMO fee or receiver' USING ERRCODE='23514'; END IF;
 ELSE
  IF NOT (NEW.finance_terms ? 'beneficiaryRevisionId') OR (NEW.finance_terms->>'feeBps')::integer IS DISTINCT FROM 200 OR
   (NEW.finance_terms->>'quotedFeeAmount')::numeric IS DISTINCT FROM round(NEW.price*200/10000,0) OR
   (NEW.finance_terms->>'quotedSellerNetAmount')::numeric IS DISTINCT FROM NEW.price-round(NEW.price*200/10000,0) THEN
   RAISE EXCEPTION 'Legacy listing fee does not match accepted terms' USING ERRCODE='23514'; END IF;
 END IF;
 RETURN NEW;
END $$;

CREATE OR REPLACE FUNCTION guard_transfer_acquisition_finance_terms() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE l transfer_listings%ROWTYPE;
BEGIN
 IF TG_OP='UPDATE' THEN
  IF NEW.finance_terms IS DISTINCT FROM OLD.finance_terms THEN RAISE EXCEPTION 'Acquisition financial terms are immutable' USING ERRCODE='23514'; END IF;
  RETURN NEW;
 END IF;
 SELECT * INTO l FROM transfer_listings WHERE id=NEW.listing_id;
 IF (l.finance_terms IS NOT NULL)<>(NEW.finance_terms IS NOT NULL) THEN RAISE EXCEPTION 'Acquisition protocol must match the accepted listing' USING ERRCODE='23514'; END IF;
 IF NEW.finance_terms IS NULL THEN RETURN NEW; END IF;
 IF jsonb_typeof(NEW.finance_terms)<>'object' OR
  NOT (NEW.finance_terms ?& ARRAY['listingId','listingVersion','acquisitionId','buyerId','bookingId','sellerId','amount','termsHash','sellerTermsHash','refundPolicy','expiresAt','acceptedAt']) OR
  (NEW.finance_terms->>'listingId',NEW.finance_terms->>'acquisitionId',NEW.finance_terms->>'buyerId',NEW.finance_terms->>'bookingId',NEW.finance_terms->>'sellerId',NEW.finance_terms->>'currency',NEW.finance_terms->>'sellerTermsHash') IS DISTINCT FROM
  (NEW.listing_id::text,NEW.id::text,NEW.buyer_id::text,l.booking_id::text,l.seller_id::text,NEW.currency,l.finance_terms->>'termsHash') OR
  (NEW.finance_terms->>'amount')::numeric IS DISTINCT FROM NEW.amount OR (NEW.finance_terms->>'expiresAt')::timestamptz IS DISTINCT FROM NEW.expires_at OR
  (NEW.finance_terms->>'listingVersion')::bigint IS DISTINCT FROM l.version OR
  (NEW.finance_terms-ARRAY['listingId','listingVersion','acquisitionId','buyerId','expiresAt','acceptedAt','sellerTermsHash','termsHash']) IS DISTINCT FROM (l.finance_terms-'termsHash') THEN
  RAISE EXCEPTION 'Acquisition terms must match the locked listing and buyer' USING ERRCODE='23514'; END IF;
 RETURN NEW;
END $$;
