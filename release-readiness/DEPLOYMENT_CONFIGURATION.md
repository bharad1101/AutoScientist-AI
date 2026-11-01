# Deployment Configuration

- **Assessment date:** 2026-10-31
- **Repository:** `bharad1101/AutoScientist-AI`
- **Production branch:** `main`
**Document scope:** Intended provider configuration based on repository files and observed live behavior. This document does not prove that provider dashboards currently match it.

## 1. Observed production endpoints

| Component | URL / identifier |
|---|---|
| Frontend production origin | `https://auto-scientist-ai-ten.vercel.app` |
| Frontend deployment alias observed | `https://auto-scientist-ls6spj9p4-auto-scientist-ai.vercel.app` |
| Backend origin | `https://autoscientist-ai-backend.onrender.com` |
| Backend API base | `https://autoscientist-ai-backend.onrender.com/api` |
| Backend health | `/api/health` |
| Backend readiness (candidate only) | `/api/health/ready` |
| Current production commit | `d13e6c6` |

The readiness endpoint and login/demo-credential fixes exist in the local candidate but are **not deployed** until an approved release occurs.

## 2. Repository deployment assets

| Asset | Purpose |
|---|---|
| `Dockerfile` | Backend image for repository-root build context |
| `backend/Dockerfile` | Backend image for `backend/` build context |
| `backend/docker-entrypoint.sh` | Fixes disk ownership, drops privileges, forwards `PORT` |
| `frontend/vercel.json` | SPA rewrite to `/index.html` |
| `backend/src/main/resources/application.yml` | Default and `mysql`/`dev` profile configuration |
| `backend/src/main/resources/db/migration/` | Flyway migrations `V1`–`V4` |
| `.dockerignore` | Excludes Git, secrets, builds, uploads, backups, and data |

There is no `render.yaml` and no GitHub Actions workflow. Render and Vercel must be configured through their dashboards/APIs.

## 3. Backend — Render

### 3.1 Service source

Recommended configuration:

- Provider: Render web service.
- Repository: `bharad1101/AutoScientist-AI`.
- Branch: `main`.
- Auto-deploy: enabled only after the release plan is approved.
- Runtime: Docker.
- Health check path: `/api/health`.

Choose one consistent build layout:

**Option A — repository root context**

- Root directory: repository root.
- Dockerfile path: `Dockerfile`.
- Build context: repository root.

**Option B — backend context**

- Root directory: `backend`.
- Dockerfile path: `Dockerfile`.
- Build context: `backend`.

Do not mix the root Dockerfile with a `backend` context or the backend Dockerfile with a repository-root context.

### 3.2 Runtime behavior

- Java: 17 (Eclipse Temurin JRE in the image).
- Default profile in the image: `mysql`.
- Container port: `8080`.
- Render supplies `PORT`; the entrypoint forwards it as `SERVER_PORT`.
- Uploads: `/app/uploads/datasets`, under the persistent disk.
- The image runs as uid `10001`; the entrypoint repairs mounted-disk ownership when started as root, then drops privileges.
- Graceful shutdown is enabled.

### 3.3 Required environment variable names

Set these in Render. Secret values must be entered through Render’s secret/environment UI and never committed.

| Variable | Required | Intended value / notes |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | Yes | `mysql` |
| `SPRING_DATASOURCE_URL` | Yes | Aiven JDBC URL, including required TLS options and timezone |
| `SPRING_DATASOURCE_USERNAME` | Yes | Dedicated application/migration user |
| `SPRING_DATASOURCE_PASSWORD` | Yes | Secret password |
| `JWT_SECRET` | Yes | Fresh Base64 value encoding at least 64 random bytes; generate outside the repo with `openssl rand -base64 64 \| tr -d '\n'` |
| `APP_CORS_ALLOWED_ORIGINS` | Yes | Exact frontend origin(s), comma separated; include `https://auto-scientist-ai-ten.vercel.app` |
| `FRONTEND_URL` | Yes | `https://auto-scientist-ai-ten.vercel.app` |
| `FILE_UPLOAD_DIR` | Yes | `/app/uploads/datasets` |
| `SPRING_FLYWAY_ENABLED` | Yes | `true` |
| `SPRING_JPA_HIBERNATE_DDL_AUTO` | Yes | `validate` |
| `SPRING_MAIL_HOST` | Conditional | Required for delivered password-reset email |
| `SPRING_MAIL_PORT` | Conditional | SMTP port |
| `SPRING_MAIL_USERNAME` | Conditional | SMTP username |
| `SPRING_MAIL_PASSWORD` | Conditional | SMTP secret |
| `PASSWORD_RESET_FROM` | Conditional | Sender accepted by the SMTP provider |
| `PORT` | Provider-managed | Render injects it; do not hard-code in the dashboard |
| `JWT_EXPIRATION_MS` | Optional | Defaults to `86400000` |
| `DB_POOL_MAX_SIZE` | Optional | Defaults to `10` |
| `DB_POOL_MIN_IDLE` | Optional | Defaults to `2` |
| `DB_CONNECTION_TIMEOUT_MS` | Optional | Defaults to `30000` |

Do not use `SPRING_JPA_HIBERNATE_DDL_AUTO=update` in production. Flyway owns schema changes; Hibernate validates only.

### 3.4 Persistent disk

- Mount a persistent disk at `/app/uploads`.
- Keep `FILE_UPLOAD_DIR=/app/uploads/datasets`.
- Confirm the mounted disk is writable by uid `10001` or that the container starts as root long enough for the entrypoint to repair ownership.
- Dataset metadata lives in MySQL; uploaded files live on this disk. Back up and restore both together.
- Multi-instance deployment is not supported by the local-filesystem storage design unless all instances share the same volume.

### 3.5 TLS and database access

- Use the Aiven JDBC URL from the provider’s connection details.
- Prefer `sslMode=VERIFY_CA` or `sslMode=VERIFY_IDENTITY` when the Aiven CA is available; `sslMode=REQUIRED` is the minimum.
- Include `serverTimezone=UTC`.
- Confirm Render can reach Aiven on the provider-issued port.
- Confirm the database user has enough privilege for Flyway but is not a general superuser.

## 4. Frontend — Vercel

### 4.1 Project configuration

- Repository: `bharad1101/AutoScientist-AI`.
- Root directory: `frontend`.
- Framework preset: Vite.
- Install command: `npm install` or `npm ci` when the lockfile is available.
- Build command: `npm run build`.
- Output directory: `dist`.
- Production branch: `main`.
- SPA rewrite: `frontend/vercel.json` rewrites all routes to `/index.html`.

### 4.2 Build-time environment

| Variable | Required | Value |
|---|---|---|
| `VITE_API_BASE_URL` | Yes | `https://autoscientist-ai-backend.onrender.com/api` |

This value is embedded at build time. Changing it requires a new frontend build and deployment.

The local build guard rejects:

- Missing values.
- Relative values.
- Non-HTTPS values.
- `localhost`, loopback, or `.localhost` hostnames.

A successful production build was verified locally with the value above. The bundle contained the Render API URL, no localhost API URL, and no development demo credentials.

### 4.3 Domains and CORS

- Production frontend origin: `https://auto-scientist-ai-ten.vercel.app`.
- The backend CORS allowlist must contain this exact scheme and host.
- Do not use `*` for CORS with authenticated requests.
- If a custom domain is added, add its exact origin to `APP_CORS_ALLOWED_ORIGINS` and update `FRONTEND_URL` before announcing the domain.

## 5. Database — Aiven MySQL

### 5.1 Intended setup

- MySQL-compatible service with TLS.
- Dedicated database for AutoScientist AI.
- Dedicated application user with migration privileges.
- Connection details stored only in Render secrets.
- Backups enabled and a pre-deployment backup available.
- Network access restricted to the Render service and authorized operators.

### 5.2 Schema management

- Flyway runs on backend startup with the `mysql` profile.
- `spring.flyway.enabled=true`.
- `baseline-on-migrate=true`.
- `baseline-version=1`.
- `validate-on-migrate=true`.
- Hibernate `ddl-auto=validate`.
- Fresh database: `V1`–`V4`.
- Legacy pre-Flyway database: baseline at `V1`, then `V2`–`V4`; data is preserved.
- Migrations are additive. There are no down-migrations.

Verified locally against MySQL 9.7.1:

- Fresh install: 4 migrations applied, schema validation passed.
- Legacy upgrade: baseline plus 3 additive migrations, existing user and dataset rows preserved, schema validation passed.

This does not prove the actual Aiven schema state. Inspect `flyway_schema_history` and take a backup before release.

### 5.3 Migration cautions

- MySQL DDL is non-transactional. A failed migration can leave partial changes.
- Do not manually drop tables, columns, indexes, or constraints as a shortcut.
- If a migration fails, stop the deployment and follow `ROLLBACK_PLAN.md`.
- Do not run `database/schema.sql` or the real-MySQL test against production.

## 6. Local production-build verification

```bash
cd frontend
VITE_API_BASE_URL='https://autoscientist-ai-backend.onrender.com/api' npm run build
grep -RIl 'jane@university.edu\|SecurePassword123' dist || echo 'developer credentials absent'
grep -RIl 'localhost:8080\|127.0.0.1' dist || echo 'localhost API absent'
```

Expected result: build succeeds, developer credentials are absent, localhost API is absent, and the Render API URL is present.

## 7. Configuration checks still requiring provider access

- [ ] Render root directory and Dockerfile path.
- [ ] Render branch and automatic deploy setting.
- [ ] Render env names and non-secret values.
- [ ] Render persistent disk and mount path.
- [ ] Render health-check path.
- [ ] Vercel root directory, build command, and output directory.
- [ ] Vercel Production `VITE_API_BASE_URL`.
- [ ] Vercel auto-deploy behavior.
- [ ] Aiven database identity, TLS, user privileges, backups, and network access.
- [ ] Actual `flyway_schema_history` state.

Do not change any provider setting without explicit approval.
