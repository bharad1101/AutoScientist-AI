# Manual Deployment Steps — AutoScientist AI

Operator checklist for taking this repository from a verified working tree to a
running production deployment. **The agent did not deploy anything, did not
build a Docker image, and did not commit or push.**

Two gates could not be executed in the autonomous environment and must be run
here:

1. Docker image build — the Docker daemon was unavailable.
2. Real-MySQL migration test — staging credentials were unavailable.

## 0. Preconditions

- JDK **17** installed. Java 21 is not supported.
- Node 22.13.0 (the CI-pinned version; `frontend/package.json` also allows `^20.19.0` and `>=24.0.0`) and npm.
- MySQL 8.0+ reachable over TLS with a migration-capable application user.
- A durable disk for uploads (mounted at `/app/uploads` in containers).
- Secrets available from your secret manager (never commit them).

## 1. Verify the toolchain

```bash
export JAVA_HOME="/path/to/jdk-17"
export PATH="$JAVA_HOME/bin:$PATH"
java -version          # must report 17.x
cd backend && ./mvnw -version
```

## 2. Full verification on Java 17

```bash
cd backend
./mvnw -B -ntp clean verify        # expect 0 failures; current totals are in the final release report
./mvnw -B -ntp -DskipTests package

cd ../frontend
npm ci
npm test -- --run                  # expect 0 failures; current totals are in the final release report
VITE_API_BASE_URL="https://your-backend.example.com/api" npm run build
```

## 3. Gate — real MySQL migration test (staging only)

The test creates uniquely named disposable schemas and drops only schemas it
created. **Never point it at production.** The URL must be server-level (no
default schema) and must use TLS (`sslMode=REQUIRED|VERIFY_CA|VERIFY_IDENTITY`).

```bash
cd backend
export AS_TEST_MYSQL_URL="jdbc:mysql://staging-db.example.com:3306?sslMode=REQUIRED"
export AS_TEST_MYSQL_USER="staging_migration_user"
export AS_TEST_MYSQL_PASSWORD="<from secret manager>"
export AS_TEST_MYSQL_ENVIRONMENT="staging"
export AS_TEST_MYSQL_ALLOW_SCHEMA_CREATE_DROP="true"

./mvnw -B -ntp -Dtest=RealMysqlMigrationTest test
```

Expected: fresh install applies V1–V7 and Hibernate validates; legacy pre-Flyway
database is baselined at V1, keeps its live row, and applies V2–V7. Both tests
must pass before the first production deploy.

## 4. Gate — Docker image build

```bash
docker build -t autoscientist-backend:java17 .
# or, to build only the backend image:
docker build -t autoscientist-backend:java17 -f backend/Dockerfile backend
```

The image must use the Java 17 base images and run as a non-root user. If the
daemon is unavailable, CI performs this build on push to the configured branch.

## 5. Provision the database and durable storage

1. Create the application database and an application user limited to it.
2. Do **not** run `database/schema.sql` or grant `root`. Flyway owns the schema.
3. Attach a durable disk at `/app/uploads` (containers) and set
   `FILE_UPLOAD_DIR=/app/uploads/datasets`.
4. Take a baseline backup before the first start.

## 6. Required environment variables (production)

Copy `backend/.env.example` and set values through the hosting provider's secret
settings. At minimum:

| Variable | Notes |
|---|---|
| `SPRING_PROFILES_ACTIVE` | `mysql` |
| `SPRING_DATASOURCE_URL` | `jdbc:mysql://<host>:<port>/<db>?sslMode=REQUIRED&serverTimezone=UTC` |
| `SPRING_DATASOURCE_USERNAME` / `SPRING_DATASOURCE_PASSWORD` | Application user |
| `SPRING_JPA_HIBERNATE_DDL_AUTO` | `validate` |
| `JWT_SECRET` | Base64 HS256 key; ≥32 decoded bytes accepted, 64 random bytes recommended (`openssl rand -base64 64 \| tr -d '\n'`) |
| `APP_CORS_ALLOWED_ORIGINS` | Exact frontend HTTPS origin(s), comma-separated |
| `FRONTEND_URL` | Frontend HTTPS base URL used in reset links |
| `FILE_UPLOAD_DIR` | `/app/uploads/datasets` (durable disk) |
| `RESOURCE_BUDGET_MAX_MB` / `_MAX_ROWS` / `_MAX_COLUMNS` | Upload bounds (defaults 50 MB / 1,000,000 / 5,000) |
| `SPRING_MAIL_*` | SMTP credentials for password reset |
| `PORT` | Container port (default 8080) |

Optional: `RATE_LIMIT_*`, `RESOURCE_BUDGET_JOB_CONCURRENCY`, `DB_POOL_*`.

Never set `SPRING_PROFILES_ACTIVE=dev` on a hosted runtime; the profile guard
rejects embedded datasources there.

## 7. Deploy the backend

1. Build/publish the Java 17 image or JAR (`java -jar target/*.jar`).
2. Start with `SPRING_PROFILES_ACTIVE=mysql`. Flyway applies V1–V7 on startup,
   then Hibernate validates the schema.
3. Configure the platform health check to `GET /api/health/ready` (datasource,
   storage writability, and Flyway state); `/api/health` remains the liveness probe.
4. Confirm logs show Flyway migrating/validating and the app listening on the
   expected port.

## 8. Deploy the frontend

1. Set `VITE_API_BASE_URL` to the real backend HTTPS URL **ending in `/api`**.
2. Run `npm run build`; the build fails on a missing, loopback, non-HTTPS, or
   non-`/api` URL by design.
3. Publish `dist/` to your static host and configure SPA fallback to
   `index.html`.

## 9. Post-deploy smoke test

```bash
BASE="https://your-backend.example.com"
curl -fsS "$BASE/api/health"
curl -fsS "$BASE/api/health/ready"

# Register/login, then use the returned token:
curl -fsS -X POST "$BASE/api/auth/register" -H 'Content-Type: application/json' \
  -d '{"username":"smoke_user","email":"smoke@example.com","password":"<strong-password>"}'
TOKEN="<jwt from login>"
curl -fsS -X POST "$BASE/api/datasets/upload" \
  -H "Authorization: Bearer $TOKEN" -F "file=@sample.csv"
```

Then verify in the UI: login, upload, EDA, associations, AutoML train, report
and PDF. Check response headers include `X-Request-Id` and that the same id
appears in backend logs.

## 10. Rollback

Migrations are additive and there are no down-migrations.

1. Stop the new instance.
2. Restore the pre-deploy database backup.
3. Redeploy the previous image/JAR.
4. For a half-applied MySQL migration (DDL is non-transactional), run a one-off
   Flyway `repair` after restoring, then redeploy the previous version.

Do **not** edit `V1__baseline.sql` or any applied migration to "fix" a failure;
its checksum is recorded in `flyway_schema_history`. Add a new migration.

## 11. Known environment notes

- Rate limiting is in-memory per instance; use one instance, sticky sessions, or
  a shared store for horizontal scaling.
- Uploads require durable storage; ephemeral filesystems lose files on redeploy.
- CSV ingestion is bounded in memory by `RESOURCE_BUDGET_*`; raise limits only
  with matching RAM.
- CI deploys only when the push/branch gates and enable variables are satisfied;
  verify provider hook targets, disk mount, and health checks in the dashboards.
