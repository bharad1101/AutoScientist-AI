# Final Production Release Report

Status: locally prepared and verified; cloud deployment, provider account state, MySQL production integration, and Docker daemon builds are **BLOCKED** from this workspace.

## A. Executive summary

The existing AutoScientist AI repository was audited and hardened without changing branches or rewriting history. The backend is Java 17 only and passes a full Maven `clean verify`. The React/Vite frontend passes tests, lint, and a production build using `VITE_API_BASE_URL`. Flyway V1–V4 were not modified; V5–V7 and all deployment/CI files were previously untracked and are included in this release. A local smoke test verified startup, readiness, registration, login, authenticated identity, dataset upload, EDA, and SPA routing. No real secret is included in the release allowlist. A historical development JWT key exists in Git history and must be rotated before production use.

BACKEND JAVA VERSION: 17

JAVA 17 COMPATIBILITY: PASS

## B. Original issues discovered

1. Hardcoded development JWT signing key in committed `HEAD` and Git history (`application.yml`, `JwtUtils.java`, test resources). Worktree removes the production default; history still requires rotation.
2. `.github/workflows/ci.yml`, `render.yaml`, Maven wrapper properties, Flyway V5–V7, guards, filters, storage abstractions, and many tests existed only as untracked files and would be missing from a clean checkout.
3. Production profile guards, JWT secret guard, MySQL configuration guard, CORS validation, and local storage provider existed only in the uncommitted working tree.
4. Raw exception messages from failed asynchronous jobs were persisted in `JobInfo.errorMessage` and returned by `/api/jobs/{id}`.
5. Documentation drift: stale branch patterns, health payload fields, health polling duration, Node version, upload defaults, test counts, and a false claim that CI does not deploy; a stale hardcoded 205-path release list.
6. Local ignored artifacts exist (`.env`, H2 database, uploads, backups). They are excluded from the allowlist and must never be force-added.
7. Real MySQL integration tests are skipped locally because no MySQL server is available.
8. Docker image builds cannot be executed locally because the Docker daemon is unavailable.
9. `database/schema.sql` is a non-authoritative reference and still differs from Flyway in nullability/FK details; Flyway plus Hibernate validation is the authoritative path.
10. Rate limiting and logout revocation are process-local; horizontal scaling requires a shared store.

## C. Engineering changes implemented

- Sanitized asynchronous job failure messages through `SafeErrorMessages` before persistence and logging, with a new regression test.
- Corrected archived-hydration/shim comments to include V6 among MySQL-only migrations.
- Corrected README, deployment guide, manual deployment steps, and Render setup documentation to match the actual CI, health, Node, and resource-budget configuration.
- Recalculated the release allowlist from the current Git working tree; it is 207 unique paths, not 205.
- Preserved all existing application features: AutoML, EDA, visualization, model evaluation, explainability, fairness, what-if, drift, Ask Dataset, research workflows, authentication, password reset, dataset management, reports/PDF, evidence, and jobs.

## D. Backend verification

| Check | Status | Evidence |
|---|---|---|
| Java runtime pinned to JDK 17 | PASS | `/usr/libexec/java_home -v 17`; `java -version` reports 17.0.21. |
| Maven wrapper version under JDK 17 | PASS | Maven 3.9.9, runtime JDK 17.0.21. |
| `./mvnw clean verify` | PASS | Exit 0; 311 tests, 0 failures, 0 errors, 6 skipped. |
| Spring Boot executable JAR repackaged | PASS | `backend/target/autoscientist-backend-1.0.0-SNAPSHOT.jar`. |
| Java 17 in POM/wrapper/CI/Docker | PASS | POM `maven.compiler.release=17`; wrapper 3.9.9; CI JDK 17; both Dockerfiles use Temurin 17. |
| Local startup and readiness | PASS | Packaged JAR started with `APP_ENVIRONMENT=local`, `SPRING_PROFILES_ACTIVE=dev`, port 18080; `/api/health` and `/api/health/ready` returned HTTP 200. |

## E. Frontend verification

| Check | Status | Evidence |
|---|---|---|
| `npm ci` | PASS | 263 packages installed; 0 vulnerabilities at install. |
| `npm test -- --run` | PASS | 24 test files, 185 tests passed, 0 failed. |
| `npm run lint` | PASS | Exit 0; 8 non-blocking warnings (React hook/fast-refresh guidance). |
| `npm audit --audit-level=high` | PASS | 0 vulnerabilities. |
| Production build with deployment-style env | PASS | `VITE_API_BASE_URL=https://preview.example.invalid/api npm run build`; exit 0. |
| Build guard without API base URL | PASS | Build intentionally refuses missing/loopback/non-HTTPS values. |
| SPA root and deep route | PASS | `vite preview` served `/` and `/datasets/1` with HTTP 200. |

## F. Java 17 compliance

BACKEND JAVA VERSION: 17

JAVA 17 COMPATIBILITY: PASS

The shell default JDK is 21, so every backend command in this report explicitly set `JAVA_HOME` to the installed JDK 17. No Java version was upgraded and no 21+ API is used.

## G. Database and Flyway verification

| Check | Status | Evidence |
|---|---|---|
| V1–V4 production migrations unchanged | PASS | `git diff -- backend/src/main/resources/db/migration` is empty for tracked files. |
| V5–V7 present and covered by CI packaging assertion | PASS | Files exist; CI asserts all V1–V7 in the JAR. |
| Fresh H2 baseline + schema/entity validation tests | PASS | `FlywayMigrationTest`, `MysqlBaselineMigrationTest`, `SchemaFlywayConsistencyTest`, `SchemaReferenceSyncTest`. |
| Deleted `password_reset_tokens.sql` replaced by `h2_baseline_shim.sql` | PASS | Shim creates `password_reset_tokens`, `jobs`, and `model_artifacts` for H2-only validation; production V4 remains unchanged. |
| Real MySQL 8 migration/runtime integration | BLOCKED | No MySQL server is available locally (`AS_TEST_MYSQL_*` credentials absent; local `mysqld` fails to load a library). Six tests are skipped: three `RealMysqlMigrationTest`, one `MysqlProfileRuntimeSmokeTest`, and two explicitly opt-in benchmarks. |
| TLS and least-privilege production grants | BLOCKED | Requires provider account access; configuration requirements are documented in `docs/MYSQL_SETUP.md`. |

## H. Security audit

| Check | Status | Evidence |
|---|---|---|
| Tracked working-tree secret scan | PASS | CI pattern reproduced; no real credential, private key, deploy hook URL, database dump, H2 file, upload, backup, or `.env` in the allowlist. |
| gitleaks 8.30.1 working tree + history | PASS | One finding at `frontend/src/test/api.test.js:82` (`generic-api-key`) is a fixed JWT-shaped test fixture, not a real credential; no other finding. |
| JWT secret handling | PASS | Worktree removes production defaults; guard rejects known dev keys and requires external `JWT_SECRET`. |
| Historical JWT key rotation | BLOCKED | A development key is present in `HEAD` and earlier commits; removing it from the tree does not erase history. Rotate the production key and treat the historical value as compromised. |
| Password hashing, reset tokens, CORS, uploads, SQL injection, error sanitization | PASS | BCrypt, SHA-256 hashed reset tokens, origin validation, UUID keys and traversal/size limits, parameterized JPQL, safe 500 responses, job error sanitization. |
| Provider secrets and live dashboard values | BLOCKED | Cannot inspect GitHub/Vercel/Render/MySQL account state from this workspace. |

## I. Backend test results

`./mvnw clean verify` under JDK 17: **311 tests run, 0 failures, 0 errors, 6 skipped**.

Skipped tests: `RealMysqlMigrationTest` x3 and `MysqlProfileRuntimeSmokeTest` x1 require MySQL credentials/infrastructure; `WorkflowPerformanceBenchmarkTest` and `WideAssociationBenchmarkTest` require `-Dbenchmark=true`.

## J. Frontend test results

`npm test -- --run`: **24 files, 185 tests passed, 0 failed, 0 skipped**. `npm run lint` exited 0 with 8 warnings. `npm audit --audit-level=high` found 0 vulnerabilities.

## K. Docker build results

Docker image builds: **BLOCKED** — the Docker CLI is installed but the daemon socket is unavailable. Both Dockerfiles were inspected and validated textually by `scripts/validate-deployment-config.sh` (Java 17 build/runtime images, non-root user, upload volume, entrypoint); no image build is claimed.

## L. GitHub Actions verification

Configuration validation: **PASS**. YAML parsed successfully; jobs are `backend`, `frontend`, `docker`, `mysql-migrations`, `security`, `deploy-config`, `workflow-lint`, `deploy-preview`, `deploy-production`. CI pins JDK 17 and Node 22.13.0, uses the Maven wrapper, runs backend/frontend tests and production build, validates deployment config, scans secrets/dependencies, and gates deploys on push, branch, all checks, and enable variables. Preview and production jobs are distinct; production requires a push to `main` after the approved merge process. Local `actionlint` is **NOT RUN** (binary not installed); CI installs pinned actionlint 1.7.12 and runs it on every workflow.

## M. Vercel configuration

`frontend/vercel.json`: **PASS**. Vite framework, root directory `frontend` set in dashboard, `npm ci`, `npm run build`, output `dist`, SPA rewrite to `/index.html`, Git deployment disabled for `main` and `preview/verify-deployment`. Preview and Production must have separate absolute HTTPS `VITE_API_BASE_URL` values ending in `/api`. Provider dashboard state and deployment-hook URLs are **BLOCKED**.

## N. Render configuration

`render.yaml`: **PASS**. Two Docker web services (`autoscientist-backend-preview` on `preview/verify-deployment`, `autoscientist-backend-production` on `main`), Java 17 Dockerfiles, port 8080, `/api/health/ready` health checks, `autoDeployTrigger: off`, persistent disks at `/app/uploads`, and secret-backed environment values with `sync: false`. Dashboard provisioning, disk attachment, and hook targets are **BLOCKED**.

## O. MySQL readiness

Configuration/documentation: **PASS**. MySQL 8+, separate preview/production databases and least-privilege users, TLS (`sslMode=REQUIRED` minimum; verify CA/identity preferred), Flyway-on-startup, `ddl-auto=validate`, connection pooling, backup/recovery, and migration verification are documented. Actual production migration and connectivity: **BLOCKED** pending provider credentials and a live database.

## P. Complete release file count

- Proposed paths: **207**
- Programmatic unique-path count: **207** (`python3` set of parsed pathspecs)
- Added files: **91**
- Modified files: **115**
- Deleted files: **1**
- Unchanged tracked files in allowlist: **0**
- Excluded: actual `.env` files, local databases, uploads, backups, private datasets, `node_modules`, `target`, `dist`, temporary files, `release-readiness/*`, and internal development reports.

## Q. Complete numbered release paths

1. `.dockerignore`
2. `.github/workflows/ci.yml`
3. `.gitignore`
4. `ARCHITECTURE.md`
5. `DEPLOYMENT_GUIDE.md`
6. `Dockerfile`
7. `README.md`
8. `backend/.dockerignore`
9. `backend/.mvn/wrapper/maven-wrapper.properties`
10. `backend/Dockerfile`
11. `backend/docker-entrypoint.sh`
12. `backend/mvnw`
13. `backend/mvnw.cmd`
14. `backend/pom.xml`
15. `backend/src/main/java/com/autoscientist/AutoScientistApplication.java`
16. `backend/src/main/java/com/autoscientist/analysis/association/AssociationEngine.java`
17. `backend/src/main/java/com/autoscientist/analysis/eda/EdaEngine.java`
18. `backend/src/main/java/com/autoscientist/analysis/fairness/FairnessEngine.java`
19. `backend/src/main/java/com/autoscientist/analysis/feature/FeatureEngineeringEngine.java`
20. `backend/src/main/java/com/autoscientist/analysis/ml/AutoMlEngine.java`
21. `backend/src/main/java/com/autoscientist/analysis/ml/ClassificationSemantics.java`
22. `backend/src/main/java/com/autoscientist/analysis/ml/ModelArtifactCodec.java`
23. `backend/src/main/java/com/autoscientist/analysis/ml/ServingArtifact.java`
24. `backend/src/main/java/com/autoscientist/analysis/profiling/DatasetProfiler.java`
25. `backend/src/main/java/com/autoscientist/analysis/quality/ColumnConsistencyValidator.java`
26. `backend/src/main/java/com/autoscientist/analysis/quality/DataQualityEngine.java`
27. `backend/src/main/java/com/autoscientist/analysis/statistics/StatisticalTestEngine.java`
28. `backend/src/main/java/com/autoscientist/analysis/xai/ExplainabilityEngine.java`
29. `backend/src/main/java/com/autoscientist/config/DeploymentProfileGuard.java`
30. `backend/src/main/java/com/autoscientist/config/DevJwtSecretInitializer.java`
31. `backend/src/main/java/com/autoscientist/config/JwtSecretGuard.java`
32. `backend/src/main/java/com/autoscientist/config/MysqlConfigurationGuard.java`
33. `backend/src/main/java/com/autoscientist/config/ResourceBudgetProperties.java`
34. `backend/src/main/java/com/autoscientist/config/SecurityConfig.java`
35. `backend/src/main/java/com/autoscientist/config/WebConfig.java`
36. `backend/src/main/java/com/autoscientist/controller/AuthController.java`
37. `backend/src/main/java/com/autoscientist/controller/HealthController.java`
38. `backend/src/main/java/com/autoscientist/controller/JobController.java`
39. `backend/src/main/java/com/autoscientist/controller/ModelServingController.java`
40. `backend/src/main/java/com/autoscientist/dto/auth/ForgotPasswordRequest.java`
41. `backend/src/main/java/com/autoscientist/dto/auth/LoginRequest.java`
42. `backend/src/main/java/com/autoscientist/dto/auth/RegisterRequest.java`
43. `backend/src/main/java/com/autoscientist/dto/ml/AutoMlResponseDto.java`
44. `backend/src/main/java/com/autoscientist/dto/ml/FairnessDto.java`
45. `backend/src/main/java/com/autoscientist/dto/ml/FeatureEffectDto.java`
46. `backend/src/main/java/com/autoscientist/dto/ml/ModelPredictionResponseDto.java`
47. `backend/src/main/java/com/autoscientist/dto/ml/ModelRunDto.java`
48. `backend/src/main/java/com/autoscientist/dto/ml/WhatIfResponseDto.java`
49. `backend/src/main/java/com/autoscientist/dto/ml/WhatIfSchemaDto.java`
50. `backend/src/main/java/com/autoscientist/entity/JobInfo.java`
51. `backend/src/main/java/com/autoscientist/entity/ModelArtifact.java`
52. `backend/src/main/java/com/autoscientist/exception/GlobalExceptionHandler.java`
53. `backend/src/main/java/com/autoscientist/exception/ResourceLimitExceededException.java`
54. `backend/src/main/java/com/autoscientist/exception/SafeErrorMessages.java`
55. `backend/src/main/java/com/autoscientist/repository/JobInfoRepository.java`
56. `backend/src/main/java/com/autoscientist/repository/ModelArtifactRepository.java`
57. `backend/src/main/java/com/autoscientist/repository/PasswordResetTokenRepository.java`
58. `backend/src/main/java/com/autoscientist/repository/UserRepository.java`
59. `backend/src/main/java/com/autoscientist/security/AuthTokenFilter.java`
60. `backend/src/main/java/com/autoscientist/security/JwtUtils.java`
61. `backend/src/main/java/com/autoscientist/security/RateLimitingFilter.java`
62. `backend/src/main/java/com/autoscientist/security/RequestIdFilter.java`
63. `backend/src/main/java/com/autoscientist/security/TokenRevocationService.java`
64. `backend/src/main/java/com/autoscientist/service/AuthService.java`
65. `backend/src/main/java/com/autoscientist/service/AutoMlService.java`
66. `backend/src/main/java/com/autoscientist/service/DatasetService.java`
67. `backend/src/main/java/com/autoscientist/service/ImportPreviewService.java`
68. `backend/src/main/java/com/autoscientist/service/JobExecutorService.java`
69. `backend/src/main/java/com/autoscientist/service/LocalStorageProvider.java`
70. `backend/src/main/java/com/autoscientist/service/ModelServingService.java`
71. `backend/src/main/java/com/autoscientist/service/PasswordResetMailNotifier.java`
72. `backend/src/main/java/com/autoscientist/service/PasswordResetService.java`
73. `backend/src/main/java/com/autoscientist/service/ResearchService.java`
74. `backend/src/main/java/com/autoscientist/service/StorageProvider.java`
75. `backend/src/main/java/com/autoscientist/util/CsvTable.java`
76. `backend/src/main/java/com/autoscientist/util/ExcelImportService.java`
77. `backend/src/main/resources/META-INF/spring.factories`
78. `backend/src/main/resources/application-dev.yml`
79. `backend/src/main/resources/application.yml`
80. `backend/src/main/resources/db/migration/V5__jobs_and_model_artifacts.sql`
81. `backend/src/main/resources/db/migration/V6__model_artifact_longblob.sql`
82. `backend/src/main/resources/db/migration/V7__job_ownership_and_parameters.sql`
83. `backend/src/test/java/com/autoscientist/AssociationEngineConfigurationInjectionTest.java`
84. `backend/src/test/java/com/autoscientist/AuthControllerTest.java`
85. `backend/src/test/java/com/autoscientist/AuthIntegrationTest.java`
86. `backend/src/test/java/com/autoscientist/AuthServiceTest.java`
87. `backend/src/test/java/com/autoscientist/AutoMlAndWhatIfRegressionTest.java`
88. `backend/src/test/java/com/autoscientist/AutoMlArtifactPersistenceFailureTest.java`
89. `backend/src/test/java/com/autoscientist/BiomedicalClassificationPipelineTest.java`
90. `backend/src/test/java/com/autoscientist/ClassificationSemanticsTest.java`
91. `backend/src/test/java/com/autoscientist/CsvTableTest.java`
92. `backend/src/test/java/com/autoscientist/DataQualityEngineTest.java`
93. `backend/src/test/java/com/autoscientist/DatasetDeletionIntegrationTest.java`
94. `backend/src/test/java/com/autoscientist/DeploymentProfileGuardTest.java`
95. `backend/src/test/java/com/autoscientist/DevJwtSecretInitializerTest.java`
96. `backend/src/test/java/com/autoscientist/DevProfileSeededUserIntegrationTest.java`
97. `backend/src/test/java/com/autoscientist/ExcelImportServiceTest.java`
98. `backend/src/test/java/com/autoscientist/FlywayMigrationTest.java`
99. `backend/src/test/java/com/autoscientist/GlobalExceptionHandlerSecurityTest.java`
100. `backend/src/test/java/com/autoscientist/HealthControllerTest.java`
101. `backend/src/test/java/com/autoscientist/HealthReadinessFlywayTest.java`
102. `backend/src/test/java/com/autoscientist/JobCleanupSchedulingTest.java`
103. `backend/src/test/java/com/autoscientist/JobErrorSanitizationTest.java`
104. `backend/src/test/java/com/autoscientist/JwtSecretConfigurationTest.java`
105. `backend/src/test/java/com/autoscientist/JwtSecretGuardTest.java`
106. `backend/src/test/java/com/autoscientist/JwtUtilsCompatibilityTest.java`
107. `backend/src/test/java/com/autoscientist/JwtUtilsLoggingTest.java`
108. `backend/src/test/java/com/autoscientist/LocalStorageProviderTest.java`
109. `backend/src/test/java/com/autoscientist/LogoutAuthorizationTest.java`
110. `backend/src/test/java/com/autoscientist/LogoutIntegrationTest.java`
111. `backend/src/test/java/com/autoscientist/MigrationSqlCompatibilityTest.java`
112. `backend/src/test/java/com/autoscientist/ModelArtifactPersistenceAndServingTest.java`
113. `backend/src/test/java/com/autoscientist/MysqlBaselineMigrationTest.java`
114. `backend/src/test/java/com/autoscientist/MysqlConfigurationGuardTest.java`
115. `backend/src/test/java/com/autoscientist/MysqlProfileRuntimeSmokeTest.java`
116. `backend/src/test/java/com/autoscientist/MysqlProfileStartupSmokeTest.java`
117. `backend/src/test/java/com/autoscientist/PasswordResetConcurrencyTest.java`
118. `backend/src/test/java/com/autoscientist/PasswordResetDeliveryTest.java`
119. `backend/src/test/java/com/autoscientist/PasswordResetIntegrationTest.java`
120. `backend/src/test/java/com/autoscientist/PasswordResetMailNotifierTest.java`
121. `backend/src/test/java/com/autoscientist/PasswordResetRetryReliabilityTest.java`
122. `backend/src/test/java/com/autoscientist/PersistentDevDatabaseRestartIntegrationTest.java`
123. `backend/src/test/java/com/autoscientist/RateLimitingFilterHardeningTest.java`
124. `backend/src/test/java/com/autoscientist/RateLimitingFilterTest.java`
125. `backend/src/test/java/com/autoscientist/RealMysqlMigrationTest.java`
126. `backend/src/test/java/com/autoscientist/RequestIdFilterTest.java`
127. `backend/src/test/java/com/autoscientist/ResearchOwnershipSecurityTest.java`
128. `backend/src/test/java/com/autoscientist/SchemaFlywayConsistencyTest.java`
129. `backend/src/test/java/com/autoscientist/SchemaReferenceSyncTest.java`
130. `backend/src/test/java/com/autoscientist/ServerErrorHardeningTest.java`
131. `backend/src/test/java/com/autoscientist/ServiceErrorMessageHardeningTest.java`
132. `backend/src/test/java/com/autoscientist/TestJwtSecrets.java`
133. `backend/src/test/java/com/autoscientist/TokenRevocationServiceTest.java`
134. `backend/src/test/java/com/autoscientist/WebConfigCorsTest.java`
135. `backend/src/test/java/com/autoscientist/WorkflowPerformanceBenchmarkTest.java`
136. `backend/src/test/java/com/autoscientist/analysis/ml/ModelArtifactCodecTest.java`
137. `backend/src/test/java/com/autoscientist/benchmark/WideAssociationBenchmarkTest.java`
138. `backend/src/test/resources/application-dev.yml`
139. `backend/src/test/resources/application-test.yml`
140. `backend/src/test/resources/application.yml`
141. `backend/src/test/resources/db/test/h2_baseline_shim.sql`
142. `backend/src/test/resources/db/test/password_reset_tokens.sql`
143. `database/migrations/README.md`
144. `database/schema.sql`
145. `docs/API_DOCUMENTATION.md`
146. `docs/ARCHITECTURE.md`
147. `docs/CI_CD_GUIDE.md`
148. `docs/DEPLOYMENT.md`
149. `docs/DEPLOYMENT_TROUBLESHOOTING.md`
150. `docs/FINAL_PRODUCTION_RELEASE_REPORT.md`
151. `docs/GITHUB_RELEASE_GUIDE.md`
152. `docs/MANUAL_DEPLOYMENT_STEPS.md`
153. `docs/MYSQL_SETUP.md`
154. `docs/RENDER_SETUP.md`
155. `docs/ROLLBACK_GUIDE.md`
156. `docs/TESTING.md`
157. `docs/TROUBLESHOOTING.md`
158. `docs/VERCEL_SETUP.md`
159. `frontend/.env.example`
160. `frontend/config/apiBaseUrl.js`
161. `frontend/eslint.config.js`
162. `frontend/index.html`
163. `frontend/package-lock.json`
164. `frontend/package.json`
165. `frontend/src/App.jsx`
166. `frontend/src/components/EdaGallery.jsx`
167. `frontend/src/components/ImportWizard.jsx`
168. `frontend/src/components/ModelEvaluationPanel.jsx`
169. `frontend/src/components/Sidebar.jsx`
170. `frontend/src/components/home/AnalyticsStoryVisual.jsx`
171. `frontend/src/components/home/DatasetStoryVisual.jsx`
172. `frontend/src/components/home/ModelStoryVisual.jsx`
173. `frontend/src/context/AuthContext.jsx`
174. `frontend/src/context/WorkflowContext.jsx`
175. `frontend/src/main.jsx`
176. `frontend/src/pages/AskDatasetPage.jsx`
177. `frontend/src/pages/AssociationsPage.jsx`
178. `frontend/src/pages/AutoMlPage.jsx`
179. `frontend/src/pages/DatasetDetailPage.jsx`
180. `frontend/src/pages/DriftPage.jsx`
181. `frontend/src/pages/ForgotPasswordPage.jsx`
182. `frontend/src/pages/LoginPage.jsx`
183. `frontend/src/pages/RegisterPage.jsx`
184. `frontend/src/pages/ReportsPage.jsx`
185. `frontend/src/pages/ResearchWorkflowPage.jsx`
186. `frontend/src/pages/ResetPasswordPage.jsx`
187. `frontend/src/pages/VisualizationsPage.jsx`
188. `frontend/src/services/api.js`
189. `frontend/src/services/apiErrors.js`
190. `frontend/src/services/authService.js`
191. `frontend/src/test/AssociationsPage.test.jsx`
192. `frontend/src/test/AutoMlFeatureEffect.test.jsx`
193. `frontend/src/test/DatasetGating.test.jsx`
194. `frontend/src/test/EdaGallery.test.jsx`
195. `frontend/src/test/ModelEvaluationPanel.test.jsx`
196. `frontend/src/test/PasswordResetPages.test.jsx`
197. `frontend/src/test/RoutingAndAuth.test.jsx`
198. `frontend/src/test/VisualizationsPage.test.jsx`
199. `frontend/src/test/api.test.js`
200. `frontend/src/test/apiBaseUrl.test.js`
201. `frontend/src/test/apiErrors.test.js`
202. `frontend/src/test/authService.test.js`
203. `frontend/src/test/viteConfigBuildGuard.test.js`
204. `frontend/vercel.json`
205. `frontend/vite.config.js`
206. `render.yaml`
207. `scripts/validate-deployment-config.sh`

## R. Deleted-file explanation

`backend/src/test/resources/db/test/password_reset_tokens.sql` is intentionally deleted. It was an H2-only shim that created just the password-reset table while pinning Flyway to V1. It is replaced by `backend/src/test/resources/db/test/h2_baseline_shim.sql`, which also creates the `jobs` and `model_artifacts` tables so Hibernate entity validation covers every entity in the baseline test. Production migration V4 is untouched and still authoritative on MySQL.

## S. Staging command

```bash
git add -- \
  '.dockerignore' \
  '.github/workflows/ci.yml' \
  '.gitignore' \
  'ARCHITECTURE.md' \
  'DEPLOYMENT_GUIDE.md' \
  'Dockerfile' \
  'README.md' \
  'backend/.dockerignore' \
  'backend/.mvn/wrapper/maven-wrapper.properties' \
  'backend/Dockerfile' \
  'backend/docker-entrypoint.sh' \
  'backend/mvnw' \
  'backend/mvnw.cmd' \
  'backend/pom.xml' \
  'backend/src/main/java/com/autoscientist/AutoScientistApplication.java' \
  'backend/src/main/java/com/autoscientist/analysis/association/AssociationEngine.java' \
  'backend/src/main/java/com/autoscientist/analysis/eda/EdaEngine.java' \
  'backend/src/main/java/com/autoscientist/analysis/fairness/FairnessEngine.java' \
  'backend/src/main/java/com/autoscientist/analysis/feature/FeatureEngineeringEngine.java' \
  'backend/src/main/java/com/autoscientist/analysis/ml/AutoMlEngine.java' \
  'backend/src/main/java/com/autoscientist/analysis/ml/ClassificationSemantics.java' \
  'backend/src/main/java/com/autoscientist/analysis/ml/ModelArtifactCodec.java' \
  'backend/src/main/java/com/autoscientist/analysis/ml/ServingArtifact.java' \
  'backend/src/main/java/com/autoscientist/analysis/profiling/DatasetProfiler.java' \
  'backend/src/main/java/com/autoscientist/analysis/quality/ColumnConsistencyValidator.java' \
  'backend/src/main/java/com/autoscientist/analysis/quality/DataQualityEngine.java' \
  'backend/src/main/java/com/autoscientist/analysis/statistics/StatisticalTestEngine.java' \
  'backend/src/main/java/com/autoscientist/analysis/xai/ExplainabilityEngine.java' \
  'backend/src/main/java/com/autoscientist/config/DeploymentProfileGuard.java' \
  'backend/src/main/java/com/autoscientist/config/DevJwtSecretInitializer.java' \
  'backend/src/main/java/com/autoscientist/config/JwtSecretGuard.java' \
  'backend/src/main/java/com/autoscientist/config/MysqlConfigurationGuard.java' \
  'backend/src/main/java/com/autoscientist/config/ResourceBudgetProperties.java' \
  'backend/src/main/java/com/autoscientist/config/SecurityConfig.java' \
  'backend/src/main/java/com/autoscientist/config/WebConfig.java' \
  'backend/src/main/java/com/autoscientist/controller/AuthController.java' \
  'backend/src/main/java/com/autoscientist/controller/HealthController.java' \
  'backend/src/main/java/com/autoscientist/controller/JobController.java' \
  'backend/src/main/java/com/autoscientist/controller/ModelServingController.java' \
  'backend/src/main/java/com/autoscientist/dto/auth/ForgotPasswordRequest.java' \
  'backend/src/main/java/com/autoscientist/dto/auth/LoginRequest.java' \
  'backend/src/main/java/com/autoscientist/dto/auth/RegisterRequest.java' \
  'backend/src/main/java/com/autoscientist/dto/ml/AutoMlResponseDto.java' \
  'backend/src/main/java/com/autoscientist/dto/ml/FairnessDto.java' \
  'backend/src/main/java/com/autoscientist/dto/ml/FeatureEffectDto.java' \
  'backend/src/main/java/com/autoscientist/dto/ml/ModelPredictionResponseDto.java' \
  'backend/src/main/java/com/autoscientist/dto/ml/ModelRunDto.java' \
  'backend/src/main/java/com/autoscientist/dto/ml/WhatIfResponseDto.java' \
  'backend/src/main/java/com/autoscientist/dto/ml/WhatIfSchemaDto.java' \
  'backend/src/main/java/com/autoscientist/entity/JobInfo.java' \
  'backend/src/main/java/com/autoscientist/entity/ModelArtifact.java' \
  'backend/src/main/java/com/autoscientist/exception/GlobalExceptionHandler.java' \
  'backend/src/main/java/com/autoscientist/exception/ResourceLimitExceededException.java' \
  'backend/src/main/java/com/autoscientist/exception/SafeErrorMessages.java' \
  'backend/src/main/java/com/autoscientist/repository/JobInfoRepository.java' \
  'backend/src/main/java/com/autoscientist/repository/ModelArtifactRepository.java' \
  'backend/src/main/java/com/autoscientist/repository/PasswordResetTokenRepository.java' \
  'backend/src/main/java/com/autoscientist/repository/UserRepository.java' \
  'backend/src/main/java/com/autoscientist/security/AuthTokenFilter.java' \
  'backend/src/main/java/com/autoscientist/security/JwtUtils.java' \
  'backend/src/main/java/com/autoscientist/security/RateLimitingFilter.java' \
  'backend/src/main/java/com/autoscientist/security/RequestIdFilter.java' \
  'backend/src/main/java/com/autoscientist/security/TokenRevocationService.java' \
  'backend/src/main/java/com/autoscientist/service/AuthService.java' \
  'backend/src/main/java/com/autoscientist/service/AutoMlService.java' \
  'backend/src/main/java/com/autoscientist/service/DatasetService.java' \
  'backend/src/main/java/com/autoscientist/service/ImportPreviewService.java' \
  'backend/src/main/java/com/autoscientist/service/JobExecutorService.java' \
  'backend/src/main/java/com/autoscientist/service/LocalStorageProvider.java' \
  'backend/src/main/java/com/autoscientist/service/ModelServingService.java' \
  'backend/src/main/java/com/autoscientist/service/PasswordResetMailNotifier.java' \
  'backend/src/main/java/com/autoscientist/service/PasswordResetService.java' \
  'backend/src/main/java/com/autoscientist/service/ResearchService.java' \
  'backend/src/main/java/com/autoscientist/service/StorageProvider.java' \
  'backend/src/main/java/com/autoscientist/util/CsvTable.java' \
  'backend/src/main/java/com/autoscientist/util/ExcelImportService.java' \
  'backend/src/main/resources/META-INF/spring.factories' \
  'backend/src/main/resources/application-dev.yml' \
  'backend/src/main/resources/application.yml' \
  'backend/src/main/resources/db/migration/V5__jobs_and_model_artifacts.sql' \
  'backend/src/main/resources/db/migration/V6__model_artifact_longblob.sql' \
  'backend/src/main/resources/db/migration/V7__job_ownership_and_parameters.sql' \
  'backend/src/test/java/com/autoscientist/AssociationEngineConfigurationInjectionTest.java' \
  'backend/src/test/java/com/autoscientist/AuthControllerTest.java' \
  'backend/src/test/java/com/autoscientist/AuthIntegrationTest.java' \
  'backend/src/test/java/com/autoscientist/AuthServiceTest.java' \
  'backend/src/test/java/com/autoscientist/AutoMlAndWhatIfRegressionTest.java' \
  'backend/src/test/java/com/autoscientist/AutoMlArtifactPersistenceFailureTest.java' \
  'backend/src/test/java/com/autoscientist/BiomedicalClassificationPipelineTest.java' \
  'backend/src/test/java/com/autoscientist/ClassificationSemanticsTest.java' \
  'backend/src/test/java/com/autoscientist/CsvTableTest.java' \
  'backend/src/test/java/com/autoscientist/DataQualityEngineTest.java' \
  'backend/src/test/java/com/autoscientist/DatasetDeletionIntegrationTest.java' \
  'backend/src/test/java/com/autoscientist/DeploymentProfileGuardTest.java' \
  'backend/src/test/java/com/autoscientist/DevJwtSecretInitializerTest.java' \
  'backend/src/test/java/com/autoscientist/DevProfileSeededUserIntegrationTest.java' \
  'backend/src/test/java/com/autoscientist/ExcelImportServiceTest.java' \
  'backend/src/test/java/com/autoscientist/FlywayMigrationTest.java' \
  'backend/src/test/java/com/autoscientist/GlobalExceptionHandlerSecurityTest.java' \
  'backend/src/test/java/com/autoscientist/HealthControllerTest.java' \
  'backend/src/test/java/com/autoscientist/HealthReadinessFlywayTest.java' \
  'backend/src/test/java/com/autoscientist/JobCleanupSchedulingTest.java' \
  'backend/src/test/java/com/autoscientist/JobErrorSanitizationTest.java' \
  'backend/src/test/java/com/autoscientist/JwtSecretConfigurationTest.java' \
  'backend/src/test/java/com/autoscientist/JwtSecretGuardTest.java' \
  'backend/src/test/java/com/autoscientist/JwtUtilsCompatibilityTest.java' \
  'backend/src/test/java/com/autoscientist/JwtUtilsLoggingTest.java' \
  'backend/src/test/java/com/autoscientist/LocalStorageProviderTest.java' \
  'backend/src/test/java/com/autoscientist/LogoutAuthorizationTest.java' \
  'backend/src/test/java/com/autoscientist/LogoutIntegrationTest.java' \
  'backend/src/test/java/com/autoscientist/MigrationSqlCompatibilityTest.java' \
  'backend/src/test/java/com/autoscientist/ModelArtifactPersistenceAndServingTest.java' \
  'backend/src/test/java/com/autoscientist/MysqlBaselineMigrationTest.java' \
  'backend/src/test/java/com/autoscientist/MysqlConfigurationGuardTest.java' \
  'backend/src/test/java/com/autoscientist/MysqlProfileRuntimeSmokeTest.java' \
  'backend/src/test/java/com/autoscientist/MysqlProfileStartupSmokeTest.java' \
  'backend/src/test/java/com/autoscientist/PasswordResetConcurrencyTest.java' \
  'backend/src/test/java/com/autoscientist/PasswordResetDeliveryTest.java' \
  'backend/src/test/java/com/autoscientist/PasswordResetIntegrationTest.java' \
  'backend/src/test/java/com/autoscientist/PasswordResetMailNotifierTest.java' \
  'backend/src/test/java/com/autoscientist/PasswordResetRetryReliabilityTest.java' \
  'backend/src/test/java/com/autoscientist/PersistentDevDatabaseRestartIntegrationTest.java' \
  'backend/src/test/java/com/autoscientist/RateLimitingFilterHardeningTest.java' \
  'backend/src/test/java/com/autoscientist/RateLimitingFilterTest.java' \
  'backend/src/test/java/com/autoscientist/RealMysqlMigrationTest.java' \
  'backend/src/test/java/com/autoscientist/RequestIdFilterTest.java' \
  'backend/src/test/java/com/autoscientist/ResearchOwnershipSecurityTest.java' \
  'backend/src/test/java/com/autoscientist/SchemaFlywayConsistencyTest.java' \
  'backend/src/test/java/com/autoscientist/SchemaReferenceSyncTest.java' \
  'backend/src/test/java/com/autoscientist/ServerErrorHardeningTest.java' \
  'backend/src/test/java/com/autoscientist/ServiceErrorMessageHardeningTest.java' \
  'backend/src/test/java/com/autoscientist/TestJwtSecrets.java' \
  'backend/src/test/java/com/autoscientist/TokenRevocationServiceTest.java' \
  'backend/src/test/java/com/autoscientist/WebConfigCorsTest.java' \
  'backend/src/test/java/com/autoscientist/WorkflowPerformanceBenchmarkTest.java' \
  'backend/src/test/java/com/autoscientist/analysis/ml/ModelArtifactCodecTest.java' \
  'backend/src/test/java/com/autoscientist/benchmark/WideAssociationBenchmarkTest.java' \
  'backend/src/test/resources/application-dev.yml' \
  'backend/src/test/resources/application-test.yml' \
  'backend/src/test/resources/application.yml' \
  'backend/src/test/resources/db/test/h2_baseline_shim.sql' \
  'backend/src/test/resources/db/test/password_reset_tokens.sql' \
  'database/migrations/README.md' \
  'database/schema.sql' \
  'docs/API_DOCUMENTATION.md' \
  'docs/ARCHITECTURE.md' \
  'docs/CI_CD_GUIDE.md' \
  'docs/DEPLOYMENT.md' \
  'docs/DEPLOYMENT_TROUBLESHOOTING.md' \
  'docs/FINAL_PRODUCTION_RELEASE_REPORT.md' \
  'docs/GITHUB_RELEASE_GUIDE.md' \
  'docs/MANUAL_DEPLOYMENT_STEPS.md' \
  'docs/MYSQL_SETUP.md' \
  'docs/RENDER_SETUP.md' \
  'docs/ROLLBACK_GUIDE.md' \
  'docs/TESTING.md' \
  'docs/TROUBLESHOOTING.md' \
  'docs/VERCEL_SETUP.md' \
  'frontend/.env.example' \
  'frontend/config/apiBaseUrl.js' \
  'frontend/eslint.config.js' \
  'frontend/index.html' \
  'frontend/package-lock.json' \
  'frontend/package.json' \
  'frontend/src/App.jsx' \
  'frontend/src/components/EdaGallery.jsx' \
  'frontend/src/components/ImportWizard.jsx' \
  'frontend/src/components/ModelEvaluationPanel.jsx' \
  'frontend/src/components/Sidebar.jsx' \
  'frontend/src/components/home/AnalyticsStoryVisual.jsx' \
  'frontend/src/components/home/DatasetStoryVisual.jsx' \
  'frontend/src/components/home/ModelStoryVisual.jsx' \
  'frontend/src/context/AuthContext.jsx' \
  'frontend/src/context/WorkflowContext.jsx' \
  'frontend/src/main.jsx' \
  'frontend/src/pages/AskDatasetPage.jsx' \
  'frontend/src/pages/AssociationsPage.jsx' \
  'frontend/src/pages/AutoMlPage.jsx' \
  'frontend/src/pages/DatasetDetailPage.jsx' \
  'frontend/src/pages/DriftPage.jsx' \
  'frontend/src/pages/ForgotPasswordPage.jsx' \
  'frontend/src/pages/LoginPage.jsx' \
  'frontend/src/pages/RegisterPage.jsx' \
  'frontend/src/pages/ReportsPage.jsx' \
  'frontend/src/pages/ResearchWorkflowPage.jsx' \
  'frontend/src/pages/ResetPasswordPage.jsx' \
  'frontend/src/pages/VisualizationsPage.jsx' \
  'frontend/src/services/api.js' \
  'frontend/src/services/apiErrors.js' \
  'frontend/src/services/authService.js' \
  'frontend/src/test/AssociationsPage.test.jsx' \
  'frontend/src/test/AutoMlFeatureEffect.test.jsx' \
  'frontend/src/test/DatasetGating.test.jsx' \
  'frontend/src/test/EdaGallery.test.jsx' \
  'frontend/src/test/ModelEvaluationPanel.test.jsx' \
  'frontend/src/test/PasswordResetPages.test.jsx' \
  'frontend/src/test/RoutingAndAuth.test.jsx' \
  'frontend/src/test/VisualizationsPage.test.jsx' \
  'frontend/src/test/api.test.js' \
  'frontend/src/test/apiBaseUrl.test.js' \
  'frontend/src/test/apiErrors.test.js' \
  'frontend/src/test/authService.test.js' \
  'frontend/src/test/viteConfigBuildGuard.test.js' \
  'frontend/vercel.json' \
  'frontend/vite.config.js' \
  'render.yaml' \
  'scripts/validate-deployment-config.sh'
```

## T. Staged diff summary

| Check | Status | Result |
|---|---|---|
| Current branch | PASS | `preview/verify-deployment` |
| Remote | PASS | `origin` points to `bharad1101/AutoScientist-AI.git`; no push executed |
| Exact allowlist staged | PASS | 207 staged paths; programmatic set equals section Q |
| `git diff --cached --name-status` | PASS | 91 added, 115 modified, 1 deleted |
| `git diff --cached --check` | PASS | Clean after normalizing `backend/mvnw.cmd` line endings to LF |
| Staged secret-pattern scan | PASS | No obvious secret material in staged non-documentation content |
| Forbidden staged files | PASS | No `.env`, key, database, backup, upload, `target`, `dist`, or `node_modules` file |
| Unrelated staged changes | PASS | Every staged path is in section Q; all other working-tree paths remain unstaged |

The full staged diff was reviewed with `git diff --cached --stat` and `git diff --cached --name-status`. `backend/mvnw.cmd` was normalized from CRLF to LF so the whitespace gate could pass; its wrapper behavior is unchanged.

## U. Commit hash

This report is committed atomically with the release, so it cannot contain its own commit hash without a prohibited amend. Retrieve the exact release commit hash after creation with `git rev-parse HEAD`.

## V. Exact GitHub push command

```bash
git push origin preview/verify-deployment
```

This command is prepared only; it was not executed.

## W. Remaining manual account-level setup

1. Rotate the historical development JWT key; generate a new 64-byte secret in provider-managed storage and set it as `JWT_SECRET` in both Render services.
2. Provision separate preview and production MySQL 8 databases, users, passwords, and TLS settings; grant only required DML/DDL rights.
3. Set GitHub Actions variables `PREVIEW_DEPLOY_ENABLED`, `PRODUCTION_DEPLOY_ENABLED`, and the four health URL variables.
4. Set the four deploy-hook secrets in GitHub; configure hook branch targets in Render/Vercel dashboards.
5. In Vercel, set Root Directory to `frontend` and configure distinct HTTPS `VITE_API_BASE_URL` values ending in `/api` for Preview and Production.
6. In Render, confirm both services, disk mount/ownership, environment secrets, and branch mappings from `render.yaml`.
7. Configure SMTP/provider mail settings for password reset.
8. Run the real MySQL integration tests with `AS_TEST_MYSQL_*` credentials and run the benchmark tests with `-Dbenchmark=true` if desired.

## X. Post-push verification checklist

1. Confirm the pushed commit SHA equals the local release commit.
2. Confirm every GitHub Actions check is green, including `actionlint` and the MySQL 8.4 job.
3. Confirm `deploy-preview` ran and both provider dashboards show the same commit SHA.
4. Call preview and production `/api/health/ready` and verify `status`, `database`, `storage`, and `migrations` are `UP`.
5. Verify the built frontend bundle contains the correct environment-specific API base URL and no localhost API reference.
6. Smoke-test registration, login, upload, EDA, AutoML, reports, and logout in the deployed preview.
7. Promote to production only through the approved pull request into `main` and required approvals.
8. Record actual public URLs and deployment evidence in a separate post-deployment note; do not edit this report to claim unverified deployment.

## Y. Known limitations and blocked validations

1. Docker image builds: **BLOCKED** (daemon unavailable). Dockerfile/config validation only.
2. Real MySQL 8 production migration/integration: **BLOCKED** (no database credentials/server); six tests skipped and not reported as passing.
3. Cloud deployment success: **BLOCKED** (no GitHub/Vercel/Render/MySQL account access).
4. Actionlint local execution: **NOT RUN**; CI job is configured and pinned.
5. `database/schema.sql` remains a non-authoritative reference with some nullability/FK drift from Flyway; production uses Flyway and Hibernate validation.
6. In-memory rate limiting and token revocation are single-instance only.
7. Historical JWT key in Git history requires rotation; history was not rewritten.
8. The React frontend stores the bearer token in `localStorage`; consider a hardened cookie/session design for a future security iteration.
