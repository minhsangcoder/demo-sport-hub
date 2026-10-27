> **HISTORY ONLY — NOT AN ACTIVE REQUIREMENT SOURCE.**
> Read `openspec/CURRENT_CHANGE.md` at the project root for the current proposal.
> Do not apply, merge, or resume requirements/tasks from this document unless
> the user explicitly requests this historical change. Task counts are historical.

# Counter booking ownership: implementation and operations

Change: `fix-counter-booking-ownership`. This runbook accompanies the source changes. It does not authorize deployment or correction of any existing environment.

## Ownership and access

- Online: creator, customer, current holder and reservation holder are the Customer.
- Counter linked: creator/operator is Staff or the authorized facility operator; customer/current holder/reservation holder are the verified Customer. A Staff account that is also that Customer retains personal Customer rights.
- Counter walk-in: customer/current holder/reservation holder are JSON/database NULL. Creator/operator and immutable name/phone snapshot remain present. No guest account or shared fake UUID is created.
- Creating a counter booking does not put it into the operator's personal booking list. Operational reads require BOOKING_READ at the booking facility. Historical Customer access does not grant current QR or transfer controls.
- A converted hold is read through booking authority. Guest hold holderId is NULL. Releasing or creating another booking from a converted hold is rejected; cancellation uses the booking lifecycle. A retry with the original creation key still returns the original booking after authority is checked again.
- Payment remains available to the current Customer owner or the original counter operator with BOOKING_CREATE_COUNTER. BOOKING_PAYMENT_COUNTER alone does not authorize another cashier. Payment payer remains the actual account making the request.
- Guest and proven legacy Staff fallback records never receive or accept an owner signed QR. Manual facility codes remain usable by authorized operations staff.

## Schema and release sequence

V11 makes these four columns nullable: bookings.customer_id, bookings.current_holder_id, slot_reservations.holder_id, booking_code_registry.holder_id. created_by/operator_id remain required. V1–V11 are not rewritten by this repair.

V12 adds a structural preflight and a deferred INSERT constraint for new counter bookings. A new counter booking must have a matching immutable guest snapshot, and its three holder values must match the verified linked_customer_id, including all NULL for a walk-in. Existing non-null legacy rows are preserved. Birth-time link validation does not constrain later legitimate Customer handoffs. Schema changes and application startup do not backfill ownership.

1. Verify in an isolated database first. Identify and review structural inconsistencies before schema migration; V12 fails rather than repairing them.
2. Prepare all Backend readers/callback processors and the active Frontend to accept UUID/NULL and project known legacy fallbacks safely.
3. Temporarily stop counter creation during the coordinated schema/writer transition. Apply V11/V12 through the existing release process, retire incompatible writers, then enable the nullable writer. Actual deployment topology is environment-specific.
4. Exercise online, linked counter, Guest, payment and check-in in the test environment before release approval.
5. Treat physical legacy correction as a separate operation with separately approved database and manifest. Never invoke it during startup or migration.

## API examples

Both entrypoints use the same policy and transaction boundaries:

- POST `/api/v1/bookings/counter`, with a unique Idempotency-Key and body `{courtId, startsAt, endsAt, expectedAmount, guestName, guestPhone, paymentMethod:"DEMO"}`.
- POST `/api/v1/bookings/holds?counter=true`, then POST `/api/v1/bookings?counter=true` with `{holdId, guestName, guestPhone}` and a stable creation key.

A Guest Booking response contains `customerId:null`, `currentHolderId:null`, the real `createdBy`, and the operational `bookingCode`. A converted Guest Hold contains `holderId:null`. GET booking returns an envelope whose data is `{booking, history, checkinToken, checkinQrSvg}`, not a bare Booking. Guest checkinToken/checkinQrSvg are NULL.

- POST `/api/v1/payments`: `{bookingId, provider:"DEMO"}`, stable key per payer/booking/attempt. Complete the same payment through `/api/v1/payments/{id}/demo/complete`; wait until `data.booking.status` becomes CONFIRMED.
- POST `/api/v1/bookings/facility/{facilityId}/check-in-by-code`: `{bookingCode:"ABCDEF1234"}`. The code is ten hexadecimal characters, normalized to uppercase. It is separate from a signed QR token.
- Signed Customer QR uses POST `/api/v1/bookings/{bookingId}/check-in` with `{token}`.

Manual check-in requires BOOKING_CHECK_IN at the correct facility, CONFIRMED, verified payment, no unresolved LOCKED transfer and startsAt minus 30 minutes <= now < endsAt. It transitions once to CHECKED_IN. Completion remains CHECKED_IN -> COMPLETED after endsAt with the existing completion authority. No IN_PROGRESS or Staff permission-catalog changes are introduced.

## Frontend workflow

1. Open the permitted facility's counter booking page, choose the court/interval and enter the guest contact snapshot. Customer lookup must succeed; an unavailable lookup is an error, not a Guest fallback.
2. Complete DEMO payment. An operator booking for a Guest or another Customer returns to the facility operations page. A true Customer owner, including self-linked Staff Customer, returns to personal booking detail.
3. On operations list/detail, read the label, creator, contact snapshot and **Mã check-in vận hành**. The code is an operational credential, not proof of account ownership.
4. An authorized operator enters that code in the check-in form. The page reloads booking/history and shows CHECKED_IN on success. Read-only users can read permitted code data but cannot open check-in actions; the server rechecks permission on every command.
5. The counter payment modal explicitly describes DEMO. It does not collect or claim to store cash amount, collector name or notes. Pending confirmation stays visible; retry/check confirmation uses the same payment/key rather than creating another obligation. Different bookings use different keys.
6. Wrong code, revoked permission, wrong facility and state/window errors remain visible; the UI does not advance booking status locally.

## Read-only preflight and approved correction

The standalone entry point is `com.sporthub.booking.tools.LegacyCounterBookingCorrectionTool`. It starts no Spring application, applies no migrations, reads no .env and has no implicit target. Supply the exact credential-free PostgreSQL JDBC URL and username. Put the password in the dedicated SPORTHUB_OWNERSHIP_DB_PASSWORD environment variable using the environment's secret mechanism; do not commit or log it.

The URL must match the connected datasource. Each candidate also records database/schema identity, booking ID/version, court/facility/operator/token version and a SHA-256 fingerprint of the expected booking, reservation, snapshot, receipts, history, registry and transfer/reconciliation evidence. The manifest omits guest name and phone; contact participates in the fingerprint only.

A safe candidate must have the original unlinked immutable snapshot, matching operator/contact/three holder values and consistent reservation scope/state/expiry. Any group, transfer lease/handoff, missing evidence or reconciliation record requires review. Booking has no financial resolution protocol, so a review annotation or arbitrary state label does not settle reconciliation. Applied payments require a matching receipt fingerprint and confirmation audit; ambiguous/unapplied receipt evidence is reviewed.

Build an executable artifact without changing the regular runtime jar names:

```powershell
# From backend/
mvn.cmd -Pownership-test-artifact -DskipTests package
```

Default mode only reads the database and creates a NEW manifest file:

```powershell
$databaseUrl = '<approved exact jdbc:postgresql://host:port/database URL>'
$databaseUser = '<approved database user>'
$bookingJar = '<absolute path to booking-service-0.0.1-SNAPSHOT-ownership-test.jar>'
java '-Dloader.main=com.sporthub.booking.tools.LegacyCounterBookingCorrectionTool' -cp $bookingJar org.springframework.boot.loader.launch.PropertiesLauncher --jdbc-url $databaseUrl --db-user $databaseUser --manifest '<new preflight JSON path>'
```

Review the target and candidates. Produce a separately approved manifest containing ONLY the explicitly selected eligible candidates. Preserve every identity/version/fingerprint field; do not replace them with freshly queried values during write execution. Obtain approval for that exact database and manifest before running write mode on existing data.

```powershell
# Separate approved maintenance action; never run automatically during apply/deployment.
java '-Dloader.main=com.sporthub.booking.tools.LegacyCounterBookingCorrectionTool' -cp $bookingJar org.springframework.boot.loader.launch.PropertiesLauncher --jdbc-url $databaseUrl --db-user $databaseUser --manifest '<approved manifest JSON path>' --apply --actor '<real operator account UUID>'
```

For each candidate, the tool locks the court read from the database, rereads and compares the full candidate/evidence. Changed records are skipped. Each successful transaction clears the two owner IDs and reservation holder, increments booking/token versions once, and appends a correction audit with the actual actor and real JSON null values. An audit/write failure rolls back both owner updates. Retrying a corrected candidate changes nothing.

Post-check every reported ID against the approved manifest: owner/reservation NULL, exactly one new correction audit, version/token incremented once. Booking code, status, original creator, contact snapshot, times, price, payment ID/paidAt, receipts, payer and historical registry must match preflight evidence. Keep skipped/ambiguous records for review. Do not alter an existing manifest to bypass a stale-evidence rejection; generate and approve a new preflight instead.

## Rollback

Keep readers/Frontend compatible with nullable ownership once Guest records exist. Stop counter creation and fix forward if needed. Do not assign Staff/fake accounts, restore NOT NULL, erase audit, rotate Guest manual codes or rewrite historical registry to make an old application work.

## Verification

Use only disposable PostgreSQL/Redis resources and explicit test URLs. A fresh Booking database must have uuid-ossp available because existing V9 uses uuid_generate_v4(); this is test provisioning, not a change to V9.

- Backend: `mvn.cmd -pl booking-service -am test`; `mvn.cmd -pl identity-service,payment-service,transfer-service -am test` with their explicit test database variables. For Redis-backed Identity tests set SPORTHUB_SECURITY_TEST_REDIS_PORT to a disposable instance.
- Frontend: `pnpm.cmd test`, `pnpm.cmd typecheck`, `pnpm.cmd lint`, `pnpm.cmd build`.
- Contract: `powershell.exe -NoProfile -File scripts/openapi/openapi.ps1 -Action validate -Contract booking`.
- Browser: build the `ownership-test-artifact` profile and Frontend; use scripts/system-e2e.py with explicit test-labelled PG/Redis, SPORTHUB_E2E_TEST_SCRIPT=counter-booking-ownership-e2e.mjs and SPORTHUB_E2E_ARTIFACT_SUFFIX=-ownership-test. The focused harness uses real BFF/gateway/services and actual OTP/payment events; only initial Admin bootstrap and loopback synthetic mail/SMS are fixtures. No real DB is used.

Global lint failures outside the modified ownership files must be reported separately and must not be called passing checks or hidden by disabled rules. OpenSpec checkboxes or schema validation alone do not establish behavior correctness.