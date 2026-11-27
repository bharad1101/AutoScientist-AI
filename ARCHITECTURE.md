# AutoScientist AI — Architecture Documentation

> **"From Raw Data to Evidence-Based Scientific Discovery."**

---

## 1. Executive Overview

**AutoScientist AI** is an autonomous scientific discovery and data science platform. It transforms raw datasets into rigorous, evidence-backed scientific discoveries using deterministic, reproducible computations in Java and presents them through an interactive React dashboard.

Key Architectural Tenets:
- **Zero Hallucination / No Fabricated Results**: Every correlation, ANOVA, p-value, decision tree, or quality score is calculated from real dataset observations.
- **Strict Separation of Correlation & Causation**: All findings, hypotheses, and reports enforce scientific integrity, preventing unwarranted causal claims.
- **Self-Contained Full-Stack**: Built entirely with Java 17 (Spring Boot 4; Java 21+ is not supported) on the backend and React (Vite, Bootstrap, Chart.js) on the frontend. No Python dependencies.

### Runtime and API configuration

- Java 17 is the mandatory backend source, build, test, container, and CI runtime.
- Development runs the Vite frontend on `http://localhost:5173` with `VITE_API_BASE_URL=/api`; Vite proxies `/api` to the backend on `http://localhost:8080`.
- Production uses either an explicitly configured HTTPS backend URL ending in `/api` (separate deployments) or same-origin `/api` plus `VITE_API_PROXY_MODE=same-origin` only when the host routes `/api` to the backend.
- Production builds reject localhost/loopback, malformed URLs, unsupported protocols, credentials, query strings, fragments, non-443 ports, and unvalidated relative paths. No production URL is embedded in source or documentation.

---

## 2. System Architecture

```
+-----------------------------------------------------------------------------------+
|                                  CLIENT TIER                                      |
|                                                                                   |
|   React 18 SPA (Vite, Bootstrap 5, Bootstrap Icons, Chart.js, React Router v7)    |
|   - Authentication Context & Route Guards                                         |
|   - Dataset Profiler, EDA Visualizations, Associations Matrix                     |
|   - Hypothesis & Research Workflow, AutoML Benchmarks, What-If Simulator          |
|   - Real-time Evidence Graph & Interactive Scientific Report Generator            |
+-----------------------------------------------------------------------------------+
                                        |  HTTPS / REST / JWT
                                        v
+-----------------------------------------------------------------------------------+
|                              SPRING BOOT BACKEND                                  |
|                                                                                   |
|  [ REST API Controllers ]                                                         |
|    - AuthController, DatasetController, EdaController, AssociationController      |
|    - ResearchController, HypothesisController, StatisticsController               |
|    - AutoMLController, ExplainabilityController, ReportController, etc.           |
|                                                                                   |
|  [ Security & Auth Layer ]                                                        |
|    - Spring Security 7, JWT Authentication Filter, BCrypt Password Encoder         |
|    - Strict IDOR (Insecure Direct Object Reference) Resource Ownership Guards     |
|                                                                                   |
|  [ Modular Analysis Engines ]                                                     |
|    ├── Profiling Engine (Column stats, robust type detection)                     |
|    ├── Quality Engine (Missingness, outliers, anomalies, quality score)           |
|    ├── EDA Engine (Histograms, box-plots, correlation matrices, cross-tabs)       |
|    ├── Association Engine (Pearson, Spearman, Welch's t-test, One-way ANOVA, χ²)  |
|    ├── Hypothesis & Research Engine (RQ parsing, H0/H1 formulation)              |
|    ├── Feature Engineering Engine (Imputation, One-Hot, Standard/MinMax scaling)  |
|    ├── AutoML Engine (Ridge/Linear Regr, Logistic Regr, Decision Trees, Forest)   |
|    ├── Explainability Engine (Permutation feature importance, coefficients)       |
|    ├── Fairness Engine (Disparate impact, demographic parity, group error rates)  |
|    ├── Drift Engine (Population Stability Index, Kolmogorov-Smirnov, χ² drift)   |
|    ├── Provenance & Evidence Engine (Directed Acyclic Evidence Graph)             |
|    └── Report & PDF Engine (Markdown synthesis + OpenPDF vector rendering)        |
+-----------------------------------------------------------------------------------+
                                        |  Spring Data JPA / Hibernate
                                        v
+-----------------------------------------------------------------------------------+
|                                DATABASE TIER                                      |
|                                                                                   |
|   MySQL 8.0+ (InnoDB, UTF-8mb4, Normalized relational tables, Foreign keys)       |
|   - Users, Datasets, DatasetColumns, DataQualityReports, Associations             |
|   - ResearchQuestions, Hypotheses, StatisticalTests, Experiments, Models          |
|   - Findings, EvidenceNodes, Reports                                              |
+-----------------------------------------------------------------------------------+
```

---

## 3. Core Analytical Pipelines

### 3.1. Statistical Association Pipeline
1. **Numeric + Numeric**:
   - Drops rows with missing values in either column concurrently (paired alignment maintained).
   - Computes Pearson correlation $r = \frac{\sum (x - \bar{x})(y - \bar{y})}{\sqrt{\sum (x-\bar{x})^2 \sum (y-\bar{y})^2}}$.
   - Derives $t = r \sqrt{\frac{n-2}{1-r^2}}$ and two-tailed p-value via Student's t-distribution.
   - Calculates effect size ($r^2$, variance explained) and confidence interval.
2. **Numeric + Binary Categorical**:
   - Splits numeric values by group.
   - Computes Welch's unequal variances t-test (robust against heteroscedasticity).
   - Computes Cohen's $d$ effect size.
3. **Numeric + Multi-Class Categorical**:
   - Executes One-Way ANOVA across all non-empty classes.
   - Calculates F-statistic, p-value via F-distribution, and Eta-squared ($\eta^2$).
4. **Categorical + Categorical**:
   - Constructs two-way contingency table.
   - Computes Pearson Chi-Square ($\chi^2 = \sum \frac{(O-E)^2}{E}$).
   - Computes Cramer's $V$ for effect magnitude.

### 3.2. AutoML & Model Explainability
- **Data Splitting**: Strict 70% Train, 15% Validation, 15% Test partitioning to eliminate data leakage.
- **Standardization & Preprocessing**: Transformers fitted exclusively on training splits and applied to validation/test sets.
- **Algorithms**:
  - Regression: Ordinary Least Squares (OLS) Linear Regression, Ridge Regression, Decision Tree Regressor.
  - Classification: Binary/Multinomial Logistic Regression, Decision Tree Classifier, Random Forest.
- **Explainability**: Permutation Feature Importance evaluated on validation splits; normalized importance scores; model weights and odds ratios.

### 3.3. Scientific Report & Evidence Provenance
- Findings are structured as an Evidence Graph where findings trace back to exact statistical tests, datasets, preprocessing versions, and model runs.
- PDF generation compiles metadata, charts, data quality matrices, and scientific narratives using OpenPDF.
