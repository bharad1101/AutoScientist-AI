# GitHub Release Guide

Status: **locally prepared; not staged, committed, pushed, or deployed; GitHub Actions and provider state not verified.**

This guide covers the release of the existing AutoScientist AI repository to
`bharad1101/AutoScientist-AI`. The first push targets
`preview/verify-deployment`. Production is reached only by an approved pull
request into `main`.

## 1. Repository state and rules

- Current branch: `preview/verify-deployment` (upstream
  `origin/preview/verify-deployment`).
- Production branch: `main`.
- Java is **17 only**. The Maven POM, CI, Dockerfiles, and wrapper all target 17.
- Never commit `.env`, local databases (`*.mv.db`), `uploads/`, `backups/`,
  `target/`, `dist/`, `node_modules/`, private keys, or generated reports.
- Never use `git add .` or `git add -A`. Stage explicit reviewed paths.
- Never push directly to `main`, never rewrite history, and never force-push.
- The final public staging list contains **207 verified paths**, enumerated in
  `docs/FINAL_PRODUCTION_RELEASE_REPORT.md` section Q. It excludes all
  `release-readiness/*` files, internal audit/roadmap reports, and
  local-path-heavy historical docs.

## 2. Required GitHub secrets

Add these under **Settings → Secrets and variables → Actions → Secrets**. They
are used by `.github/workflows/ci.yml` deploy jobs only. Values are provider
generated and must never be pasted into the repository or documentation.

| Name | Used by | Purpose |
|---|---|---|
| `RENDER_PREVIEW_DEPLOY_HOOK_URL` | `deploy-preview` | Starts the Render preview service deploy |
| `VERCEL_PREVIEW_DEPLOY_HOOK_URL` | `deploy-preview` | Starts the Vercel preview deployment |
| `RENDER_PRODUCTION_DEPLOY_HOOK_URL` | `deploy-production` | Starts the Render production service deploy |
| `VERCEL_PRODUCTION_DEPLOY_HOOK_URL` | `deploy-production` | Starts the Vercel production deployment |

Hook URLs are secrets because possession of the URL triggers a deployment.
The workflow passes `?ref=<full commit SHA>` to the Render hook. Confirm in the
Render dashboard that each hook is pinned to its intended branch; a hook cannot
be re-pointed from this repository.

## 3. Required GitHub variables

Add these under **Settings → Secrets and variables → Actions → Variables**.

| Name | Purpose |
|---|---|
| `PREVIEW_DEPLOY_ENABLED` | Set to `true` to allow preview deployment after CI passes |
| `PRODUCTION_DEPLOY_ENABLED` | Set to `true` to allow production deployment after CI passes |
| `PREVIEW_BACKEND_HEALTH_URL` | Full URL to preview `/api/health/ready` |
| `PREVIEW_FRONTEND_HEALTH_URL` | Full URL to preview frontend root |
| `PRODUCTION_BACKEND_HEALTH_URL` | Full URL to production `/api/health/ready` |
| `PRODUCTION_FRONTEND_HEALTH_URL` | Full URL to production frontend root |

If an enable variable is absent or not `true`, the deploy job is skipped: CI
can be green with no deployment. Check the Actions summary for the deploy job,
not just the workflow conclusion.

## 4. Branch protection (manual)

For `main`:

- Require a pull request before merging.
- Require the CI checks to pass.
- Require review approval.
- Dismiss stale approvals when new commits are pushed.
- Do not allow force pushes or deletion.

For `preview/verify-deployment`: require CI checks on direct pushes; this branch
is the deployment source for preview.

## 5. Exact release commands

Perform these only after reviewing the complete final public file list below
and obtaining explicit approval. Run from the repository root.

```bash
cd "/path/to/AutoScientist AI"
git status --short
git diff --check
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
git diff --cached --check
git diff --cached --stat
git diff --cached --name-status
git commit -m "release: finalize production deployment readiness"
git push origin preview/verify-deployment
```

The list includes the deletion of
`backend/src/test/resources/db/test/password_reset_tokens.sql`. It intentionally
excludes ignored local artifacts and internal/historical reports. Do not replace
the list with `git add .` or `git add -A`.

## 6. After the push

1. Open the GitHub Actions run for the pushed commit.
2. Confirm every check job is green: backend, frontend, Docker, MySQL migrations,
   security, deploy-config, workflow-lint.
3. Confirm `deploy-preview` ran. If it was skipped, check the enable variable,
   the four secrets, and the six variables.
4. Confirm the health checks passed and record the actual preview URLs.
5. Check Render and Vercel dashboards for the deployed commit. The health probe
   alone cannot prove the new SHA is live; verify the provider deployment record.
6. Run a browser smoke test: register/login, upload a dataset, run an analysis,
   verify dashboards and PDF export, then log out.

## 7. Promote to production

1. Open a pull request from `preview/verify-deployment` into `main`.
2. Wait for all required CI checks and approvals.
3. Merge the pull request; do not push directly to `main`.
4. Confirm `deploy-production` ran after the merge and health checks passed.
5. Verify the production provider dashboards show the merged commit SHA.
6. Run the production smoke test and record the result in the release report.

## 8. Evidence labels used in this repository

- **Locally verified**: executed in the workspace on this machine.
- **CI verified**: observed in a GitHub Actions run.
- **Provider configured**: observed in a Render/Vercel/MySQL dashboard.
- **Actually deployed / publicly accessible**: observed at a live public URL.
- **Not yet verified**: not observed; never reported as passing.
