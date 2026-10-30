# Automatic Deployment Guide

Repository evidence: a Dockerfile exists at `backend/Dockerfile`; a SPA rewrite exists at `frontend/vercel.json`; no GitHub Actions workflow, Render blueprint, or other provider manifest was found. Existing docs reference Render for backend `PORT` behavior, but that alone does not prove configured services or automatic deploys.

## Verify in provider dashboards

1. Identify the GitHub repository connected to each service and confirm the production branch and automatic deploy-on-push setting.
2. Backend service: root `backend`; Dockerfile `backend/Dockerfile` (build context `backend`); ensure provider uses the Dockerfile, Java 17 runtime, and forwards its assigned port via `PORT`.
3. Backend env: set the values in `PRODUCTION_ENVIRONMENT_VARIABLES.md`, including `SPRING_PROFILES_ACTIVE=mysql`, `SPRING_JPA_HIBERNATE_DDL_AUTO=validate`, and durable `FILE_UPLOAD_DIR` mount.
4. Configure health check `GET /api/health`; ensure it expects HTTP 200.
5. Frontend service: root `frontend`; install `npm install` (or `npm ci` where lockfile is committed); build `npm run build`; output `dist`; set actual HTTPS `VITE_API_BASE_URL` ending in `/api` at build time; rebuild after any value change.
6. Confirm `frontend/vercel.json` SPA rewrite is applied by the static host. Verify custom domain HTTPS and exact backend CORS origin.
7. Verify backend network access to MySQL, migration privileges, backups, and durable disk support.
8. Push only after reviewing the current worktree and provider settings. This pass did not stage, commit, or push.

## Acceptance test

Push to the configured branch and inspect provider build logs for both frontend and backend. Confirm Flyway completion, health check, frontend API requests, login, dataset upload, backend restart persistence, and password reset email. A failed check requires fixing its configuration before declaring automatic deployment ready.
