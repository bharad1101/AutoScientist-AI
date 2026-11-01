# PRODUCTION_DEPLOYMENT_CHECKLIST.md

- [ ] Backup verified/restorable (Aiven)
- [ ] Migrations compatible (tested against MySQL)
- [ ] Render env vars set (no secrets in repo)
- [ ] Render health check passes
- [ ] Vercel env vars set (VITE_API_BASE_URL)
- [ ] CORS allows Vercel domain
- [ ] Auth flows tested
- [ ] /api/health and /api/health/ready accessible
- [ ] Upload persistence confirmed or external storage configured
- [ ] SMTP tested
- [ ] All features smoke tested
- [ ] Rollback plan ready
