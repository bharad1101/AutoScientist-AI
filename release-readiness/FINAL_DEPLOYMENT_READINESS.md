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
- Frontend dependencies were upgraded; post-upgrade npm audit reports 0 advisories. The production build still requires the real API URL.

## Required before push / enabling automatic deploy

Configure and verify the service settings and environment in `AUTOMATIC_DEPLOYMENT_GUIDE.md` and `PRODUCTION_ENVIRONMENT_VARIABLES.md`. Run the blocked provider-specific build with the real API URL. Confirm current Flyway migrations against the Aiven MySQL 8.x service in staging and verify an actual persistent mount. The current local `.env` points to localhost and is not suitable for production.

The checked-out `main` branch reports **4 commits ahead and 4 behind** its configured upstream (`rajesh/main`). Before pushing, inspect both sides and integrate the upstream history with a normal merge or rebase; do not force-push. This pass did not fetch or change Git history.

## Post-deploy acceptance

From a clean push, verify both providers build from the intended branch; backend starts in `mysql`; Flyway completes; `/api/health` returns HTTP 200; browser requests reach the HTTPS `/api` endpoint without CORS errors; upload a dataset, redeploy, and download/use the same dataset; exercise login, analysis, report, and password-reset email flows.

No successful live deployment is claimed.
