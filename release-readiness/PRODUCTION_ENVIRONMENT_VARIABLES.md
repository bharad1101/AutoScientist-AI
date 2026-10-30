# Production Environment Variables

Set backend values in the backend service's secret/environment settings and frontend values in the frontend service's **build-time** environment settings. Never put secrets in frontend variables.

| Variable | Required value | Where / rebuild or restart |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | `mysql` | Backend runtime; restart |
| `SPRING_DATASOURCE_URL` | Provider-issued MySQL JDBC URL, with provider-required TLS options | Backend secret settings; restart |
| `SPRING_DATASOURCE_USERNAME` | Dedicated migration-capable DB username | Backend secret settings; restart |
| `SPRING_DATASOURCE_PASSWORD` | DB password | Backend secret settings; restart |
| `JWT_SECRET` | Fresh Base64 encoding of at least 64 random bytes; generate with `openssl rand -base64 64` | Backend secret settings; restart. Changing it invalidates existing JWTs |
| `APP_CORS_ALLOWED_ORIGINS` | Exact deployed frontend origin(s), comma separated, scheme + host only | Backend runtime; restart |
| `FRONTEND_URL` | Exact public frontend origin used for reset links | Backend runtime; restart |
| `FILE_UPLOAD_DIR` | `/app/uploads/datasets`, under mounted durable storage | Backend runtime; restart after disk mount |
| `PORT` | Provider-assigned port (usually injected by provider) | Backend runtime; ensure forwarded; restart |
| `VITE_API_BASE_URL` | Actual HTTPS backend origin plus `/api` | Frontend build settings; **rebuild required** |
| `SPRING_MAIL_HOST`, `SPRING_MAIL_PORT`, `SPRING_MAIL_USERNAME`, `SPRING_MAIL_PASSWORD` | Provider-approved SMTP account and TLS/auth settings | Backend secret settings; restart; required for delivered password-reset email |
| `PASSWORD_RESET_FROM` | Sender address accepted by the SMTP account | Backend runtime; restart |

`JWT_EXPIRATION_MS`, `DB_POOL_MAX_SIZE`, `DB_POOL_MIN_IDLE`, and `DB_CONNECTION_TIMEOUT_MS` are optional tuning values. Keep `SPRING_FLYWAY_ENABLED=true`; do not set Hibernate schema management to `update` in production. Hibernate now defaults to `validate`; set it explicitly in the provider environment for clarity.

Verification: inspect provider settings without revealing secret values; check backend startup logs for active `mysql` profile, successful Flyway completion, and storage initialization; request `GET https://<backend-origin>/api/health`; inspect built frontend assets/network requests to confirm the configured HTTPS API origin.
