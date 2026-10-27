> **HISTORY ONLY — NOT AN ACTIVE REQUIREMENT SOURCE.**
> Read `openspec/CURRENT_CHANGE.md` at the project root for the current proposal.
> Do not apply, merge, or resume requirements/tasks from this document unless
> the user explicitly requests this historical change. Task counts are historical.

# Ownership repair verification — 2026-10-21

All 11 findings from the review have source fixes and regression coverage. No deployment, migration or correction was executed against an existing operational database.

## Passing checks

- Booking: 190 tests; Common: 28 tests. Command: mvn.cmd -pl booking-service -am test.
- Identity: 110; Payment: 55; Transfer: 40. Command: mvn.cmd -pl identity-service,payment-service,transfer-service -am test. Disposable PostgreSQL and Redis were explicitly selected. Identity's stale migration-version expectation was corrected to match its existing V12; its permission catalog was not changed.
- Frontend: 39 tests; pnpm typecheck; pnpm build; lint on the modified ownership files has zero errors/warnings.
- All backend application test artifacts packaged using ownership-test-artifact. The regular Booking jar was locked on Windows, so the isolated artifact names were used for system testing without stopping unrelated processes.
- OpenAPI booking validation, strict OpenSpec validation, Python/Node harness syntax validation and git diff --check passed.
- The standalone maintenance CLI successfully ran read-only preflight against the disposable test database. Write behavior was exercised only by isolated PostgreSQL correction tests.

## Browser/system evidence

The focused browser harness passed through the real Frontend BFF, gateway, six services, PostgreSQL, RabbitMQ and local test mail/SMS delivery. No browser request interception or forced booking state was used.

Result: PASS; 4 API checks, 6 browser checks, 0 failures, 0 uncaught browser errors.

- Online Customer: UI hold/create/DEMO payment -> personal detail, QR and transfer control.
- Counter Guest: UI hold/create/DEMO payment -> operational detail with NULL owners -> displayed manual code -> CHECKED_IN. Guest does not appear in the operator's personal bookings and has no owner QR/transfer control.
- Counter linked Customer: verified phone lookup -> Staff operations result; Customer sees the booking personally with QR/transfer control.
- Revoked permission and wrong facility are rejected. Invalid manual code remains an error and does not change state; restoring authority allows check-in.
- Staff Customer self-link: result returns to personal detail and preserves actual Customer QR/transfer rights.

Evidence directory: C:\Users\ADMIN\AppData\Local\Temp\sporthub-ownership-system-026d2478
Result JSON: C:\Users\ADMIN\AppData\Local\Temp\sporthub-ownership-system-026d2478\system-e2e-results.json

## Explicit acceptance gaps

Task 7.8 stays unchecked: global pnpm lint still reports the 13 existing errors outside this repair. They were not suppressed or relabeled as passing.

Task 8.3 stays unchecked: the real system/browser run covers creation, DEMO confirmation and check-in, but did not wait for the real scheduled end to run every model through COMPLETED on that same stack. Full lifecycle completion, including the HTTP completion endpoint for online/linked/Guest, passed in Booking integration tests with a controlled test Clock. This is distinct from the actual-clock browser evidence.

Task 8.5 stays unchecked because complete change acceptance depends on those checks. The rest of the reopened, verified tasks can be marked complete; the checklist must not imply 51/51 acceptance.

## Logs retained locally

- Booking: C:\Users\ADMIN\AppData\Local\Temp\sporthub-ownership-fix-booking-verified.log
- Other services: C:\Users\ADMIN\AppData\Local\Temp\sporthub-ownership-fix-other-services-final.log
- Frontend tests: C:\Users\ADMIN\AppData\Local\Temp\sporthub-ownership-fix-frontend-verified.log
- Typecheck/build: C:\Users\ADMIN\AppData\Local\Temp\sporthub-ownership-fix-typecheck-verified.log and sporthub-ownership-fix-build-verified.log
- Global lint baseline: C:\Users\ADMIN\AppData\Local\Temp\sporthub-ownership-fix-lint-final.log

Disposable service processes were stopped after the system run. Owned test containers/databases are removed after verification; evidence files are retained. Existing operational data and unrelated running services are untouched.