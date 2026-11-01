# AUTODEPLOYMENT_CONFIGURATION.md

## GitHub
- Repo: bharad1101/AutoScientist-AI, branch main. 1 commit ahead.

## Render (Backend) - unverified via API
- Root: backend/
- Build: ./mvnw clean package -DskipTests (or Docker)
- Start: java -jar target/*.jar
- Health: /api/health/ready (no auth). Ensure configured.
- Env names: SPRING_PROFILES_ACTIVE=prod, SPRING_DATASOURCE_URL/USERNAME/PASSWORD, JWT_SECRET, SPRING_MAIL_*, CORS_ALLOWED_ORIGINS, etc.

## Vercel (Frontend) - unverified via API
- Root: frontend/
- Build: npm run build
- Output: dist
- Env: VITE_API_BASE_URL=https://autoscientist-ai-backend.onrender.com/api
- SPA rewrites required

## Aiven MySQL - unverified
- TLS required, backups enabled, least privilege. Flyway manages schema.

**Status:** Not verified via authenticated CLI/API. Requires dashboard confirmation.
