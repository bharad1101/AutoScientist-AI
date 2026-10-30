# Pre-Push Checklist

## Local repository

- [ ] Review all existing and new diffs; preserve the password-reset work.
- [ ] `git diff --check` passes and no unintended files/secrets are included.
- [ ] Backend `./mvnw clean test`, `./mvnw verify`, and `./mvnw -DskipTests package` pass.
- [ ] Frontend tests pass; build with actual HTTPS `VITE_API_BASE_URL` ending `/api`.
- [ ] Rerun `npm audit` before push; this pass's post-upgrade audit reports 0 vulnerabilities.
- [ ] Verify migrations against isolated staging MySQL 8.x.

## Provider configuration

- [ ] Confirm connected GitHub repo, production branch, and automatic deploy-on-push for both services.
- [ ] Set production backend secrets and frontend build variable from `PRODUCTION_ENVIRONMENT_VARIABLES.md`.
- [ ] Set Hibernate DDL mode to `validate`; retain Flyway enabled.
- [ ] Attach durable disk at `/app/uploads`; verify uid 10001 write and data survives redeploy.
- [ ] Set health check `/api/health`, port forwarding, TLS, CORS, DB network access, and backups.
- [ ] Confirm SMTP delivery if password reset is a production feature.

No staging, commit, or push is performed by this checklist.

## Repository hygiene review

- [ ] Confirm no `.env` or real secret files are staged.
- [ ] Confirm `node_modules`, `dist`, `target`, uploads, backups, and local databases are not staged.
- [ ] Review the ignored root `AutoScientist AI` macOS bookmark file; leave it untouched.
- [ ] Confirm password-reset files/migration and tests remain intentional changes.
- [ ] Review `git status`, `git diff --check`, and `git diff --stat` immediately before push.

## Exact outstanding configuration

The real production API URL, MySQL host/database/user/password/TLS details, JWT secret, frontend origin, mail credentials, persistent disk mount, and connected provider branch are not available in the repository. Configure them in their respective service dashboards as described in `PRODUCTION_ENVIRONMENT_VARIABLES.md`; the frontend URL requires rebuilding the frontend. Do not substitute the sample `api.example.com` URL from templates.

Git history is reconciled: local `main` tracks `origin/main` and is seven commits ahead, zero behind as of the final report. The worktree is clean; the backup ref `backup/pre-push-2026-10-30` remains available. Fetch again before pushing in case `origin/main` has advanced; use a normal fast-forward push and never force-push.
