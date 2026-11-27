# Deployment Troubleshooting

Status: **procedures documented; no provider incident was observed from this worktree.**

Use this guide in order. Record every conclusion as locally verified, CI
verified, provider configured, actually deployed, or not yet verified.

## 1. Workflow did not deploy

- Check that the push was to `main` or `preview/verify-deployment`. Pull
  requests only run checks.
- Check the relevant variable is exactly `true`:
  `PREVIEW_DEPLOY_ENABLED` or `PRODUCTION_DEPLOY_ENABLED`.
- Check the four deploy-hook secrets exist and point to the intended provider
  target. Hook URLs are never printed in logs.
- Check the deploy job summary; a skipped deploy is different from a failed one.
- Check provider dashboards for the commit SHA and build result.

## 2. Backend readiness returns 503

`GET /api/health/ready` checks datasource, durable storage, and Flyway.

- `status: DOWN`, `database: DOWN`: verify `SPRING_PROFILES_ACTIVE=mysql`, the
  JDBC URL, username/password, TLS mode, provider CA, network access, and that
  the URL is not localhost.
- `migrations: DOWN`: check Flyway logs and `flyway_schema_history`. Do not set
  `SPRING_FLYWAY_ENABLED=false` or change `ddl-auto` to bypass it.
- `storage: DOWN`: verify the disk is mounted at `/app/uploads`, that
  `/app/uploads/datasets/temp` exists, and that `appuser` can write there.
- If the body contains SQL or connection errors in provider logs, redact secret
  values before sharing logs. The public endpoint itself returns only statuses.

## 3. Guard rejects startup

The production guard intentionally fails closed. Match the log message:

- `SPRING_DATASOURCE_URL must be a MySQL JDBC URL`: use `jdbc:mysql://...`.
- `must not point at a loopback host`: use the provider host.
- `must require TLS`: add `sslMode=VERIFY_IDENTITY`, `VERIFY_CA`, or `REQUIRED`.
- `APP_CORS_ALLOWED_ORIGINS must contain only deployed HTTPS origins`: use exact
  Vercel origins, not `*` and not localhost.
- `FRONTEND_URL must be the deployed HTTPS frontend URL`: use the public Vercel
  root URL.
- `JWT_SECRET`: generate a fresh Base64 secret with at least 32 decoded bytes;
  keep preview and production secrets different.
- actuator/DDL/rate-limit/header messages: follow `docs/RENDER_SETUP.md`.

The `dev` profile is rejected on hosted runtimes. The only exception is an
explicit disposable test opt-in used by CI with
`APP_ENVIRONMENT=test-mysql-disposable` and
`AS_TEST_MYSQL_ALLOW_SCHEMA_CREATE_DROP=true`.

## 4. Frontend loads but API calls fail

- Confirm `VITE_API_BASE_URL` is the correct absolute HTTPS URL ending in `/api`.
- Confirm the value was set before the Vercel build; `VITE_*` is baked into the
  bundle at build time.
- Confirm the request reaches the intended backend host, not localhost.
- Confirm `APP_CORS_ALLOWED_ORIGINS` on Render includes the exact frontend origin
  with scheme and no trailing slash.
- Confirm Render header trust settings: `ALLOW_PROXY_HEADERS=true`,
  `FORWARD_HEADERS_STRATEGY=framework`, `TRUSTED_PROXY_COUNT=1`.
- If using `VITE_API_PROXY_MODE=same-origin`, confirm a real reverse proxy is
  configured; this repository does not provide one on Vercel.

## 5. Uploads disappear after restart

- Confirm the Render persistent disk is attached to the exact service and mount
  path `/app/uploads`.
- Confirm `FILE_UPLOAD_DIR=/app/uploads/datasets`.
- Confirm startup logs show `/app/uploads/datasets/temp` is writable.
- Do not store uploads in the container image or under `target/`, `dist/`, or
  `node_modules/`.

## 6. CI failures

| Failure | First check |
|---|---|
| Java compilation/tests | Java 17 only; do not switch the workflow to 21 |
| Frontend build | `VITE_API_BASE_URL=https://ci.invalid/api` in CI; no committed `.env` |
| Frontend test | Run `npm test` locally after `npm ci` |
| Docker | Use matching context/Dockerfile pairs from `docs/CI_CD_GUIDE.md` |
| MySQL migrations | Use the disposable MySQL job logs; do not point tests at a managed DB |
| Security | Remove forbidden tracked files or secrets; rotate any exposed secret |
| deploy-config | Run `bash scripts/validate-deployment-config.sh` |
| workflow-lint | Run actionlint 1.7.12 locally before pushing |

## 7. Duplicate deployments

`render.yaml` declares `autoDeployTrigger: off`; `frontend/vercel.json` disables
Git deploys for `main` and `preview/verify-deployment`. Provider dashboard
settings can still trigger duplicate builds. Disable Git deploys for those
branches in both dashboards and keep deploy hooks as the only release trigger.
Other Vercel preview branches may remain enabled for pull-request previews.
