# AutoScientist AI — REST API Documentation

Base URL: `http://localhost:8080/api`

All protected endpoints require the HTTP Authorization header:
```http
Authorization: Bearer <JWT_TOKEN>
```

---

## 1. Authentication (`/api/auth`)

### `POST /api/auth/register`
Register a new scientific researcher account.
- **Request Body**:
  ```json
  {
    "name": "Dr. Jane Doe",
    "email": "jane@university.edu",
    "password": "SecurePassword123!"
  }
  ```
- **Response**: `201 Created` with JWT `token` and user profile.

### `POST /api/auth/login`
Authenticate with email and password.
- **Request Body**:
  ```json
  {
    "email": "jane@university.edu",
    "password": "SecurePassword123!"
  }
  ```
- **Response**: `200 OK` with JWT `token`.

### `POST /api/auth/forgot-password`
Request a password-reset link. The response is always the same for known and
unknown addresses (no account enumeration).
- **Request Body**:
  ```json
  { "email": "jane@university.edu" }
  ```
- **Response**: `200 OK` with a generic confirmation. When the account exists,
  a single-use link valid for 30 minutes is emailed to `FRONTEND_URL/reset-password?token=...`.

### `POST /api/auth/reset-password`
Consume a reset token and set a new password. Tokens are stored only as SHA-256
hashes and are invalidated after one successful use.
- **Request Body**:
  ```json
  {
    "token": "<token from the reset email>",
    "newPassword": "NewSecurePassword456!"
  }
  ```
- **Response**: `200 OK` on success; `400 Bad Request` for an expired, used, or
  unknown token, a password shorter than 8 characters, or reuse of the current password.

### `GET /api/auth/me`
Retrieve authenticated researcher details.

---

## 2. Dataset Management & Profiling (`/api/datasets`)

### `POST /api/datasets/upload`
Upload a raw CSV dataset (`multipart/form-data`).
- **Form Parameters**:
  - `file`: CSV file binary
  - `name`: (Optional) Dataset name
  - `description`: (Optional) Dataset description
- **Response**: `201 Created` returning dataset metadata, profiled columns, and quality audit report.

### `GET /api/datasets`
List all datasets owned by the authenticated researcher.

### `GET /api/datasets/{id}`
Retrieve complete dataset profile, columns, extrema, and quality report.

### `DELETE /api/datasets/{id}`
Securely delete dataset and storage artifacts.

---

## 3. Exploratory Data Analysis (`/api/datasets/{id}/eda`)

### `GET /api/datasets/{id}/eda/correlations`
Returns full Pearson correlation matrix between all numeric variables.

### `GET /api/datasets/{id}/eda/boxplots`
Returns five-number summaries (min, Q1, median, Q3, max) for numeric variables.

### `GET /api/datasets/{id}/eda/grouped?numericVar=score&categoricalVar=group`
Computes group-wise means, standard deviations, and sample counts.

---

## 4. Association Discovery Engine (`/api/datasets/{id}/associations`)

### `GET /api/datasets/{id}/associations`
Returns all ranked bivariate associations computed using:
- Pearson correlation ($r$, $r^2$, $p$-value)
- Welch's t-test ($t$, Cohen's $d$, $p$-value)
- One-Way ANOVA ($F$, $\eta^2$, $p$-value)
- Chi-Square Test of Independence ($\chi^2$, Cramer's $V$, $p$-value)

### `POST /api/datasets/{id}/associations/refresh`
Recomputes and updates all pairwise association tests.

---

## 5. Research Questions & Hypotheses (`/api/datasets/{id}/research`)

### `POST /api/datasets/{id}/research/questions`
Submit a research question in natural language or with explicit variables.
- **Request Body**:
  ```json
  {
    "questionText": "Does study_hours predict exam_score?",
    "targetVariable": "exam_score",
    "predictorVariable": "study_hours"
  }
  ```
- **Response**: NLP entity matching, formal $H_0$ / $H_1$ hypotheses formulation, and suggested statistical test.

### `POST /api/hypotheses/{id}/test`
Executes empirical hypothesis test against dataset rows. Records decision (`REJECT_NULL` vs `FAIL_TO_REJECT_NULL`).

---

## 6. AutoML, Explainability & Fairness (`/api/datasets/{id}/automl`)

### `POST /api/datasets/{id}/automl/train`
Triggers automated machine learning benchmark.
- **Request Body**:
  ```json
  {
    "targetColumn": "exam_score",
    "problemType": "REGRESSION",
    "sensitiveColumn": "gender"
  }
  ```
- **Response**:
  - Model leaderboard (Ridge Regression, Decision Trees, Random Forest).
  - Permutation Feature Importance ranking.
  - Demographic Parity and Disparate Impact (Four-Fifths rule) audit.

### `POST /api/datasets/{id}/automl/whatif`
Counterfactual scenario simulation.
- **Request Body**:
  ```json
  {
    "targetColumn": "exam_score",
    "featureValues": {
      "study_hours": 14.5,
      "attendance_pct": 95.0
    }
  }
  ```
- **Response**: Predicted outcome, marginal delta from baseline mean, and top influences.

---

## 7. Data Drift & Stability (`/api/drift`)

### `GET /api/drift/compare?referenceId={refId}&targetId={tarId}`
Calculates Population Stability Index (PSI), mean shifts, and Welch-t distribution shifts across features.

---

## 8. Ask Dataset Natural Language Query (`/api/datasets/{id}/ask`)

### `POST /api/datasets/{id}/ask`
Query dataset in plain English with mathematical verification.
- **Request Body**:
  ```json
  {
    "query": "What is the average exam_score and are there outliers?"
  }
  ```
- **Response**: Formatted scientific answer with exact calculated metrics.

---

## 9. Scientific Reports & Vector PDF (`/api`)

### `POST /api/datasets/{id}/reports`
Synthesizes end-to-end scientific Markdown report.

### `GET /api/reports`
List generated discovery reports.

### `GET /api/reports/{id}`
Retrieve report Markdown and metadata.

### `GET /api/reports/{id}/pdf`
Downloads publication-ready PDF document (`application/pdf`).

---

## 10. Live Dashboard (`/api/dashboard`)

### `GET /api/dashboard/stats`
Aggregates live counts of datasets, observations, associations, models, findings, quality index, and audit provenance timeline.
