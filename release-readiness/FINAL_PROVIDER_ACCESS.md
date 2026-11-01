# FINAL_PROVIDER_ACCESS.md

Project: AutoScientist AI
Local directory: /Applications/AutoScientist AI
Repo: bharad1101/AutoScientist-AI
Date: 2026-11-01

## GitHub
- Repo: bharad1101/AutoScientist-AI (verified from git remotes)
- Branches: main (ahead of origin/main by 1), remotes/origin/main, remotes/rajesh/*
- Local HEAD: 8b4cf87 fix: align Flyway support with MySQL 8.4
- git push access not executed (requires explicit approval). gh CLI presence unknown; authenticated state not verified from CLI here.
- Workflows: check .github/workflows/ in repo if present.

## Render (Backend)
- Expected: Java Spring Boot backend on Render, automatic deploy from GitHub
- Build/start/health not inspected via API/CLI (no authenticated Render API/session confirmed).
- Must verify: service name, root dir (backend/), build command (mvnw/Java), start command, health check path (/api/health or /api/health/ready), env vars (names only), persistent disk if uploads need persistence.
- Note: /api/health/ready returns 503 when DB unreachable; /api/health returns 200 and is permitAll.

## Vercel (Frontend)
- Expected: React/Vite frontend, auto-deploy from GitHub main
- Must verify: project, root dir (frontend/), build (npm run build), output (dist), VITE_API_BASE_URL, SPA rewrites, prod domain.
- Demo credentials in UI removed/handled? (verify in prod bundle - not executed; local code inspected).

## Aiven MySQL
- Expected: production DB
- Must verify: service, TLS, backups, schema/Flyway history, privileges, connection limits.
- Migrations: Flyway present; RealMysqlMigrationTest exists. No production migrations run.

## Access Discovery Conclusion
- Git repo/branch state confirmed. Provider API/CLI auth not fully confirmed here; manual verification needed via authenticated dashboards/CLIs. No secrets printed.
