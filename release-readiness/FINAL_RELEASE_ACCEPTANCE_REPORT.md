# AutoScientist AI — Final Release Acceptance

- **Date:** 2026-10-31
- **Repository:** `/Applications/AutoScientist AI`
- **Production baseline:** `d13e6c6`
- **Local candidate:** `8b4cf87` plus uncommitted release fixes
- **Production services:** Vercel frontend and Render backend
**Overall decision:** **CONDITIONAL — NOT READY TO PUSH/DEPLOY WITHOUT APPROVAL.** The technical candidate passes local unit, integration, real-MySQL, frontend, production-bundle, auth, feature, and isolation checks. Authenticated production smoke testing and provider dashboard verification remain incomplete. No commit, push, deployment, or production mutation was performed.

## Changes in this pass

| Issue | Fix | Status |
|---|---|---|
| Production login page displayed development demo credentials and a Quick Fill button that only work in the dev profile. | Render the demo block only when `import.meta.env.DEV` is true; label it as local development. | Fixed and verified in a production bundle. |
| Registration passwords longer than BCrypt’s 72-byte limit produced a confusing 400 message. | Reject over-72-byte UTF-8 passwords in `AuthService` and `RegisterPage` with a clear message. | Fixed and verified locally. |
| Email normalization used the default locale. | Use `Locale.ROOT` in registration, login, current-user lookup, and authentication lookup. | Fixed and covered by tests. |
| Concurrent duplicate registration could surface as an opaque 500 at commit. | Use `saveAndFlush` and translate `DataIntegrityViolationException` to a clear 400. | Fixed and covered by tests. |
| `/api/health` proves process liveness but not database connectivity. | Add `/api/health/ready`, which runs `SELECT 1` and returns 200/503 without connection details. Allow it publicly. | Fixed and verified locally. |
| Real-MySQL test inherited H2 driver/dialect from test resources. | Pass the MySQL driver and dialect explicitly when starting the validation context. | Fixed; real-MySQL tests now pass. |
| Legacy migration test dropped a unique index still required by a foreign key and dropped nonexistent tables. | Drop the foreign key first, then the index, re-add the foreign key, and use `DROP TABLE IF EXISTS`. | Fixed; real-MySQL tests now pass. |

No migrations were deleted or rewritten. No features were removed or replaced with mocks.

## Verification results

| Area | Result |
|---|---|
| Backend test suite | **PASS locally:** 114 tests, 0 failures, 0 errors, 2 skipped without env. |
| Real-MySQL migration tests | **PASS locally:** 2/2 on disposable MySQL 9.7.1 with TLS, fresh and legacy paths. |
| Java 17 package | **PASS locally:** executable JAR built. |
| Frontend tests | **PASS locally:** 21 files, 126 tests. |
| Frontend production build | **PASS locally** with `VITE_API_BASE_URL=https://autoscientist-ai-backend.onrender.com/api`. |
| Production bundle safety | **PASS:** no demo credentials, no local-development credential block, no localhost API URL. |
| Local readiness | **PASS:** `/api/health/ready` returns 200 with `database=UP`. |
| Local auth regressions | **PASS:** over-72-byte password 400, duplicate 400, normalized login 200, invalid token 401. |
| Local cross-user isolation | **PASS:** 403 for another user’s dataset, quality report, report, and PDF. |
| Local feature smoke | **PASS:** upload, profiling, quality, EDA, associations, dashboard, research, AutoML, fairness, explanation, what-if, drift, reports, CSV/Excel/Markdown/PDF. |
| Live backend health | **PASS:** HTTP 200 `status=UP` over HTTP/1.1. HTTP/2 timed out and is recorded as a transport anomaly. |
| Live frontend | **PASS:** production origin 200; bundle uses the Render `/api` URL. |
| Live CORS | **PASS:** exact frontend origin allowed; unapproved origin 403. |
| Live unauthenticated boundary | **PASS:** protected endpoints return 401. |
| Live authenticated workflows | **NOT RUN:** no production account or approved test data. |
| Actual Aiven database | **NOT VERIFIED:** no provider access. |
| Render configuration | **NOT VERIFIED:** no provider access. |
| Vercel configuration | **PARTIAL:** GitHub status and live bundle only. |
| Docker image build | **BLOCKED:** Docker daemon unavailable. |
| SMTP/password-reset delivery | **NOT VERIFIED:** no production SMTP access. |

## Database migration and TLS

- Migrations `V1`–`V4` are present.
- The `mysql` profile enables Flyway with baseline-on-migrate, baseline version 1, checksum validation, and Hibernate `ddl-auto=validate`.
- Fresh schemas apply `V1`–`V4`; pre-Flyway schemas baseline at `V1` and apply the additive `V2`–`V4`.
- Flyway resolves `11.15.0` through the local POM bump.
- Real-MySQL tests passed on MySQL 9.7.1, including legacy data preservation and idempotent re-run.
- Flyway reports MySQL 9.7 as newer than its verified 9.4 ceiling; this warning is expected and does not itself indicate failure.
- The actual Aiven MySQL version, schema history, user privileges, TLS mode, backups, and network path remain unknown.
- JDBC must use `sslMode=REQUIRED` at minimum, preferably `VERIFY_CA` or `VERIFY_IDENTITY` with the Aiven CA configured.

**Minimum external action:** confirm the Aiven backup and schema state, then run the guarded migration test against a disposable staging schema on the same major version as Aiven. Never point it at production.

## File storage and dataset lifecycle

- Upload path is `${FILE_UPLOAD_DIR}`; the Docker image sets `/app/uploads/datasets`.
- The entrypoint creates/chowns the upload and data directories, checks writability, drops to UID 10001, and starts the JVM.
- Local smoke confirmed upload, profiling, reports, and PDF generation.
- **Requires provider verification:** Render must mount a persistent disk at `/app/uploads`, and the path must survive a restart. The image’s `VOLUME` declaration does not prove persistence.
- Multi-instance deployment is unsupported unless all instances share the same upload volume.

## SMTP and password reset

- Unit/integration tests cover missing transport, successful send, delivery failure, token cleanup, expiration, and single-use behavior.
- **Requires provider verification:** production SMTP variables, sender identity, TLS, and actual delivery.
- Required names include `SPRING_MAIL_HOST`, `SPRING_MAIL_PORT`, `SPRING_MAIL_USERNAME`, `SPRING_MAIL_PASSWORD`, `PASSWORD_RESET_FROM`, and `FRONTEND_URL`.
- Never expose SMTP values in Git or logs.

## Deployment automation and provider access

- Repository has root and backend Dockerfiles, `frontend/vercel.json`, and no GitHub Actions workflow or Render blueprint.
- GitHub is verified: repository public, branch `main`, latest origin commit `d13e6c6`, Vercel status success, no workflows/hooks/Actions secrets, environment `Production` exists.
- Vercel is partially verified: live frontend and correct API bundle; dashboard/build settings not inspectable.
- Render is partially verified: live health/CORS/auth behavior; service settings, env vars, disk, logs, and auto-deploy not inspectable.
- Aiven is blocked: no CLI, token, or dashboard access.
- `render`, `vercel`, and `avn` CLIs are not installed; no provider tokens are available.
- GitHub token has `repo`, `workflow`, `read:org`, and `gist` scopes.

## End-to-end scope

Local backend/frontend suites and the local smoke test exercise auth, ownership, upload/profile, analytics/statistics, AutoML/what-if, fairness/drift, reports/PDF, and frontend flows. Frontend component tests use mocked service calls and are not a live browser-to-production E2E suite.

**Not verified on production:** registration → login → upload → analysis → AutoML → explainability/fairness → what-if/drift → report/PDF → relogin and cross-user isolation. This requires an approved production/staging account, test dataset, SMTP sink, and a storage restart test.

## Remaining release blockers

1. Explicit approval to commit, push, and deploy.
2. Render provider verification: service source, branch, auto-deploy, env names, disk, health check, logs.
3. Vercel provider verification: root/build/output, Production `VITE_API_BASE_URL`, auto-deploy, domain.
4. Aiven verification: version, schema history, TLS, privileges, backup, network path, connection limits.
5. Confirmed restorable backup before deployment.
6. Authenticated production/staging E2E and SMTP delivery.
7. Upload persistence across a real provider restart.
8. Docker image build verification, or explicit risk acceptance for the unavailable daemon.
9. Resolution or risk acceptance for the live HTTP/2 timeout.

## Current production risk note

Production `d13e6c6` still serves the old frontend login page with development demo credentials, and the backend does not have `/api/health/ready`. These candidate fixes are not live. The demo credentials are not production secrets, but they mislead users because those accounts exist only in the dev profile.

## Final release command

Only after blockers are cleared and explicit approval is received:

```bash
git push origin main
```

Do not force-push. Confirm Render and Vercel build the matching commit, then run the production smoke checklist. A successful build or `/api/health` response alone is not release acceptance.

No passwords, JWT secrets, database credentials, SMTP credentials, or API keys are included in this report.
