# Smoke Test Report

- **Test date:** 2026-10-31
- **Tester:** Automated/local release verification
- **Candidate:** local `8b4cf87` plus uncommitted release fixes
- **Production baseline tested read-only:** `d13e6c6`
**Overall result:** CONDITIONAL PASS — local functional and migration checks pass; authenticated production smoke testing remains pending.

## 1. Test environments

| Environment | Purpose | Result |
|---|---|---|
| Local backend, Java 17, dev profile, file-backed H2 | Functional smoke and auth/ownership tests | PASS |
| Local MySQL 9.7.1, disposable schema | Flyway fresh install and legacy upgrade | PASS |
| Backend test suite, Java 21 | Unit/integration regression | PASS |
| Frontend test suite, Vitest/jsdom | UI regression | PASS |
| Local production frontend build | Bundle safety and API URL | PASS |
| Live Render backend | Public health, CORS, unauthenticated security | PASS (limited) |
| Live Vercel frontend | Availability and bundle inspection | PASS (limited) |
| Live authenticated production workflow | End-to-end production proof | NOT RUN |

## 2. Automated test results

### Backend

Command:

```bash
cd backend
./mvnw -B -ntp clean test
```

Result:

- Tests run: 114
- Failures: 0
- Errors: 0
- Skipped: 2
- Build: SUCCESS

The two skipped tests are the opt-in real-MySQL migration tests when their environment variables are absent.

### Real MySQL

Command pattern:

```bash
AS_TEST_MYSQL_URL='jdbc:mysql://127.0.0.1:3307/?sslMode=REQUIRED' \
AS_TEST_MYSQL_ENVIRONMENT='staging' \
AS_TEST_MYSQL_USER='<disposable-user>' \
AS_TEST_MYSQL_PASSWORD='<disposable-password>' \
AS_TEST_MYSQL_ALLOW_SCHEMA_CREATE_DROP='true' \
./mvnw -B -ntp -Dtest=RealMysqlMigrationTest test
```

Environment: disposable local MySQL 9.7.1, TLS required, uniquely named schemas.

Result:

- Tests run: 2
- Failures: 0
- Errors: 0
- Build: SUCCESS

Verified paths:

- Fresh install applied `V1`–`V4` and passed Hibernate schema validation.
- Legacy pre-Flyway database was baselined at `V1`, applied `V2`–`V4`, preserved existing user and dataset rows, and passed schema validation.
- Re-running migration was idempotent.

### Frontend

Command:

```bash
cd frontend
npm test -- --run
```

Result:

- Test files: 21 passed
- Tests: 126 passed
- Failures: 0

### Frontend production build

Command:

```bash
VITE_API_BASE_URL='https://autoscientist-ai-backend.onrender.com/api' npm run build
```

Result:

- Build: PASS
- Production bundle contains the Render API URL: PASS
- Production bundle contains `jane@university.edu` or `SecurePassword123`: NO
- Production bundle contains `Local Development Credentials`: NO
- Production bundle contains `localhost`/`127.0.0.1` API URL: NO

## 3. Local functional smoke

Local backend:

- URL: `http://127.0.0.1:18080`
- Profile: `dev`
- Database: file-backed H2
- Uploads: `/tmp/autoscientist-local-verify/uploads/datasets`
- Process was restarted with the newly built Java 17 jar before the final checks.

| Area | Check | Result |
|---|---|---|
| Health | `GET /api/health` | 200, `status=UP` |
| Readiness | `GET /api/health/ready` | 200, `status=UP`, `database=UP` |
| Registration | New account | 201 |
| Login | Correct credentials | 200 |
| Login | Wrong password | 401, `Invalid email or password.` |
| Login | Mixed-case email with surrounding spaces | 200, normalized email returned |
| Registration | Duplicate email with different case | 400, clear “already registered” message |
| Registration | 100-byte password | 400, `Password must be at most 72 bytes (UTF-8).` |
| Token | Valid JWT `/auth/me` | 200 |
| Token | Invalid JWT | 401 |
| Authorization | `/api/datasets` without token | 401 |
| Authorization | `/api/datasets` with token | 200 |
| Upload | CSV upload | 200 |
| Profiling | Row/column profile | 30 rows, 7 columns, `MIXED` |
| Quality | Data quality report | PASS |
| EDA | Correlations, boxplots, histograms, categorical, missingness, matrix, scatter, grouped, distributions | PASS |
| Associations | GET and refresh | PASS, 15 associations |
| Dashboard | Stats | PASS |
| Research | Question and hypothesis execution | PASS |
| AutoML | Classification training | PASS |
| Fairness | Disparate impact check | FLAGGED |
| Explanation | Model explanation | AVAILABLE |
| Features | Feature importance | 6 features |
| Analysis | Latest, what-if schema, feature effect, evidence, reliability, findings, experiments | PASS |
| Drift | Self-comparison | PASS |
| What-if | Prediction request | 200 |
| Reports | Generate/list/get | PASS, report id 1 |
| Export | CSV | 200, 9,210 bytes |
| Export | Excel | 200, 13,465 bytes |
| Export | Markdown | 200, 15,728 bytes |
| Export | PDF | 200, 87,590 bytes, PDF 1.5, 10 pages |

A 404 observed for `/export/pdf` was caused by requesting the wrong path; the supported PDF endpoint `/api/reports/1/pdf` returns 200.

## 4. Cross-user isolation

A second account was registered and used to request the first account’s resources.

| Request | Expected | Actual |
|---|---|---|
| `GET /api/datasets/1` | Denied | 403 |
| `GET /api/datasets/1/quality` | Denied | 403 |
| `GET /api/reports/1` | Denied | 403 |
| `GET /api/reports/1/pdf` | Denied | 403 |

Result: PASS.

## 5. Live production read-only checks

| Check | Result |
|---|---|
| Frontend origin `https://auto-scientist-ai-ten.vercel.app` | 200 |
| Frontend bundle API base | `https://autoscientist-ai-backend.onrender.com/api` |
| Backend `GET /api/health` over HTTP/1.1 | 200, `status=UP` |
| Backend `GET /api/health` over HTTP/2 | Timed out; treated as transport anomaly |
| Backend root `/` | 404, expected |
| CORS preflight from production frontend origin | Allowed |
| CORS preflight from unapproved origin | 403 |
| Unauthenticated `GET /api/datasets` | 401 |
| Unauthenticated `GET /api/auth/me` | 401 |
| GitHub commit `d13e6c6` Vercel status | Success |

## 6. Fixes verified by this smoke pass

- Production login page hides developer demo credentials outside Vite dev mode.
- Over-72-byte registration passwords return a clear 400 instead of a misleading BCrypt error.
- Email normalization is locale-safe.
- Duplicate-registration race is converted to a clear 400.
- Readiness endpoint proves database connectivity without exposing connection details.
- Real-MySQL migration test correctly configures the MySQL driver/dialect and handles foreign-key/index ordering and missing legacy tables.

## 7. Not verified

The following require production access or provider credentials and are not claimed as passed:

- Authenticated login and full workflow on the live production database.
- Live `/api/health/ready` (candidate change not deployed).
- Existing production account compatibility.
- Actual Aiven schema and Flyway history.
- Render environment variables, disk, build logs, and auto-deploy settings.
- Vercel build settings and production environment values.
- SMTP/password-reset delivery.
- Docker image build (Docker daemon unavailable).
- Persistence across a real Render restart with the mounted disk.
- HTTP/2 behavior through the Render edge.

## 8. Verdict

**Technical candidate: PASS.** Local unit/integration, real-MySQL migration, frontend, production-bundle, auth, feature, report, readiness, and cross-user isolation checks passed.

**Production release: CONDITIONAL.** The live service is reachable and correctly routes API calls, but authenticated production smoke testing and provider dashboard verification are still required. See `DEPLOYMENT_CHECKLIST.md` and `PROVIDER_ACCESS_STATUS.md`.
