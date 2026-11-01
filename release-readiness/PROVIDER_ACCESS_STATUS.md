# Provider Access Status

- **Assessment date:** 2026-10-31
- **Repository:** `bharad1101/AutoScientist-AI`
- **Branch:** `main`
**Purpose:** Record what could and could not be verified directly, so no release claim depends on assumed provider access.

## Summary

| Provider | Status | What was verified | What remains unverified |
|---|---|---|---|
| GitHub | VERIFIED | Authentication, repository, branch, latest commit, commit statuses, workflows, hooks, Actions secrets/variables, environment | Repository settings not exposed by API without admin; not needed for release gate |
| Vercel | PARTIAL | GitHub deployment status, production deployment alias, live frontend response, production API URL baked into the live bundle | Dashboard/build settings, project root, build environment values, domain ownership, auto-deploy policy |
| Render | PARTIAL | Live backend health, CORS behavior, unauthenticated auth behavior, frontend-backend connectivity | Service definition, root directory, Dockerfile path, env names/values, disk, logs, auto-deploy, health-check config, region |
| Aiven | BLOCKED | Nothing on the actual Aiven service | Database identity/version, TLS, migration state, user privileges, backups, network access, connection limits |

**Overall provider readiness:** CONDITIONAL. GitHub is fully verified (including repository admin permission). Vercel and Render are live and partially verified. Aiven remains blocked and must not be treated as production-verified.

**Re-verification date:** 2026-10-31 (Phase 2 release-blocker resolution). No provider configuration was changed and no resource was created or deleted.

## GitHub — VERIFIED

Authenticated CLI identity:

- Active account: `bharad1101`
- Token scopes observed: `gist`, `read:org`, `repo`, `workflow`
- Other authenticated accounts present but inactive: `Rajesh27-k`, `vaishnavipittala1811-lang`

Repository facts:

- Repository is public.
- Authenticated account `bharad1101` has `admin`, `maintain`, `push`, `triage`, and `pull` permission on the repository.
- `main` is not branch-protected (HTTP 404 from the protection endpoint).
- Only `main` exists as a branch.
- `origin/main` latest commit: `d13e6c6` (`chore: finalize push-to-deploy readiness`).
- Latest commit status: Vercel `success`; target URL points to the Vercel deployment.
- No GitHub Actions workflows are present.
- No repository webhooks are configured.
- No Actions secrets or variables are configured (verified through the authenticated API; names would have been listed, values never read).
- Environment `Production` exists.
- GitHub deployment records exist (8 total) and all are `Production` deployments created by `vercel[bot]`; the newest references `d13e6c6`. No Render-created deployment or commit status appears in GitHub, so Render deployment state cannot be inferred from GitHub.

Local Git state during this assessment:

- `HEAD` was `8b4cf87` before local fixes, one commit ahead of `origin/main`.
- That commit bumps Flyway and adds the prior release report.
- Additional local fixes are currently uncommitted.
- No push, commit, deployment, or provider configuration change was performed during this assessment.

Verification commands used included:

```bash
git status --short
git log --oneline -3 origin/main
gh auth status
gh api repos/bharad1101/AutoScientist-AI
gh api repos/bharad1101/AutoScientist-AI/commits/d13e6c6/status
gh api repos/bharad1101/AutoScientist-AI/actions/workflows
gh api repos/bharad1101/AutoScientist-AI/hooks
```

## Vercel — PARTIAL

Verified:

- Vercel GitHub integration is active through `vercel[bot]`.
- Commit `d13e6c6` has a successful Vercel status.
- Live frontend origin responds: `https://auto-scientist-ai-ten.vercel.app`
- Deployment-specific alias observed: `https://auto-scientist-ls6spj9p4-auto-scientist-ai.vercel.app`
- Live frontend HTML returns HTTP 200.
- The live production bundle references `https://autoscientist-ai-backend.onrender.com/api`.
- No localhost API URL was found in the live bundle.
- A fresh local production build using the same API URL succeeded and contained no demo credentials or localhost API URL.

Not verified:

- Vercel project root directory and framework preset.
- Build command and output directory in the dashboard.
- `VITE_API_BASE_URL` value in Production settings.
- Whether automatic deployment is enabled for `main`.
- Domain ownership and DNS configuration.
- Environment separation between Preview and Production.
- Rollback history and previous healthy deployment ID.

Reason: no Vercel CLI or API token is available in this environment. The Vercel CLI is not installed, and no provider token was found.

Required approval before changes: adding or changing environment variables, production domains, build settings, or redeploying.

## Render — PARTIAL

Verified:

- Backend origin responds: `https://autoscientist-ai-backend.onrender.com`
- `GET /api/health` returns HTTP 200 with `status=UP` over HTTP/1.1.
- During re-verification the first HTTP/1.1 request took ~18.7 s and the next took ~0.45 s, consistent with a Render free-tier cold start. The earlier HTTP/2 timeouts are consistent with the same cold start/edge behavior and are recorded as a transport anomaly, not an application failure.
- HTTP/2 requests to the same health endpoint timed out during earlier testing; HTTP/1.1 is healthy.
- Root `/` returns 404, as expected for an API-only service.
- CORS preflight from the exact frontend origin is allowed.
- CORS preflight from an unapproved origin returns 403.
- Unauthenticated `/api/datasets` returns 401.
- Unauthenticated `/api/auth/me` returns 401.

Not verified:

- Render service name, region, instance type, and root directory.
- Dockerfile path or build command.
- Whether the service deploys automatically from `main`.
- Environment variable names and presence of required secrets.
- Persistent disk mount and upload directory writability.
- Build/deploy logs and Flyway startup output.
- Health-check path configured in Render.
- Outbound connectivity and TLS details for the actual Aiven database.
- Render deployment ID currently serving traffic.

Reason: no Render CLI, API token, or dashboard session is available. The Render CLI is not installed, and no provider token was found.

Required approval before changes: service settings, environment variables, disk changes, deploy hook changes, manual deploys, or rollbacks.

## Aiven — BLOCKED

No Aiven CLI, API token, or dashboard session is available. The Aiven CLI is not installed.

Consequently the following remain unknown:

- Actual MySQL service identity, version, and region.
- Database name and application username.
- TLS mode and CA configuration used by Render.
- Current Flyway schema-history state.
- Backup schedule and latest restorable backup.
- Connection limits and pool sizing.
- IP allowlist/firewall requirements.
- Whether production data exists and whether it is consistent with the frontend accounts users expect.

The real-MySQL migration path was validated only against a disposable local MySQL 9.7.1 instance. That proves migration behavior, not Aiven production configuration.

Required approval before changes: database users, schema changes, migrations, backups, network rules, or restore operations.

## Docker — INSTALLED, DAEMON UNAVAILABLE (NON-BLOCKING)

- Docker CLI: installed at `/usr/local/bin/docker`.
- Docker Compose: `v2.40.3-desktop.1`.
- Docker Desktop: installed at `/Applications/Docker.app`.
- Docker daemon: not running. `docker info`, `docker ps`, and `docker version` fail against `unix:///Users/bharadwajpola/.docker/run/docker.sock`.
- No alternative daemon is available (`colima`, `podman`, and `nerdctl` are not installed).
- No software was installed and no system setting was changed.

**Does Render need local Docker?** No. This repository has no `render.yaml`; Render is configured through its dashboard/API as a Docker service, and Render builds the image in its own build environment. A local Docker daemon is therefore **not required** for the configured push-to-deploy method. Local Docker would only pre-validate the image and entrypoint before pushing; that is an optional risk-reduction step, not a deployment dependency. The image remains unbuilt locally.

## Blocking access gaps

Release cannot be declared fully production-verified until an authorized operator confirms the following inside provider dashboards:

1. Render service is connected to `bharad1101/AutoScientist-AI`, branch `main`, with the intended root directory and Dockerfile.
2. Render has `SPRING_PROFILES_ACTIVE=mysql` and all required secret variable names present, without exposing secret values.
3. Render has a writable persistent disk matching `FILE_UPLOAD_DIR`.
4. Render health check uses `/api/health`.
5. Vercel Production has `VITE_API_BASE_URL=https://autoscientist-ai-backend.onrender.com/api`.
6. Vercel project root is `frontend`, output is `dist`, and SPA rewrite is active.
7. Aiven has a current backup, TLS enabled, migration-capable user, and reachable network path from Render.
8. The exact production frontend origin is present in `APP_CORS_ALLOWED_ORIGINS`.

## Safety constraints

- Do not print or commit secret values.
- Do not modify provider settings without explicit approval.
- Do not run destructive database operations.
- Do not push or deploy without explicit approval.
- Do not treat a live health endpoint as proof that the production database, uploads, or authenticated workflows are correct.

## Final release decision (2026-10-31)

### A. VERIFIED SAFE FOR LOCAL CANDIDATE

- Working tree contains only the intended release files; no unrelated files changed.
- `git diff --check` is clean; no secrets, credentials, `.env` files, uploads, backups, or generated artifacts are present in the diff or untracked set.
- Candidate is based on `origin/main` (`d13e6c6`) plus local commit `8b4cf87` (Flyway/MySQL 8.4 dependency alignment) and the reviewed uncommitted fixes.
- Java 17 package build passes (`Build-Jdk-Spec: 17`); frontend production build passes with the Render API URL; the bundle contains no localhost URL or demo credentials.
- Migrations `V1`–`V4` were reviewed: no drops, truncates, or deletes of user data; legacy paths are guarded and additive/backfilling; both fresh and legacy migration paths already passed against disposable MySQL.
- Existing application features are preserved; changes are limited to auth hardening, readiness, release docs, and tests.
- No production database, provider configuration, or persistent storage was modified.

### B. REQUIRES EXTERNAL AUTHORIZATION

- Explicit approval to commit and push the candidate to `main`.
- Explicit approval to deploy backend and frontend.
- Provider-operator authorization to verify (read-only) Render, Vercel, and Aiven settings.
- Database-operator authorization to confirm a pre-deployment Aiven backup and current `flyway_schema_history`.
- Release-owner approval to run the authenticated production smoke test with a controlled account.

### C. REQUIRES MANUAL ACCOUNT ACTION

- Provide Render dashboard/API access, or have an operator confirm: repository/branch, root directory + Dockerfile path, `mysql` profile, env names present (no values), persistent disk at `/app/uploads`, health path `/api/health`, auto-deploy.
- Provide Vercel dashboard/API access, or have an operator confirm: root `frontend`, build `npm run build`, output `dist`, Production `VITE_API_BASE_URL`, SPA rewrite, auto-deploy, domain.
- Provide Aiven access, or have an operator confirm: service/version, TLS mode, migration user privileges, backup identifier, network reachability from Render, connection limits, `flyway_schema_history`.
- Supply a controlled production test account for the authenticated smoke test (or authorize account creation during the test).
- Optionally start Docker Desktop locally if image pre-validation is desired.

### D. BLOCKED

- Aiven production verification: no CLI, token, or dashboard session; actual schema, TLS, privileges, backups, and network path are unknown.
- Authenticated production smoke test: no production account/credentials available.
- Render and Vercel dashboard configuration: no CLI, token, or dashboard session.
- Local Docker image build: daemon unavailable (non-blocking for Render's remote Docker build).

### E. READY FOR DEPLOYMENT APPROVAL

The **local candidate is technically ready** for deployment approval. Deployment must not begin until the items in B, C, and D are resolved or explicitly risk-accepted and the approvals in `RELEASE_PLAN.md` are recorded.
