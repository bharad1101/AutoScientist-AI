# AutoScientist AI — Complete Production Readiness Audit

Audit date: 2026-10-30
Repository: `/Applications/AutoScientist AI`
Production frontend: `https://auto-scientist-ai-ten.vercel.app`
Production backend: `https://autoscientist-ai-backend.onrender.com`

## Executive assessment

**The deployed frontend and backend are reachable and their public integration
signals are consistent. Local backend/frontend tests and production builds pass.
The application is not fully production-verified:** the production Aiven
connection, Flyway execution against Aiven, durable upload disk, SMTP delivery,
and authenticated user workflows could not be safely verified without staging
credentials or using real production accounts. The health endpoint reports
process liveness and does not check database readiness.

No production data was created, changed, or deleted. No dashboard was changed,
and nothing was pushed or deployed. Git started clean; generated build outputs
are ignored. Local changes from this audit are limited to this report, the
corrected Vercel environment-template comment, and the corrected API reference.

## 1. Application and feature map

### Frontend routes and backend integration

| Frontend route/page | Backend integration and behavior |
|---|---|
| `/`, `/home` — landing | Public product page; `/home` redirects to `/` |
| `/login`, `/register` | `/api/auth/login`, `/api/auth/register`; session restored via `/api/auth/me` |
| `/forgot-password`, `/reset-password` | `/api/auth/forgot-password`, `/api/auth/reset-password` |
| `/dashboard` | `/api/dashboard/stats` |
| `/datasets` | `/api/datasets`, multipart import preview `/api/datasets/preview`, upload `/api/datasets/upload`, and delete `/api/datasets/{id}` |
| `/datasets/:id` plus `/overview`, `/quality`, `/eda` | `/api/datasets/{id}`, `/quality`, `/workflow-status`; EDA data from `/api/datasets/{id}/eda/*` |
| `/associations`, `/datasets/:id/associations` | `/api/datasets/{id}/associations` and `/associations/refresh` |
| `/research`, `/hypotheses`, `/statistics` and dataset variants | `/api/datasets/{id}/research-questions`, `/api/research-questions/{id}`, and hypothesis execution endpoint |
| `/automl`, `/ml`, `/datasets/:id/automl`, `/xai` and variants | `/api/datasets/{id}/automl/train`, `/latest`, `/feature-effect`, `/whatif`, `/whatif/schema`; fairness and explainability are model-run outputs |
| `/visualizations` and dataset variant | EDA API family `/api/datasets/{id}/eda/*` |
| `/findings` and dataset variant | `/api/datasets/{id}/findings`, `/api/findings/{id}`, evidence and reliability endpoints |
| `/reports` and dataset variants | `/api/datasets/{id}/reports`, `/api/reports*`; PDF/CSV/Excel/Markdown downloads |
| `/drift` | `/api/drift/compare` |
| `/ask` and dataset variant | `/api/datasets/{id}/ask`; deterministic local analysis engine, no external LLM dependency |
| `/experiments` | `/api/experiments`, `/api/experiments/{id}`, `/comparison` |

Protected routes require authentication; analysis pages additionally require a
selected dataset. A catch-all redirects to the dashboard for authenticated
users and the landing page otherwise. Vercel's direct `/dashboard` request was
verified to return the SPA entry point.

### Implemented backend feature families

- Registration/login, JWT identity, current-user lookup, logout acknowledgement,
  and password-reset token/email flow.
- CSV and Excel preview/import, schema/profile/quality reporting, reprofile,
  column exclusions, workflow status, and deletion.
- EDA summaries/plots, association tests and confidence intervals, research
  questions/hypotheses/statistical tests, findings, evidence graph and
  reliability, experiment/version tracking.
- AutoML model training/evaluation, feature effects, fairness metrics,
  explainability, what-if analysis, drift comparison, natural-language dataset
  queries, dashboard statistics, report generation and exports.

Static inspection found no obvious production mock response or unimplemented
controller stub. Tests use mocks where appropriate, but application services
are implemented. Fairness and explainability are part of AutoML results rather
than separate HTTP controllers.

## 2. Persistence and migrations

The JPA model and migration scripts cover users, datasets, dataset columns,
quality reports/issues, associations, research questions, hypotheses,
statistical tests, experiments/versions, models, findings, evidence nodes,
reports, analysis runs, drift snapshots, and password-reset tokens. Core
relationships include users owning datasets; datasets owning columns, quality
reports and analysis artifacts; experiments holding versions and model runs;
and findings holding evidence nodes. Dataset deletion removes associated
analysis artifacts and stored files through the service lifecycle.

MySQL profile startup runs Flyway migrations V1–V4 and Hibernate schema
validation (`ddl-auto=validate`). Fresh databases apply the baseline and
additive migrations; the code also supports baselining a pre-Flyway schema.
Current integration tests that exercise a real MySQL server were skipped because
staging MySQL credentials were unavailable. H2 tests do not establish Aiven or
MySQL compatibility.

Dataset files are written beneath `FILE_UPLOAD_DIR`; Docker defaults to
`/app/uploads/datasets`, with a volume at `/app/uploads`. The Docker entrypoint
creates/chowns the directory and exits if it is not writable after dropping to
uid 10001. The actual Render disk attachment and post-restart file persistence
cannot be inspected from this environment.

## 3. Authentication, isolation, and security

- Spring Security is stateless; all routes require a JWT except health and
  account bootstrap/recovery endpoints. JWT secret is required in the `mysql`
  profile and token lifetime defaults to 24 hours.
- Passwords are BCrypt encoded. Reset tokens are random, stored as SHA-256
  hashes, single-use, and time-limited. Forgot-password uses the same response
  for known and unknown email addresses.
- Controllers/services resolve the authenticated user and ownership checks
  scope dataset, report, research and experiment access. Existing regression
  tests cover cross-user dataset/report access.
- CORS is explicit and credential-aware. Production preflight from the supplied
  Vercel origin was accepted with that exact origin in the response.
- The frontend stores the bearer JWT in `localStorage`. This exposes sessions to
  token theft if an XSS vulnerability is introduced; no `dangerouslySetInnerHTML`
  or direct `innerHTML` sinks were found in the frontend source scan. Logout is
  client-side for stateless JWTs; it does not revoke a copied token before
  expiry.
- Frontend dependency audit reports zero known advisories. A Maven security
  advisory scan was not run, so backend dependency vulnerability status is not
  independently established by this audit.

## 4. Production checks performed

| Check | Result |
|---|---|
| Render `GET /api/health` | **PASS** — HTTP 200, JSON reports `UP` |
| Vercel root | **PASS** — HTTP 200 |
| Vercel direct `/dashboard` | **PASS** — HTTP 200 via SPA rewrite |
| Deployed frontend API configuration | **PASS** — inspected current bundle; it targets the supplied Render HTTPS `/api` URL |
| Production CORS preflight | **PASS** — HTTP 200; exact Vercel origin, credentials, Authorization and JSON headers allowed |
| Protected production dataset API without credentials | **PASS** — HTTP 401 |
| Production registration/login/reset | **NOT RUN** — would create or interact with real accounts |
| Production database read/write/migration | **NOT RUN** — no production SQL was issued |
| Render/Vercel/Aiven dashboard settings | **NOT INSPECTED** — no connected dashboard access in this task |
| Health-to-database readiness | **LIMITATION** — `/api/health` is process-only and does not query Aiven |

## 5. Test and build results

- Backend `./mvnw test`: **PASS**, 106 tests, 0 failures, 0 errors, 2 skipped.
- Backend `./mvnw verify`: **PASS**, 106 tests, 0 failures, 0 errors, 2 skipped.
  Both skipped cases are real-MySQL migration tests requiring separate staging
  credentials. Initial sandbox run could not bind a local port for the
  persistence-restart test; rerun with local loopback permission passed.
- Frontend `npm test -- --run`: **PASS**, 21 test files, 126 tests.
- Frontend `npm run build`: with the local localhost environment value, the
  build correctly refused to bake localhost into production. With the supplied
  production API URL passed at build time: **PASS**, Vite emitted the production
  bundle.
- Frontend `npm audit --json`: **PASS**, zero known vulnerabilities.
- `git diff --check`: **PASS**.

Coverage includes authentication, password reset, ownership, persistence restart,
health, dataset import/profiling, analytics, statistics, AutoML/what-if,
fairness/drift, reports/PDF, and frontend component/service flows. The React
suite uses mocked services for component tests; it is not a live browser-to-API
end-to-end suite. No browser was available for viewport interaction testing
(browser use is disabled in this desktop session), so mobile layout and every
visual route were not manually inspected.

## 6. Findings and changes made

1. **Stale frontend environment instructions:** `frontend/.env.example` called
   the production Vercel build a Render configuration. Updated that comment to
   direct production configuration to Vercel.
2. **Stale API reference:** `docs/API_DOCUMENTATION.md` had endpoint paths and
   feature descriptions that no longer matched the controllers and omitted
   current endpoints. Replaced it with a controller-aligned route inventory and
   clarified that health is process-only and logout is stateless.
3. **No application logic bug reproduced:** current local tests pass and
   read-only production checks show the supplied URLs, API base URL, CORS and
   authentication boundary working. This does not verify authenticated
   production workflows or Aiven connectivity.

No application code, database schema or migration was changed.

## 7. Required production configuration

### Render backend runtime

Confirm the existing service uses the repository's Java 17 Docker image,
`SPRING_PROFILES_ACTIVE=mysql`, and health path `/api/health`. The image forwards
Render's assigned `PORT` to Spring Boot. Required variables:

- `SPRING_DATASOURCE_URL` — Aiven MySQL JDBC URL with TLS enabled;
  `sslMode=REQUIRED` or a configured verified CA mode.
- `SPRING_DATASOURCE_USERNAME` and `SPRING_DATASOURCE_PASSWORD` — migration-
  capable database account; values must stay in secret settings.
- `JWT_SECRET` — fresh Base64-encoded random secret with at least 64 decoded
  bytes; keep its value private.
- `APP_CORS_ALLOWED_ORIGINS=https://auto-scientist-ai-ten.vercel.app`.
- `FRONTEND_URL=https://auto-scientist-ai-ten.vercel.app` for reset links.
- `FILE_UPLOAD_DIR=/app/uploads/datasets` beneath a durable Render disk mounted
  at `/app/uploads`, writable by container uid 10001.
- `SPRING_JPA_HIBERNATE_DDL_AUTO=validate` and
  `SPRING_FLYWAY_ENABLED=true`.
- For delivered password-reset email: `SPRING_MAIL_HOST`, `SPRING_MAIL_PORT`,
  `SPRING_MAIL_USERNAME`, `SPRING_MAIL_PASSWORD`,
  `SPRING_MAIL_PROPERTIES_MAIL_SMTP_AUTH=true`,
  `SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_ENABLE=true`, and a verified
  `PASSWORD_RESET_FROM` sender.

The deployed preflight confirms the current CORS setting permits the supplied
Vercel origin. It does not reveal or verify the secret values, SMTP delivery,
disk attachment, database TLS configuration, or Render auto-deploy policy.

### Vercel frontend build

- Production build variable: `VITE_API_BASE_URL` set to the supplied Render API
  URL ending in `/api`. The currently deployed bundle was checked and contains
  that URL.
- Root directory `frontend`; install `npm ci`; build `npm run build`; output
  `dist`; keep `frontend/vercel.json` SPA rewrite.
- Confirm the project deploys the intended GitHub repository and production
  branch. Preview deployments need an HTTPS API URL and a corresponding CORS
  origin if they are expected to call the backend.

### Aiven MySQL

- Confirm the service and application database are running and reachable from
  Render; keep the JDBC host/database/user/password in Render secret settings.
- Require TLS for the application account and permit the Render service's
  outbound IP ranges in the Aiven allowlist.
- Verify the migration-capable user has the DDL rights needed by Flyway.
- Run the current V1–V4 migration path against a separate disposable/staging
  MySQL service before relying on it for production. Never point destructive
  migration tests at production.

## 8. Remaining risks and readiness decision

1. **Aiven is not verified:** MySQL connection/TLS, migration compatibility,
   DDL privileges, database network allowlisting, and persistence cannot be
   confirmed without a non-production Aiven instance or authorized dashboard
   visibility.
2. **Uploads are not verified across Render restart:** Docker permissions are
   prepared and checked, but disk attachment/durability is a dashboard/runtime
   property.
3. **SMTP and reset delivery are not verified:** requires provider settings and
   a controlled test account/mailbox.
4. **Health is liveness-only:** the endpoint can report `UP` without checking
   the database. App startup requires MySQL, but post-start database loss would
   not be reflected by this health response.
5. **Visual/mobile and live authenticated workflows are not verified:** UI
   browser access is unavailable here, and production account actions were
   intentionally not performed. Existing local unit/component tests pass.
6. **Backend dependency advisories are not checked:** npm audit passed; Maven
   advisory scanning remains outstanding.

**Assessment: operationally reachable and locally build/test ready, but not
fully production verified.** Before describing every feature as production
ready, confirm Render/Aiven settings and perform controlled authenticated
smoke tests, reset-email delivery, file upload plus restart persistence, and
staging Flyway verification. No push or deployment was performed as part of
this audit.
