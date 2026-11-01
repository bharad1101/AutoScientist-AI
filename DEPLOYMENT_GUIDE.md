# AutoScientist AI — Production Deployment Guide

> **Branch:** `fix-dataset-storage-reliability`  
> **Last verified commit:** see `git log --oneline -1`  
> **Status:** ✅ Ready to deploy after completing the Cloudflare R2 and Render steps below

---

## Table of Contents

1. [Architecture overview](#1-architecture-overview)
2. [Root cause of the storage problem](#2-root-cause-of-the-storage-problem)
3. [Solution: Cloudflare R2](#3-solution-cloudflare-r2)
4. [Step-by-step: Cloudflare R2 setup](#4-step-by-step-cloudflare-r2-setup)
5. [Step-by-step: Render environment variables](#5-step-by-step-render-environment-variables)
6. [Step-by-step: GitHub push and merge](#6-step-by-step-github-push-and-merge)
7. [Vercel — no changes required](#7-vercel--no-changes-required)
8. [Post-deployment verification checklist](#8-post-deployment-verification-checklist)
9. [How to verify dataset persistence after a redeployment](#9-how-to-verify-dataset-persistence-after-a-redeployment)
10. [Rollback procedure](#10-rollback-procedure)
11. [Environment variable reference](#11-environment-variable-reference)
12. [Security notes](#12-security-notes)
13. [Changed files summary](#13-changed-files-summary)
14. [Test results](#14-test-results)

---

## 1. Architecture overview

| Layer | Technology | Host |
|-------|-----------|------|
| Frontend | React 18 + Vite + Bootstrap 5 | Vercel |
| Backend API | Java 21 + Spring Boot 3 | Render (free) |
| Database | MySQL 8.4 | Aiven |
| Dataset storage | **Cloudflare R2** (S3-compatible) | Cloudflare |

The backend is deployed as a Docker container on Render's free tier. Render's free containers have an **ephemeral filesystem** — any files written to disk are lost on every redeployment or instance restart. Dataset files are now stored in Cloudflare R2, which persists across all Render container lifecycle events.

---

## 2. Root cause of the storage problem

`FileStorageService` previously stored uploaded CSV/Excel files at `/app/uploads/datasets` inside the Docker container. When Render redeploys the container (on every push, or when the instance is recycled), the container filesystem is wiped clean. MySQL metadata (file names, column types, row counts) survived — but the physical files did not. Every feature that reads a dataset file crashed with:

```
BadRequestException: File does not exist or access is restricted.
```

---

## 3. Solution: Cloudflare R2

A pluggable `StorageBackend` abstraction was added with two implementations:

| Backend | `STORAGE_BACKEND` value | Use case |
|---------|------------------------|----------|
| `LocalStorageBackend` | `local` | Development (H2) or Render Persistent Disk |
| `S3StorageBackend` | `s3` | **Production** — Cloudflare R2, AWS S3, MinIO |

The `FileStorageService` public API is identical to before — all downstream services (`AutoML`, `EDA`, `Drift`, `Ask Dataset`, `Association`, `Research`) require zero modification.

**Cloudflare R2 free tier** (as of 2025):
- 10 GB storage/month
- 10 million Class B reads (GET, HEAD) per month
- 1 million Class A writes (PUT, DELETE) per month
- No egress fees when accessed via the S3 API from your own application
- No credit card required for free tier

---

## 4. Step-by-step: Cloudflare R2 setup

> ⚠️ **You must do this yourself in the Cloudflare dashboard** — the agent cannot create cloud accounts or buckets on your behalf.

### 4a. Create a Cloudflare account

If you don't have one, go to [dash.cloudflare.com](https://dash.cloudflare.com) and sign up (free).

### 4b. Create an R2 bucket

1. In the Cloudflare dashboard, click **R2 Object Storage** in the left sidebar.
2. Click **Create bucket**.
3. Bucket name: `autoscientist-datasets` (or any name you prefer — use the same name in the env var below).
4. Location: choose the region closest to your users (or leave as default).
5. Click **Create bucket**.

### 4c. Note your Account ID

Your Account ID is visible in the URL when you are in the R2 dashboard:
```
https://dash.cloudflare.com/<ACCOUNT_ID>/r2/...
```
Copy this 32-character hex string — you will need it for the endpoint URL.

### 4d. Create an R2 API token

1. In the R2 dashboard, click **Manage R2 API Tokens** (top-right area).
2. Click **Create API Token**.
3. Token name: `autoscientist-render` (any descriptive name).
4. Permissions: **Object Read & Write**.
5. Bucket: restrict to **Specific bucket** → select `autoscientist-datasets`.
6. Click **Create API Token**.
7. **Copy both values immediately** — they are shown only once:
   - **Access Key ID** → this is `STORAGE_S3_ACCESS_KEY`
   - **Secret Access Key** → this is `STORAGE_S3_SECRET_KEY`

> ⚠️ Store these in a password manager. They cannot be retrieved again.

---

## 5. Step-by-step: Render environment variables

> ⚠️ **You must do this in the Render dashboard.** Never paste secrets into chat or commit them to Git.

1. Go to [dashboard.render.com](https://dashboard.render.com).
2. Select your **autoscientist-ai-backend** service.
3. Click **Environment** in the left menu.
4. Add or update the following variables:

| Variable | Value |
|----------|-------|
| `STORAGE_BACKEND` | `s3` |
| `STORAGE_S3_BUCKET` | `autoscientist-datasets` (your bucket name) |
| `STORAGE_S3_ENDPOINT` | `https://<YOUR_ACCOUNT_ID>.r2.cloudflarestorage.com` |
| `STORAGE_S3_REGION` | `auto` |
| `STORAGE_S3_ACCESS_KEY` | *(your R2 access key ID from step 4d)* |
| `STORAGE_S3_SECRET_KEY` | *(your R2 secret access key from step 4d)* |
| `FILE_UPLOAD_DIR` | *(can be removed — it is unused when `STORAGE_BACKEND=s3`)* |

> **Replace `<YOUR_ACCOUNT_ID>`** with the 32-character Account ID from step 4c.

5. After setting all variables, click **Save Changes**.
6. Render will automatically trigger a new deployment.

### What happens when the backend starts

1. Spring Boot reads `STORAGE_BACKEND=s3`.
2. `StorageConfig` sees valid S3 credentials and constructs an `S3StorageBackend`.
3. `FileStorageService` logs: `FileStorageService ready — backend: s3://autoscientist-datasets`
4. The `GET /api/health/ready` endpoint runs a **HeadBucket** check against R2.
5. If R2 is reachable, readiness returns `200 UP`.

The container starts successfully **without** any persistent disk mounted, because `docker-entrypoint.sh` skips the local filesystem write-check when `STORAGE_BACKEND=s3`.

---

## 6. Step-by-step: GitHub push and merge

The feature branch is already pushed to GitHub. Once you have verified the deployment:

```bash
# Verify branch is up to date
git log --oneline origin/fix-dataset-storage-reliability | head -5

# Option A: Create a Pull Request on GitHub (recommended)
# → Go to https://github.com/bharad1101/AutoScientist-AI
# → Click "Compare & pull request" for fix-dataset-storage-reliability
# → Review → Merge

# Option B: Fast-forward merge locally (only if you have checked it yourself)
git checkout main
git merge --ff-only fix-dataset-storage-reliability
git push origin main
```

> ⛔ **Do not force-push. Do not push directly to main without reviewing the diff.**

---

## 7. Vercel — no changes required

The frontend connects to the backend via `VITE_API_BASE_URL` (set at build time in Vercel). The storage change is entirely backend-side. No Vercel changes are needed.

The frontend has already been improved with:
- **2-minute upload timeout** (axios) — prevents requests hanging forever
- **Double-click submit protection** — prevents duplicate uploads
- **150-second safety timer** — auto-resets the spinner with an informative message if the server never responds
- **Meaningful error messages** — distinguishes timeout from server errors

---

## 8. Post-deployment verification checklist

After Render redeploys with the new environment variables:

- [ ] **Health liveness:** `curl https://autoscientist-ai-backend.onrender.com/api/health`  
  Expected: `{"success":true,"data":{"status":"UP","timestamp":"..."}}`

- [ ] **Health readiness:** `curl https://autoscientist-ai-backend.onrender.com/api/health/ready`  
  Expected: `{"success":true,"data":{"status":"UP","database":"UP","storage":"UP","storageBackend":"s3://autoscientist-datasets","timestamp":"..."}}`

- [ ] **Upload a dataset:** Log in to the frontend → Datasets → Import  
  Upload a small CSV (e.g., 5 rows × 3 columns). It should complete in < 30 seconds.

- [ ] **Run EDA** on the uploaded dataset.

- [ ] **Trigger a Render manual deploy** (or push a trivial commit).

- [ ] **After redeploy, open the dataset again** — EDA, AutoML, and Ask Dataset should still work.  
  This proves the file survived the redeployment (it is stored in R2, not the container disk).

- [ ] **Try AutoML** on the same dataset.

- [ ] **Try Ask Dataset** on the same dataset.

---

## 9. How to verify dataset persistence after a redeployment

This is the key test that proves the storage fix works:

1. Upload a dataset via the frontend.
2. Note the dataset ID (visible in the URL, e.g., `/datasets/42`).
3. Note the `stored_filename` UUID (visible via `GET /api/datasets/42` response, or in the Aiven MySQL console).
4. In the Render dashboard: **Manual Deploy** → **Deploy latest commit** (or wait for a push-triggered deploy).
5. Wait for the new container to start (Render health check passes).
6. Open the dataset in the frontend.
7. Run EDA or AutoML. If it succeeds, the file was loaded from R2 — **not the container disk**.
8. Optionally verify in the Cloudflare R2 dashboard: Objects tab → you should see the UUID-named file.

**Before this fix:** step 7 would produce `BadRequestException: File does not exist or access is restricted.`  
**After this fix:** step 7 succeeds every time.

---

## 10. Rollback procedure

### If the deployment fails immediately (container won't start)

1. In Render: **Manual Deploy** → select the previous successful deploy commit hash.
2. That restores the `main` branch behaviour (local filesystem — files will be lost on next redeploy, but the app will start).
3. Remove the S3 env vars added in step 5.

### If R2 credentials are wrong (container starts but storage fails)

`GET /api/health/ready` will return `503 DOWN` with `"storage":"DOWN"`.

1. Verify the bucket name, endpoint, access key, and secret key in Render.
2. Check the Render logs for `S3StorageBackend initialised` — it should log `bucket=autoscientist-datasets, region=auto`.
3. Verify in Cloudflare: R2 → `autoscientist-datasets` bucket exists and the API token has `Object Read & Write` permission on it.
4. Update the env vars in Render → Save → the service will redeploy.

### Git rollback (last resort)

```bash
# Revert to main (pre-fix) on the branch — this does NOT alter main
git revert HEAD --no-edit
git push origin fix-dataset-storage-reliability
```

---

## 11. Environment variable reference

These are the **exact** environment variable names used in the source code, verified against `application.yml` and `StorageConfig.java`:

| Variable | Required | Default | Description |
|----------|----------|---------|-------------|
| `STORAGE_BACKEND` | No | `local` | `s3` or `local` |
| `STORAGE_S3_BUCKET` | If S3 | — | R2 bucket name |
| `STORAGE_S3_ENDPOINT` | If S3 | — | `https://<ACCOUNT_ID>.r2.cloudflarestorage.com` |
| `STORAGE_S3_REGION` | If S3 | `auto` | Use `auto` for Cloudflare R2 |
| `STORAGE_S3_ACCESS_KEY` | If S3 | — | R2 Access Key ID |
| `STORAGE_S3_SECRET_KEY` | If S3 | — | R2 Secret Access Key |
| `FILE_UPLOAD_DIR` | If local | `/app/uploads/datasets` | Local filesystem path; unused when S3 |
| `SPRING_DATASOURCE_URL` | Yes | — | Aiven MySQL JDBC URL |
| `SPRING_DATASOURCE_USERNAME` | Yes | — | DB username |
| `SPRING_DATASOURCE_PASSWORD` | Yes | — | DB password |
| `JWT_SECRET` | Yes | — | Base64-encoded JWT signing key (≥ 64 bytes) |
| `APP_CORS_ALLOWED_ORIGINS` | Yes | — | e.g. `https://auto-scientist-ai-ten.vercel.app` |
| `FRONTEND_URL` | Yes | — | e.g. `https://auto-scientist-ai-ten.vercel.app` |

> When `STORAGE_BACKEND=s3`, setting `FILE_UPLOAD_DIR` is **optional** — if omitted it defaults to `/app/uploads/datasets` which is never used.

---

## 12. Security notes

- ✅ No credentials are hardcoded in any source file.
- ✅ No credentials appear in Git history (`git log -p | grep -i "secret\|access.key\|password"` returns nothing from the storage code).
- ✅ `S3StorageBackend` logs only `bucket` and `region` at startup — **never** logs endpoint, access key, or secret key.
- ✅ Exception messages are sanitised before logging to remove potential credential fragments.
- ✅ R2 API token should be scoped to a **single bucket** with **Object Read & Write** only — do not use account-level tokens.
- ✅ Path traversal protection is enforced: keys containing `..`, `/`, or `\` are rejected before hitting the storage backend.
- ✅ File type validation: only `.csv`, `.xls`, `.xlsx` are accepted.
- ✅ File size limit: 50 MB (Spring multipart config in `application.yml`).
- ✅ JWT and authentication configuration is unchanged.
- ✅ CORS configuration is unchanged.
- ✅ `.env` files are excluded from Git via `.gitignore`.

---

## 13. Changed files summary

All changes are on branch `fix-dataset-storage-reliability`. No changes to `main`.

| File | Change |
|------|--------|
| `backend/pom.xml` | Added `software.amazon.awssdk:s3:2.31.60` dependency |
| `backend/src/main/java/.../util/StorageBackend.java` | Added `checkAccessible()` to interface (non-destructive health probe) |
| `backend/src/main/java/.../util/LocalStorageBackend.java` | Implemented `checkAccessible()` via directory stat |
| `backend/src/main/java/.../util/S3StorageBackend.java` | Implemented `checkAccessible()` via HeadBucket; sanitised error logs |
| `backend/src/main/java/.../config/StorageConfig.java` | New `@Bean` that selects local vs S3 based on env vars |
| `backend/src/main/java/.../util/FileStorageService.java` | Delegating implementation; `isHealthy()` now uses non-destructive `checkAccessible()` |
| `backend/src/main/java/.../controller/HealthController.java` | `/api/health/ready` checks DB + storage; returns `storageBackend` description |
| `backend/src/main/resources/application.yml` | Added `app.storage.*` config; gave `FILE_UPLOAD_DIR` a safe default in mysql profile |
| `backend/docker-entrypoint.sh` | Skips writability check when `STORAGE_BACKEND=s3` |
| `backend/.env.example` | Full documentation of all storage env vars with setup instructions |
| `backend/src/test/java/.../StorageBackendTest.java` | 14 tests covering storage interface, health, path-traversal, non-destructive probe |
| `frontend/src/services/datasetService.js` | Upload timeout 120s; preview timeout 60s |
| `frontend/src/components/ImportWizard.jsx` | Double-click guard; 150s safety timer; meaningful error messages |

---

## 14. Test results

Verified on commit in branch `fix-dataset-storage-reliability`:

| Check | Result |
|-------|--------|
| `./mvnw clean test` | ✅ **130 tests, 0 failures, 0 errors, 2 skipped** |
| `./mvnw clean package` | ✅ **BUILD SUCCESS** — `autoscientist-backend-1.0.0-SNAPSHOT.jar` |
| `npm run build` | ✅ **129 modules, 0 errors** |
| Secret scan | ✅ No hardcoded credentials |
| R2 live integration test | ⚠️ **Not run** — requires live R2 credentials. Verify via the readiness endpoint after deploying. |

The 2 skipped tests are `PasswordResetIntegrationTest` variants that require SMTP transport — expected and unchanged from the main branch.
