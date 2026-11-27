# AutoScientist AI — Operational & Statistical Troubleshooting Guide

## 1. Operational & Infrastructure Issues

### Issue 1.1: Database Connection Refused
**Symptom**: `Communications link failure. The last packet sent successfully to the server was 0 milliseconds ago.`
**Resolution**:
1. Check that MySQL service is running:
   ```bash
   sudo systemctl status mysql # On Linux
   brew services list          # On macOS
   ```
2. Verify credentials match `backend/.env` or provider secret settings (never committed).
3. Confirm you are pointed at the correct environment: preview and production
   require two **separate** managed databases; a shared URL is a misconfiguration.
4. If running tests or development without a local MySQL instance, activate the
   local H2 profile:
   ```bash
   SPRING_PROFILES_ACTIVE=dev ./mvnw spring-boot:run
   ```
   Deployed services must use `SPRING_PROFILES_ACTIVE=mysql`; `dev` is refused on
   hosted runtimes.

### Issue 1.2: Path Traversal or File Upload Failure
**Symptom**: `Invalid path traversal attempt` or `File does not exist or access is restricted.`
**Resolution**:
- AutoScientist AI validates that stored files reside strictly within `FILE_UPLOAD_DIR` (`./uploads/datasets` locally, `/app/uploads/datasets` in Docker) using normalized paths.
- Ensure the upload directory has write permissions:
  ```bash
  mkdir -p uploads/datasets
  chmod 755 uploads/datasets
  ```
- In deployed services, confirm a persistent provider disk is mounted at
  `/app/uploads` and is writable by uid 10001. A Dockerfile `VOLUME` line alone
  does not provision storage.

### Issue 1.3: JWT Token Expired / Unauthorized 401
**Symptom**: `UnauthorizedException: Full authentication is required to access this resource.`
**Resolution**:
- Tokens expire by default after 24 hours (`86400000 ms`).
- Log out and log back in, or adjust `app.jwt.expiration-ms` in
  `backend/src/main/resources/application.yml`.
- If every user is logged out after a deploy, `JWT_SECRET` likely changed;
  rotation invalidates all existing tokens.

### Issue 1.4: Login Rejected but Registration Says Email Already Exists
**Symptom**: An existing account cannot log in, but registering the same email returns "Email is already registered".
**Reason**: The browser is usually talking to a different backend/database than the one where the account was created. Common causes are a production bundle with a baked-in `http://localhost:8080/api` URL, a backend running `dev` (H2) instead of `mysql`, or preview and production sharing a database.
**Resolution**:
1. Check the deployed bundle for a localhost URL:
   ```bash
   rg -n "localhost:8080" frontend/dist || echo "no localhost URL baked in"
   ```
2. Confirm the frontend build environment has an absolute HTTPS
   `VITE_API_BASE_URL` ending in `/api` for both Production and Preview. Builds
   reject missing, relative, non-HTTPS, loopback, credentialed, query/fragment,
   non-standard-port, and trailing-slash values.
3. Confirm the backend runs `SPRING_PROFILES_ACTIVE=mysql` with the correct
   environment's datasource. Local runs default to `dev`; deployments must use
   `mysql`.
4. Verify the account is in the intended managed database and that
   `password_hash` is populated from the legacy `password` column when needed.

### Issue 1.5: `UnsupportedClassVersionError` / wrong JDK at startup or in tests
**Symptom**: The JVM refuses classes compiled for a newer release, or the build uses a different JDK than expected.
**Resolution**:
- This project requires **Java 17** everywhere (POM, CI, Docker, Maven wrapper).
  Java 21+ is not supported. On macOS the shell may default to Java 21, so
  select 17 explicitly before **any** Maven command:
  ```bash
  export JAVA_HOME="$(/usr/libexec/java_home -v 17)"
  export PATH="$JAVA_HOME/bin:$PATH"
  java -version
  cd backend && ./mvnw -B -ntp clean verify
  ```
  The wrapper bootstraps Maven 3.9.9 with a `distributionSha256Sum`; no
  separate Maven install is needed.

### Issue 1.6: Node engine mismatch or frontend build rejects the API URL
**Symptom**: npm reports an unsupported engine, or Vite fails with an invalid `VITE_API_BASE_URL`.
**Resolution**:
- Supported engines: `^20.19.0 || ^22.13.0 || >=24.0.0`; CI pins 22.13.0. The
  old "Node 22.12+" requirement is wrong. Use `npm ci`.
- Production and Preview require an absolute HTTPS `VITE_API_BASE_URL` ending in
  `/api`; never a localhost URL and never a secret. Bare `/api` is accepted only
  with `VITE_API_PROXY_MODE=same-origin` and a real reverse proxy. The Vercel
  SPA rewrite is not an API proxy. Rebuild after changing the value.

### Issue 1.7: Upload returns HTTP 413
**Symptom**: `413 Payload Too Large` with a resource-limit message.
**Resolution**:
- Uploads are bounded by `RESOURCE_BUDGET_MAX_MB` (default **50 MB**, aligned with
  `UPLOAD_MAX_FILE_SIZE`/`UPLOAD_MAX_REQUEST_SIZE` defaults of 50 MB),
  `RESOURCE_BUDGET_MAX_ROWS`, and `RESOURCE_BUDGET_MAX_COLUMNS`.
- Reduce the file or raise the limits deliberately, ensuring the container has
  enough RAM. Ingestion is bounded in memory, not fully streaming.

### Issue 1.8: Flyway `Validate failed: checksum mismatch`
**Symptom**: Startup fails with a migration checksum mismatch after pulling new code.
**Resolution**:
- Never edit an applied migration, especially `V1__baseline.sql`. Restore it and
  add a new versioned migration instead.
- For a local database that diverged, reset the **local** schema and rerun
  Flyway. For staging/production, restore the pre-deploy backup and redeploy the
  matching version; do not run `repair` blindly.

### Issue 1.9: Docker build cannot connect to the daemon
**Symptom**: `failed to connect to the docker API at unix:///.../docker.sock`.
**Resolution**:
- Start Docker Desktop (or the daemon) and retry one of the matched pairs:
  ```bash
  docker build -f Dockerfile -t autoscientist-backend:ci .
  docker build -f backend/Dockerfile -t autoscientist-backend-service:ci backend
  ```
- CI performs both builds on PRs and pushes; a local daemon is not required for
  CI evidence.

### Issue 1.10: Readiness returns HTTP 503 or `storage: DOWN`
**Symptom**: `GET /api/health/ready` is not `UP`, or Render marks the service unhealthy.
**Resolution**:
- The readiness payload contains only `status`, `database`, `storage`, and
  `timestamp` under `data` — there is no `storageBackend` field.
- `database: DOWN`: check datasource variables, TLS, network access, and that
  the service uses its own managed database.
- `storage: DOWN`: mount a durable disk at `/app/uploads`, set
  `FILE_UPLOAD_DIR=/app/uploads/datasets`, and verify write access for uid 10001.
- Liveness is `GET /api/health`; Render's `healthCheckPath` is
  `/api/health/ready`. Frontend health is the deployed root URL.

### Issue 1.11: `/actuator/info` is missing or not public
**Reason**: Intentional. `SecurityConfig` does not permit `/actuator/info`
(dev-style `info.env` could expose environment data), and the base configuration
exposes `health,metrics,prometheus` only.
**Resolution**: Use `/api/health` and `/api/health/ready` for deployment checks;
do not add `/actuator/info` to the public allowlist.

### Issue 1.12: Deployment did not run, or ran twice
**Symptom**: No deploy after a push, or two deployments for one commit.
**Resolution**:
- CI deploy jobs run only on a **push** to `preview/verify-deployment` or `main`,
  only when `PREVIEW_DEPLOY_ENABLED` / `PRODUCTION_DEPLOY_ENABLED` is `true`, and
  only after all check jobs pass. A pull request never deploys.
- Duplicate deploys mean provider Git auto-deploy is still on. Keep
  `autoDeployTrigger: off` in `render.yaml` and Git deploys disabled in
  `frontend/vercel.json` for `main` and `preview/verify-deployment`
  (`git.deploymentEnabled`), while leaving PR branches enabled for previews.
- Confirm the deploy-hook secrets (`RENDER_PREVIEW_DEPLOY_HOOK_URL`,
  `VERCEL_PREVIEW_DEPLOY_HOOK_URL`, `RENDER_PRODUCTION_DEPLOY_HOOK_URL`,
  `VERCEL_PRODUCTION_DEPLOY_HOOK_URL`) and health URLs
  (`PREVIEW_BACKEND_HEALTH_URL`, `PREVIEW_FRONTEND_HEALTH_URL`,
  `PRODUCTION_BACKEND_HEALTH_URL`, `PRODUCTION_FRONTEND_HEALTH_URL`) are set in
  GitHub as names only. No values belong in the repository.
- If a check such as `workflow-lint` or `deploy-config` fails, no hook is
  called. `scripts/validate-deployment-config.sh` also fails when required files
  (for example `.github/workflows/ci.yml` or `render.yaml`) are untracked in the
  pushed commit.

### Issue 1.13: CI flags secrets, backups, or generated artifacts
**Symptom**: Secret/artifact scan or `git ls-files` check fails.
**Resolution**:
- Ignored local secrets/data/artifacts (`.env`, `uploads/`, `backups/`,
  `target/`, `dist/`, `node_modules/`, `*.mv.db`) remain local and must not be
  staged. Stage reviewed files explicitly instead of using `git add -A`.
- Supply values via provider secret settings and repository variables only.

> No real provider deployment has been verified from this worktree. Provider
> dashboards (repository/branch, build settings, databases, disks, hooks, health
> URLs, auto-deploy flags) must be checked manually. See
> [docs/DEPLOYMENT.md](DEPLOYMENT.md) and
> [release-readiness/CANONICAL_DEPLOYMENT_CHECKLIST.md](../release-readiness/CANONICAL_DEPLOYMENT_CHECKLIST.md).
---

## 2. Statistical & Machine Learning Edge Cases

### Issue 2.1: Zero Variance Detected in Feature Column
**Symptom**: Correlation returns `r = 0.0` or ANOVA returns non-significant status.
**Reason**:
- If all values in a column are identical (e.g. constant value `10.0`), the sample standard deviation $\sigma = 0$. Division by zero in Pearson $r$ or Welch's $t$ is mathematically undefined.
**Engine Handling**:
- The statistical engine detects $\sigma_x = 0$ or $\sigma_y = 0$, halts gracefully without crashing, and sets:
  ```json
  {
    "statistic": 0.0,
    "pValue": 1.0,
    "isSignificant": false,
    "interpretation": "Zero variance detected in variable; correlation is undefined."
  }
  ```

### Issue 2.2: Insufficient Paired Observations ($N < 3$)
**Symptom**: Warning issued: `Insufficient paired observations for correlation test.`
**Reason**:
- Degrees of freedom for Pearson correlation is $df = N - 2$. Calculating a valid Student's t-test p-value requires $N \ge 3$.
**Engine Handling**:
- The engine enforces aligned pair validation and requires $N \ge 3$ before calculating test statistics.

### Issue 2.3: Disparate Impact Ratio < 0.80 (Four-Fifths Rule Flagged)
**Symptom**: Fairness card displays status `FLAGGED` with disparate impact warning.
**Reason**:
- The ratio of selection rates between demographic subgroups $\frac{\min(\text{rate}_g)}{\max(\text{rate}_g)} < 0.80$.
**Resolution**:
- Inspect whether non-sensitive features act as proxies for the sensitive column.
- Consider balanced re-sampling or debiased thresholding.

### Issue 2.4: Significant Population Stability Index ($\text{PSI} \ge 0.25$)
**Symptom**: Drift report marks feature as `SIGNIFICANT_DRIFT`.
**Reason**:
- The quantile distribution of the target cohort has substantially shifted from the reference baseline cohort.
**Resolution**:
- Investigate feature schema shifts, seasonal trends, or sensor recalibrations.
- Retrain downstream AutoML models incorporating the new distribution.
