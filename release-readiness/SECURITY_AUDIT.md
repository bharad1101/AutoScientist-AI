# Security Audit

Date: 2026-10-30

## Verified controls

- JWT auth and BCrypt password hashing; the `mysql` profile requires `JWT_SECRET`.
- Hosted runtime guard rejects `dev`; embedded DB guard rejects H2 outside `dev`/`test`.
- Report question selection is scoped to user and dataset; dataset storage uses UUID names and normalized path containment.
- Password reset uses random single-use expiring tokens stored as hashes; SMTP must be configured for real delivery.
- CORS is configurable; production must use exact frontend origins and must not use `*` with credentials.
- Health response contains only status and timestamp.
- No provider secret values were supplied or added by this pass.

## Findings requiring action

- Configure strong production secrets and exact origins per `PRODUCTION_ENVIRONMENT_VARIABLES.md`.
- Production Hibernate DDL fallback is `validate`; keep Flyway enabled.
- Durable upload storage is not established by the Docker image alone.
- The initial npm audit found 7 advisories; Vite, Vitest, and React Router were upgraded to patched majors. The post-upgrade `npm audit` reports 0 vulnerabilities; 126 frontend tests pass.
- No Maven vulnerability scan was completed in this run.

See `DEPENDENCY_AUDIT.md` and `TEST_RESULTS.md` for details.
