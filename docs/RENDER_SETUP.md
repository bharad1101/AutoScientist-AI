# Render Setup

Status: **repository blueprint prepared; Render services and disks not verified; no backend deployment confirmed.**

## 1. Services

`render.yaml` declares two Docker web services:

| Service | Branch | Docker context / Dockerfile | Health check | Disk |
|---|---|---|---|---|
| `autoscientist-backend-preview` | `preview/verify-deployment` | root context `.` / `./Dockerfile` | `/api/health/ready` | `autoscientist-preview-uploads`, 1 GB at `/app/uploads` |
| `autoscientist-backend-production` | `main` | root context `.` / `./Dockerfile` | `/api/health/ready` | `autoscientist-production-uploads`, 1 GB at `/app/uploads` |

Use the root context with the root `Dockerfile`. The separate
`backend/Dockerfile` is valid only with `backend` as the build context; do not
mix the two combinations. Both images build and run on Java 17 and drop to the
non-root `appuser`. The entrypoint creates and verifies `/app/uploads/datasets`
and `/app/uploads/datasets/temp`.

Both services set `autoDeployTrigger: off`. GitHub Actions deploy hooks are the
only release deployment trigger. Confirm this setting in the Render dashboard.

## 2. Environment variables (manual, never in Git)

Both services require:

| Name | Notes |
|---|---|
| `SPRING_PROFILES_ACTIVE` | `mysql`; the `dev` profile is rejected on hosted runtimes |
| `SPRING_DATASOURCE_URL` | MySQL 8.x JDBC URL with `sslMode=VERIFY_IDENTITY`, `VERIFY_CA`, or `REQUIRED`; no localhost; separate DB per environment |
| `SPRING_DATASOURCE_USERNAME` | environment-specific user |
| `SPRING_DATASOURCE_PASSWORD` | environment-specific secret |
| `JWT_SECRET` | Base64 key, at least 32 decoded bytes; different per environment |
| `APP_CORS_ALLOWED_ORIGINS` | exact deployed Vercel HTTPS origin(s), comma-separated |
| `FRONTEND_URL` | deployed Vercel HTTPS root URL for password reset links |
| `FILE_UPLOAD_DIR` | `/app/uploads/datasets` |
| `SPRING_JPA_HIBERNATE_DDL_AUTO` | `validate` |
| `SPRING_FLYWAY_ENABLED` | `true` |
| `RATE_LIMIT_ENABLED` | `true` |
| `ALLOW_PROXY_HEADERS` | `true` |
| `FORWARD_HEADERS_STRATEGY` | `framework` |
| `TRUSTED_PROXY_COUNT` | `1` (verify the real proxy hop count) |

SMTP/password reset (optional but needed for real delivery):
`SPRING_MAIL_HOST`, `SPRING_MAIL_PORT`, `SPRING_MAIL_USERNAME`,
`SPRING_MAIL_PASSWORD`, `PASSWORD_RESET_FROM`. Other supported names are in
`backend/.env.example` and `docs/DEPLOYMENT.md`.

The production guard rejects localhost datasource URLs, HTTP or localhost CORS
origins, localhost `FRONTEND_URL`, missing TLS, disabled Flyway/rate limiting,
`ddl-auto` other than `validate`, sensitive actuator exposure, and forwarded
header trust without `ALLOW_PROXY_HEADERS=true`.

## 3. Deploy hooks and health

Create one Render deploy hook per service and store the URLs as GitHub Secrets:
`RENDER_PREVIEW_DEPLOY_HOOK_URL` and `RENDER_PRODUCTION_DEPLOY_HOOK_URL`.
The workflow posts to the hook with `?ref=<commit SHA>`; confirm in the Render
dashboard that the hook starts a build of the intended branch and commit.

Set the health check path to `/api/health/ready`. The endpoint returns HTTP 200
only when the datasource and configured storage path are reachable, and reports
the current Flyway state; it does not by itself prove that a persistent disk is
attached. Verify the disk and its ownership separately in the Render dashboard.

## 4. Verification after deployment

1. Request `GET /api/health/ready` and confirm `status`, `database`, `storage`,
   and `migrations` are `UP`.
2. Confirm the Render dashboard shows the tested commit SHA.
3. Confirm the persistent disk is mounted and writable by `appuser`.
4. Check logs for startup errors without sharing secret values.
5. Confirm CORS with a real browser request from the deployed frontend origin.
