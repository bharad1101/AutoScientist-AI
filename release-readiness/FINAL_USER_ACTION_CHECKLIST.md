# Final User Action Checklist

## Render

- [ ] In the existing Render backend service, confirm it is connected to `bharad1101/AutoScientist-AI`, watches `main`, and has automatic deploys enabled.
- [ ] Confirm one matching Docker setup: either Root Directory `backend`, Dockerfile Path `Dockerfile`, Docker Context `.`, or repository-root context with Root Directory blank, Dockerfile Path `Dockerfile`, Docker Context `.`. Do not override the Docker start command.
- [ ] Set the HTTP health check path to `/api/health`. Leave Render's assigned `PORT` in place; the Docker entrypoint forwards it to Spring Boot.
- [ ] Set these backend environment variables in Render:
  - `SPRING_PROFILES_ACTIVE=mysql`
  - `SPRING_DATASOURCE_URL` — Aiven's Java JDBC URI with `sslMode=REQUIRED`
  - `SPRING_DATASOURCE_USERNAME`
  - `SPRING_DATASOURCE_PASSWORD`
  - `JWT_SECRET` — fresh Base64 secret from at least 64 random bytes
  - `APP_CORS_ALLOWED_ORIGINS` — exact production Vercel origin, with no path or trailing slash
  - `FRONTEND_URL` — the same production Vercel origin
  - `FILE_UPLOAD_DIR=/app/uploads/datasets`
  - `SPRING_JPA_HIBERNATE_DDL_AUTO=validate`
  - `SPRING_FLYWAY_ENABLED=true`
- [ ] Configure SMTP in Render so password reset works: `SPRING_MAIL_HOST`, `SPRING_MAIL_PORT`, `SPRING_MAIL_USERNAME`, `SPRING_MAIL_PASSWORD`, `SPRING_MAIL_PROPERTIES_MAIL_SMTP_AUTH=true`, `SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_ENABLE=true`, and a verified `PASSWORD_RESET_FROM` sender.
- [ ] Attach a Render persistent disk at `/app/uploads` on a plan that supports disks. The entrypoint prepares the dataset directory and drops the JVM to uid 10001; keep the container's default entrypoint enabled.
- [ ] After Vercel's production domain is known, use it for both `APP_CORS_ALLOWED_ORIGINS` and `FRONTEND_URL`. Restart/redeploy the Render service after changing runtime values or the disk.

## Vercel

- [ ] Confirm the project is connected to `bharad1101/AutoScientist-AI`, uses `main` for production, and automatically deploys pushes to that branch.
- [ ] Set Root Directory to `frontend`, Node.js version to `24.x`, Install Command to `npm ci`, Build Command to `npm run build`, and Output Directory to `dist`. Keep the existing `frontend/vercel.json` SPA rewrite enabled.
- [ ] Set the Production environment variable `VITE_API_BASE_URL` to the actual Render HTTPS service URL ending in `/api`. Get the URL from the Render service dashboard; do not use the sample URL in repository templates.
- [ ] Save the variable before the push so the first automatic Vercel build receives it. If Vercel Preview deployments are enabled, provide an appropriate HTTPS API URL for Preview builds too.

## Aiven MySQL

- [ ] In Aiven Console, confirm the service is running a MySQL 8.x version and copy its Java JDBC URI, host, port, database name, username, and password. The repository uses Flyway 11.7.2 and Connector/J 9.7.0; the actual Aiven service was not available for verification.
- [ ] Use an empty dedicated application database for a fresh install, or verify the existing database has the expected pre-Flyway schema before use. Flyway runs V1–V4 on a fresh database; a pre-Flyway database baselines at V1 and runs V2–V4, then Hibernate validates the schema.
- [ ] Ensure the datasource account can run Flyway migrations (CREATE, ALTER, INDEX, REFERENCES, and normal DML); this same datasource account is used by the application at startup.
- [ ] Require TLS for the application connection: add/merge MySQL Connector/J `sslMode=REQUIRED` in `SPRING_DATASOURCE_URL` and require SSL for the Aiven app user. Aiven MySQL does not enforce TLS for users by default. If selecting `VERIFY_CA` or `VERIFY_IDENTITY` instead, also provide Aiven's project CA and configure it in the Java truststore.
- [ ] Restrict Aiven's inbound IP filter to the Render service's outbound IP ranges. Get those ranges from the Render service's Connect → Outbound panel before changing Aiven's allowlist; do not remove existing access until the Render ranges are added.
- [ ] Before production, run the repository's real-MySQL migration test against a separate staging Aiven MySQL 8.x service using the current dependency graph. The test creates and drops schemas named `as_it_fresh` and `as_it_legacy`; never point it at production or a server where those schemas are in use.

## Final push

After the settings above are complete, and after fetching once more to confirm `origin/main` has not advanced, run:

```bash
git push origin main
```
