# Vercel Setup

Status: **repository config prepared; Vercel project state not verified; no public deployment confirmed.**

The authoritative frontend deployment trigger is the GitHub Actions deploy hook.
Vercel Git integration must not also deploy the release branches.

## 1. Project settings (manual)

| Setting | Value |
|---|---|
| Repository | `bharad1101/AutoScientist-AI` |
| Root Directory | `frontend` |
| Framework Preset | Vite |
| Node.js Version | 22.13.0 |
| Install Command | `npm ci` |
| Build Command | `npm run build` |
| Output Directory | `dist` |

`frontend/vercel.json` already declares the framework, commands, output
directory, SPA rewrite (`/(.*)` → `/index.html`), and Git deployment disabled for
`main` and `preview/verify-deployment`. Confirm the same values in the dashboard;
dashboard settings can override repository configuration.

## 2. Environment variables (manual)

Create separate Preview and Production environment scopes. `VITE_*` values are
embedded in the public JavaScript bundle, so never put a token or password in
them.

| Scope | Name | Value pattern |
|---|---|---|
| Preview | `VITE_API_BASE_URL` | `https://<preview-backend-host>/api` |
| Production | `VITE_API_BASE_URL` | `https://<production-backend-host>/api` |
| Optional | `VITE_API_TIMEOUT_MS` | positive integer milliseconds |

The build fails if `VITE_API_BASE_URL` is missing, uses `localhost`/loopback,
uses HTTP, has credentials or a non-443 port, or does not end in `/api`.
Same-origin `/api` is allowed only with `VITE_API_PROXY_MODE=same-origin`, and
there is no Vercel `/api` reverse proxy in this repository; do not use that mode
unless a proxy is configured and tested.

Preview and Production must point to different Render backend services and
therefore different databases.

## 3. Deploy hooks (manual)

Create one Vercel deploy hook for the preview deployment and another for the
production deployment. Confirm which branch or environment each hook targets in
the Vercel dashboard; the repository cannot verify or change that mapping.

Store the hook URLs as GitHub Secrets:
`VERCEL_PREVIEW_DEPLOY_HOOK_URL` and `VERCEL_PRODUCTION_DEPLOY_HOOK_URL`.

Keep Git deployment disabled for `main` and `preview/verify-deployment` so a push
does not create a second deployment. Other preview branches may remain enabled
for pull-request previews; those are not production releases.

## 4. Verification after deployment

1. Open the deployed root URL; confirm the SPA loads and a deep link refreshes.
2. Open browser developer tools and confirm API requests go to the intended
   backend host and include `/api`.
3. Search the built bundle for `localhost`; the CI job already rejects it.
4. Record the actual URLs in `release-readiness/FINAL_RELEASE_REPORT.md` as
   **actually deployed/publicly accessible** only after observing them.
