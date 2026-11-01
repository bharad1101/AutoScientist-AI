# Deployment Checklist

- **Release date:** 2026-10-31
- **Candidate:** local `8b4cf87` plus uncommitted release fixes
- **Production baseline:** `d13e6c6`
**Rule:** A box is checked only when evidence exists. Provider dashboard boxes remain unchecked until an authorized operator verifies them.

## 1. Pre-deployment approval

- [ ] Release owner identified.
- [ ] Rollback owner identified.
- [ ] Deployment window approved.
- [ ] Explicit approval to commit and push received.
- [ ] Explicit approval to deploy backend received.
- [ ] Explicit approval to deploy frontend received.
- [ ] No unrelated `main` changes are in flight.

## 2. Code and secret hygiene

- [x] Backend tests pass locally: 114 run, 0 failures, 0 errors, 2 skipped.
- [x] Real-MySQL migration tests pass locally: 2 run, 0 failures.
- [x] Frontend tests pass locally: 126 passed.
- [x] Java 17 package build passes.
- [x] Frontend production build passes with the Render API URL.
- [x] Production bundle contains no demo credentials.
- [x] Production bundle contains no localhost API URL.
- [x] Review final diff before staging (Phase 2: only intended source/docs/tests changed).
- [x] Confirm no `.env`, credentials, backups, uploads, or generated artifacts are staged.
- [x] Confirm no secret values appear in logs or documentation (diff and untracked files scanned).
- [ ] Commit only intended files.
- [ ] Push the reviewed commit to `main` after approval.

## 3. Database backup

- [ ] Confirm Aiven backup is enabled.
- [ ] Create or identify the latest restorable backup.
- [ ] Record backup identifier and timestamp.
- [ ] Confirm expected restore duration.
- [ ] Confirm uploaded files are included in backup or separately copied.
- [ ] Confirm rollback owner can restore the backup if needed.

**Do not proceed without a confirmed backup.**

## 4. Provider configuration verification

### Render

- [ ] Correct repository and `main` branch.
- [ ] Correct root directory and Dockerfile path.
- [ ] Docker build succeeds.
- [ ] `SPRING_PROFILES_ACTIVE=mysql`.
- [ ] `SPRING_DATASOURCE_URL` present.
- [ ] `SPRING_DATASOURCE_USERNAME` present.
- [ ] `SPRING_DATASOURCE_PASSWORD` present.
- [ ] `JWT_SECRET` present and not the development default.
- [ ] `APP_CORS_ALLOWED_ORIGINS` includes the exact production frontend origin.
- [ ] `FRONTEND_URL` is the production frontend origin.
- [ ] `FILE_UPLOAD_DIR=/app/uploads/datasets`.
- [ ] Persistent disk mounted at `/app/uploads`.
- [ ] Health-check path is `/api/health`.
- [ ] Auto-deploy setting matches the approved process.

### Vercel

- [ ] Project root is `frontend`.
- [ ] Build command is `npm run build`.
- [ ] Output directory is `dist`.
- [ ] Production branch is `main`.
- [ ] `VITE_API_BASE_URL=https://autoscientist-ai-backend.onrender.com/api`.
- [ ] SPA rewrite from `frontend/vercel.json` is active.
- [ ] Production domain and aliases are correct.
- [ ] Auto-deploy setting matches the approved process.

### Aiven

- [ ] Production database identified.
- [ ] TLS mode confirmed.
- [ ] Application user confirmed.
- [ ] Migration privileges confirmed.
- [ ] Render network access confirmed.
- [ ] Connection limit adequate for the configured pool.
- [ ] Current `flyway_schema_history` state recorded.
- [ ] Backup and restore procedure confirmed.

## 5. Deployment

### Backend

- [ ] Confirm the exact commit being deployed.
- [ ] Start Render deployment.
- [ ] Build completes successfully.
- [ ] Startup log shows `mysql` profile.
- [ ] Flyway validates existing migrations.
- [ ] Only expected migrations apply.
- [ ] Hibernate schema validation succeeds.
- [ ] Upload directory initialization succeeds.
- [ ] Service remains running without restart loops.

### Frontend

- [ ] Confirm the exact commit being deployed.
- [ ] Vercel build completes successfully.
- [ ] Production deployment is assigned to the production domain.
- [ ] Built bundle references the Render API URL.
- [ ] Built bundle contains no localhost URL or demo credentials.
- [ ] Deep-link routing works.

## 6. Post-deployment smoke test

Use a controlled, non-destructive account.

### Public and security checks

- [ ] `GET /api/health` returns 200.
- [ ] `GET /api/health/ready` returns 200 after the candidate is deployed.
- [ ] Root `/` returns 404.
- [ ] Unauthenticated `/api/datasets` returns 401.
- [ ] Unauthenticated `/api/auth/me` returns 401.
- [ ] CORS from the production frontend origin succeeds.
- [ ] CORS from an unapproved origin fails.
- [ ] No secret or stack trace is exposed in responses.

### Authentication

- [ ] Controlled registration succeeds.
- [ ] Correct login succeeds.
- [ ] Wrong password returns 401.
- [ ] Invalid token returns 401.
- [ ] Case/whitespace email normalization works.
- [ ] Duplicate registration returns 400.
- [ ] Password over 72 UTF-8 bytes returns a clear 400.
- [ ] Login page does not show demo credentials in production.
- [ ] Password-reset behavior matches the configured SMTP setup.

### Data and analysis

- [ ] Upload a small CSV.
- [ ] Profiling completes with expected row/column counts.
- [ ] Data quality completes.
- [ ] EDA endpoints complete.
- [ ] Associations complete.
- [ ] Research question and hypothesis execute.
- [ ] AutoML training completes.
- [ ] Explanation and fairness data render.
- [ ] What-if and drift workflows complete.

### Reports and isolation

- [ ] Report generation completes.
- [ ] CSV download succeeds.
- [ ] Excel download succeeds.
- [ ] Markdown download succeeds.
- [ ] PDF download succeeds and opens.
- [ ] A second account receives 403 for the first account’s dataset.
- [ ] A second account receives 403 for the first account’s report.

### Persistence

- [ ] Restart backend and confirm datasets/reports remain listed.
- [ ] Confirm an uploaded file remains downloadable after restart.
- [ ] Confirm no duplicate or missing records appear.

## 7. Monitoring window

- [ ] Monitor backend logs for 30 minutes.
- [ ] Check 5xx rate.
- [ ] Check response latency.
- [ ] Check Render restarts and memory.
- [ ] Check database connections and slow queries.
- [ ] Check Flyway errors.
- [ ] Check Vercel build/runtime errors.
- [ ] Check frontend network calls return expected status codes.
- [ ] Confirm no user-reported login or upload failures.

## 8. Release decision

- [ ] All required checks pass.
- [ ] Any skipped check has an owner and accepted risk.
- [ ] Rollback path remains available.
- [ ] Release notes updated.
- [ ] Stakeholders notified.

| Decision | Name | Date/time | Notes |
|---|---|---|---|
| Go / No-go | | | |
| Backend | | | |
| Frontend | | | |
| Database | | | |

## 9. Current known limitations

- [ ] Aiven production access has not been verified.
- [ ] Render dashboard/API configuration has not been verified.
- [ ] Vercel dashboard/build settings have not been verified.
- [ ] Authenticated production smoke test has not been completed.
- [ ] Docker daemon was unavailable, so the image itself was not built locally. This is **not required** for Render, which builds the Docker image remotely; local Docker is optional pre-validation only.
- [ ] HTTP/2 health requests timed out; HTTP/1.1 is healthy. Re-verification showed an ~18.7 s first request followed by ~0.45 s, consistent with a Render free-tier cold start.
- [ ] Local upload persistence depends on the local filesystem; production requires the Render disk.

Do not deploy while the blocking limitations remain unresolved or explicitly risk-accepted.

## 10. Final release decision (2026-10-31)

### A. VERIFIED SAFE FOR LOCAL CANDIDATE

- [x] Only intended files changed; no unrelated files.
- [x] `git diff --check` clean; no secrets, `.env`, uploads, backups, or generated artifacts in the candidate.
- [x] Java 17 package build passes (`Build-Jdk-Spec: 17`).
- [x] Frontend production build passes; bundle uses the Render API URL with no localhost URL or demo credentials.
- [x] Migrations reviewed: no destructive operations on user data; legacy paths guarded and additive/backfilling.
- [x] No production database, provider configuration, or persistent storage modified.

### B. REQUIRES EXTERNAL AUTHORIZATION

- [ ] Approval to commit and push.
- [ ] Approval to deploy backend and frontend.
- [ ] Read-only provider verification (Render, Vercel, Aiven).
- [ ] Aiven backup and schema-history confirmation.
- [ ] Authenticated production smoke-test approval.

### C. REQUIRES MANUAL ACCOUNT ACTION

- [ ] Render settings confirmed by an operator (source/branch, root + Dockerfile, `mysql` profile, env names, disk, health path, auto-deploy).
- [ ] Vercel settings confirmed by an operator (root, build, output, Production `VITE_API_BASE_URL`, rewrite, auto-deploy, domain).
- [ ] Aiven settings confirmed by an operator (version, TLS, privileges, backup, network, limits, `flyway_schema_history`).
- [ ] Controlled production test account supplied.
- [ ] Optional: start Docker Desktop for local image pre-validation.

### D. BLOCKED

- [ ] Aiven production verification.
- [ ] Authenticated production smoke test.
- [ ] Render and Vercel dashboard configuration verification.
- [ ] Local Docker image build (non-blocking).

### E. READY FOR DEPLOYMENT APPROVAL

The local candidate is ready for deployment approval. Deployment remains blocked until B, C, and D are resolved or explicitly risk-accepted and the approvals in Section 1 are recorded.

### Minimum actions to authorize

1. Commit + push the reviewed candidate to `main`.
2. Read-only Render/Vercel/Aiven verification and Aiven backup/schema confirmation.
3. Backend and frontend deployment after verification.
4. Authenticated production smoke test with a controlled account.
5. Optional local Docker image pre-validation.
