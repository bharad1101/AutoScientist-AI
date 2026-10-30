# AutoScientist AI — Database Schema Documentation

## Database Engine: MySQL 8.0+
- **Database Name**: `autoscientist_db`
- **Character Set**: `utf8mb4`
- **Collation**: `utf8mb4_unicode_ci`

---

## Entity Relationship Overview

```
[ users ]
   │
   ├──< [ password_reset_tokens ]
   │
   ├──< [ datasets ]
   │       │
   │       ├──< [ dataset_columns ]
   │       ├──< [ data_quality_reports ]
   │       │         │
   │       │         └──< [ data_quality_issues ]
   │       │
   │       ├──< [ associations ]
   │       │
   │       ├──< [ research_questions ]
   │       │         │
   │       │         └──< [ hypotheses ]
   │       │                   │
   │       │                   └──< [ statistical_tests ]
   │       │
   │       ├──< [ experiments ]
   │       │         │
   │       │         └──< [ experiment_versions ]
   │       │                   │
   │       │                   └──< [ models ]
   │       │
   │       ├──< [ findings ]
   │       │         │
   │       │         └──< [ evidence_nodes ]
   │       │
   │       └──< [ reports ]
```

---

## Detailed Table Specifications

### 1. `users`
Stores authenticated users with BCrypt password hashing.
| Column | Type | Constraints | Description |
| :--- | :--- | :--- | :--- |
| `id` | BIGINT | PRIMARY KEY, AUTO_INCREMENT | Unique user identifier |
| `name` | VARCHAR(100) | NOT NULL | User's full display name |
| `email` | VARCHAR(150) | NOT NULL, UNIQUE | User login email address |
| `password_hash` | VARCHAR(255) | NOT NULL | BCrypt hashed password |
| `role` | VARCHAR(50) | NOT NULL, DEFAULT 'ROLE_USER' | User role (ROLE_USER, ROLE_ADMIN) |
| `created_at` | TIMESTAMP | DEFAULT CURRENT_TIMESTAMP | Account creation timestamp |
| `updated_at` | TIMESTAMP | ON UPDATE CURRENT_TIMESTAMP | Last profile modification |

### 2. `datasets`
Stores user-uploaded datasets with storage references and metadata.
| Column | Type | Constraints | Description |
| :--- | :--- | :--- | :--- |
| `id` | BIGINT | PRIMARY KEY, AUTO_INCREMENT | Unique dataset identifier |
| `user_id` | BIGINT | NOT NULL, FK -> users(id) | Owning user (enforces IDOR security) |
| `name` | VARCHAR(255) | NOT NULL | User-assigned or original name |
| `original_filename` | VARCHAR(255) | NOT NULL | Original CSV filename |
| `stored_filename` | VARCHAR(255) | NOT NULL | UUID filename in upload storage |
| `file_size` | BIGINT | NOT NULL | File size in bytes |
| `row_count` | INT | NOT NULL, DEFAULT 0 | Valid row count |
| `column_count` | INT | NOT NULL, DEFAULT 0 | Total variable count |
| `dataset_type` | VARCHAR(50) | DEFAULT 'MIXED' | Inferred type: REGRESSION, CLASSIFICATION, MIXED |
| `description` | TEXT | NULL | User notes or description |
| `created_at` | TIMESTAMP | DEFAULT CURRENT_TIMESTAMP | Ingestion timestamp |

### 3. `dataset_columns`
Variable-level descriptive statistics computed by Apache Commons Math.
| Column | Type | Constraints | Description |
| :--- | :--- | :--- | :--- |
| `id` | BIGINT | PRIMARY KEY, AUTO_INCREMENT | Column identifier |
| `dataset_id` | BIGINT | NOT NULL, FK -> datasets(id) | Parent dataset |
| `column_index` | INT | NOT NULL | Zero-indexed CSV column order |
| `column_name` | VARCHAR(255) | NOT NULL | Header name |
| `data_type` | VARCHAR(50) | NOT NULL | NUMERIC, CATEGORICAL, BOOLEAN, DATE_TIME, TEXT |
| `type_confidence` | DOUBLE | DEFAULT 1.0 | Statistical type confidence (0.0 to 1.0) |
| `missing_count` | INT | DEFAULT 0 | Count of null or whitespace entries |
| `missing_pct` | DOUBLE | DEFAULT 0.0 | Missing percentage |
| `unique_count` | INT | DEFAULT 0 | Distinct cardinality |
| `min_val`, `max_val` | DOUBLE | NULL | Extrema values |
| `mean_val`, `median_val`| DOUBLE | NULL | Measures of central tendency |
| `std_dev`, `variance` | DOUBLE | NULL | Dispersion metrics |
| `q1`, `q3` | DOUBLE | NULL | 25th and 75th percentiles |
| `skewness` | DOUBLE | NULL | Distribution asymmetry |
| `categories_json` | LONGTEXT | NULL | Frequency distribution for categories |
| `distribution_json` | LONGTEXT | NULL | 10-bin histogram intervals |

### 4. `data_quality_reports` & `data_quality_issues`
Quantifies dataset cleanliness and catalogs anomalies.
- `overall_score`: Composite metric (0-100) weighting missing values, duplicates, outliers, and type consistency.
- `issues`: Catalogs `DUPLICATE_ROWS`, `HIGH_MISSINGNESS`, `OUTLIERS`, `CONSTANT_COLUMN`, and `ID_COLUMN` with severity levels (`INFO`, `WARNING`, `ERROR`).

### 5. `associations`
Bivariate empirical statistical tests across all column pairs:
- Tests: `PEARSON` (with $r^2$ and exact t-distribution p-values), `WELCH_T_TEST` (with Cohen's d), `ANOVA` (with $\eta^2$), `CHI_SQUARE` (with Cramer's V).
- `is_significant`: Boolean flag evaluated at $\alpha = 0.05$.
- `rank_score`: Sort order balancing effect size, significance, and sample size.

### 6. `research_questions`, `hypotheses` & `statistical_tests`
Scientific workflow formulating formal falsifiable inquiries:
- $H_0$: Null hypothesis (no effect / zero difference).
- $H_1$: Alternative hypothesis (significant directional or non-directional effect).
- Empirical test execution records decision (`REJECT_NULL` vs `FAIL_TO_REJECT_NULL`).

### 7. `experiments`, `experiment_versions` & `models`
Tracks AutoML runs, hyperparameter configurations, and model performance:
- Algorithms: `RidgeRegression`, `DecisionTreeRegressor`, `LogisticRegression`, `DecisionTreeClassifier`, `RandomForestClassifier`.
- Train, validation, and test split metrics (leak-free 70/15/15).
- `is_best_model`: Flags top-performing candidate based on validation metrics.
- `fairness_metrics_json`: Records Demographic Parity Ratio and Four-Fifths rule compliance.

### 8. `findings` & `evidence_nodes`
Scientific conclusions synthesized from verified statistical tests and ML models with strict provenance links and explicit causality disclaimers.

### 9. `reports`
Assembles all discovery results into reproducible Markdown documents and vector PDF binary exports.

### 10. `password_reset_tokens`
Stores single-use password-reset tokens. Only the SHA-256 hash is persisted; the
raw token exists solely in the emailed link.
| Column | Type | Constraints | Description |
| :--- | :--- | :--- | :--- |
| `id` | BIGINT | PRIMARY KEY, AUTO_INCREMENT | Token identifier |
| `user_id` | BIGINT | NOT NULL, FK -> users(id) ON DELETE CASCADE | Account the token belongs to |
| `token_hash` | VARCHAR(64) | NOT NULL, UNIQUE | Hex SHA-256 of the raw token |
| `expires_at` | TIMESTAMP | NOT NULL | Expiry (default 30 minutes after issue) |
| `used_at` | TIMESTAMP | NULL | Set on successful use before the row is removed |
| `created_at` | TIMESTAMP | NOT NULL, DEFAULT CURRENT_TIMESTAMP | Issue timestamp |
