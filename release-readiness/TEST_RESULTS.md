# Test Results

- **Date:** 2026-10-31
- **Candidate:** local `8b4cf87` plus uncommitted release fixes
- **Production baseline:** `d13e6c6`
**Overall:** PASS locally; provider and authenticated production checks remain.

## Current pass

| Check | Result |
|---|---|
| Backend `./mvnw -B -ntp clean test` | **PASS:** 114 tests, 0 failures, 0 errors, 2 skipped (guarded real-MySQL tests without env). |
| Backend real-MySQL migration test | **PASS:** 2 tests, 0 failures, 0 errors against disposable MySQL 9.7.1 with TLS required. Fresh install and legacy upgrade both passed. |
| Backend Java 17 package | **PASS:** `clean package -DskipTests` produced the executable JAR. |
| Backend `flyway-core` / `flyway-mysql` | **11.15.0** after the local POM bump. |
| Frontend `npm test -- --run` | **PASS:** 21 test files, 126 tests passed. |
| Frontend production build | **PASS:** built with `VITE_API_BASE_URL=https://autoscientist-ai-backend.onrender.com/api`. |
| Production bundle safety | **PASS:** no developer demo credentials, no `Local Development Credentials`, and no localhost/loopback API URL found in `dist`. |
| Local readiness endpoint | **PASS:** `GET /api/health/ready` returned 200 with `database=UP`. |
| Local auth validation | **PASS:** over-72-byte password returns a clear 400; duplicate registration returns 400; mixed-case/whitespace login normalization works. |
| Local cross-user isolation | **PASS:** second account received 403 for the first account’s dataset, quality report, report, and PDF. |
| Local feature smoke | **PASS:** upload, profiling, quality, EDA, associations, dashboard, research, AutoML, fairness, explanation, what-if, drift, reports, CSV/Excel/Markdown/PDF. |
| Live backend `/api/health` | **PASS:** HTTP 200, `status=UP` over HTTP/1.1. HTTP/2 timed out and is recorded separately. |
| Live CORS | **PASS:** production frontend origin allowed; unapproved origin 403. |
| Live authorization | **PASS:** unauthenticated dataset and profile endpoints return 401. |
| Live frontend | **PASS:** production origin returns 200; bundle references the Render `/api` URL. |
| Docker image build | **BLOCKED:** Docker CLI exists, daemon unavailable. |
| `git diff --check` | **PASS** after documentation changes. |

## Real MySQL coverage

The opt-in test created uniquely named disposable schemas and dropped only schemas it created. It requires `AS_TEST_MYSQL_ENVIRONMENT=staging`, credentials, TLS, and `AS_TEST_MYSQL_ALLOW_SCHEMA_CREATE_DROP=true`.

Verified:

- Fresh schema: `V1`–`V4` applied, Hibernate `validate` passed.
- Legacy pre-Flyway schema: baseline at `V1`, `V2`–`V4` applied, existing user and dataset rows preserved, Hibernate `validate` passed.
- Re-running migration was idempotent.

This does not prove the actual Aiven database version, schema state, privileges, backups, or network path.

## Test fixes made during this pass

- Real-MySQL application validation now explicitly selects the MySQL driver and dialect; the test-classpath H2 defaults no longer override the MySQL JDBC URL.
- Legacy setup drops the quality-report foreign key before dropping its unique index, then re-adds the foreign key.
- Legacy cleanup uses `DROP TABLE IF EXISTS` for tables that may not exist.
- Added auth tests for locale normalization, 72-byte rejection, and duplicate-race handling.
- Added a readiness endpoint integration test.

## Previously recorded dependency evidence

Prior passes reported:

- Flyway `11.15.0` resolution for both `flyway-core` and `flyway-mysql`.
- Commons Lang `3.18.0` overriding the affected `3.17.0`.
- Frontend `npm audit` reporting 0 advisories.
- Backend OSV scan findings for Spring Framework 6.2.19 SSE fragment rendering and `XsltView` path limitation; source inspection found no corresponding usage in this REST application.
- OWASP Dependency-Check could not produce a report because NVD rejected an unavailable API key.

These scans were not rerun in this pass. Treat them as prior evidence, not fresh results.

## Not verified

- Authenticated production workflows against the live Aiven/Render deployment.
- Actual Aiven schema, Flyway history, TLS, backups, and network access.
- Render environment variables, disk, logs, and auto-deploy behavior.
- Vercel dashboard/build settings and Production environment values.
- SMTP/password-reset delivery.
- Docker image build and startup.
- Upload persistence across a real Render restart.
- HTTP/2 behavior through the Render edge.
