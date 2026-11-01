# GITHUB_RELEASE_MANIFEST.md

Repo: bharad1101/AutoScientist-AI
Branch: main (ahead of origin/main by 1 commit)
HEAD: 8b4cf87 fix: align Flyway support with MySQL 8.4
Unpushed: 1 commit (safe to retain; related to Flyway/MySQL 8.4 alignment)

## Modified files (13) - review
- backend/src/main/java/com/autoscientist/config/SecurityConfig.java
- backend/src/main/java/com/autoscientist/controller/HealthController.java
- backend/src/main/java/com/autoscientist/security/UserDetailsServiceImpl.java
- backend/src/main/java/com/autoscientist/service/AuthService.java
- backend/src/main/java/com/autoscientist/service/PasswordResetService.java (fixed)
- backend/src/test/java/com/autoscientist/AuthServiceTest.java
- backend/src/test/java/com/autoscientist/HealthControllerTest.java
- backend/src/test/java/com/autoscientist/RealMysqlMigrationTest.java
- docs/DEPLOYMENT.md
- frontend/src/pages/LoginPage.jsx (demo creds gated to DEV)
- frontend/src/pages/RegisterPage.jsx (72-byte guard)
- release-readiness/FINAL_RELEASE_ACCEPTANCE_REPORT.md
- release-readiness/TEST_RESULTS.md

## Untracked files (14) - do NOT commit blindly
- release-readiness/AUTODEPLOYMENT_CONFIGURATION.md
- release-readiness/DEPLOYMENT_CHECKLIST.md
- release-readiness/DEPLOYMENT_CONFIGURATION.md
- release-readiness/FINAL_CODE_REVIEW.md
- release-readiness/FINAL_GO_NO_GO_REPORT.md
- release-readiness/FINAL_PROVIDER_ACCESS.md
- release-readiness/FINAL_TEST_REPORT.md
- release-readiness/GITHUB_RELEASE_MANIFEST.md
- release-readiness/PRE_RELEASE_CHANGE_REVIEW.md
- release-readiness/PRODUCTION_DEPLOYMENT_CHECKLIST.md
- release-readiness/PROVIDER_ACCESS_STATUS.md
- release-readiness/RELEASE_PLAN.md
- release-readiness/ROLLBACK_PLAN.md
- release-readiness/SMOKE_TEST_REPORT.md

## Secret scan
- No secrets committed. Config uses env vars (JWT_SECRET, DB creds). Docs reference placeholders only.

## Recommendation
- Proposed for commit: the 13 modified source/docs files (after review).
- Keep untracked release-readiness docs uncommitted unless explicitly requested.
