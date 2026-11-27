# CI/CD Guide

Status: **workflow implemented and locally validated; GitHub run not verified.**

Authoritative workflow: `.github/workflows/ci.yml`. There is one workflow and one
deployment mechanism per provider (GitHub Actions deploy hooks). `render.yaml`
disables Render Git auto-deploy and `frontend/vercel.json` disables Vercel Git
deploys for the two release branches.

## Triggers

- Push to `main` or `preview/verify-deployment`.
- Pull requests targeting `main` or `preview/verify-deployment`.
- Manual `workflow_dispatch`.

Deployment jobs run **only** on a push to the exact release branch and only when
the corresponding enable variable is `true`. Pull requests never deploy.

## Check jobs

| Job | What it proves |
|---|---|
| `backend` | Java 17, Maven resolution, compilation, tests, production guards, package, V1–V7 in JAR |
| `frontend` | Node 22.13.0, `npm ci`, ESLint, Vitest, `npm audit --audit-level=high`, production build, no localhost in `dist` |
| `docker` | Both valid Docker context/Dockerfile pairs build |
| `mysql-migrations` | Flyway V1–V7 and app startup against disposable MySQL 8.4 |
| `security` | No obvious tracked secrets or forbidden tracked artifacts; OSV lockfile scan |
| `deploy-config` | `scripts/validate-deployment-config.sh` passes |
| `workflow-lint` | actionlint 1.7.12 passes with a checksummed binary |

Third-party GitHub Actions are pinned to immutable commit SHAs; the Maven, npm,
actionlint, and OSV tools use checksums or lockfiles where available.

## Deployment jobs

| Job | Source | Secrets | Variables |
|---|---|---|---|
| `deploy-preview` | push to `preview/verify-deployment` | `RENDER_PREVIEW_DEPLOY_HOOK_URL`, `VERCEL_PREVIEW_DEPLOY_HOOK_URL` | `PREVIEW_DEPLOY_ENABLED`, `PREVIEW_BACKEND_HEALTH_URL`, `PREVIEW_FRONTEND_HEALTH_URL` |
| `deploy-production` | push to `main` | `RENDER_PRODUCTION_DEPLOY_HOOK_URL`, `VERCEL_PRODUCTION_DEPLOY_HOOK_URL` | `PRODUCTION_DEPLOY_ENABLED`, `PRODUCTION_BACKEND_HEALTH_URL`, `PRODUCTION_FRONTEND_HEALTH_URL` |

Deployment concurrency groups are `deploy-preview` and `deploy-production` with
`cancel-in-progress: false`. Check jobs cancel superseded runs independently so
they cannot cancel a deployment already in progress. A manual re-run of an
already deployed commit will call the hooks again; treat re-runs as intentional.

The backend health polling requires a JSON `data.status` of `UP` from
`/api/health/ready`. The frontend check requires HTTP 200 from its root URL.
Both poll for up to five minutes. The probe verifies availability, not that the
new commit is live; confirm the commit in the provider dashboard.

## Readiness payload

`/api/health/ready` returns:

```json
{
  "success": true,
  "data": {
    "status": "UP",
    "database": "UP",
    "storage": "UP",
    "migrations": "UP",
    "timestamp": "..."
  }
}
```

`migrations` is `UP` when Flyway is enabled, the current migration succeeded,
and no migrations are pending. It is `SKIPPED` when Flyway is disabled (local
dev/test only). A failed or pending migration returns HTTP 503 and
`status: DOWN`. Exception text, JDBC URLs, and credentials are never included.
