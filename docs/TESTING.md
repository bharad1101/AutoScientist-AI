# AutoScientist AI — Comprehensive Testing Guide

> **Runtime requirement:** all backend tests must run on **JDK 17**. Set
> `JAVA_HOME` to a JDK 17 installation before invoking `./mvnw`.
> `backend/mvnw` is the official Apache Maven Wrapper; it bootstraps Maven 3.9.9
> automatically, so no system Maven installation is required.
> Current measured baseline: **272 tests, 0 failures, 0 errors, 6 environment-gated skips**.

## 1. Testing Philosophy

AutoScientist AI adheres to the principle of **empirical verification**:

- Zero fabricated tests or mocks of mathematical results.

- Statistical engines and ML benchmarks must be verified on real datasets (`test-data/student_performance.csv` and `test-data/biomedical_patient_data.csv`).

- Code changes must never be marked complete without executing the backend test suite and compiling the frontend bundle.



---



## 2. Test Architecture & Directory Layout



```

backend/src/test/java/com/autoscientist/

├── AuthControllerTest.java                   # REST security, JWT, login, register endpoints

├── AuthServiceTest.java                      # Password hashing, token generation, user repo mocks

├── PasswordResetIntegrationTest.java         # Forgot/reset flow, expiry, single use, reuse rejection

├── DatasetProfilerTest.java                  # Type inference, extrema, distributions, missing values

├── DataQualityEngineTest.java                # Quality scoring (0-100), duplicate detection, outliers

├── StatisticalTestEngineTest.java            # Pearson, Welch t-test, ANOVA, Chi-Square verification

├── QuestionAnalysisEngineTest.java           # NLP entity matching, variable extraction, H0/H1 hypotheses

├── AutoMlEngineTest.java                     # Pure Java ML benchmarks (Ridge, Decision Trees, Forest)

├── DataDriftAndAskTest.java                  # Population Stability Index (PSI) & natural language Ask engine

├── ReportAndPdfTest.java                     # Scientific report synthesis & OpenPDF vector binary export

├── EndToEndPipelineTest.java                 # Full regression workflow on student_performance.csv

├── BiomedicalClassificationPipelineTest.java # Full clinical classification workflow on biomedical_patient_data.csv

├── CsvTableTest.java                         # CSV parsing, quoted newlines, row/column resource limits
├── RequestIdFilterTest.java                  # Correlation ID generation, propagation, injection safety
├── FlywayMigrationTest.java                  # Fresh V1 baseline, legacy baseline, real V5 DDL on H2
└── RealMysqlMigrationTest.java               # Env-gated real-MySQL fresh/legacy migration validation

```



---



## 3. Running Backend Tests



Execute the Maven Surefire test suite from the backend directory with the bundled
wrapper (no system Maven installation needed):

```bash

export JAVA_HOME="/path/to/jdk-17"

export PATH="$JAVA_HOME/bin:$PATH"

cd backend

./mvnw clean test

```

> Real-MySQL migration and MySQL-profile smoke tests are skipped unless their
> environment variables are set; see `docs/MANUAL_DEPLOYMENT_STEPS.md` for the
> staging MySQL command. The opt-in `WorkflowPerformanceBenchmarkTest` and the
> expensive case in
> `backend/src/test/java/com/autoscientist/benchmark/WideAssociationBenchmarkTest.java`
> skip unless the `benchmark` system property is set:
> `./mvnw -Dtest=WorkflowPerformanceBenchmarkTest -Dbenchmark=true test`.
> The small functional `WideAssociationBenchmarkTest` case still runs in the normal suite.



### Expected Output

```

[INFO] -------------------------------------------------------

[INFO]  T E S T S

[INFO] -------------------------------------------------------

[INFO] Running com.autoscientist.DataQualityEngineTest

[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0

[INFO] Running com.autoscientist.QuestionAnalysisEngineTest

[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0

[INFO] Running com.autoscientist.AutoMlEngineTest

[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0

[INFO] Running com.autoscientist.StatisticalTestEngineTest

[INFO] Tests run: 5, Failures: 0, Errors: 0, Skipped: 0

[INFO] Running com.autoscientist.EndToEndPipelineTest

[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0

[INFO] Running com.autoscientist.DataDriftAndAskTest

[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0

[INFO] Running com.autoscientist.ReportAndPdfTest

[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0

[INFO] Running com.autoscientist.AuthControllerTest

[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0

[INFO] Running com.autoscientist.AuthServiceTest

[INFO] Tests run: 7, Failures: 0, Errors: 0, Skipped: 0

[INFO] Running com.autoscientist.BiomedicalClassificationPipelineTest

[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0

[INFO] Running com.autoscientist.DatasetProfilerTest

[INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0

[INFO]

[INFO] Results:

[INFO] Tests run: 272, Failures: 0, Errors: 0, Skipped: 6

[INFO] BUILD SUCCESS

```



---



## 4. Running Frontend Verification



Compile and bundle the React 18 / Vite frontend:

```bash

cd frontend

npm ci

npm test

VITE_API_BASE_URL="https://api.example.com/api" npm run build

```

The frontend URL validator is covered by `src/test/apiBaseUrl.test.js` and the
Vite build wiring by `src/test/viteConfigBuildGuard.test.js`. Builds reject
missing, invalid, non-HTTPS, loopback (including mapped IPv6 and numeric
encodings), credentialed, query/fragment, non-standard-port, and trailing-slash
values. A relative `/api` is accepted only when
`VITE_API_PROXY_MODE=same-origin` is set for a real reverse-proxy deployment.



### Expected Output

```

✓ 23 test files, 165 tests passed

vite v8.x building for production...

✓ modules transformed.

dist/index.html                               0.5 kB │ gzip:   0.3 kB

dist/assets/index-*.css                     ~310 kB │ gzip:  ~45 kB

dist/assets/index-*.js                      ~218 kB │ gzip:  ~61 kB

✓ built successfully

```



---



## 5. End-to-End Benchmark Datasets



### 5.1 `student_performance.csv` (Regression Benchmark)

- **Rows**: 32 | **Columns**: 7

- **Target**: `exam_score` (Continuous)

- **Verified Relationship**: Decisive positive linear correlation with `study_hours` ($r > 0.85, p < 10^{-6}$).

- **Validates**: RFC-4180 parsing, numeric profiling, ANOVA, Pearson tests, Ridge regression, Decision Tree regressor, permutation feature importance.



### 5.2 `biomedical_patient_data.csv` (Classification Benchmark)

- **Rows**: 27 | **Columns**: 8

- **Target**: `outcome` (Positive / Negative)

- **Predictors**: `age`, `bmi`, `systolic_bp`, `cholesterol`, `smoker`, `exercise_level`.

- **Validates**: Categorical encoding, Chi-square independence tests, Welch t-tests, Logistic regression, Random forest classification, Demographic parity fairness auditing.



---



## 6. Monorepo One-Click Build Script



Execute the project-level build script:

```bash

./scripts/build_all.sh

```

This script sequentially builds and validates the Spring Boot backend (`mvn package -DskipTests`) and builds the Vite frontend bundle (`npm run build`).
