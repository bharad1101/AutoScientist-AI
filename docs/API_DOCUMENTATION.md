# AutoScientist AI REST API

The backend API is rooted at `/api`. All endpoints require
`Authorization: Bearer <JWT>` except `GET /api/health` and the four account
bootstrap/recovery endpoints: register, login, forgot password, and reset
password. Successful controller responses use the common `ApiResponse` wrapper
unless the endpoint returns a file download.

## Authentication

| Method | Path | Purpose |
|---|---|---|
| POST | `/api/auth/register` | Create an account and start a session |
| POST | `/api/auth/login` | Authenticate and issue a JWT |
| POST | `/api/auth/forgot-password` | Request a reset email (generic response) |
| POST | `/api/auth/reset-password` | Consume a single-use reset token |
| GET | `/api/auth/me` | Get the authenticated user |
| POST | `/api/auth/logout` | Client logout acknowledgement; JWT is otherwise stateless |

## Datasets and workflow

| Method | Path | Purpose |
|---|---|---|
| POST | `/api/datasets/preview` | Preview CSV or Excel import; multipart `file`, optional `options` |
| POST | `/api/datasets/upload` | Import and profile CSV or Excel; multipart `file`, optional `name`, `description`, `options` |
| GET | `/api/datasets` | List the authenticated user's datasets |
| GET | `/api/datasets/{id}` | Get dataset detail and profile |
| GET | `/api/datasets/{id}/quality` | Get data quality report |
| GET | `/api/datasets/{id}/workflow-status` | Get workflow completion state |
| POST | `/api/datasets/{id}/reprofile` | Reprofile with optional overrides |
| PUT | `/api/datasets/{id}/exclusions` | Update excluded columns |
| DELETE | `/api/datasets/{id}` | Delete the owned dataset and stored files |

## EDA and association analysis

| Method | Path | Purpose |
|---|---|---|
| GET | `/api/datasets/{datasetId}/associations` | Read computed associations |
| POST | `/api/datasets/{datasetId}/associations/refresh` | Recompute associations |
| GET | `/api/datasets/{datasetId}/eda/correlations` | Correlation matrix |
| GET | `/api/datasets/{datasetId}/eda/boxplots` | Numeric five-number summaries |
| GET | `/api/datasets/{datasetId}/eda/grouped` | Group summaries |
| GET | `/api/datasets/{datasetId}/eda/histograms` | Numeric histograms |
| GET | `/api/datasets/{datasetId}/eda/scatter` | Scatter data |
| GET | `/api/datasets/{datasetId}/eda/missingness` | Missingness summary |
| GET | `/api/datasets/{datasetId}/eda/categorical` | Categorical summaries |
| GET | `/api/datasets/{datasetId}/eda/timeseries` | Time series data |
| GET | `/api/datasets/{datasetId}/eda/group-distributions` | Group distributions |
| GET | `/api/datasets/{datasetId}/eda/missingness-matrix` | Row/column missingness matrix |

## Research, evidence, findings, and experiments

| Method | Path | Purpose |
|---|---|---|
| POST | `/api/datasets/{datasetId}/research-questions` | Create a research question and hypotheses |
| GET | `/api/datasets/{datasetId}/research-questions` | List dataset research questions |
| GET | `/api/research-questions/{id}` | Get a research question |
| POST | `/api/research-questions/{rqId}/hypotheses/{hypothesisId}/execute` | Run the hypothesis test |
| GET | `/api/datasets/{datasetId}/evidence` | Get evidence graph |
| GET | `/api/datasets/{datasetId}/reliability` | Get evidence reliability summary |
| GET | `/api/datasets/{datasetId}/findings` | List findings for a dataset |
| GET | `/api/findings/{id}` | Get a finding |
| GET | `/api/experiments` | List experiments for the authenticated user |
| GET | `/api/experiments/{id}` | Get an experiment and versions |
| GET | `/api/experiments/{id}/comparison` | Compare experiment versions |

## AutoML, explainability, fairness, what-if, and drift

| Method | Path | Purpose |
|---|---|---|
| POST | `/api/datasets/{id}/automl/train` | Train candidate models and persist the model run |
| GET | `/api/datasets/{id}/automl/latest` | Read latest model run |
| POST | `/api/datasets/{id}/automl/whatif` | Evaluate a what-if scenario |
| GET | `/api/datasets/{id}/automl/whatif/schema` | Get supported scenario inputs |
| GET | `/api/datasets/{id}/automl/feature-effect` | Get feature effect data |
| GET | `/api/drift/compare` | Compare reference and target datasets (query parameters) |
| POST | `/api/datasets/{id}/ask` | Ask a natural-language question about a dataset |

Fairness metrics and explainability outputs are included in model run results;
they do not have separate controller routes.

## Asynchronous jobs

| Method | Path | Purpose |
|---|---|---|
| POST | `/api/jobs` | Submit `type=EDA\|ASSOCIATION\|AUTOML` with `datasetId`; `targetColumn` required for `AUTOML`; returns `202` |
| GET | `/api/jobs` | List the authenticated user's jobs |
| GET | `/api/jobs/{id}` | Get an owned job's status, progress, and error |
| POST | `/api/jobs/{id}/cancel` | Cancel an owned job (best effort) |

`INGESTION` is rejected with guidance to use `POST /api/datasets/upload`. Queue
saturation returns `413` rather than silently dropping work.

## Model serving

| Method | Path | Purpose |
|---|---|---|
| POST | `/api/v1/models/{modelId}/predict` | Real inference from the persisted trained artifact (ownership-checked) |

## Reports, dashboard, and health

| Method | Path | Purpose |
|---|---|---|
| POST | `/api/datasets/{id}/reports` | Generate and persist a report |
| GET | `/api/datasets/{id}/reports` | List reports for a dataset |
| GET | `/api/reports` | List the authenticated user's reports |
| GET | `/api/reports/{id}` | Get a report |
| GET | `/api/reports/{id}/pdf` | Download PDF |
| GET | `/api/reports/{id}/export/csv` | Download CSV export |
| GET | `/api/reports/{id}/export/excel` | Download Excel export |
| GET | `/api/reports/{id}/export/markdown` | Download Markdown export |
| GET | `/api/dashboard/stats` | Get authenticated user's dashboard statistics |
| GET | `/api/health` | Public liveness response |
| GET | `/api/health/ready` | Public readiness: datasource and storage backend reachability (`503` when down) |
| GET | `/actuator/health`, `/actuator/info` | Public matcher in `SecurityConfig`; actuator exposure is configured only in the `dev` profile document, so do not rely on these in `mysql` production |
| GET | `/actuator/metrics`, `/actuator/prometheus` | Configured in the `dev` profile document only; not exposed by default in production |

## Production notes

- Dataset and associated feature access is scoped to the authenticated owner;
  report, job, model, drift, and dataset ownership regression tests cover
  cross-user access.
- Upload endpoints accept CSV and Excel files and enforce `resource-budget`
  file-size/row/column limits. Exceeding them returns **HTTP 413** with a
  `ResourceLimitExceededException` message; the old silent-truncation path is gone.
- Every response includes `X-Request-Id` (caller-supplied when safe, otherwise
  generated). The same value is attached to server logs as `requestId`.
- `GET /api/health` is a liveness check. `GET /api/health/ready` additionally
  proves the datasource and storage backend are reachable, returning `503` when
  either is down.
- Rate limiting returns `429` with `Retry-After` and `X-RateLimit-*` headers.
  It is in-memory per instance.
- Reset-email delivery requires working SMTP configuration; the API keeps the
  forgot-password response generic to avoid account enumeration.
