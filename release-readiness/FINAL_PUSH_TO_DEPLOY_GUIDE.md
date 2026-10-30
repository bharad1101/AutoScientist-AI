# AutoScientist AI — Push-to-Deploy Readiness

**Date:** 2026-10-30
**Repository:** `/Applications/AutoScientist AI`
**Decision:** **NOT READY TO PUSH as a guaranteed one-command production release.** Repository-side verification is complete, but live provider auto-deploy settings, Aiven staging migration, and Render persistent storage cannot be verified from this environment. No push, deployment, or external dashboard change was made.

## 1. Repository-side changes completed

- Hardened password-reset delivery failure behavior. When mail transport is absent or delivery fails, the generic public response is preserved and the unusable reset token is deleted. Added tests for missing mail transport, successful delivery, and SMTP failure, including service-level cleanup coverage.
- Reworked the real-MySQL migration tests to require an explicit staging environment and schema create/drop opt-in, use unique schema names, require TLS, avoid deleting pre-existing schemas, and validate the complete Spring application schema after both fresh V1–V4 migration and legacy baseline-plus-deltas.
- Updated Maven dependency management for Commons Lang to 3.18.0, clearing the moderate CVE-2025-48924 finding from the prior OSV scan.
- Corrected deployment documentation that showed an unencrypted JDBC example and root-user/manual production schema initialization. Added Aiven TLS guidance and native Render/Vercel push-to-deploy instructions.
- Existing user changes to `docs/API_DOCUMENTATION.md`, `frontend/.env.example`, and prior audit artifacts were preserved. No production URL or credential was added to source code. No existing feature or migration was removed.

## 2. Tests and checks completed

| Check | Result |
|---|---|
| Backend `./mvnw -Dmaven.repo.local=/private/tmp/autoscientist-m2-audit verify -B -ntp` | **PASS** — 110 tests; 0 failures; 0 errors; 2 skipped |
| Skipped backend tests | Real-MySQL migration tests gated to disposable staging credentials; no staging DB was provided |
| Frontend `npm test -- --run` | **PASS** — 21 files; 126 tests |
| Frontend production build with `VITE_API_BASE_URL=https://autoscientist-ai-backend.onrender.com/api` | **PASS** — Vite production bundle generated |
| `npm audit --json` | **PASS** — 0 known frontend advisories |
| Backend OSV scan (prior verification) | Commons Lang finding fixed. Two Spring Framework advisories remain in OSV output, but affected SSE fragment rendering and `XsltView` paths are not used in this REST app. OWASP Dependency-Check did not complete because NVD rejected a missing API key. |
| Git fetch | **PASS** — fetched `origin`; local `main` and `origin/main` currently have the same commit (`0 0` ahead/behind) |
| Live frontend/backend checks in this turn | **NOT VERIFIED** — requests timed out or were inaccessible in the current network environment. The prior audit records successful health, CORS, SPA route, frontend API URL, and unauthenticated API rejection checks; they were not repeated successfully here. |
| Aiven connection / authenticated production workflows / SMTP / persistent disk | **NOT VERIFIED** — no staging credentials, provider dashboard access, or safe test account was available |

Maven unit/integration tests cover application logic locally, but they do not prove the deployed Vercel-to-Render-to-Aiven path. No production database writes or destructive tests were run.

## 3. Production configuration verified in repository

- Backend Docker runtime is Java 17 and Spring Boot `mysql` profile; Render's `PORT` is forwarded to Spring's server port. The health route is `/api/health`.
- The MySQL profile requires `SPRING_DATASOURCE_URL`, username/password, `JWT_SECRET`, `APP_CORS_ALLOWED_ORIGINS`, `FRONTEND_URL`, and `FILE_UPLOAD_DIR`; Flyway migrates at startup and Hibernate validates the schema.
- Migrations V1–V4 remain present. No migration is deleted or edited by this work. Production Flyway behavior against the actual Aiven MySQL 8.4 service remains unverified.
- Use Aiven's JDBC host/port/database and TLS. `sslMode=REQUIRED` encrypts transport; `VERIFY_CA` or `VERIFY_IDENTITY` additionally verifies the certificate when the Aiven CA trust is configured. Production must not disable TLS.
- `FILE_UPLOAD_DIR=/app/uploads/datasets`; dataset metadata stores generated filenames resolved under this path. The Docker image declares `/app/uploads`, and its entrypoint prepares ownership for UID 10001. Render must mount a persistent disk at `/app/uploads`; image `VOLUME` alone does not guarantee durability.
- Frontend production build requires an absolute HTTPS `VITE_API_BASE_URL` ending in `/api`; the current supplied API URL is used for the verified local production build. Vercel SPA rewrite is in `frontend/vercel.json`.
- CORS must contain the exact frontend origin `https://auto-scientist-ai-ten.vercel.app`; password reset links use `FRONTEND_URL` equal to the frontend origin.
- SMTP is optional for startup but required for real password-reset delivery. Variables are `SPRING_MAIL_HOST`, `SPRING_MAIL_PORT`, `SPRING_MAIL_USERNAME`, `SPRING_MAIL_PASSWORD`, `SPRING_MAIL_PROPERTIES_MAIL_SMTP_AUTH`, `SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_ENABLE`, and (recommended) `SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_REQUIRED`, `SPRING_MAIL_PROPERTIES_MAIL_SMTP_SSL_CHECKSERVERIDENTITY`, plus `PASSWORD_RESET_FROM` and `FRONTEND_URL`. Never put their values in Git.

## 4. Render deployment readiness

Repository support is present:

- Recommended Render configuration: GitHub repository `bharad1101/AutoScientist-AI`, production branch `main`, root directory `.`, Dockerfile `Dockerfile` (the repository-root Docker build context includes the backend). Alternatively use root directory `backend` and Dockerfile `Dockerfile` together.
- Health check path: `/api/health`.
- Persistent disk mount: `/app/uploads`.
- Required runtime variables must be configured in Render's private environment settings. The non-secret values include `SPRING_PROFILES_ACTIVE=mysql`, `APP_CORS_ALLOWED_ORIGINS=https://auto-scientist-ai-ten.vercel.app`, `FRONTEND_URL=https://auto-scientist-ai-ten.vercel.app`, and `FILE_UPLOAD_DIR=/app/uploads/datasets`. Datasource credentials, JWT secret, and optional SMTP credentials must remain masked secrets.

**External state not verified:** whether the existing Render service is connected to this GitHub repository, targets `main`, auto-deploys on push, uses either matching Docker context, has the health path, has all required environment settings, or has a writable persistent disk. No Render dashboard connector was available.

## 5. Vercel deployment readiness

Repository support is present:

- Root directory: `frontend`.
- Build command: `npm run build`.
- Output directory: `dist`.
- SPA deep-link rewrite: `frontend/vercel.json` rewrites requests to `/index.html`.
- Production build variable: `VITE_API_BASE_URL=https://autoscientist-ai-backend.onrender.com/api` (verify that this is the current backend URL before saving; this value is public configuration, not a secret).

**External state not verified:** whether the existing Vercel project is connected to this GitHub repository, targets `main`, auto-deploys on push, uses `frontend` as its root, or has the production build variable. No Vercel dashboard connector was available.

## 6. GitHub auto-deployment readiness

- Repository contains both Docker deployment definitions and the Vercel SPA config.
- There is no GitHub Actions deployment workflow, Render Blueprint, or Vercel project manifest. This is intentional: use Render and Vercel native Git integrations, avoiding a second competing deploy pipeline.
- `git fetch --prune origin` succeeded. At verification time `main` equaled `origin/main`; no remote commits needed integrating. The requested repository work was collected in one local commit so the final push command will include it.
- The local changes were committed as one reviewable commit after tests passed. No remote push or deployment was performed. The commit preserves all user-requested prior changes and adds the final guide.

## 7. Smallest unavoidable external actions

1. **One-time provider check:** in Render and Vercel, confirm each is connected to `bharad1101/AutoScientist-AI`, production branch `main`, and automatic deployment on push is enabled. Configure only the repository settings above that are missing. Secret values should be entered directly in provider dashboards; never send them here or commit them.
2. **One-time runtime check:** confirm Render has the persistent disk at `/app/uploads`, its environment values are present, and Vercel has the production API build variable. Confirm Aiven TLS/network allowlisting and a successful disposable staging migration before relying on production migrations.
3. **If password reset email must work:** enter the SMTP settings above in Render and validate delivery using a test inbox/provider sandbox.
4. **Before the push command:** commit the preserved local changes. The final `git push` command cannot transmit uncommitted work.

The exact values of masked settings are not visible here, and no provider dashboard can be edited or inspected in this environment. No password, JWT secret, database credential, SMTP credential, or API key is requested.

## 8. Final push command

After the external checks above, use:

```bash
git push origin main
```

Do not force-push. The tested commit is a normal fast-forward from the fetched `origin/main`; do not force-push.

## 9. How to confirm deployments succeeded

- Render: verify the commit's deploy completed successfully, logs show Flyway validation/migrations and application startup, and `https://autoscientist-ai-backend.onrender.com/api/health` returns HTTP 200.
- Vercel: verify a Production deployment for the same commit completed and the project reports the `main` branch. Open `https://auto-scientist-ai-ten.vercel.app/` and a deep route such as `/dashboard`; verify browser API requests target the Render `/api` URL and pass CORS.
- Full application readiness still requires isolated authenticated staging checks for registration/login, ownership isolation, dataset upload/read after a Render restart, analysis/AutoML/analytics, report/PDF download, and password reset. Do not use destructive tests or create disposable test data in production.
