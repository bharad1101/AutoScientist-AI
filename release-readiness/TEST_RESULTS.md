# Test Results

Date: 2026-10-30

## This pass

| Check | Result |
|---|---|
| Backend `./mvnw clean test` | **PASS:** 106 tests, 0 failures, 0 errors, 2 skipped (real MySQL tests skipped without staging URL). First run exposed null handling in hosted marker detection; guard was fixed and rerun passed. |
| Backend `./mvnw verify` | **PASS:** 106 tests, 0 failures, 0 errors, 2 skipped; executable jar repackaged. |
| Backend `./mvnw -DskipTests package` | **PASS:** executable JAR created. |
| Backend Flyway dependency | **11.7.2** via Spring Boot 3.5.16 dependency management. |
| Frontend dependency upgrades | **PASS:** Vite 8.3.5, Vitest 4.1.12, `@vitejs/plugin-react` 6.1.2, React Router 7.18.5; lockfile updated. |
| Frontend `npm ci` | **PASS:** clean lockfile install; 0 vulnerabilities reported. |
| Frontend `npm test -- --run` | **PASS after clean install and history merge:** 21 test files, 126 tests passed. |
| Frontend `npm run build` | **Blocked as intended:** repository `.env` points at `http://localhost:8080/api`; guard refused to bake localhost into production. Real API URL unavailable. |
| `npm audit --json` | **PASS after upgrades:** 0 vulnerabilities. |
| Docker image build | **Blocked:** Docker CLI is present, but daemon is unavailable (`Cannot connect to ... docker.sock`). Root-context image was attempted after updating the Dockerfile. |
| `git diff --check` | PASS after merge; final check recorded with final repository state. |

Git history: fetch of `origin` and `rajesh` succeeded. Local four-ahead/four-behind divergence was reconciled with a local merge commit; `main` now tracks `origin/main`. No push or deployment occurred.

## Previously recorded evidence

Existing readiness notes report two real MySQL migration tests passing on isolated MySQL 9.7.1. No staging MySQL 8.x test was available in this pass. Current build resolves Flyway 11.7.2, so rerun migrations against the actual staging database using this dependency graph.

## Not verified

No MySQL 8.x staging credentials, actual production API URL, provider pipeline access, or persistent provider disk were available. Docker CLI is installed, but its daemon was unavailable, so the container build and startup were not verified. No live deploy or production test was performed.
