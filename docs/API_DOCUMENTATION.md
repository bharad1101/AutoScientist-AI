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
| GET | `/api/health` | Public lightweight process health response |

## Production notes

- Dataset and associated feature access is scoped to the authenticated owner;
  report and dataset ownership regression tests cover cross-user access.
- Upload endpoints accept CSV and Excel files and enforce the application's
  configured multipart size limit.
- `GET /api/health` reports process status only. It does not perform a database
  readiness check.
- Reset-email delivery requires working SMTP configuration; the API keeps the
  forgot-password response generic to avoid account enumeration.
