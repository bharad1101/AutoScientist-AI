# Release Notes — Pre-Push Hardening Pass

Date: 2026-10-30

## Security

- Fixed a cross-tenant authorization defect in report generation: a supplied `researchQuestionId` must now belong to both the authenticated user and the requested dataset.
- Scoped default research-question selection to the current user.
- Added `ReportOwnershipSecurityTest` regression coverage.

## Correctness

- Fixed train/validation/test leakage for tiny datasets in `FeatureEngineeringEngine`. Partitions are always disjoint; datasets with fewer than three usable rows degrade to train-only or train+test.
- Training metadata now records `smallSample` and `splitWarning`.
- Added `FeatureEngineeringSplitTest` coverage.
- Updated `RealMysqlMigrationTest` to expect the current migration set (V1–V4 fresh, V2–V4 legacy) instead of the stale V1+V2 expectation.

## Tooling and documentation

- Replaced the machine-specific `backend/mvnw` stub with a portable launcher.
- Corrected README Java 21 → Java 17.
- Ignored the stray root macOS bookmark file.
- Updated deployment docs for the V1–V4 migration set.
- Added the `release-readiness/` documentation set.

## Verification

- Backend: `clean test` and `verify` → 106 tests, 0 failures, 0 errors, 2 skipped.
- Backend: executable jar (Java 17, Spring Boot 3.2.4).
- Frontend: 126 tests across 21 files, all passing.
- Historical real MySQL: 2/2 migration tests passed on isolated MySQL 9.7.1 (V1–V4 fresh, V2–V4 legacy). Current build uses Flyway 11.7.2; verify on staging MySQL 8.x with the current dependency graph.
- Frontend production build: **BLOCKED** pending the real `VITE_API_BASE_URL`; no placeholder build was used for the release gate.

## Known issues / follow-ups

- Real production API URL and staging MySQL credentials are not available in this environment.
- Initial `npm audit` reported 7 advisories; the dependency remediation update below upgraded these packages and current audit reports 0.
- The current `frontend/dist/` is an ignored placeholder build and must not be deployed.
- No commit, push, deploy, release, or Git-history change was performed.

## Dependency remediation update (2026-10-30)

- Upgraded frontend Vite to 8.3.5, Vitest to 4.1.12, `@vitejs/plugin-react` to 6.1.2, and runtime React Router to 7.18.5.
- Updated the lockfile; `npm test -- --run` passes (126 tests), and `npm audit --json` now reports 0 vulnerabilities.
- Pin the provider build to Node 22.12 or newer. The production build remains blocked pending the real HTTPS API URL.
