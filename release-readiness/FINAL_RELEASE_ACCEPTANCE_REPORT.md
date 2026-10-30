# AutoScientist AI — Final Release Acceptance

**Date:** 2026-10-30
**Repository:** `/Applications/AutoScientist AI`
**Starting commit:** `d13e6c6` (preserved; no amend/reset)
**Production services:** Vercel frontend and Render backend URLs supplied by the user
**Overall decision:** **NOT READY TO PUSH** as a verified production release. A real Flyway/MySQL 8.4 staging run and external service configuration checks remain unavailable. No production mutation, push, or deployment was performed.

## Repository issues found and fixed

| Issue | Fix | Classification |
|---|---|---|
| Spring Boot 3.5.16 managed Flyway 11.7.2, which predates explicit MySQL 8.4 support and emits an unsupported/newer-server warning. | Pinned the Flyway BOM property to 11.15.0. `dependency:tree` confirms both `flyway-core` and `flyway-mysql` resolve to 11.15.0. | **Fixed and verified locally** |
| Commons Lang 3.17.0 had CVE-2025-48924. | Dependency management pins Commons Lang 3.18.0. | **Fixed and verified by OSV scan** |
| Reset token was retained when email delivery failed. | Reset service removes the unusable token while preserving a generic public response. Unit/integration tests cover missing transport, success, delivery failure, and token cleanup. | **Fixed and verified locally** |
| MySQL migration test could use unsafe fixed schemas and non-TLS connections. | Test now requires an explicit staging marker and create/drop opt-in, creates unique disposable schemas, enforces TLS, drops only schemas it created, and starts the app with Hibernate schema validation for fresh and legacy paths. | **Fixed and verified by compilation/local suite; real DB paths not run** |
| Deployment documentation contained an unencrypted MySQL example and manual root schema initialization that conflicted with production Flyway. | Replaced with Aiven TLS and Flyway-owned production migration instructions. Added native Render/Vercel branch deployment setup. | **Fixed and verified by repository inspection** |

No migrations were deleted or changed. No features were removed or replaced with mocks.

## Verification results

| Area | Result and classification |
|---|---|
| Backend Maven `verify` after Flyway upgrade | **PASS — verified locally:** 110 tests, 0 failures, 0 errors, 2 skipped staging-only real-MySQL tests; package build succeeded. |
| Flyway resolved artifacts | **PASS — verified locally:** `flyway-core:11.15.0` and `flyway-mysql:11.15.0`. |
| Frontend tests | **PASS — verified locally:** 21 test files, 126 tests. |
| Frontend production build | **PASS — verified locally:** built with `VITE_API_BASE_URL=https://autoscientist-ai-backend.onrender.com/api`. |
| Frontend dependency audit | **PASS — verified locally:** `npm audit` reports 0 advisories. |
| Backend dependency scan | **Completed with findings:** OSV scan of the updated Maven POM reports two Spring Framework 6.2.19 advisories concerning SSE fragment rendering and `XsltView` path limitation. Source scan found no SSE emitter, `text/event-stream`, `XsltView`, or view resolver usage in this REST application; these code paths are not present. The Commons Lang advisory is no longer reported. OWASP Dependency-Check did not produce a report because NVD rejected the unavailable API key. |
| Production backend health | **PASS — verified against production:** `GET /api/health` returned HTTP 200 with status `UP`; this endpoint is process liveness and does not prove Aiven connectivity. |
| Production frontend and deep route | **PASS — verified against production:** `/` and `/dashboard` returned HTTP 200 with the SPA document. |
| Production API bundle | **PASS — verified against production:** downloaded JavaScript bundle contains the supplied HTTPS Render `/api` base URL. |
| Production CORS | **PASS — verified against production:** preflight returned HTTP 200 and allowed the exact Vercel origin plus Authorization/content-type headers. |
| Production authorization boundary | **PASS — verified against production:** unauthenticated `/api/datasets` returned HTTP 401. No authenticated account was used. |
| Production authenticated workflows | **Not verified:** no production registration/login/upload/report/reset data was created. No staging API or safe test accounts were available. |
| Production database connectivity/schema | **Not verified:** no production SQL, migration, or data operation was performed. `/api/health` does not test the datasource. |
| GitHub remote state | **PASS — verified:** repository started clean at `d13e6c6`; `main` equals `origin/main`. No push was performed. |

## Database migration and TLS

- Repository has migrations V1–V4. Production Flyway has `baseline-on-migrate=true`, baseline version 1, checksum validation, and Hibernate `ddl-auto=validate`; fresh schemas apply V1–V4 and pre-Flyway schemas baseline at V1 before applying V2–V4.
- Flyway was updated to 11.15.0. Flyway's MySQL support guidance lists versions through 9.4, so its MySQL 8.4 “newer than tested” warning is addressed by the version bump. This does not replace an actual migration run against MySQL 8.4. [Flyway support issue/version confirmation](https://github.com/flyway/flyway/issues/4168), [Flyway MySQL driver reference](https://documentation.red-gate.com/flyway/reference/database-driver-reference/mysql).
- MySQL Connector/J 9.7 is compatible with supported MySQL server versions; connector support is distinct from Flyway migration validation. [MySQL Connector/J compatibility](https://dev.mysql.com/doc/connector-j/en/connector-j-versions.html).
- JDBC must use `sslMode=REQUIRED` or preferably `VERIFY_CA`/`VERIFY_IDENTITY` with Aiven CA trust configured. `REQUIRED` encrypts traffic but does not verify the server certificate.
- **Staging migration result:** not run. No `AS_TEST_MYSQL_*` credentials were available; Docker was unavailable; the installed MySQL 9.7.1 local server was not running. A temporary isolated initialization attempt crashed inside `mysqld` before accepting connections. No production or existing local database was touched. The two guarded migration tests remain skipped in the passing suite.
- **Minimum external action:** provide a disposable MySQL 8.4 staging endpoint and credentials through a secret manager, then run `RealMysqlMigrationTest` with the explicit staging marker and schema create/drop opt-in. Never point it at production.

## File storage and dataset lifecycle

- **Verified locally:** upload path is `${FILE_UPLOAD_DIR}`; Docker sets `/app/uploads/datasets`. Dataset records retain generated basenames resolved within this directory. The Docker entrypoint creates/chowns storage and checks it is writable after dropping to UID 10001. Local test suite covers file-backed service behavior and dataset workflows.
- Missing stored files produce an explicit error; the app does not synthesize dataset content.
- **Requires external access:** Render must mount a persistent disk at `/app/uploads`, with the app path `/app/uploads/datasets` writable by UID 10001. A container volume declaration does not prove Render persistence. No dashboard disk inspection or restart persistence test was possible.

## SMTP and password reset

- **Verified locally:** missing JavaMailSender, successful send, and simulated `MailSendException` cases; service returns the same generic public response and deletes the reset token after delivery failure. Existing tests cover reset-token expiration and single-use behavior.
- **Requires external access:** production SMTP settings and successful mail delivery were not verified. Configure `SPRING_MAIL_HOST`, `SPRING_MAIL_PORT`, `SPRING_MAIL_USERNAME`, `SPRING_MAIL_PASSWORD`, `SPRING_MAIL_PROPERTIES_MAIL_SMTP_AUTH`, `SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_ENABLE`, recommended `STARTTLS_REQUIRED` and `SSL_CHECKSERVERIDENTITY`, `PASSWORD_RESET_FROM`, and `FRONTEND_URL` in Render. Never expose values in Git or chat.

## Deployment automation

- **Verified in repository:** root `Dockerfile` builds the Java 17 backend; `backend/Dockerfile` is also available for a `backend` build context. Render app binds the provided port and serves health at `/api/health`. Vercel configuration is `frontend/vercel.json`; frontend root is `frontend`, build is `npm run build`, output is `dist`, with SPA rewrite.
- There is no GitHub Actions deploy workflow or Render/Vercel project manifest. Native provider Git integrations should be used to avoid duplicate deploy pipelines.
- **Requires external access:** GitHub CLI authentication is invalid; no Render or Vercel CLI/dashboard connector is installed. I could not verify provider repository connections, production branch, automatic deploy-on-push, masked environment variables, Render disk, or Aiven networking. Do not claim the integrations are enabled until checked in their dashboards.
- Once confirmed, one push to `main` should cause Render to build/deploy the backend and Vercel to build/deploy the frontend. Expected behavior must be confirmed from both providers' logs; it cannot be guaranteed from repository files alone.

## End-to-end scope

Local backend/frontend suites exercise auth, ownership, upload/profile, analytics/statistics, AutoML/what-if, fairness/drift, reports/PDF, and frontend service/page flows. The frontend tests use mocked service calls in component tests and are not a live browser-to-API E2E suite. **Not verified on staging or production:** full registration → login → upload → preprocessing/statistics → AutoML/evaluation/explainability/fairness/what-if/drift/Ask Dataset → report/PDF → logout/relogin and cross-user isolation. This requires a staging account, test dataset, SMTP sink, and a successful storage restart test. Mobile visual/responsive testing was not performed because local browser use is disabled in this environment.

## Remaining release blockers

1. A disposable Aiven/MySQL 8.4 migration test and database TLS/network validation have not been completed.
2. Render persistent disk attachment and write access have not been verified.
3. Render/Vercel GitHub integration, production branch `main`, and auto-deploy-on-push settings are not inspectable here.
4. Authenticated staging E2E workflows and SMTP delivery remain unverified.

No passwords, JWT secrets, database credentials, SMTP credentials, or API keys are included in this report.

## Final push command

After the blockers above are cleared and the local release changes are committed on `main`:

```bash
git push origin main
```

Do not force-push. Confirm the matching commit completed in Render and Vercel; verify Render `/api/health` returns HTTP 200, Vercel `/` and `/dashboard` load, and browser API calls pass CORS. Then perform the authenticated workflow against staging before treating the release as fully accepted.
