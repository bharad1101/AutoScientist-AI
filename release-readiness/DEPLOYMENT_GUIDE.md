# Deployment Guide — AutoScientist AI

Date: 2026-10-30
This guide describes the configuration the repository expects. It does **not** change, create, or trigger any deployment pipeline. The operator pushes code; the existing external pipeline handles deployment.

## 1. Backend environment variables (required)

| Variable | Purpose |
|---|---|
| `SPRING_PROFILES_ACTIVE=mysql` | Production profile |
| `SPRING_DATASOURCE_URL` | JDBC MySQL URL |
| `SPRING_DATASOURCE_USERNAME` | Database user |
| `SPRING_DATASOURCE_PASSWORD` | Database password |
| `JWT_SECRET` | Base64 secret, at least 64 bytes decoded; no fallback in `mysql` |
| `APP_CORS_ALLOWED_ORIGINS` | Deployed frontend origin(s), comma-separated |
| `FRONTEND_URL` | Public frontend URL used in password-reset links |
| `FILE_UPLOAD_DIR` | Persistent upload path (container must mount/write it) |

Optional: `JWT_EXPIRATION_MS`, `SPRING_FLYWAY_ENABLED`, `SERVER_PORT`/`PORT`, password-reset mail settings.

Startup fails fast when required MySQL settings or `JWT_SECRET` are missing. `DeploymentProfileGuard` refuses the `dev` profile on hosted runtimes and refuses embedded databases outside `dev`/`test`.

## 2. Frontend environment variables

| Variable | Purpose |
|---|---|
| `VITE_API_BASE_URL` | Absolute HTTPS backend URL ending in `/api` |

The Vite build refuses empty, non-HTTPS, or localhost/loopback values. This guard must not be bypassed.

**BLOCKED:** the real production backend URL is not recorded in this repository. Supply it from the operator/pipeline secret store. Do not reuse the example URL seen in docs/tests as fact.

## 3. Build commands (production frontend is BLOCKED until the real URL is supplied)

Backend:

```bash
cd backend
./mvnw clean package
```

Docker (uses Maven inside the build image, so it is unaffected by local Maven setup):

```bash
docker build -t autoscientist-backend backend
```

Frontend:

```bash
cd frontend
VITE_API_BASE_URL=https://<real-backend-host>/api npm install
VITE_API_BASE_URL=https://<real-backend-host>/api npm run build
```

## 4. Database

- `mysql` profile enables Flyway from `classpath:db/migration`.
- `baseline-on-migrate=true`, `baseline-version=1` supports databases that predate Flyway.
- Migrations: `V1__baseline.sql`, `V2__provenance_delta.sql`, `V3__association_confidence_intervals.sql`, `V4__password_reset_tokens.sql`.
- Verified on an isolated real MySQL 9.7.1 instance:
  - fresh install applies V1–V4 and passes Hibernate validation;
  - legacy pre-Flyway database baselines at V1 and applies V2–V4 with data preserved.
- The current dependency tree resolves Flyway 11.7.2. Flyway's current driver reference lists MySQL 5.7, 8.0, and 9.4 as verified; re-run against the actual MySQL 8.x staging database before production.
- Required DB privileges: `CREATE`, `ALTER`, `INDEX`, `REFERENCES`, plus normal DML, for the migration user.

Run the real-MySQL test yourself (URL must end at the server, no database name):

```bash
cd backend
AS_TEST_MYSQL_URL='jdbc:mysql://<staging-host>:3306/' \
AS_TEST_MYSQL_USER='<migration-user>' \
AS_TEST_MYSQL_PASSWORD='<password>' \
./mvnw -Dtest=RealMysqlMigrationTest test
```

The test creates/drops only `as_it_fresh` and `as_it_legacy`. Do not point it at a server where those names are in use.

## 5. Deployed services

- Backend container exposes the port supplied by the platform (`PORT`/`SERVER_PORT`).
- `frontend/vercel.json` rewrites all routes to `index.html` for client-side routing.
- No repository CI workflow exists; use the existing external pipeline.

## 6. Post-deploy smoke test

1. `GET /api/health` returns healthy.
2. Register a user and log in.
3. Upload a CSV/XLSX dataset and confirm profiling completes.
4. Run an analysis and view associations/statistics.
5. Train AutoML and load the latest benchmark.
6. Generate a report and download the PDF.
7. Exercise forgot-password / reset-password.
8. Confirm browser requests are not blocked by CORS.

## 7. Hosting provider checks (verify in the provider dashboard)

There is no repository CI workflow, so none of the following can be inferred from the repo:

**Backend service**
- [ ] Confirm Render root directory and Dockerfile path agree: root `backend` + `Dockerfile` or root `.` + `Dockerfile`. Both builds use Java 17 and a non-root runtime.
- [ ] Runtime uses Java 17 and the built jar `autoscientist-backend-1.0.0-SNAPSHOT.jar`.
- [ ] `SPRING_PROFILES_ACTIVE=mysql` is set (the Dockerfile sets it, confirm it is not overridden).
- [ ] All required env vars are present (datasource, `JWT_SECRET`, `APP_CORS_ALLOWED_ORIGINS`, `FRONTEND_URL`, SMTP, `FILE_UPLOAD_DIR`).
- [ ] Health check path is `/api/health` (public endpoint).
- [ ] The platform forwards its assigned port to the app (`PORT`/`SERVER_PORT`).
- [ ] A persistent disk (or object storage) is mounted for uploads; the runtime user (uid 10001) can write the mount.
- [ ] Only one backend instance, or shared storage/cache is configured (model cache and uploads are local).
- [ ] Database user can run Flyway DDL and the database is reachable over TLS/firewall.
- [ ] Automatic deploy is paused until the production `VITE_API_BASE_URL` and DB values are confirmed.

**Frontend service**
- [ ] Build command runs `npm install` then `npm run build`.
- [ ] `VITE_API_BASE_URL` is set **at build time** to an absolute HTTPS URL ending in `/api`.
- [ ] Output directory is `dist`.
- [ ] SPA rewrite (`vercel.json`) is applied so deep links work.
- [ ] Pin the frontend build to Node 22 LTS (Vite 8 / Vitest 4 require Node 20.19+ or 22.12+; Node 25 was used locally).
- [ ] The deployed origin exactly matches `APP_CORS_ALLOWED_ORIGINS` (scheme + host, no trailing slash).
- [ ] Any `frontend/dist` artifact is not uploaded manually; the provider must rebuild from source.

## 8. Rollback

- Redeploy the previous backend image.
- Flyway migrations are additive; do not downgrade schema automatically. Restore from backup if a destructive change is ever introduced.
- Redeploy the previous frontend build if the API contract changes.
