# AutoScientist AI — Production Deployment Guide

## 1. System Requirements & Architecture
- **Operating System**: Linux (Ubuntu 22.04 LTS / Debian 12 / RHEL 9), macOS, Windows WSL2.
- **Java Runtime**: JDK 17 or JDK 21 (OpenJDK / Eclipse Temurin).
- **Node.js**: Node 18.x or 20.x LTS + npm 9+.
- **Database**: MySQL 8.0+ (Production) or in-memory H2 (Dev / Testing fallback).
- **Memory**: Minimum 2 GB RAM (4 GB recommended for large ML models).
- **Disk**: 1 GB free disk space.

---

## 2. Environment Variables Configuration

Create `.env` in `backend/` and `frontend/` by copying the provided templates:

### Backend Configuration (`backend/.env`)
```bash
# Server Port
PORT=8080

# Staging/production must use MySQL. The dev profile is local-only.
SPRING_PROFILES_ACTIVE=mysql

# Database Configuration (MySQL 8.0+) — REQUIRED for the mysql profile (no defaults)
SPRING_DATASOURCE_URL=jdbc:mysql://localhost:3306/autoscientist_db?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC
SPRING_DATASOURCE_USERNAME=autoscientist_user
SPRING_DATASOURCE_PASSWORD=<your-database-password>

# JWT Security Secret — REQUIRED for the mysql profile (no default; startup fails fast).
# Must be base64 with NO line breaks. Generate a fresh key per environment:
#   openssl rand -base64 64 | tr -d '\n'
# Decoded length must be >= 64 bytes for HS512. Never reuse the dev default.
JWT_SECRET=<generated-base64-secret>
JWT_EXPIRATION_MS=86400000

# File Upload Storage Directory (wired to app.upload.dir)
FILE_UPLOAD_DIR=./uploads/datasets
```

> **Never commit real credentials.** `backend/.env` and `frontend/.env` are
> gitignored. The `dev` profile runs on local file-backed H2 and does not need these
> variables; the `mysql` profile refuses to start if any of
> `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`,
> `SPRING_DATASOURCE_PASSWORD` or `JWT_SECRET` is missing. The application now
> defaults to `mysql`, and the Docker image forces `mysql`; `dev` is refused on
> Render so a deployment cannot silently serve a different H2 account database.

### Frontend Configuration (`frontend/.env`)
```bash
# BUILD-TIME value. Set this in the deployment build environment (e.g. Vercel/Render).
# It must be an absolute HTTPS URL for the deployed backend, ending in /api.
VITE_API_BASE_URL=https://<your-backend-host>/api
```

> Local development does not need this variable: leaving it unset uses `/api`
> through Vite's dev proxy. Production builds fail fast if the value is missing,
> relative, non-HTTPS, or points to localhost/loopback.

---

## 3. Database Initialization (MySQL)

Log in to MySQL as root and initialize the schema:
```bash
mysql -u root -p < database/schema.sql
```
Create the application database user:
```sql
CREATE USER IF NOT EXISTS 'autoscientist_user'@'localhost' IDENTIFIED BY '<your-database-password>';
GRANT ALL PRIVILEGES ON autoscientist_db.* TO 'autoscientist_user'@'localhost';
FLUSH PRIVILEGES;
```

### 3.1 Schema strategy & migrations (durable)

- The schema is managed by **Flyway** (`backend/src/main/resources/db/migration/`),
  enabled automatically on the `mysql` profile (`spring.flyway.enabled=true`,
  `baseline-on-migrate=true`, `baseline-version=1`, `validate-on-migrate=true`).
  - `dev` profile: local file-backed H2 with Hibernate `update`; accounts persist
    across local restarts. `test` profile: isolated in-memory H2 with `create-drop`.
    Flyway is disabled for both.
  - `mysql` profile: Flyway migrates on startup, then Hibernate validates or
    updates per `SPRING_JPA_HIBERNATE_DDL_AUTO` (default `update`; set
    `validate` after the first successful migration for strict checking).
- Migration files:
  - `V1__baseline.sql` — full current schema (17 tables) for **fresh** databases.
  - `V2__provenance_delta.sql` — additive, guarded delta (`datasets.source_stored_filename`,
    `analysis_runs`, `drift_snapshots` + index, `data_quality_reports.uq_quality_dataset`).
    Every statement is a no-op when the object already exists, so it is safe on
    databases that Hibernate has already updated and idempotent under re-runs.
- **Existing databases that predate Flyway** (no `flyway_schema_history` table):
  Flyway writes a baseline row at version 1 and applies only V2; V1 is never
  executed against them. Missing tables/columns from older releases are then
  added by Hibernate `ddl-auto=update` on the same startup. All statements in
  both directions are additive — nothing is dropped or rewritten.
- `database/schema.sql` remains the human-readable reference schema (with
  MySQL-specific decorations) and is kept in sync with the JPA entities by
  `SchemaReferenceSyncTest`. `MigrationSqlCompatibilityTest` rejects SQL that
  H2 accepts but real MySQL rejects (e.g. `CREATE INDEX IF NOT EXISTS`).
- **Validated migration scenarios** (recorded in `docs/IMPLEMENTATION_PROGRESS.md`):
  fresh install (V1+V2, Hibernate `validate`), legacy pre-Flyway database
  (baseline@1 + V2 + `update`, data preserved), restart idempotency.
- **Rollback / recovery**: migrations are additive-only, so recovery from a
  failed migration is (1) restore the pre-start backup (see §3.3), (2) `java -jar
  ... repair` equivalent via a one-off Flyway repair if a migration half-applied
  (MySQL DDL is non-transactional), then (3) redeploy the previous JAR. There are
  no down-migrations by design; the additive policy keeps rollback = restore +
  redeploy-old-jar.

### 3.2 Durable file storage

- Uploaded files are stored under `app.upload.dir` (env `FILE_UPLOAD_DIR`, default
  `./uploads/datasets`) with UUID filenames on the **local filesystem**.
- This is **not** multi-instance durable: a container restart with an ephemeral filesystem,
  or a horizontal scale-out where instances do not share the mount, will lose or fail to see
  files. For production, mount a persistent volume here (or replace `FileStorageService`
  with object storage). Dataset metadata in the DB would then reference missing files; the
  API surfaces this as an explicit "file does not exist" error rather than fabricating data.
- In-memory caches (`AutoMlService.MODEL_CACHE`, EDA `BoundedCache`) are **process-local**;
  they are bounded but are not shared across instances and are rebuilt on demand from
  persisted data.

### 3.3 Backup & recovery

- **Before every deployment or migration**, take a logical backup:

  ```bash
  mysqldump -u root -p --single-transaction --routines autoscientist_db \
    > backups/autoscientist_db_$(date +%Y%m%d_%H%M%S).sql
  tar -czf backups/autoscientist_uploads_$(date +%Y%m%d_%H%M%S).tar.gz uploads/
  ```

- **Restore** into the same or a new database:

  ```bash
  mysql -u root -p -e "CREATE DATABASE IF NOT EXISTS autoscientist_db CHARACTER SET utf8mb4;"
  mysql -u root -p autoscientist_db < backups/autoscientist_db_<timestamp>.sql
  ```

- Uploaded files live under `FILE_UPLOAD_DIR`; back up and restore that
  directory together with the database so dataset metadata and files stay in
  sync. Restores have been verified by importing a dump into an isolated
  database and starting the application against it (see
  `docs/IMPLEMENTATION_PROGRESS.md`).
- Backups may contain user data and password hashes — store them outside the
  repository with restricted permissions (the provided `backups/` directory is
  local-only and should be added to `.gitignore` before any commit that could
  include it).


---

## 4. Production Build & Packaging

### 4.1 Packaging Spring Boot Jar
From the repository root:
```bash
cd backend
./mvnw clean package -DskipTests
```
The resulting executable JAR will be generated at:
`backend/target/autoscientist-backend-1.0.0-SNAPSHOT.jar`

### 4.2 Building React Production Bundle
From the repository root:
```bash
cd frontend
npm install
VITE_API_BASE_URL=https://<your-backend-host>/api npm run build
```
Static production assets are compiled to `frontend/dist/`. On Vercel/Render, set
`VITE_API_BASE_URL` in the project's build environment instead of the shell. A
build without a valid absolute HTTPS backend URL is rejected before the bundle
is written.

---

## 5. Running the Application

### 5.1 Starting Backend
Using the provided launcher script:
```bash
./scripts/run_backend.sh
```
Or directly via Java for production (the `mysql` profile is the default):
```bash
SPRING_PROFILES_ACTIVE=mysql java -jar backend/target/autoscientist-backend-1.0.0-SNAPSHOT.jar
```
For local H2 only:
```bash
SPRING_PROFILES_ACTIVE=dev java -jar backend/target/autoscientist-backend-1.0.0-SNAPSHOT.jar
```

### 5.2 Starting Frontend
For development/preview mode:
```bash
./scripts/run_frontend.sh
```
Or for production hosting, serve `frontend/dist` with Nginx or a static HTTP server.

---

## 6. Nginx Reverse Proxy Production Setup (Recommended)

```nginx
server {
    listen 80;
    server_name autoscientist.yourdomain.com;

    # Frontend Static Files
    location / {
        root /Applications/AutoScientist\ AI/frontend/dist;
        try_files $uri $uri/ /index.html;
    }

    # Backend API Proxy
    location /api/ {
        proxy_pass http://127.0.0.1:8080/api/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

---

## 7. CORS Configuration (staging-critical)

The backend only accepts browser requests from origins listed in `app.cors.allowed-origins`
(env `APP_CORS_ALLOWED_ORIGINS`, comma-separated). The default is the local dev set
(`http://localhost:5173,http://localhost:3000,http://127.0.0.1:5173`). For a deployed
frontend you **must** add its origin, or every browser API call will fail CORS:

```bash
APP_CORS_ALLOWED_ORIGINS=https://autoscientist.yourdomain.com
```

When the frontend is served from the same origin via the Nginx block above, the origin
matches automatically and no extra entry is required.

---

## 8. Post-Deploy Verification (smoke test)

The public health endpoint is `/api/health`. Use the checks below to confirm a
healthy deployment and the correct database/API wiring.

```bash
BASE=https://autoscientist.yourdomain.com/api

# 0. Public health check (expect 200 + status UP)
curl -s $BASE/health

# 1. Auth is enforced (expect 401, not 500)
curl -s -o /dev/null -w '%{http_code}\n' $BASE/datasets

# 2. Register a throwaway account and capture the token
TOKEN=$(curl -s -X POST $BASE/auth/register -H 'Content-Type: application/json' \
  -d '{"name":"smoke","email":"smoke@yourdomain.com","password":"Pass1234!"}' \
  | python3 -c 'import sys,json;print(json.load(sys.stdin)["data"]["token"])')

# 3. Authenticated dashboard (expect 200)
curl -s -o /dev/null -w '%{http_code}\n' -H "Authorization: Bearer $TOKEN" $BASE/dashboard/stats
```

A healthy deployment returns `401`, then a JWT, then `200`. If step 1 returns `500`, the
database or `JWT_SECRET` is misconfigured (the app would normally fail fast at startup —
check the logs for `Could not resolve placeholder`).

Confirm the schema was migrated exactly once:

```sql
SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank;
-- expect: 1 baseline | 1,  2 provenance delta | 1
```
