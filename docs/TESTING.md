# AutoScientist AI — Comprehensive Testing Guide



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

└── BiomedicalClassificationPipelineTest.java # Full clinical classification workflow on biomedical_patient_data.csv

```



---



## 3. Running Backend Tests



Execute Maven Surefire test suite from the backend directory:

```bash

cd backend

./mvnw clean test

```



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

[INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0

[INFO] Running com.autoscientist.BiomedicalClassificationPipelineTest

[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0

[INFO] Running com.autoscientist.DatasetProfilerTest

[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0

[INFO]

[INFO] Results:

[INFO] Tests run: 24, Failures: 0, Errors: 0, Skipped: 0

[INFO] BUILD SUCCESS

```



---



## 4. Running Frontend Verification



Compile and bundle the React 18 / Vite frontend:

```bash

cd frontend

npm run build

```



### Expected Output

```

vite v5.4.x building for production...

✓ 116 modules transformed.

dist/index.html                               0.52 kB │ gzip:   0.35 kB

dist/assets/bootstrap-icons-*.woff2         134.04 kB

dist/assets/bootstrap-icons-*.woff          180.29 kB

dist/assets/index-*.css                     308.39 kB │ gzip:  44.94 kB

dist/assets/index-*.js                      478.62 kB │ gzip: 149.03 kB

✓ built in 3.60s

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
