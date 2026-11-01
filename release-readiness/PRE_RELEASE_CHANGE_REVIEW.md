# AutoScientist AI — Pre-Release Change Review

- **Date:** 2026-11-01
- **Review type:** Read-only pre-release change review
- **Repository:** `/Applications/AutoScientist AI`
- **Branch:** `main`
- **HEAD:** `8b4cf87` (`fix: align Flyway support with MySQL 8.4`)
- **Production baseline:** `d13e6c6` (`origin/main`)
- **Working tree before this report:** 12 modified tracked files, 7 untracked files, 0 staged
- **Unpushed commit:** `8b4cf87` is one commit ahead of `origin/main`
- **Production services reviewed read-only:** Vercel frontend, Render backend
- **Mutations performed:** None. No commit, push, deployment, provider setting change, environment-variable change, production migration, service restart, or production data change was performed.

## Decision

**APPROVE FOR STAGING**

**Production is NOT approved.** The local candidate is technically suitable for staging verification, but production remains blocked by unverified provider configuration, an unverified Aiven schema/backup, no authenticated production smoke test, unverified SMTP delivery, and unverified upload persistence. The production authentication issue must not be described as fixed until it is tested against the deployed backend and database.

---

## 1. Local changes

### 1.1 Commands and results

| Check | Result |
|---|---|
| `git status` | `main` is ahead of `origin/main` by 1 commit; 12 modified tracked files; 7 untracked release documents; nothing staged |
| `git diff --stat` | 12 files changed, 321 insertions, 113 deletions |
| `git diff --check` | Clean; no whitespace errors |
| `git diff --cached` | Empty; no staged changes |
| `git diff` | Reviewed in full |
| Untracked files | Enumerated with `git ls-files --others --exclude-standard` and reviewed |
| Secret-pattern scan of changed/untracked files | No private keys, cloud keys, GitHub/Slack/Google/OpenAI-style tokens, or obvious high-entropy credentials found |
| Tracked `.env` files | None; only `.env.example` files are tracked |

The unpushed commit `8b4cf87` changes `backend/pom.xml` to pin Flyway at `11.15.0` and adds the prior release acceptance report. That commit would be included in any push of `main`.

### 1.2 Modified tracked files

| File | Why it changed | Behavior change | Risk area | Necessary for this release? |
|---|---|---|---|---|
| `backend/src/main/java/com/autoscientist/config/SecurityConfig.java` | Permit the new readiness endpoint. | Adds `/api/health/ready` to the public matcher list. All other authorization rules are unchanged. | Authentication/security | Yes, otherwise Render/operators cannot call readiness without a token. |
| `backend/src/main/java/com/autoscientist/controller/HealthController.java` | Add a datasource-aware readiness probe. | `/api/health` still returns `UP` and timestamp. New `/api/health/ready` runs `SELECT 1`, returns 200/503, and reports only `status`, `database`, and timestamp. Injects `DataSource`. | Security/operations/database | Yes, needed to distinguish process liveness from database readiness. |
| `backend/src/main/java/com/autoscientist/security/UserDetailsServiceImpl.java` | Make email normalization locale-independent. | Uses `Locale.ROOT` instead of the JVM default locale when lowercasing. | Authentication | Yes for locale-safe login lookup. |
| `backend/src/main/java/com/autoscientist/service/AuthService.java` | Harden registration/login consistency. | Rejects registration passwords over 72 UTF-8 bytes with a clear 400; uses `Locale.ROOT`; checks duplicate email with one case-insensitive query; uses `saveAndFlush` and converts a duplicate-key race to 400; refactors current-user lookup. | Authentication/database | Yes for the stated login/registration hardening. |
| `backend/src/test/java/com/autoscientist/AuthServiceTest.java` | Update mocks and add regression tests. | Tests 72-byte rejection, normalization, duplicate race, and `saveAndFlush`. | Test-only | Yes for regression coverage. |
| `backend/src/test/java/com/autoscientist/HealthControllerTest.java` | Cover the new readiness endpoint. | Verifies `/api/health/ready` is public and reports database `UP`. | Test-only | Yes for regression coverage. |
| `backend/src/test/java/com/autoscientist/RealMysqlMigrationTest.java` | Fix the real-MySQL test harness. | Passes the MySQL driver/dialect explicitly; drops the quality-report FK before its unique index and re-adds it; uses `DROP TABLE IF EXISTS`. | Test-only; database test infrastructure | Yes for reliable real-MySQL validation. |
| `docs/DEPLOYMENT.md` | Correct the legacy migration description. | Documents the legacy path as `baseline@1 + V2–V4 + validate`, matching production `ddl-auto=validate`. | Documentation | Useful, not runtime-critical. |
| `frontend/src/pages/LoginPage.jsx` | Stop advertising development-only credentials in production. | Renders the demo credential block only when `import.meta.env.DEV` is true; labels it “Local Development Credentials.” | Frontend/security UX | Yes for the production login-page fix. |
| `frontend/src/pages/RegisterPage.jsx` | Mirror the backend password limit client-side. | Rejects passwords over 72 UTF-8 bytes before calling the API with a clear message. | Frontend/auth UX | Yes for consistent registration validation. |
| `release-readiness/FINAL_RELEASE_ACCEPTANCE_REPORT.md` | Update release acceptance evidence and decision. | Documentation only. | Documentation | Release record. |
| `release-readiness/TEST_RESULTS.md` | Update test evidence. | Documentation only. | Documentation | Release record. |

### 1.3 Untracked files

| File | Purpose | Runtime effect |
|---|---|---|
| `release-readiness/DEPLOYMENT_CHECKLIST.md` | Pre/post-deployment checklist, including backup and smoke-test gates. | None |
| `release-readiness/DEPLOYMENT_CONFIGURATION.md` | Intended Render, Vercel, Aiven, and application configuration. | None |
| `release-readiness/FINAL_GO_NO_GO_REPORT.md` | Prior go/no-go summary and blocker list. | None |
| `release-readiness/PROVIDER_ACCESS_STATUS.md` | What was and was not verified for each provider. | None |
| `release-readiness/RELEASE_PLAN.md` | Release sequence, approvals, and rollback triggers. | None |
| `release-readiness/ROLLBACK_PLAN.md` | Frontend, backend, database, and full-service rollback procedures. | None |
| `release-readiness/SMOKE_TEST_REPORT.md` | Local and read-only production smoke evidence. | None |
| `release-readiness/PRE_RELEASE_CHANGE_REVIEW.md` | This review. | None |

The untracked documents are release records, not application code. Some contain claims that are stronger than the underlying evidence, particularly around password-reset normalization and production verification. Those are noted below.

### 1.4 Findings from the diff

- No unrelated source behavior was changed.
- No migrations were edited or deleted.
- No features were removed or replaced with mocks.
- No `.env`, credential, backup, upload, generated database, or build artifact is part of the candidate.
- The only staged state is empty; nothing was accidentally staged.

---

## 2. High-risk modification review

### 2.1 Authentication

**Authentication has not been weakened.**

- `SecurityConfig` remains stateless, CSRF-disabled as before, with `/api/auth/**` public and all other paths requiring authentication. The only new public path is `/api/health/ready`.
- BCrypt remains the password encoder. No password-check bypass, no plaintext storage, no token-validation relaxation, and no change to JWT generation/validation was introduced.
- `AuthService.register` now rejects passwords over 72 UTF-8 bytes before BCrypt is called. This is stricter, not weaker.
- `AuthService.login` still delegates credential verification to `AuthenticationManager`; it does not compare passwords itself.
- `UserDetailsServiceImpl` still loads the user by email and builds standard authorities.
- The duplicate-registration race is converted from a possible 500 to a 400. This does not grant access.
- Existing account lookup remains case-insensitive through `findByEmailIgnoreCase`. Stored password hashes are not rehashed, reset, or overwritten by any changed code.

**Residual authentication issue — password reset is not fully aligned.**

`PasswordResetService.requestReset` still uses the JVM default locale:

```java
String normalized = email == null ? "" : email.trim().toLowerCase();
```

It does not use `Locale.ROOT`. `resetPassword` also has no 72-byte guard; a reset password over 72 UTF-8 bytes reaches `BCryptPasswordEncoder.encode`, which throws `IllegalArgumentException` (“password cannot be more than 72 bytes”). The global handler returns that as a 400, so this is not an authentication bypass or a server error, but it is inconsistent with the new registration message.

This does not weaken authentication, but it means the release plan’s statement that “Password-reset and login email normalization uses `Locale.ROOT`” is not accurate. Either patch the reset path or correct the release notes before production.

### 2.2 Health endpoints and information disclosure

- `/api/health` still returns only `status` and `timestamp`; it does not query the database.
- `/api/health/ready` returns only `status`, `database`, and `timestamp` on success, or a generic `503` payload with `success=false` and `message="Service is not ready."` on failure.
- Database failure logging records only the exception class name, not the message, SQL, connection string, host, username, or stack trace.
- Neither endpoint returns environment details, versions, secrets, data, or connection information.
- The readiness endpoint is public. It does reveal whether the database is reachable. That is the intended operational behavior, but it also means an unauthenticated caller can trigger a datasource check. Consider provider-level rate limiting if that becomes an abuse concern. This is not a release blocker.

### 2.3 Existing users and password hashes

- No migration or application change deletes users or rewrites non-empty password hashes.
- `V2` backfills `users.password_hash` from a legacy `password` column only when `password_hash` is null or empty.
- `DataInitializer` performs the same guarded backfill only when the legacy column exists and the target is empty.
- `V2` relaxes the legacy `password` column from `NOT NULL` to nullable but keeps the column and its values.
- Existing mixed-case email accounts remain findable through case-insensitive lookup; new registrations are stored lowercased.
- The real-MySQL legacy test asserts a legacy BCrypt hash survives into `password_hash` and a pre-existing user row remains.

**Conclusion:** repository changes preserve valid accounts and existing hashes by inspection and by the local real-MySQL legacy test. Preservation in the actual Aiven database is not verified.

### 2.4 Database migrations

- `V1` creates the baseline tables with `CREATE TABLE IF NOT EXISTS`.
- `V2` adds/backfills provenance, legacy columns, and the quality-report uniqueness constraint with guards. It does not drop tables, columns, or rows.
- `V3` adds confidence-interval columns with guards. It does not drop data.
- `V4` adds the password-reset token table with `CREATE TABLE IF NOT EXISTS`.
- No destructive SQL keywords are executed by the migrations. The only `ON DELETE CASCADE` is on password-reset tokens, not user data.
- The production `mysql` profile uses Flyway with `baseline-on-migrate=true`, `baseline-version=1`, `validate-on-migrate=true`, and Hibernate `ddl-auto=validate`.
- Local evidence (as reported and reviewed in the code): fresh `V1–V4` and legacy baseline plus `V2–V4` both passed against disposable MySQL 9.7.1, including data preservation and idempotent re-run.

**Conclusion:** the migration scripts are non-destructive by inspection. They are not proven safe against the actual Aiven version/schema, and the actual Aiven version is not verified.

### 2.5 Deployment configuration

- Root `Dockerfile` builds the backend with Maven/Temurin 17 and runs it as uid 10001 through `backend/docker-entrypoint.sh`.
- The entrypoint repairs upload/data ownership when started as root, verifies the upload directory is writable, and forwards `PORT` to the JVM.
- The image sets `SPRING_PROFILES_ACTIVE=mysql`, `FILE_UPLOAD_DIR=/app/uploads/datasets`, and `PORT=8080`.
- `frontend/vercel.json` rewrites all routes to `/index.html`.
- The `mysql` profile requires `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`, `JWT_SECRET`, `APP_CORS_ALLOWED_ORIGINS`, `FILE_UPLOAD_DIR`, and `FRONTEND_URL`; startup fails if required values are missing.
- The default profile retains a development JWT fallback, which is existing behavior; the `mysql` profile has no fallback.
- `VOLUME ["/app/uploads"]` in the Dockerfile does not prove that Render provides persistent storage.

### 2.6 Per-file high-risk conclusions

| File | Conclusion |
|---|---|
| `SecurityConfig.java` | Safe; only adds the public readiness path. No authorization weakening. |
| `HealthController.java` | Safe; no sensitive output. Public DB probe is intentional; consider rate limiting. |
| `UserDetailsServiceImpl.java` | Safe; locale-safe normalization. |
| `AuthService.java` | Safe and stricter. Minor: the broad `DataIntegrityViolationException` catch could mislabel a non-email integrity failure as “email already registered.” |
| `AuthServiceTest.java` | Useful coverage, but the “RootLocale” test does not actually change the default locale, so it does not reproduce a Turkish-locale failure. |
| `HealthControllerTest.java` | Valid integration coverage for the new endpoint. |
| `RealMysqlMigrationTest.java` | Test-only; improves driver/dialect correctness and legacy cleanup. Does not affect production. |
| `LoginPage.jsx` | Correct production hiding of dev credentials. No automated test covers production-mode absence; bundle inspection is the evidence. |
| `RegisterPage.jsx` | Correct client-side guard. No frontend test covers the over-72-byte branch. |
| `docs/DEPLOYMENT.md` | Corrects legacy migration wording to match `validate`. |
| Release-readiness documents | Documentation only; some statements require correction or stronger evidence, noted in this review. |

---

## 3. Production login fix review

### 3.1 What caused the previous login/registration inconsistency?

**No single root cause is established by the available evidence.** No production log, reproduction, or database inspection proves why a specific production login/registration failure occurred.

What is confirmed:

- The production frontend at `https://auto-scientist-ai-ten.vercel.app` still serves the pre-candidate bundle.
- That bundle contains `jane@university.edu`, `SecurePassword123`, and “Demo Credentials.”
- Those demo accounts are seeded only in the `dev` profile. They do not exist in the production `mysql` profile unless separately created.
- Therefore the production login page advertises credentials that are expected to fail in production. This is a confirmed user-facing inconsistency.

What the candidate also addresses defensively:

- Locale-dependent lowercasing could cause registration and login to normalize the same email differently on a non-root-locale JVM.
- BCrypt rejects passwords over 72 UTF-8 bytes with a technical message; registration now returns a clear 400 first.
- A concurrent duplicate registration could surface as an opaque 500; it now returns a 400.

These are plausible contributors, but they are not proven to be the production root cause. The report must not claim the production authentication issue is fixed.

### 3.2 What code changed

- `AuthService.register`: 72-byte UTF-8 guard; `Locale.ROOT` normalization; case-insensitive duplicate check; `saveAndFlush`; duplicate-race translation to `BadRequestException`.
- `AuthService.login`: `Locale.ROOT` normalization and lookup.
- `AuthService` current-user lookup: shared locale-safe helper.
- `UserDetailsServiceImpl`: `Locale.ROOT` normalization.
- `LoginPage.jsx`: dev-only demo credential block.
- `RegisterPage.jsx`: client-side 72-byte guard.
- `SecurityConfig` / `HealthController`: new public readiness probe.
- Tests: auth service regressions, readiness integration test, real-MySQL harness fixes.

### 3.3 Tests that reproduce the original issue

| Test | Reproduces original issue? | Notes |
|---|---|---|
| `AuthServiceTest.testRegisterRejectsPasswordOverBcryptByteLimit` | **Yes, for the over-72-byte registration issue.** | Before the guard, the service passed the password to `BCryptPasswordEncoder.encode`, which throws `IllegalArgumentException: password cannot be more than 72 bytes`. The new test expects `BadRequestException` containing “72 bytes.” |
| `AuthServiceTest.testRegisterConvertsDuplicateRaceToBadRequest` | **Yes, for the duplicate-race behavior.** | Simulates a `DataIntegrityViolationException` from `saveAndFlush`; the old code did not catch it, so the request would not become a clean 400. |
| `AuthServiceTest.testRegisterNormalizesEmailWithRootLocale` | **No, not in its current form.** | It asserts trimming and lowercasing, but it never sets a non-root default locale, so it would pass against the old `toLowerCase()` code in a normal JVM. |
| `AuthIntegrationTest` case/whitespace tests | **No, these pre-date the fix.** | They already exercised case-insensitive and whitespace-trimmed login before this candidate. |
| `HealthControllerTest.readinessEndpointIsPublicAndChecksDatabase` | **No; this tests a new endpoint,** not the original login issue. | Confirms the new readiness behavior. |
| Login-page demo-credential tests | **No automated production-mode test.** | The existing `LoginPage.test.jsx` still expects Quick Fill because Vitest runs in dev mode. Production absence was checked by bundle inspection only. |

### 3.4 Tests that demonstrate the fix

- The over-72-byte regression test demonstrates the new clear `400` behavior.
- The duplicate-race test demonstrates the new `400` instead of an opaque failure.
- The readiness integration test demonstrates public 200 with `database=UP`.
- The local smoke report claims mixed-case/whitespace login succeeds, duplicate registration returns 400, over-72-byte registration returns 400, and invalid tokens return 401. Those results are reported, not re-executed in this read-only review.
- The local production bundle check is reported to contain no demo credentials and no localhost API URL. This is bundle inspection, not a browser-level test.
- The real-MySQL tests demonstrate migration/data-preservation behavior on disposable MySQL 9.7.1, not production.

### 3.5 What remains unverified in production

- Whether existing production accounts can log in after deployment.
- Whether existing production password hashes are in the format the current encoder expects.
- Whether production registration and login return the expected results against the actual Aiven database.
- Whether case/whitespace normalization behaves as expected against production data.
- Whether the duplicate-race path behaves correctly under real concurrent load.
- Whether the deployed backend serves `/api/health/ready`; it currently returns 401 because the readiness change is not deployed.
- Whether the deployed frontend hides demo credentials; the live bundle still contains them.
- Whether production SMTP delivers password-reset mail.
- Whether production migrations have run cleanly against the actual Aiven schema.
- Whether uploads survive a real provider restart.

**Do not claim the production authentication issue is fixed until the above are verified.**

---

## 4. Provider access status

### 4.1 Summary

| Provider | Access status | What was verified in this review | What remains unavailable |
|---|---|---|---|
| GitHub | **Authenticated / verified** | `gh` active account, repository, `main`, commit, permissions, status, Actions secrets/variables, branch protection | Repository settings not needed for this gate |
| Render | **Unavailable / unverified** | Live public health/auth behavior only | Dashboard/API, service config, env names, disk, logs, auto-deploy |
| Vercel | **Unavailable / unverified** | Live frontend response and bundle contents only | Dashboard/API, root/build/output, env values, domain, auto-deploy |
| Aiven | **Unavailable / blocked** | Nothing on the actual Aiven service | Version, schema, TLS, privileges, backups, network, connection limits |

### 4.2 GitHub

- GitHub CLI is authenticated. Active account: `bharad1101`.
- Observed token scopes: `gist`, `read:org`, `repo`, `workflow`.
- Other authenticated accounts exist but are inactive: `Rajesh27-k`, `vaishnavipittala1811-lang`.
- Repository: `bharad1101/AutoScientist-AI`; public.
- Authenticated account has `admin`, `maintain`, `push`, `triage`, and `pull` permission.
- `origin/main` is `d13e6c6`; `main` is not branch-protected.
- Commit `d13e6c6` has a successful Vercel status.
- No GitHub Actions workflows, Actions secrets, or Actions variables are configured.
- Local `HEAD` is `8b4cf87`, one commit ahead of `origin/main`; no push was performed.

### 4.3 Render

- No Render CLI, API token, or dashboard session is available.
- `render` CLI is not installed; no Render configuration directory or token was found.
- Read-only live checks:
  - The first `/api/health` attempts timed out at 40 s and 90 s, consistent with a Render cold start or edge/transport delay.
  - A later HTTP/1.1 request returned HTTP 200 with `status=UP` in 0.35 s.
  - `/api/health/ready` returned HTTP 401 in 20.6 s, confirming the readiness change is not deployed.
  - `/api/datasets` returned HTTP 401 without a token.
  - `/` returned HTTP 404, expected for an API-only service.
- Not verified: service source/branch, root directory, Dockerfile path, environment variable names, persistent disk, health-check path, logs, auto-deploy, region, instance type, deployment ID.

### 4.4 Vercel

- No Vercel CLI, API token, or dashboard session is available.
- `vercel` CLI is not installed; no Vercel configuration directory or token was found.
- Read-only live checks:
  - `https://auto-scientist-ai-ten.vercel.app/` returned HTTP 200.
  - The live bundle references `https://autoscientist-ai-backend.onrender.com/api`.
  - The live bundle contains `jane@university.edu`, `SecurePassword123`, and “Demo Credentials.”
  - The live bundle does not contain “Local Development Credentials.”
  - The live bundle does not contain `localhost:8080` or `127.0.0.1`.
- The presence of the demo credentials confirms production is still serving the pre-candidate frontend.
- Not verified: project root, build command, output directory, Production `VITE_API_BASE_URL` setting, auto-deploy, domain/DNS, environment separation, rollback history.

### 4.5 Aiven

- No Aiven CLI, API token, or dashboard session is available.
- `avn` and `aiven` CLIs are not installed; no Aiven configuration directory or token was found.
- Not verified: service identity/version, database name/user, TLS mode and CA, Flyway schema history, backup schedule/latest restorable backup, connection limits, network/firewall path from Render, production data consistency.
- Local backup artifacts exist in the repository workspace, but they are not proof of an Aiven production backup and were not restored or validated against Aiven.

### 4.6 Environment-variable names only

The following names are referenced by the release documentation. No values are included in this report.

**Backend/Render:** `SPRING_PROFILES_ACTIVE`, `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`, `JWT_SECRET`, `JWT_EXPIRATION_MS`, `APP_CORS_ALLOWED_ORIGINS`, `FRONTEND_URL`, `FILE_UPLOAD_DIR`, `SPRING_FLYWAY_ENABLED`, `SPRING_JPA_HIBERNATE_DDL_AUTO`, `PORT`, `DB_POOL_MAX_SIZE`, `DB_POOL_MIN_IDLE`, `DB_CONNECTION_TIMEOUT_MS`.

**SMTP/password reset:** `SPRING_MAIL_HOST`, `SPRING_MAIL_PORT`, `SPRING_MAIL_USERNAME`, `SPRING_MAIL_PASSWORD`, `SPRING_MAIL_PROPERTIES_MAIL_SMTP_AUTH`, `SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_ENABLE`, `PASSWORD_RESET_FROM`, `FRONTEND_URL`, `PASSWORD_RESET_TTL_MINUTES`, `PASSWORD_RESET_MIN_INTERVAL_SECONDS`.

**Frontend/Vercel:** `VITE_API_BASE_URL`.

**Local real-MySQL test:** `AS_TEST_MYSQL_URL`, `AS_TEST_MYSQL_ENVIRONMENT`, `AS_TEST_MYSQL_USER`, `AS_TEST_MYSQL_PASSWORD`, `AS_TEST_MYSQL_ALLOW_SCHEMA_CREATE_DROP`.

---

## 5. Deployment blockers

| # | Blocker | What exactly needs verification | Can OpenCode do it? | Account authorization required? | User action required? | Blocks production? |
|---|---|---|---|---|---|---|
| 1 | Commit/push approval | Review and approve the exact candidate, including unpushed `8b4cf87` and the 19 working-tree entries. | Only with explicit approval. | Yes, release-owner approval. | Yes. | Yes. |
| 2 | Render service configuration | Repository/branch, root directory, Dockerfile path, Docker build, `SPRING_PROFILES_ACTIVE=mysql`, env names present, disk, health path, auto-deploy, logs. | No; no CLI/token/dashboard. | Yes. | Yes; provide access or have an operator confirm. | Yes. |
| 3 | Vercel project configuration | Root `frontend`, build `npm run build`, output `dist`, Production `VITE_API_BASE_URL`, SPA rewrite, production branch/domain, auto-deploy. | No; no CLI/token/dashboard. | Yes. | Yes; provide access or have an operator confirm. | Yes. |
| 4 | Aiven database compatibility | Actual MySQL version, database identity, TLS mode, migration user privileges, network reachability from Render, connection limits, `flyway_schema_history`. | No; no CLI/token/dashboard. | Yes. | Yes; provide access or have a database operator confirm. | Yes. |
| 5 | Aiven backup and restoration | A current restorable backup, backup identifier/timestamp, restore duration, restore owner, and a tested restore path. | No. | Yes. | Yes; database operator must confirm and preferably test restoration. | **Yes; hard gate.** |
| 6 | Authenticated production smoke test | Registration/login, existing account, upload, analysis, AutoML, reports/PDF, relogin, cross-user isolation against Render + Aiven. | No; no controlled production account. | Yes. | Yes; provide a controlled test account or authorize account creation. | Yes. |
| 7 | SMTP/password-reset delivery | Required SMTP names present, sender identity, TLS, and actual delivery; reset token lifecycle. | No; no production SMTP access. | Yes. | Yes; provide/confirm SMTP configuration and a test mailbox. | Yes if password reset is in scope; otherwise explicit risk acceptance. |
| 8 | Upload persistence | Render persistent disk mounted at `/app/uploads`, writable by uid 10001, and files survive a real restart. | No. | Yes. | Yes; provider operator must verify disk and perform/authorize a restart test. | Yes if uploads are in scope; otherwise explicit risk acceptance. |
| 9 | Production migration validation | Run the guarded real-MySQL test against a disposable staging schema of the same major version as Aiven; confirm target schema history. | No; no staging credentials. | Yes. | Yes; provide disposable staging credentials. | Yes for full database confidence. |
| 10 | Docker image build | Local image build/startup if desired. | No; Docker daemon is unavailable. | No. | Optional; start Docker Desktop if local pre-validation is wanted. | No; Render builds remotely. |
| 11 | HTTP/2 / cold-start anomaly | Confirm health behavior through Render's edge, including HTTP/1.1 and HTTP/2, and whether the timeouts are cold-start/edge related. | Partially; can make read-only requests, but not inspect Render edge. | Yes for provider-side inspection. | Yes; provider operator should check. | No by itself, but investigate before close. |
| 12 | Password-reset locale/72-byte gap | Decide whether to patch `PasswordResetService` to use `Locale.ROOT` and a 72-byte guard, or correct the release notes and accept the gap. | Can identify and patch only with approval; this review is read-only. | No for code change, but release-owner decision required. | Yes; decide patch vs. risk acceptance. | No security blocker, but release-note accuracy blocker. |

### 5.1 Aiven backup restoration — explicit status

**Unverified.** There is no evidence in this environment that an Aiven production backup exists, is current, or is restorable. The local `backups/` artifacts are not a substitute and were not tested against Aiven. A production deployment should not proceed until a database operator records the backup identifier/timestamp and confirms a restore path.

### 5.2 Database schema compatibility — explicit status

**Partially verified.** The repository migrations are additive and guarded, and the real-MySQL test paths passed locally on MySQL 9.7.1. The actual Aiven MySQL version, current schema, Flyway history, privileges, and TLS configuration are unknown. Compatibility with Aiven is therefore **not verified**.

### 5.3 SMTP — explicit status

**Unverified.** Required names are documented, but presence, sender identity, TLS, and delivery are not confirmed. Password-reset email delivery cannot be considered release-ready until tested.

### 5.4 Upload persistence — explicit status

**Unverified.** The image and entrypoint support a mounted upload directory, but Render disk attachment, ownership, writability, and restart persistence are unknown. A container `VOLUME` declaration is not proof.

---

## 6. Final recommendation

### Decision: **APPROVE FOR STAGING**

The local candidate is technically suitable for staging deployment and verification. It passes the reported local backend/frontend/package/bundle checks, the reviewed code changes are scoped and do not weaken authentication, the health endpoints do not disclose sensitive data, existing hashes are preserved by the additive migrations, and the migration scripts are non-destructive by inspection.

### Production: **NOT READY**

Production deployment is not approved until the blockers in Section 5 are resolved or explicitly risk-accepted. In particular:

1. Confirm a restorable Aiven backup and record its identifier.
2. Verify the actual Aiven schema, version, TLS, privileges, and Flyway history.
3. Verify Render and Vercel configuration read-only.
4. Deploy and test in staging first, including migrations against the same major MySQL version as Aiven.
5. Run an authenticated production smoke test with a controlled account.
6. Verify SMTP delivery and upload persistence across a restart.
7. Correct or explicitly scope the password-reset locale/72-byte documentation gap.
8. Obtain explicit approval to commit, push, and deploy.

No commit, push, deployment, provider change, environment-variable change, production migration, service restart, or production data change was performed during this review.
