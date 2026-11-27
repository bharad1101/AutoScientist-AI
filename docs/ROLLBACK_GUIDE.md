# Rollback Guide

Status: **procedures documented; no rollback was performed.**

Rollback is operator-controlled. No command in this repository changes remote
branches, provider services, or managed databases automatically.

## 1. Decide the rollback scope

| Symptom | Scope |
|---|---|
| Frontend UI broken, backend healthy | Roll back Vercel deployment only |
| Backend unhealthy but database healthy | Roll back Render backend only |
| Flyway migration failed or schema incompatible | Stop writes; database rollback with operator review |
| Bad commit deployed to both providers | Roll back both provider deployments in order: backend then frontend |

Do not roll back the database unless the schema is actually incompatible.
Rolling back application code to V1-compatible code while leaving V2–V7 applied
may be safer than reversing migrations.

## 2. Application rollback

1. Identify the last known-good commit SHA from Git and the provider dashboard.
2. Confirm `/api/health/ready` failures are not caused by missing environment
   variables or provider outage first.
3. In Render, redeploy the last known-good commit for the affected environment.
   Do not delete the service or persistent disk.
4. In Vercel, promote or redeploy the last known-good deployment for the
   affected environment. Confirm `VITE_API_BASE_URL` matches the backend
   environment.
5. Verify readiness, browser login, upload, analysis, and PDF export.
6. Record the SHA and URLs in the incident/release report.

Do not revert `main` with a force push. If a fix is needed, create a new commit
and let CI deploy it.

## 3. Database rollback

Flyway does not provide automatic down migrations in this repository.

1. Stop or scale down writes to the affected backend service.
2. Take an immediate logical backup with `mysqldump --single-transaction`.
3. Identify the failed version in `flyway_schema_history`.
4. For a pre-migration snapshot, restore into a disposable copy first and test
   the application against it.
5. If a migration must be reversed, perform an operator-reviewed SQL restoration
   on the same provider/environment. Never copy preview data into production.
6. Reconcile `flyway_schema_history` only with documented operator approval.
   `flyway repair` is appropriate when checksum metadata is wrong, not when
   applied schema objects still differ.
7. Redeploy code compatible with the restored schema and rerun readiness checks.

For V7, remember that a failed foreign key may require cleaning orphaned
`owner_id` values on a copy before retrying the migration. For V2, review
whether tables were created or altered before choosing a reversal.

## 4. Uploaded files

The Render disk at `/app/uploads` is separate from the database. Do not delete
it during an application rollback. If a release wrote invalid files, move only
the affected files to a backup location inside the mounted disk and test with
the restored application.

## 5. Post-rollback verification

- `/api/health/ready` returns `status: UP` and `migrations: UP`.
- Provider dashboards show the expected restored SHA.
- Login, one upload, one analysis, and PDF export succeed.
- CORS works from the deployed frontend origin.
- No secret value appears in logs, commits, or documentation.
- Create a new fix commit through the normal CI path; do not silently leave the
  release branch pointing at an older SHA.
