# Project Audit — AutoScientist AI

Date: 2026-10-30 (final release gate)
Scope: `/Applications/AutoScientist AI` — backend, frontend, database, docs, scripts, deployment artifacts.

## 1. Stack

| Layer | Technology |
|---|---|
| Backend | Java 17, Spring Boot 3.5.16, Maven, Spring Security + JWT, Spring Data JPA, Flyway 11.7.2 |
| Frontend | React 18, Vite 8.3.5, Bootstrap 5, Chart.js, Vitest 4.1.12, React Router 7.18.5 |
| Persistence | MySQL 8.0+ (production), H2 (dev/test) |
| Deployment artifacts | root and backend Dockerfiles, `frontend/vercel.json`, `scripts/*.sh` |

## 2. Final verification

| Check | Status | Evidence |
|---|---|---|
| Backend tests | PASS | `./mvnw clean test` and `./mvnw verify` → 106 tests, 0 failures, 0 errors, 2 skipped |
| Backend package | PASS | Executable jar, Java 17, Spring Boot 3.5.16 |
| Frontend tests | PASS | 126 tests across 21 files |
| Frontend production build | BLOCKED | No real `VITE_API_BASE_URL`; localhost guard correctly refuses the repository `.env` |
| Real MySQL migrations | HISTORICAL PASS | 2/2 on isolated MySQL 9.7.1; current build uses Flyway 11.7.2, re-check staging MySQL 8.x |
| Docker image build | BLOCKED | Docker daemon unavailable in this pass |
| Tracked secrets | PASS | Only `.env.example` files are tracked; no real credentials found |
| CI workflows in repo | NOT FOUND | Deployment is external; pipeline behavior must be verified in the provider |

## 3. Findings and disposition

| Severity | Area | Finding | Disposition |
|---|---|---|---|
| High | Authorization | Report generation accepted any `researchQuestionId` (cross-tenant data exposure) | Fixed: question must belong to the requesting user and dataset; regression tests added |
| High | Maven portability | `backend/mvnw` hardcoded a local user path | Fixed: portable resolution order |
| Medium | ML correctness | Tiny datasets reused rows for train/val/test (target leakage) | Fixed: disjoint partitions, `smallSample` flag, regression tests |
| Medium | MySQL test drift | `RealMysqlMigrationTest` expected only V1+V2; actual migrations are V1–V4 | Fixed expectations; tests pass on real MySQL |
| Medium | Dependencies | Initial audit found 7 npm advisories | Upgraded Vite/Vitest/React Router; current audit reports 0 |
| Low | Documentation | README claimed Java 21 | Fixed to Java 17 |
| Low | Repository hygiene | Stray root macOS bookmark file | Ignored; file untouched |
| Info | Deployment | Uploads use local filesystem; Docker declares `/app/uploads` volume | Provider must mount durable storage and verify uid 10001 permissions |
| Info | Configuration | Dev JWT fallback exists in `default` profile only | `mysql` profile requires `JWT_SECRET`; deployment guard blocks dev on hosted runtimes |

## 4. Files changed by the hardening passes

- `backend/src/main/java/com/autoscientist/service/ReportService.java` — ownership scoping.
- `backend/src/main/java/com/autoscientist/analysis/feature/FeatureEngineeringEngine.java` — disjoint splits.
- `backend/src/test/java/com/autoscientist/ReportOwnershipSecurityTest.java` — new.
- `backend/src/test/java/com/autoscientist/FeatureEngineeringSplitTest.java` — new.
- `backend/src/test/java/com/autoscientist/RealMysqlMigrationTest.java` — updated migration counts/comments.
- `backend/mvnw` — portable launcher.
- `README.md`, `.gitignore`, `docs/DEPLOYMENT.md` — accuracy updates.
- `release-readiness/*` — audit and gate documentation.

Pre-existing password-reset work (backend service/entity/repository/migration, frontend pages/tests, related auth/config changes) is preserved and included in the working tree.

## 5. Remaining blockers

1. Real production `VITE_API_BASE_URL` is not available → production frontend build and end-to-end integration unverified.
2. Staging MySQL 8.x credentials are not available → production-version migration verification pending (real MySQL 9.7 passed).
3. Hosting-provider configuration (env vars, persistent disk, CORS, DB privileges, health check) must be confirmed by the operator.
4. Docker image build and startup require a working Docker daemon for verification.
