# AutoScientist AI — Production Deployment Guide

> **Branch:** `fix-dataset-storage-reliability`  
> **Last verified commit:** see `git log --oneline -1`  
> **Status:** ✅ Ready to deploy

---

## Table of Contents

1. [Architecture overview](#1-architecture-overview)
2. [Dataset storage and the ephemeral filesystem](#2-dataset-storage-and-the-ephemeral-filesystem)
3. [Step-by-step: Render environment variables](#3-step-by-step-render-environment-variables)
4. [Step-by-step: GitHub push and merge](#4-step-by-step-github-push-and-merge)
5. [Vercel — no changes required](#5-vercel--no-changes-required)
6. [Post-deployment verification checklist](#6-post-deployment-verification-checklist)
7. [Rollback procedure](#7-rollback-procedure)
8. [Environment variable reference](#8-environment-variable-reference)
9. [Security notes](#9-security-notes)
10. [Changed files summary](#10-changed-files-summary)
11. [Test results](#11-test-results)

---

## 1. Architecture overview

| Layer | Technology | Host |
|-------|-----------|------|
| Frontend | React 18 + Vite + Bootstrap 5 | Vercel |
| Backend API | Java 17 + Spring Boot 3 | Render (free Docker) |
| Database | MySQL 8.4 | Aiven |
| Dataset file storage | **Local filesystem** (`/app/uploads/datasets`) | Render container |

The backend is deployed as a Docker container on Render's free tier. Dataset
metadata (file names, column types, row counts) is stored permanently in the
Aiven MySQL database. Physical dataset files (CSV / Excel) are stored on the
container's local filesystem.

---

## 2. Dataset storage and the ephemeral filesystem

### How it works

When a user uploads a CSV or Excel file, `FileStorageService` stores it at
`/app/uploads/datasets/<UUID>.<ext>` inside the Docker container and records
the UUID reference in MySQL.

### ⚠️ Known limitation — ephemeral filesystem

**Render's free tier uses an ephemeral filesystem.** This means:

- Uploaded dataset files **do NOT survive** redeployments, instance restarts,
  or container recycling.
- After any deployment event, MySQL metadata for previously uploaded datasets
  will still exist, but the physical files will be gone.
- Features that require the physical file (EDA, AutoML, Ask Dataset, Data Drift,
  Association Analysis) will return a friendly error:
  > *"Dataset file is no longer available on the server. This can happen after a
  > server redeployment. Please re-upload the dataset."*

### What users should do

- **Re-upload datasets** after any deployment or if the error above appears.
- For a portfolio demo, upload datasets right before showing the application.
- The MySQL metadata (column definitions, dataset name, etc.) is permanent and
  never lost.

### Why this is acceptable

This is a portfolio / demonstration project deployed on free infrastructure.
The ephemeral limitation does not affect the application's correctness — it is
a platform constraint of Render's free tier, not a bug.

---

## 3. Step-by-step: Render environment variables

> ⚠️ **You must do this in the Render dashboard.** Never commit secrets to Git.

1. Go to [dashboard.render.com](https://dashboard.render.com).
2. Select your **autoscientist-ai-backend** service.
3. Click **Environment** in the left menu.
4. Verify or set the following variables:

| Variable | Value |
|----------|-------|
| `SPRING_PROFILES_ACTIVE` | `mysql` |
| `SPRING_DATASOURCE_URL` | `jdbc:mysql://<aiven-host>:<port>/<database>?sslMode=REQUIRED&serverTimezone=UTC` |
| `SPRING_DATASOURCE_USERNAME` | *(your Aiven DB username)* |
| `SPRING_DATASOURCE_PASSWORD` | *(your Aiven DB password)* |
| `JWT_SECRET` | *(base64-encoded, ≥ 64 bytes decoded)* |
| `APP_CORS_ALLOWED_ORIGINS` | `https://auto-scientist-ai-ten.vercel.app` |
| `FRONTEND_URL` | `https://auto-scientist-ai-ten.vercel.app` |
| `FILE_UPLOAD_DIR` | `/app/uploads/datasets` *(already set in Dockerfile; optional override)* |

**Variables to REMOVE (no longer used):**

| Variable | Reason |
|----------|--------|
| `STORAGE_BACKEND` | Removed — only local storage exists now |
| `STORAGE_S3_BUCKET` | Removed — S3 backend was deleted |
| `STORAGE_S3_ENDPOINT` | Removed |
| `STORAGE_S3_REGION` | Removed |
| `STORAGE_S3_ACCESS_KEY` | Removed |
| `STORAGE_S3_SECRET_KEY` | Removed |

5. Click **Save Changes**. Render will trigger a new deployment.

### What happens when the backend starts

1. `docker-entrypoint.sh` creates `/app/uploads/datasets` and verifies it is writable.
2. Spring Boot reads `FILE_UPLOAD_DIR=/app/uploads/datasets`.
3. `StorageConfig` creates a `LocalStorageBackend` pointing at that directory.
4. `FileStorageService` logs: `FileStorageService ready — backend: local:/app/uploads/datasets`
5. `GET /api/health/ready` checks MySQL connectivity and upload directory accessibility.

---

## 4. Step-by-step: GitHub push and merge

The feature branch should be pushed to GitHub first:

```bash
# Verify what will be pushed
git log --oneline origin/fix-dataset-storage-reliability..HEAD

# Push the branch
git push origin fix-dataset-storage-reliability
```

Then merge via GitHub Pull Request:

1. Go to https://github.com/bharad1101/AutoScientist-AI
2. Click **Compare & pull request** for `fix-dataset-storage-reliability`
3. Review the diff
4. Merge into `main`

> ⛔ **Do not force-push. Do not push directly to main without reviewing.**

---

## 5. Vercel — no changes required

The frontend connects to the backend via `VITE_API_BASE_URL` (set as a build-time
environment variable in the Vercel dashboard). The storage simplification is
entirely backend-side. No Vercel changes are needed.

Verify this variable is set in Vercel:
```
VITE_API_BASE_URL=https://autoscientist-ai-backend.onrender.com/api
```

---

## 6. Post-deployment verification checklist

After Render redeploys:

- [ ] **Health liveness:** `curl https://autoscientist-ai-backend.onrender.com/api/health`  
  Expected: `{"success":true,"data":{"status":"UP",...}}`

- [ ] **Health readiness:** `curl https://autoscientist-ai-backend.onrender.com/api/health/ready`  
  Expected: `{"success":true,"data":{"status":"UP","database":"UP","storage":"UP","storageBackend":"local:/app/uploads/datasets",...}}`

- [ ] **Register / Login:** Create an account or log in at the frontend.

- [ ] **Upload a dataset:** Datasets → Import → Upload a small CSV.

- [ ] **Preview data:** Verify columns and rows display correctly.

- [ ] **Run EDA** on the uploaded dataset.

- [ ] **Run AutoML** on the uploaded dataset.

- [ ] **Try Ask Dataset** on the uploaded dataset.

---

## 7. Rollback procedure

### If the deployment fails (container won't start)

1. In Render: **Manual Deploy** → select the previous successful deploy commit.
2. That restores the `main` branch behaviour.

### Git rollback (last resort)

```bash
git revert HEAD --no-edit
git push origin fix-dataset-storage-reliability
```

---

## 8. Environment variable reference

These are the **exact** environment variable names used in the source code,
verified against `application.yml` and `StorageConfig.java`:

| Variable | Required | Default | Description |
|----------|----------|---------|-------------|
| `FILE_UPLOAD_DIR` | No | `/app/uploads/datasets` (Docker) | Local filesystem path for dataset files |
| `SPRING_DATASOURCE_URL` | Yes (mysql) | — | Aiven MySQL JDBC URL |
| `SPRING_DATASOURCE_USERNAME` | Yes (mysql) | — | DB username |
| `SPRING_DATASOURCE_PASSWORD` | Yes (mysql) | — | DB password |
| `JWT_SECRET` | Yes (mysql) | — | Base64-encoded JWT signing key (≥ 64 bytes) |
| `APP_CORS_ALLOWED_ORIGINS` | Yes (mysql) | — | e.g. `https://auto-scientist-ai-ten.vercel.app` |
| `FRONTEND_URL` | Yes | `http://localhost:5173` | Password reset redirect base URL |
| `SPRING_PROFILES_ACTIVE` | Yes | `dev` | Use `mysql` for production |
| `PORT` | No | `8080` | HTTP listen port |

---

## 9. Security notes

- ✅ No credentials are hardcoded in any source file.
- ✅ No S3/R2 secrets exist anywhere in the codebase.
- ✅ `.env` files are excluded from Git via `.gitignore`.
- ✅ Path traversal protection is enforced: keys containing `..`, `/`, or `\` are rejected.
- ✅ File type validation: only `.csv`, `.xls`, `.xlsx` are accepted.
- ✅ File size limit: 50 MB (Spring multipart config in `application.yml`).
- ✅ JWT and authentication configuration is unchanged.
- ✅ CORS configuration is unchanged.

---

## 10. Changed files summary

All changes are on branch `fix-dataset-storage-reliability` relative to `main`.

| File | Change |
|------|--------|
| `backend/pom.xml` | **Removed** `software.amazon.awssdk:s3` dependency |
| `backend/src/.../util/S3StorageBackend.java` | **Deleted** — S3 backend removed |
| `backend/src/.../config/StorageConfig.java` | Simplified to local-only (removed S3 selection logic) |
| `backend/src/.../util/StorageBackend.java` | Cleaned javadoc (removed S3 references) |
| `backend/src/.../util/FileStorageService.java` | Cleaned javadoc (removed S3 references) |
| `backend/src/main/resources/application.yml` | **Removed** `app.storage.*` config block (S3 keys) |
| `backend/docker-entrypoint.sh` | Simplified: removed `STORAGE_BACKEND` check and S3 bypass |
| `backend/.env.example` | Rewritten for local-only storage with ephemeral limitation note |
| `Dockerfile` | Removed misleading `VOLUME ["/app/uploads"]` directive |
| `DEPLOYMENT_GUIDE.md` | Rewritten for Render local-only architecture |

Files **unchanged** from `main` (verified correct as-is):

| File | Reason |
|------|--------|
| `backend/src/.../util/LocalStorageBackend.java` | Already implements local storage correctly |
| `backend/src/.../controller/HealthController.java` | Already reports local storage health |
| `frontend/` (all files) | No storage-related changes needed |

---

## 11. Test results

Verified locally after all S3 removal changes:

| Check | Result |
|-------|--------|
| `./mvnw clean test` | ✅ **130 tests, 0 failures, 0 errors, 2 skipped** |
| `./mvnw clean package` | ✅ **BUILD SUCCESS** — `autoscientist-backend-1.0.0-SNAPSHOT.jar` |
| `npm run build` | ✅ **129 modules, 0 errors** |
| Secret scan | ✅ No hardcoded credentials |

The 2 skipped tests are `PasswordResetIntegrationTest` variants that require
SMTP transport — expected and unchanged from the `main` branch.
