# Final Release Gate — AutoScientist AI

Date: 2026-10-30
Scope: final verification before pushing source to GitHub. No push, commit, deploy, release, or history change was performed.

## Gate results

| Gate | Status | Detail |
|---|---|---|
| Source fixes present | PASS | Report IDOR fix, disjoint ML splits, portable `mvnw`, docs |
| Regression tests | PASS | 5 new tests; all pass |
| Password-reset work preserved | PASS | Backend service/entity/repository/migration and frontend pages/tests intact |
| Backend `clean test` | PASS | 106 tests, 0 failures, 0 errors, 2 skipped |
| Backend `verify` | PASS | 106 tests, 0 failures, 0 errors, 2 skipped |
| Backend package | PASS | Executable jar, Java 17, Spring Boot 3.2.4 |
| Frontend tests | PASS | 126 tests / 21 files |
| Real MySQL migrations | HISTORICAL PASS | 2/2 on isolated MySQL 9.7.1; current dependency is Flyway 11.7.2; re-test current build on staging MySQL 8.x |
| Frontend production build | BLOCKED | Real `VITE_API_BASE_URL` unavailable; localhost guard not bypassed |
| Production config completeness | BLOCKED | Datasource, JWT, CORS, frontend URL, SMTP, upload disk not confirmed |
| Tracked secrets | PASS | Only `.env.example` files tracked |
| Upload persistence | WARNING | Local filesystem; Docker declares `/app/uploads`; provider must mount durable storage |
| Dependency risk | PASS (current audit) | Vite 8.3.5, Vitest 4.1.12, React Router 7.18.5; post-upgrade npm audit reports 0 vulnerabilities |
| Deployment pipeline | NOT VERIFIED | No repo CI workflow; provider config cannot be confirmed from source |

## Blockers

1. **Production frontend URL missing** — `VITE_API_BASE_URL` must be an absolute HTTPS URL ending in `/api`; the repository `.env` is localhost and is correctly rejected.
2. **Production environment values missing** — datasource, `JWT_SECRET`, `APP_CORS_ALLOWED_ORIGINS`, `FRONTEND_URL`, SMTP, and `FILE_UPLOAD_DIR` must be set in the hosting provider.
3. **Persistent upload storage unconfirmed** — the Dockerfile creates `/app/uploads` but does not declare a volume.
4. **Staging MySQL 8.x not available** — the current Flyway dependency is 11.7.2; historical MySQL 9.7.1 success does not establish compatibility with provider staging. Current Flyway docs list MySQL 5.7, 8.0, and 9.4 as verified.
5. **Provider pipeline unverified** — build commands, health check, port wiring, disk mount, and CORS must be confirmed in the provider dashboard.

## Decision

**READY AFTER EXTERNAL DASHBOARD CONFIGURATION.** See the current test results and deployment readiness report for this pass; prior test and dependency results below are historical.

Pushing the source is acceptable: the codebase builds, tests pass, the security defect is fixed, and no secrets or generated artifacts are tracked. Deployment must wait until blockers 1–5 are resolved and the frontend is rebuilt with the real API URL. The existing `frontend/dist/` placeholder bundle must not be deployed.

After the operator supplies the production URL and environment values, re-run:

```bash
cd backend && ./mvnw clean test && ./mvnw verify && ./mvnw -DskipTests package
cd frontend && npm test -- --run
cd frontend && VITE_API_BASE_URL='https://<real-backend>/api' npm run build
git diff --check
```

Then complete the provider checklist in `DEPLOYMENT_GUIDE.md` and the post-deploy smoke test.
