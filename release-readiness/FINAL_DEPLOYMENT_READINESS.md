# Final Deployment Readiness

Date: 2026-10-30

## Classification

**READY AFTER EXTERNAL DASHBOARD CONFIGURATION.** Repository-level configuration and safeguards are present, but production values and the hosting provider's automatic pipeline are not inspectable from this repository. Live deployment was not attempted.

## Verified path

- Backend image uses Java 17, builds an executable Spring Boot jar, sets `SPRING_PROFILES_ACTIVE=mysql`, runs under uid 10001, maps `PORT` into `SERVER_PORT`, and gracefully shuts down.
- `/api/health` is public and returns a minimal status payload.
- MySQL profile requires datasource credentials, JWT secret, CORS origin, frontend URL, and upload directory; missing required values fail configuration/startup rather than using local values.
- Frontend build validates `VITE_API_BASE_URL` and rejects missing, localhost, or non-HTTPS values. It must be present at build time and end with `/api`.
- Flyway migrations are V1–V4. Current build resolves Flyway 11.7.2; historical tests against MySQL 9.7.1 are not a substitute for staging verification. Officially verified versions listed by Flyway include MySQL 5.7, 8.0, and 9.4.
- Files are local filesystem-backed. Docker declares `/app/uploads`, but the hosting provider must attach durable storage there.
- Frontend dependencies were upgraded; clean `npm ci`, 126 tests, and post-upgrade npm audit (0 advisories) pass. The production build still requires the real API URL.

## Required before push / enabling automatic deploy

Configure and verify service settings and environment in `AUTOMATIC_DEPLOYMENT_GUIDE.md` and `PRODUCTION_ENVIRONMENT_VARIABLES.md`. Set the real API URL and rebuild; confirm current Flyway migrations against the Aiven MySQL 8.x service in staging; verify the Render disk mount and provider pipelines. The current local `.env` points to localhost and is not suitable for production.

Git history is reconciled. The fetched `origin/main` is an ancestor of local `main`; local `main` tracks `origin/main`, so the prepared branch can be pushed as a normal fast-forward without force. The backup ref `backup/pre-push-2026-10-30` remains available.

## Post-deploy acceptance

From a clean push, verify both providers build from the intended branch; backend starts in `mysql`; Flyway completes; `/api/health` returns HTTP 200; browser requests reach the HTTPS `/api` endpoint without CORS errors; upload a dataset, redeploy, and download/use the same dataset; exercise login, analysis, report, and password-reset email flows.

No successful live deployment is claimed.
