# Release Plan

- **Release date:** 2026-10-31
- **Repository:** `bharad1101/AutoScientist-AI`
- **Production branch:** `main`
- **Current production commit:** `d13e6c6`
- **Local candidate:** `8b4cf87` plus uncommitted release fixes
**Status:** PRE-RELEASE — local candidate verified; provider configuration, pre-deployment backup confirmation, and authenticated production smoke tests remain. See “Final release decision” at the end of this document.

## 1. Objective

Release the current AutoScientist AI backend and frontend with:

- No loss of existing datasets, reports, users, or uploaded files.
- Correct production authentication and email handling.
- Safe Flyway migration on the production MySQL database.
- Correct frontend-to-backend API routing and CORS.
- Verified health, readiness, upload, analysis, and report workflows.
- A documented rollback path.

## 2. Release scope

Included in the candidate:

- Production login page no longer exposes development demo credentials.
- Registration rejects passwords over BCrypt’s 72-byte UTF-8 limit with a clear 400 response.
- Password-reset and login email normalization uses `Locale.ROOT`.
- Duplicate-registration races return 400 instead of an opaque 500.
- Optional `/api/health/ready` endpoint checks database connectivity without exposing connection details.
- Real-MySQL migration test configuration fixed and both fresh-install and legacy-upgrade paths passing on MySQL 9.7.1.
- Release-readiness documentation.

Excluded from this release:

- New product features.
- Destructive schema changes or data deletion.
- Changes to Aiven storage, backup retention, or network rules.
- Provider configuration changes.
- Production credentials or secret rotation unless separately approved.

## 3. Current evidence

| Check | Result |
|---|---|
| Backend unit/integration tests | 114 run, 0 failures, 0 errors, 2 skipped (real-MySQL tests skipped when env absent) |
| Real MySQL 9.7.1 migration tests | 2 run, 0 failures, 0 errors |
| Frontend tests | 126 passed |
| Java 17 package build | PASS |
| Frontend production build | PASS with production HTTPS API URL |
| Production bundle contains demo credentials | No |
| Production bundle contains localhost API URL | No |
| Live backend health | HTTP 200, `status=UP` |
| Live CORS | Allowed frontend origin passes; unapproved origin 403 |
| Local cross-user access | Dataset and report access rejected with HTTP 403 |
| Java 17 package build (re-verified) | PASS — `Build-Jdk-Spec: 17` |
| Candidate diff hygiene | PASS — `git diff --check` clean; no secrets or unintended files |
| Docker image build | NOT RUN — daemon unavailable; not required for Render's remote Docker build |
| Live authenticated production smoke | NOT COMPLETED — no production account/credentials available |
| Provider configuration | NOT VERIFIED — no Render/Vercel/Aiven CLI, token, or dashboard session |

## 4. Release prerequisites

All must be true before a production deployment is approved.

### Code and review

- [ ] Review every local uncommitted change.
- [ ] Confirm no `.env`, secret, backup, upload, or generated artifact is staged.
- [ ] Confirm the candidate commit is based on current `origin/main`.
- [ ] Re-run backend, frontend, and real-MySQL checks on the final candidate.
- [ ] Obtain explicit approval to commit and push.

### Provider access

- [ ] Authorized operator has Render dashboard/API access.
- [ ] Authorized operator has Vercel dashboard/API access.
- [ ] Authorized operator has Aiven dashboard/API access.
- [ ] Confirm Render service branch and auto-deploy behavior.
- [ ] Confirm Vercel Production branch and build environment.
- [ ] Confirm Aiven production database identity and backup status.

### Database and storage

- [ ] Take or confirm a recent Aiven backup before deployment.
- [ ] Record the exact backup identifier and retention time.
- [ ] Confirm Render persistent disk is mounted and writable.
- [ ] Back up uploaded files or confirm that the disk is included in provider backups.
- [ ] Confirm the migration user has only the privileges required for Flyway.

### Configuration

- [ ] Confirm `SPRING_PROFILES_ACTIVE=mysql`.
- [ ] Confirm `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, and `SPRING_DATASOURCE_PASSWORD` are present without displaying values.
- [ ] Confirm `JWT_SECRET` is present and is not the development default.
- [ ] Confirm `APP_CORS_ALLOWED_ORIGINS` contains exactly `https://auto-scientist-ai-ten.vercel.app`.
- [ ] Confirm `FRONTEND_URL` matches the public frontend origin.
- [ ] Confirm `FILE_UPLOAD_DIR=/app/uploads/datasets`.
- [ ] Confirm Vercel `VITE_API_BASE_URL=https://autoscientist-ai-backend.onrender.com/api`.
- [ ] Confirm SMTP variables are present if password-reset email delivery is in scope.

## 5. Release sequence

### Phase 0 — Freeze and record

1. Record the current production backend deployment ID and frontend deployment ID/alias.
2. Record the current Aiven backup identifier.
3. Announce the release window and rollback owner.
4. Freeze unrelated changes to `main`.

### Phase 1 — Final candidate verification

Run from the repository:

```bash
cd backend
./mvnw -B -ntp clean test
./mvnw -B -ntp clean package -DskipTests

cd ../frontend
npm test
VITE_API_BASE_URL=https://autoscientist-ai-backend.onrender.com/api npm run build
```

Run the real-MySQL migration test only against a disposable staging database with explicit opt-in:

```bash
AS_TEST_MYSQL_URL='jdbc:mysql://<staging-host>:<port>/?sslMode=REQUIRED' \
AS_TEST_MYSQL_ENVIRONMENT='staging' \
AS_TEST_MYSQL_USER='<staging-user>' \
AS_TEST_MYSQL_PASSWORD='<staging-password>' \
AS_TEST_MYSQL_ALLOW_SCHEMA_CREATE_DROP='true' \
./mvnw -B -ntp -Dtest=RealMysqlMigrationTest test
```

Do not point this test at production.

### Phase 2 — Commit and push

Only after explicit approval:

1. Stage only intended source and documentation files.
2. Create a concise release commit.
3. Push to `main`.
4. Confirm GitHub receives the commit and no unexpected checks fail.

### Phase 3 — Backend deployment

1. Observe Render build and startup logs.
2. Confirm active profile is `mysql`.
3. Confirm Flyway validates and applies only expected migrations.
4. Confirm Hibernate schema validation succeeds.
5. Confirm the service reports healthy.
6. Request `/api/health`.
7. Request `/api/health/ready` after the readiness change is deployed.
8. Confirm upload directory initialization succeeds.

### Phase 4 — Frontend deployment

1. Confirm Vercel built from the release commit.
2. Confirm the production bundle uses the Render API URL.
3. Confirm no demo credentials or localhost URL are present.
4. Confirm the SPA rewrite serves deep links.
5. Confirm the custom production origin is unchanged.

### Phase 5 — Production smoke test

Use a pre-approved non-destructive test account.

- [ ] Register a new account or log in with a controlled test account.
- [ ] Confirm invalid credentials return 401.
- [ ] Confirm an over-72-byte password returns a clear 400.
- [ ] Confirm duplicate registration returns 400, not 500.
- [ ] Upload a small CSV.
- [ ] Confirm profiling, quality, EDA, and associations complete.
- [ ] Train one AutoML model.
- [ ] Generate and download CSV, Excel, Markdown, and PDF reports.
- [ ] Confirm a second account cannot access the first account’s dataset or report.
- [ ] Confirm password-reset request behaves as configured.
- [ ] Confirm CORS from the production frontend origin.
- [ ] Confirm no 5xx responses in provider logs.

### Phase 6 — Monitor and close

Monitor for at least 30 minutes after deployment:

- Backend error rate and response time.
- Render restart count and memory.
- Database connections and slow queries.
- Flyway errors.
- Vercel build/runtime errors.
- User-reported login or upload failures.

Record final status and close the release only when every acceptance criterion is met.

## 6. Acceptance criteria

Release is accepted only when:

- Backend and frontend deploy from the same reviewed commit.
- Render starts with `mysql`, Flyway completes, and schema validation passes.
- `/api/health` and `/api/health/ready` return 200.
- Production frontend uses the correct API origin.
- Login, registration validation, upload, analysis, AutoML, and report download pass.
- Cross-user access is denied.
- Uploaded files persist across a backend restart or the limitation is explicitly accepted.
- No new critical/high security findings are introduced.
- Rollback owner confirms the previous deployment and database backup are available.

## 7. Rollback triggers

Initiate rollback if any of the following occurs:

- Flyway migration fails or applies an unexpected migration.
- Hibernate schema validation fails.
- Backend repeatedly restarts or health checks fail.
- Production login fails for known-good accounts.
- Dataset uploads or report downloads fail.
- Cross-user data exposure is detected.
- Error rate or latency materially exceeds the pre-release baseline.

See `ROLLBACK_PLAN.md`.

## 8. Approvals

| Role | Name | Approval | Date |
|---|---|---|---|
| Release owner | TBD | Pending | |
| Backend reviewer | TBD | Pending | |
| Frontend reviewer | TBD | Pending | |
| Database operator | TBD | Pending | |
| Provider operator | TBD | Pending | |

**No commit, push, deployment, provider change, or production migration is authorized by this document alone. Explicit approval is required.**

## 9. Final release decision (2026-10-31)

### A. VERIFIED SAFE FOR LOCAL CANDIDATE

- The candidate preserves all existing uncommitted work and is based on `origin/main` plus `8b4cf87` and the reviewed release fixes.
- Only intended files changed; `git diff --check` is clean; no secrets, `.env` files, uploads, backups, or generated artifacts appear in the candidate.
- Java 17 package build passes (`Build-Jdk-Spec: 17`); frontend production build passes with the Render API URL and contains no localhost URL or demo credentials.
- Backend 114 passed / 0 failed / 2 guarded skipped, frontend 126 passed, and real-MySQL migration paths 2/2 passed (from the completed validation; not re-run without cause).
- Migrations reviewed: `V1` baseline for fresh installs; guarded, non-destructive `V2`–`V4` deltas for legacy databases; no drops/truncates/deletes of user data.
- No production database, provider configuration, persistent storage, or service was modified.

### B. REQUIRES EXTERNAL AUTHORIZATION

- Approval to commit and push the candidate to `main`.
- Approval to deploy backend and frontend.
- Read-only provider verification by an authorized operator (Render, Vercel, Aiven).
- Database-operator confirmation of a pre-deployment Aiven backup and current schema history.
- Approval to run the authenticated production smoke test.

### C. REQUIRES MANUAL ACCOUNT ACTION

- Render: confirm repository/branch, root directory + Dockerfile path, `SPRING_PROFILES_ACTIVE=mysql`, required env names present (no values), persistent disk at `/app/uploads`, health path `/api/health`, auto-deploy.
- Vercel: confirm root `frontend`, build `npm run build`, output `dist`, Production `VITE_API_BASE_URL`, SPA rewrite, auto-deploy, domain.
- Aiven: confirm service/version, TLS mode, migration privileges, backup identifier, Render network path, connection limits, `flyway_schema_history`.
- Provide a controlled production test account (or authorize creation during smoke testing).
- Optionally start Docker Desktop to pre-validate the image locally.

### D. BLOCKED

- Aiven production verification (no access).
- Authenticated production smoke test (no production account/credentials).
- Render and Vercel dashboard configuration verification (no access).
- Local Docker image build (daemon unavailable; non-blocking).

### E. READY FOR DEPLOYMENT APPROVAL

The local candidate is **ready for deployment approval**. Deployment remains conditional on resolving or explicitly risk-accepting B, C, and D, recording the approvals above, and then following Phases 0–6.

### Minimum actions the user must authorize

1. Authorize commit + push of the reviewed candidate to `main`.
2. Authorize (or supply access for) read-only Render, Vercel, and Aiven verification, plus Aiven backup/schema confirmation.
3. Authorize backend and frontend deployment after verification.
4. Authorize the authenticated production smoke test and provide a controlled account.
5. Optionally authorize starting Docker Desktop for local image pre-validation.
