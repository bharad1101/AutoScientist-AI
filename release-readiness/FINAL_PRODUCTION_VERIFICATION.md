# AutoScientist AI — Final Production Verification

**Audit date:** 2026-10-30
**Scope:** Local repository review and safe local verification. No deploy, push, production write, or external dashboard edit was performed.

## Executive result

Production readiness is **conditional**. Locally, backend tests and build pass, and the dependency scan cleared the actionable Commons Lang finding. Actual Aiven/MySQL 8.4 migration validation, authenticated end-to-end workflows, SMTP delivery, and Render persistent-disk behavior remain unverified because no staging service/database credentials or SMTP sandbox were available. The two MySQL integration tests are deliberately gated to an explicitly named disposable staging database and were skipped. Production health/CORS/unauthenticated-access observations are carried forward from `COMPLETE_PRODUCTION_AUDIT.md`; they were not re-executed during this verification.

## Verified locally

- Backend Maven test suite: **110 tests, 0 failures, 0 errors, 2 skipped**. The skipped tests are the staging-only real MySQL migration tests.
- Backend Maven `verify`: passed after the dependency version update. The final test run was rerun against the latest source and passed.
- The password-reset notifier handles absent mail transport, delivery failure, and successful delivery in three unit tests. A service integration test confirms that failed delivery retains the generic API response and removes the unusable reset token.
- MySQL migration test harness now requires explicit `AS_TEST_MYSQL_ENVIRONMENT=staging`, explicit schema-create/drop opt-in, URL/user/password, no database in the URL, and TLS (`sslMode=REQUIRED` or certificate-verifying modes). It creates unique schema names and only drops a schema it created. Its design covers fresh V1–V4 migration and a legacy baseline-at-V1 then V2–V4 upgrade, followed by Spring startup with Hibernate `validate`.
- File uploads are stored under `${FILE_UPLOAD_DIR}` (Docker default `/app/uploads/datasets`). Dataset metadata stores generated basenames, resolved against this directory; changing the directory without carrying files over makes existing metadata point to missing content. Upload processing checks CSV content/format and uses generated filenames/path checks; configured multipart limit is 50 MB.
- Docker runtime uses UID 10001 after preparing/chowning `/app/uploads`; the directory must be writable. The Docker image declares `/app/uploads` as a volume, but that declaration alone does not make Render storage persistent.
- Production MySQL profile enables Flyway, validates migration checksums, baselines pre-Flyway schemas at V1, applies additive V2–V4, and configures Hibernate `ddl-auto=validate`. Four repository migrations exist: V1 baseline, V2 provenance delta, V3 association confidence intervals, and V4 password reset tokens.
- Production datasource has no URL or credential defaults and must be provided with `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, and `SPRING_DATASOURCE_PASSWORD`. MySQL TLS parameters must be present in the JDBC URL; use `sslMode=REQUIRED` for encryption or `VERIFY_CA`/`VERIFY_IDENTITY` with the provider CA/trust configuration for certificate validation. Do not use `DISABLED` or `PREFERRED` in production.
- Production profile requires a sufficiently strong `JWT_SECRET` and `APP_CORS_ALLOWED_ORIGINS`; the default localhost CORS value is not suitable for production. Server binds to Render's `PORT` through Spring's server port configuration; `/api/health` is implemented as the health route. Health is a process check, not proof of DB migrations or SMTP delivery.
- Frontend reads `VITE_API_BASE_URL` at build time. Production build validation rejects missing, relative, localhost, or non-HTTPS API base URLs. `frontend/.env.example` documents the variable but does not set an actual Vercel dashboard value. The user-provided live API URL is documented in the prior audit; no external Vercel variable value was independently read from the dashboard.
- OSV Scanner scanned Maven runtime dependencies. Commons Lang 3.17.0 had a moderate advisory (CVE-2025-48924); the POM now manages it at 3.18.0, the fixed version. The updated scan no longer reports this issue. It reports two Spring Framework 6.2.19 advisories: SSE fragment corruption and XsltView path limitation. Repository review found no SSE rendering or XsltView/wildcard-view usage, so those affected paths are not used by this REST application. Reassess if such view features are introduced.
- OWASP Dependency-Check did not complete: NVD rejected the empty API key and produced no valid scan report. This is not represented as a successful OWASP scan.
- Existing audit’s frontend results remain: **126 tests**, production build pass, `npm audit` reported zero vulnerabilities. No frontend source code changed in this continuation.

## Verified against staging

- **None.** No staging API, Aiven staging instance/credentials, test email sink, or Render staging service was available. The real MySQL test harness compiled but was skipped. Do not interpret local H2 tests as MySQL 8.4 validation.

## Verified against production

The preceding `COMPLETE_PRODUCTION_AUDIT.md` records successful live checks for the supplied frontend/backend: backend `/api/health`, CORS preflight, frontend route response, and unauthenticated protected API rejection. These were read as prior evidence and not repeated in this continuation. No authenticated production user workflow, production DB schema, production file persistence, password reset delivery, or data mutation was performed.

## Not tested

- Actual Aiven connection from Render, Aiven CA trust/certificate identity, MySQL 8.4 migration execution, and Hibernate schema validation.
- Fresh database and legacy database migration paths against real MySQL. Flyway's published MySQL driver compatibility reference does not specifically enumerate 8.4; actual staging migration is required to establish compatibility.
- Render file persistence across deploy/restart, disk capacity, permissions under deployed UID, and recovery/backup behavior.
- SMTP live delivery, provider authentication, sender verification, inbox deliverability, and timeout behavior.
- Authenticated E2E flow: registration, login, upload, analysis, AutoML, analytics, report creation, PDF download, and cross-account ownership denial.
- Production dashboard environment values. No secret values or dashboard access were available.
- Other authenticated workflows against deployed services, production database persistence, or mobile-browser interaction in this continuation.

## Security and behavior review

- Spring Security/JWT protects API routes except explicitly public auth/health routes. Dataset and report controllers/services resolve records through the authenticated user's ownership; controllers do not accept an arbitrary owner identity from the browser. Review was source-level; cross-account denial still needs staging E2E proof.
- Dataset files are addressed by generated stored filenames under the configured storage root. Input validation and filename/path safeguards are present. Remaining deployment risk is persistence and write permission, not a missing absolute path in database metadata.
- Password reset intentionally uses a generic response. When mail is unconfigured or delivery fails, reset token cleanup now prevents a token being retained when no email was delivered; logs do not include reset credentials.
- Error handling review and existing tests cover application responses, but no production rate/load or malformed-file abuse test was run.

## SMTP variables

To enable actual reset-email delivery in Render, configure the provider-specific values without committing them:

- `SPRING_MAIL_HOST`
- `SPRING_MAIL_PORT`
- `SPRING_MAIL_USERNAME`
- `SPRING_MAIL_PASSWORD`
- `SPRING_MAIL_PROPERTIES_MAIL_SMTP_AUTH=true`
- `SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_ENABLE=true` (when provider supports STARTTLS)
- `SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_REQUIRED=true` (recommended for STARTTLS)
- `SPRING_MAIL_PROPERTIES_MAIL_SMTP_SSL_CHECKSERVERIDENTITY=true` (hostname validation when using STARTTLS)
- `PASSWORD_RESET_FROM` (provider-verified sender address)
- `FRONTEND_URL` (the deployed frontend origin used in reset links)

Spring mail configuration is optional at startup, so missing SMTP configuration does not stop the service; reset requests remain generic and delivery is unavailable. Configure the above before claiming password reset works in production.

## Exact dashboard configuration required

### Render backend service

- Runtime: Docker using `backend/Dockerfile`; service must bind the platform-provided `PORT`.
- Health check path: `/api/health`.
- Set `SPRING_PROFILES_ACTIVE=mysql`.
- Set `SPRING_DATASOURCE_URL` to the Aiven-provided JDBC endpoint, including TLS (`sslMode=REQUIRED`; prefer `VERIFY_CA`/`VERIFY_IDENTITY` when configured with Aiven CA), and no embedded credentials. Set `SPRING_DATASOURCE_USERNAME` and `SPRING_DATASOURCE_PASSWORD` as secret variables.
- Set a generated production `JWT_SECRET` meeting the app's decoded minimum of 64 bytes. Never reuse the development fallback.
- Set `APP_CORS_ALLOWED_ORIGINS` to the exact Vercel production origin, e.g. `https://auto-scientist-ai-ten.vercel.app`, with no API path. Add preview origins only if intentionally supported.
- Set `FILE_UPLOAD_DIR=/app/uploads/datasets`.
- Attach a **persistent disk mounted at `/app/uploads`** to the backend service. Select capacity appropriate to retained uploads. Keep the configured subdirectory and ensure runtime UID 10001 can write there. Without the disk, uploaded files can disappear on replacement/redeploy while Aiven rows remain.
- Configure SMTP values above if password-reset delivery is required.
- No actual current Render dashboard values were available for inspection.

### Vercel project

- Set Production build environment variable `VITE_API_BASE_URL` to the actual HTTPS backend API base URL ending in `/api` (the URL supplied in the request implies `https://autoscientist-ai-backend.onrender.com/api`; verify it is the current Render service URL before saving).
- Set Vercel Root Directory to `frontend` so the project uses `frontend/vercel.json` (SPA rewrite to `/index.html`) and `frontend/package.json` (`npm run build`). No explicit `vercel.json` build/output overrides are required by the repository config.
- Redeploy/rebuild after changing the variable because Vite embeds it at build time.
- No actual current Vercel dashboard values were available for inspection.

### Aiven MySQL

- Use MySQL 8.4 with a database/user dedicated to this application, TLS required, and credentials stored only in Render secrets.
- Provide Aiven CA trust material/config if using certificate-verifying TLS; `sslMode=REQUIRED` encrypts but does not validate the server identity.
- Permit connections from Render's currently assigned outbound IP ranges (obtain these from the Render dashboard; they can change by service/region/plan).
- Confirm the database user's privileges permit Flyway to create/update its schema history and application tables. Do not grant broad administrative privileges unless required by the chosen migration process.
- Run the provided migration integration test only against a disposable staging Aiven database. It explicitly creates and drops test schemas and must never be pointed at production.
- Production schema migrations are enabled at application startup. Before the first production start, take/verify an Aiven backup and inspect staging migration results; never run a reset/clean operation.

## Safe authenticated smoke-test plan (staging only)

1. Create isolated staging Render/Vercel-preview services and a disposable Aiven staging database; configure a test-only email sink. Store credentials in secret environment variables.
2. Run the gated `RealMysqlMigrationTest` against the staging MySQL server only, after setting `AS_TEST_MYSQL_ENVIRONMENT=staging` and explicit schema-create/drop opt-in. Confirm fresh and legacy paths and Hibernate `validate` pass.
3. Register a unique disposable user; log in; call `/api/auth/me` with its JWT. Verify invalid credentials are rejected.
4. Upload repository sample `test-data/student_performance.csv`; verify file persists, dataset list/detail and quality endpoints work.
5. Exercise EDA (correlations and grouped summaries), analysis/association refresh, then train AutoML on `exam_score` as a regression target. Verify result persistence and dashboard analytics.
6. Create a report for the owned dataset; retrieve report metadata and download PDF; verify a non-empty PDF response and expected content type.
7. Register a second test user and request the first user's dataset/report IDs; verify denial (403/404) and no data leakage.
8. Restart/redeploy staging backend and confirm dataset file and metadata still match. Request a password reset to the test sink and verify the link and reset flow; test provider failure produces generic response and no retained unusable token.
9. Delete only staging dataset IDs created by the run; do not target production. Keep the staging user disposable if no account-delete route exists.

## Sources

- Repository configuration and migrations: `backend/src/main/resources/application.yml`, `backend/src/main/resources/db/migration/`, `backend/Dockerfile`, `backend/src/test/java/com/autoscientist/RealMysqlMigrationTest.java`, and the prior audit.
- MySQL Connector/J 9.7 supports MySQL Server 8.0+: [Connector/J release notes](https://dev.mysql.com/doc/relnotes/connector-j/en/news-9-7-0.html). TLS modes: [Connector/J SSL reference](https://dev.mysql.com/doc/connector-j/en/connector-j-reference-using-ssl.html).
- Aiven Java/MySQL connection guidance: [Aiven MySQL Java connection](https://aiven.io/docs/products/mysql/howto/connect-with-java).
- Flyway's published compatibility list does not explicitly enumerate 8.4: [Flyway MySQL driver reference](https://documentation.red-gate.com/flyway/reference/database-driver-reference/mysql).
- Commons Lang 3.18.0 fix details: [Apache Commons Lang changes](https://commons.apache.org/proper/commons-lang/changes.html).
- Render persistent disk behavior: [Render disks](https://render.com/docs/disks); outbound ranges: [Render outbound IPs](https://render.com/docs/outbound-ip-addresses).
- Spring advisories surfaced by OSV: [CVE-2026-47890](https://spring.io/security/cve-2026-47890), [CVE-2026-47884](https://spring.io/security/cve-2026-47884).
