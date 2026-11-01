# ROLLBACK_PLAN.md

## Backend (Render)
- Redeploy previous successful deploy (Render deployment history)
- Roll back env vars if changed
- Check DB migrations (do not auto-rollback destructive migrations)

## Frontend (Vercel)
- Promote previous deployment to production

## Database (Aiven)
- Restore from backup if schema/data corrupted (only with approval)
- Do not truncate/reset

## Git
- Revert commit if needed (no force push)
