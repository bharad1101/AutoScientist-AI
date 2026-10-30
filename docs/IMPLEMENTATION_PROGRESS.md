# AutoScientist AI — Implementation Progress (persistent)

Purpose: survive context-window boundaries. Update this file at the end of every work session.
Rule: never discard prior work; never run destructive git/database commands.

## Current verified baseline (2026-10-19, session 3)

- Backend: `./mvnw test` from `backend/` → **40 tests, 0 failures/errors**, 16 test classes.
- Frontend: `npm run build` from `frontend/` → success, main bundle **607.23 kB** (gzip 183.52 kB).
- Runtime smoke: JDK 21, H2 create-drop, `SERVER_PORT=8099`, temp upload dir; full flow verified (see session 3).
- Git: working tree intentionally dirty (all upgrade work uncommitted, per instruction); stray untracked `AutoScientist AI` left untouched.

## Historical baseline (recorded 2026-10-19, session 1)

- Backend: `./mvnw -q test` from `backend/` → exit 0; 12 test classes, 26 tests, 0 failures.
- Frontend: `npm run build` from `frontend/` → success, 121 modules, main bundle 515.75 kB.
- Git: clean except untracked `docs/AUDIT_AND_ROADMAP.md` and stray file `AutoScientist AI`.
- Environment: OpenJDK 21.0.12, Node v25.8.2, npm 11.11.1, system `mvn` absent (use `./mvnw`).

## Architecture summary (verified)

- Backend Spring Boot 3.2.4 (`backend/`), Java 17 target in pom (runs on JDK 21), packages:
  `controller/ service/ analysis/{profiling,eda,statistics,association,quality,ml,xai,fairness,drift,findings,core,report,feature}/ entity/ dto/ repository/ config/ security/ util/ exception/`.
- Frontend React 18 + Vite (`frontend/src`): pages 14, components 6, context 2, services 4. No test infra yet.
- DB: H2 create-drop (dev), MySQL update (prod), no Flyway; `database/migrations/` empty.
- Uploads: CSV only today, stored under `app.upload.dir` (default `./uploads/datasets`), UUID filenames.

## Phase status

| Phase | Scope | Status |
|---|---|---|
| A | Backend defect fixes (error taxonomy, identifiers, XAI truth, dashboard, reports/PDF, versionNumber, case-sensitivity) | done (session 2) |
| B | Excel (.xls/.xlsx) upload + import wizard backend (POI) | done (session 3) |
| C | Public homepage + responsive navigation | done (session 3) |
| D | Prerequisite-aware workflows (backend + frontend) | done (session 3) |
| E | Visualization/statistics/ML chart system + engine gaps (ROC/PR, CV, confusion matrix, etc.) | done (session 3) |
| F | AutoML/XAI/fairness hardening | done (session 3) |
| G | Ask My Dataset 2.0 | done (session 3) |
| H | Experiment versions + evidence graph + reliability scorecard | done (session 3) |
| I | Reports/exports (Markdown/PDF/CSV/Excel) | done (session 3) |
| J | Performance & security hardening | done, scoped (session 3) — see limitations |
| K | Test suite expansion + E2E smoke | done (session 3) — API smoke, no browser automation |

## Session log

- Session 1 (2026-10-19): baseline recorded; repo mapped (backend 143 files, frontend 31 files); three explore agents produced file:line maps of frontend, backend services, engines/tests. Key findings confirmed: no public homepage; inert hamburger (no Bootstrap JS); no stale-id fallback anywhere; feature importances cache-only; versionNumber hardcoded 1; dashboard `+= 3` fabrication; identifier gate defeatable; PDF uses base-14 Helvetica with literal markdown; POI absent; AskDatasetPage uses non-existent `activeDatasetId` context key.

## Known decisions

- PDF strategy: embed a Unicode TTF (DejaVuSans bundled under `backend/src/main/resources/fonts/`) via OpenPDF `BaseFont`, with ASCII fallback sanitization for glyphs the font lacks. Documented in tests.
- Excel strategy: add `org.apache.poi:poi-ooxml` (handles .xlsx) + legacy `poi` (comes transitively for .xls via HSSF in poi-ooxml? verify — HSSF is in `poi`, XSSF in `poi-ooxml`; add both explicitly).
- Reprofile endpoint: `POST /api/datasets/{id}/reprofile` (additive).
- Feature importances: persist as JSON on `experiment_version`/`model_run` (additive column) + recompute on cache miss.

- Session 2 (2026-10-19): Phase A completed and verified.
  - New provenance layer: `AnalysisRun` entity + `AnalysisRunRepository` + `AnalysisRunService` (REQUIRES_NEW, failure-swallowing, payload capped 200k chars) + `AnalysisOperations` constants. Recorded by upload (IMPORT/PROFILE/QUALITY), EDA, associations, research/stat tests/findings, AutoML/XAI/fairness, reprofile, exclusions.
  - New `ColumnSelection` util (active/analytic/numericAnalytic/isIdentifier) is the single policy for analytic columns; used by DatasetService, AutoMlService, EdaService, AssociationService, ResearchService, QuestionAnalysisEngine, ReportGeneratorService, AskDatasetEngine.
  - `Dataset`: +sourceFormat/sheetName/headerRow/version/parentDatasetId/excludedColumnsJson/importOptionsJson. `ModelRun`: +featureImportancesJson/evaluationJson/randomSeed/train|val|testRows. `ExperimentVersion`: +datasetVersion/randomSeed/engineVersion. All additive.
  - New endpoints: `POST /api/datasets/{id}/reprofile` (ReprofileRequestDto forceIdentifier/forceNonIdentifier, bumps version), `PUT /api/datasets/{id}/exclusions` (DatasetExclusionRequestDto, validates names, bumps version). `DatasetProfiler.profile(in, dataset, forceId, forceNonId)` overload applies overrides before stats.
  - `deleteDataset` now deletes dependents in FK-safe order (reports → findings → tests → hypotheses → questions → model runs → versions → experiments → analysis runs → associations → dataset) — fixes latent MySQL FK failures.
  - AutoMlService: case-insensitive target resolution with explicit-exclusion error, identifier-target rejection, active-columns for training/fairness.
  - QuestionAnalysisEngine: auto-detection skips IDENTIFIER columns and throws a clear BadRequest when fewer than 2 analysable variables exist (was silently testing column 1 vs 1).
  - AskDatasetEngine + ReportGeneratorService: answer/profile over active columns; report table gained an `In Analysis` column and "%d of %d variables enter analysis" note.
  - DashboardService: removed fabricated `+= 3`; all counts now DB-derived (AnalysisRun/Finding/ModelRun/Experiment repos gained aggregate queries).
  - WorkflowService rewritten: 13 stages, completion strictly from persisted evidence (analysis runs, quality report, model runs w/ importances, findings, reports), prerequisite messages + recommended next stage.
  - Frontend: new `src/utils/columns.js` shared policy (isIdentifier/isExcluded/isInAnalysis/analytic/numeric/categorical helpers, dataTypeBadgeClass); Visualizations/AutoML/Research/DatasetDetail pages now use it (local `isIdentifierColumn` heuristic removed); WorkflowContext got stale-id fallback (clears localStorage on 401/403/404 + `clearDataset`); AskDatasetPage fixed to use `datasetId/selectDataset` (was non-existent `activeDatasetId/setActiveDatasetId`).
  - DTOs: DatasetDto/DatasetDetailDto +sourceFormat/sheetName/headerRow/version/parentDatasetId/excludedColumns/importOptions; DatasetColumnDto +excluded; new ReprofileRequestDto, DatasetExclusionRequestDto.
  - Verification: backend `./mvnw test` → 26 tests / 0 failures (WorkflowServiceTest updated for new mocks + dataset columns); frontend `npm run build` → success.

## Open items / blockers

(none blocking so far)

- Session 3 (2026-10-19): Phases B–K implemented end to end; all verifications green.
  - Ingest (B): POI-backed `ExcelImportService` (multi-sheet, header-row detection, canonical CSV conversion), `ImportPreviewService` + preview/confirm endpoints, `ImportWizard.jsx`; dataset stores original file (`sourceStoredFilename`) and canonical analysis CSV (`storedFilename`), plus `sourceFormat`/`sheetName`; CSV path unchanged. `ExcelImportServiceTest` added.
  - Landing/nav (C): new public `LandingPage.jsx` at `/`; authenticated layout now uses `AppFrame` + `Sidebar.jsx` (collapsible, grouped nav, dataset-aware, localStorage state); `Navbar.jsx`/`LandingPage.jsx` hamburgers are React state; Bootstrap JS bundle import removed (CSS/icons only); responsive width fixes (`min-width: 0`, overflow guards) in `index.css`.
  - Prerequisites (D): `PrerequisiteAlert` on Drift/AutoML/Experiments/Ask; clear "upload two datasets" drift message; backend workflow status remains source of truth.
  - EDA/ML depth (E, F): `EdaEngine` + `EdaService`/`EdaController` endpoints for histograms, scatter (≤2000 pts), missingness, categorical summaries, time series (mean/median/min/max); `EdaGallery.jsx`; AutoML evaluation now includes confusion matrix, ROC/PR, per-class metrics, cross-validation, calibration reliability bins + Brier, residual summary/points; `ModelEvaluationPanel` renders calibration and residuals; experiment comparison table.
  - Ask 2.0 (G): typed intent parsing over analytic columns, normalized token matching (underscore/hyphen ↔ space), data-backed answers with chart + citations; causal questions explicitly unsupported (no causal claim); expanded tests. Profiler now recognizes space-separated timestamps (`yyyy-MM-dd HH:mm[:ss]`), so `"trend of exam_score over time"` works.
  - Experiments/evidence (H): AutoML assigns incrementing version numbers per experiment and persists dataset version/seed/engine version; `GET /api/experiments/{id}/comparison`; `EvidenceGraphService` emits dataset→question→hypothesis→test→model→finding graph plus transparent reliability scorecard (20/20/15/20/15/10 weights); `EvidenceGraphPanel.jsx` + scorecard on Findings page.
  - Reports/exports (I): 13-section honest Markdown (coverage row, calibration/residual diagnostics when persisted, real drift/fairness or explicit "not run"); `PdfExportService` rewritten with embedded DejaVu regular/bold/oblique, real tables, markdown/LaTeX cleanup; `ReportExportService` CSV (table sections) and XLSX (one sheet per table); endpoints `GET /reports/{id}/export/{csv,excel}` with ownership checks.
  - Performance (J): `BoundedCache` LRU for 7 EDA results keyed by `datasetId@version:operation[:qualifier]` (capacity 64); `@EntityGraph` on dataset/finding/experiment/report/question repositories to remove N+1s; dashboard counts DB-derived.
  - Tests (K): 40 tests (added `AnalyticsFeatureTest`, `ReportExportServiceTest`, `BoundedCacheTest`, `ExcelImportServiceTest`, profiler timestamp test, report diagnostics test; `ReportAndPdfTest` assertions updated to truthful strings).
  - Runtime smoke (port 8099, 120-row CSV): register → upload → types (`id` IDENTIFIER, `when` DATE_TIME) → 3 Ask intents with chart+citations → associations → classification training (calibration/Brier present) → versions 1,2 + comparison (3 models, seed 42, dataset v1) → evidence (19 nodes/18 edges, score 90 STRONG) → report → PDF 80,223 bytes `%PDF`, CSV 6,303 bytes, XLSX 10,673 bytes `PK`.
  - Known limitations (documented, not silently ignored): AutoML in-memory `MODEL_CACHE` is still unbounded/process-local; report/PDF generation is synchronous; no Flyway migrations (dev H2 create-drop, prod `ddl-auto=update`); no automated browser E2E (API-level smoke only); frontend has no unit-test framework; multi-user concurrency/load limits unmeasured.


## Final integration pass (2026-10-20)

### Verified state
- Backend tests (from `backend/`): `./mvnw test` → **40 tests, 0 failures, 0 errors**, BUILD SUCCESS.
- Frontend production build (from `frontend/`): `npm run build` → **129 modules transformed, built in ~3.8s**, bundle `dist/assets/index-z4QLLWpr.js` **607.23 kB** (gzip 183.52 kB).
- EDA endpoints present and consumed: histograms, scatter (≤2000 pts with downsample), missingness, categorical summaries, time series, correlations, boxplots/grouped summary. Frontend `EdaGallery` renders all with loading/empty/error states; `VisualizationsPage` uses shared `columns` utils with identifier exclusion.
- Ask Dataset: typed intent parsing over analytic columns, data-backed answers with chart + citations; causal unsupported explicitly; UI in `AskDatasetPage` renders charts and citations from API response.
- Prerequisite-aware gating: `WorkflowService` enforces stages from persisted evidence; frontend `PrerequisiteAlert`, `WorkflowNavigator`, and `WorkflowContext` apply gating; drift message clarified for two-dataset requirement.
- Calibration/residuals, experiment comparison, reliability scorecard, evidence graph: `ModelEvaluationPanel` shows calibration/reliability and residuals; `GET /api/experiments/{id}/comparison` with versioning (incrementing per experiment, dataset version/seed/engine recorded); `EvidenceGraphPanel` + `ReliabilityScorecard` on Findings; `EvidenceGraphService` emits dataset→question→hypothesis→test→model→finding graph with weighted scorecard.
- Exports: reports generate Markdown/PDF/CSV/XLSX; `ReportExportService` produces CSV/XLSX with ownership checks; `PdfExportService` embeds DejaVu fonts with real tables.
- AutoML cache: bounded LRU-style cache via `MODEL_CACHE` with `MODEL_CACHE_MAX=32` and size-based eviction in `AutoMlService`; `BoundedCache` used for EDA results (capacity 64) keyed by dataset@version:operation[:qualifier].
- Migrations/risks documented: no Flyway (dev H2 create-drop, prod `ddl-auto=update`); report/PDF synchronous; no browser E2E automation; frontend has no unit test framework.

### Notes on remaining risks (truthful)
- AutoML `MODEL_CACHE` is static in-memory/process-local and bounded to 32 entries (evicts oldest on overflow) — safe additive bound but not shared across instances.
- Report/PDF generation synchronous on request thread (documented).
- Schema changes additive only; no destructive migrations. No Flyway configured.
- Upload limits, auth/authorization, dataset ownership, and report access enforced by services/controllers; expensive queries bounded by caching where added.
- Browser E2E not verified by automation in this environment (API smoke previously recorded in docs; no browser automation run now).

### Files changed in working tree (snapshot)
69 modified files (see `git diff --name-only`), 110 total changes in status including untracked. No commits made, no files deleted/cleaned.

---

## Verification session (2026-10-20, session 4 — end-to-end completion)

### Commands actually run (with results)

| Command | Location | Exit | Result |
|---|---|---|---|
| `./mvnw test` | `backend/` | 0 | **41 tests, 0 failures, 0 errors** (16 classes), BUILD SUCCESS |
| `npm run build` | `frontend/` | 0 | 129 modules, bundle `dist/assets/index-*.js` ≈607.8 kB (gzip 183.6 kB) |
| `node cdp_crawl.cjs` (headless Chrome via CDP) | temp | 0 | 12 pages, 0 console errors, 0 page exceptions, 0 failed API requests |
| `python3 final_e2e.py` (live backend :8099) | temp | 0 | **27/27 E2E assertions passed** |

Backend runtime used for E2E: JDK 21, `dev` profile, file-backed H2
(`jdbc:h2:file:...;MODE=MySQL`, `ddl-auto=update`) so persistence across restart could be
tested; uploads in a temp dir. No production DB touched.

### End-to-end scenarios actually exercised (live, not simulated)

- **Ingest**: CSV upload (30 rows, `student_id` → IDENTIFIER, `passed` → CATEGORICAL) and a
  second CSV for drift; Excel `.xlsx` generated via POI → `POST /datasets/preview` returned
  sheets + auto-detected header + typed columns + identifier warnings, then
  `POST /datasets/upload` with `sheetName`/`headerRow` imported 40 rows as XLSX.
- **Identifier policy**: `order_no` with only 8 distinct numeric values (the previously
  defeatable case) is typed IDENTIFIER and excluded from correlations (`variables: amount,
  qty, value` only) and from AutoML features.
- **Profiling/quality**: `/quality` returns a real score (100.0) + issues.
- **EDA**: correlations, histograms, scatter (5000-row file → 2000 points, `downsampled:true,
  totalRows:5000`), missingness, categorical, boxplots (grouped), time series — all 200 with
  real data.
- **Ask Dataset**: "average exam_score by passed" → `GROUP_COMPARISON` with bar chart +
  citation; "does study_hours cause exam_score" → explicit causal refusal; ambiguous/"meaning
  of life" → truthful clarification (no fabricated answer).
- **AutoML**: 3 models trained; two trainings → experiment with 2 versions;
  `/experiments/{id}/comparison` returns aligned train/val/test metrics + `randomSeed=42` +
  `datasetVersion`; `/automl/latest` returns `explanationStatus=AVAILABLE` with 6 persisted
  importances.
- **Persistence across restart**: with file-backed H2, after killing and restarting the
  backend, `/automl/latest` still returned `AVAILABLE` importances read from the DB, and
  `/automl/whatif` returned a real prediction (`Pass`, confidence 0.9999) — explanations and
  inference are reproducible, not cache-only.
- **Research → stats → findings → evidence**: research question → hypothesis → execute
  (Pearson p≈0, r²=0.967) → 1 finding; `/evidence` returned 38 nodes; `/reliability` score 72
  GOOD with per-check detail; `/workflow-status` derived stages from persisted evidence.
- **Reports/exports**: report markdown 8,720 chars; PDF 78,067 B `%PDF`; CSV 3,090 B; XLSX
  10,096 B `PK`; new Markdown export 8,724 B `# Comprehensive…`.
- **Drift**: `/drift/compare` returned 200 with honest `STABLE` (0 shared features reported as
  0/0, not fabricated).
- **Error taxonomy**: missing request param → 400; unknown dataset → 404; unknown target → 400;
  bad extension upload → 400; malformed JSON → 400.
- **Authorization/ownership**: cross-user access to datasets, reports, report exports
  (incl. new markdown), experiments, comparison, evidence, EDA → **403**; unauthenticated →
  **401**.

### Defects found and fixed this session

1. **Raw JSON blobs embedded in the report ML metrics table** (`ReportGeneratorService`):
   the "Validation Metrics"/"Test Metrics" cells contained literal
   `{"accuracy":1.0,...}` JSON. Fixed with a `formatMetrics(...)` helper that renders
   `acc=…, prec=…, rec=…, f1=…` (and any extra numeric metric as `key=value`). Regression
   test `ReportAndPdfTest.testReportMetricsAreHumanReadable` added. The same fix propagates to
   the CSV/XLSX exports, which parse the report markdown.
2. **No downloadable Markdown export** despite Markdown being a first-class report format.
   Added additive `GET /api/reports/{id}/export/markdown` (ownership-enforcing) +
   `downloadReportMarkdown` service + a Markdown button in `ReportsPage`. Verified 200 on
   owner and 403 on non-owner.
3. **Stale reference schema** (`database/schema.sql`) missing the additive `datasets`,
   `experiment_versions`, and `models` columns. Brought up to date (non-destructive) and added
   a documented additive `ALTER TABLE` migration path + durable-storage guidance in
   `docs/DEPLOYMENT.md`.

Non-defects confirmed (earlier parser confusion resolved): report-creation JSON was valid
(stress-tested 10×, 0 parse failures); the "…latest" endpoint field names are nested
(`featureName`, `trainMetrics`); `/workflow/status` field is `currentRecommendedStage`;
`/ask` param is `query` and the answer field is `answerMarkdown`; `/drift/compare` params are
`referenceId`/`targetId`.

### Per-feature status

| Feature | Status | Evidence |
|---|---|---|
| CSV ingest + profiling | PASS | live upload, types via API |
| Excel `.xlsx` ingest (preview/sheet/header) | PASS | POI workbook preview + upload |
| Identifier exclusion policy | PASS | `order_no`/`student_id` excluded from EDA + ML |
| Data quality audit | PASS | `/quality` real score |
| EDA (hist/scatter/missing/categorical/corr/box/ts) | PASS | all 200, scatter cap verified |
| Ask Dataset (grouped/summary/clarify/refuse) | PASS | 27-check E2E |
| AutoML training + metrics + CV/calibration | PASS | 3 models, comparison table |
| Persisted explanations after restart | PASS | file-H2 restart → AVAILABLE |
| Experiment versions + comparison | PASS | 2 versions, seed=42, datasetVersion |
| Evidence graph + reliability scorecard | PASS | 38 nodes, score 72 GOOD |
| Prerequisite gating (backend + UI) | PASS | workflow-status; UI navigator |
| Reports: Markdown/PDF/CSV/XLSX (now +MD) | PASS | magic bytes + sizes |
| Report export ownership | PASS | 403 for non-owner incl. markdown |
| Drift compare | PASS | 200 STABLE (honest 0/0) |
| Auth/authorization/ownership | PASS | 401/403 matrix |
| Error taxonomy (400/404) | PASS | 5 cases |
| UI integration (routes/states/console) | PASS | 12 pages, 0 errors |
| Bounded caches / process-local note | PASS (scoped) | `BoundedCache`, `MODEL_CACHE_MAX=32` |
| DB migration + durable storage | PASS (documented) | schema.sql synced; DEPLOYMENT.md |

### Remaining limitations (explicit, not silently ignored)

- **Process-local, non-durable state**: AutoML `MODEL_CACHE` and EDA `BoundedCache` are
  in-memory and bounded but not shared across instances. No multi-instance persistence is
  claimed for them.
- **No Flyway/Liquibase**: dev is H2 `create-drop` (ephemeral); prod is `ddl-auto=update`.
  Schema evolution is additive-only; the additive `ALTER TABLE` path is documented.
- **File storage** under `app.upload.dir` is local-filesystem and not multi-instance durable;
  a missing backing file yields an explicit error, never fabricated data.
- **Report/PDF generation is synchronous** on the request thread.
- **No automated browser E2E in CI**: browser checks here were run manually via headless
  Chrome + CDP (results recorded above); the repo has no committed browser-test harness.
- **Frontend has no unit-test framework** (`package.json` has no `test` script).
- **Concurrency/load limits unmeasured**; MySQL prod path not exercised on this machine
  (root creds rejected at audit time — H2 file mode used instead).
- The stray untracked file `AutoScientist AI` and all pre-existing modifications were left
  untouched. No commits were made.

---

## Staging release readiness session (2026-10-21, session 5)

Scope: same-day staging release — database reliability, security/secrets, regression,
staging configuration. Work in the existing tree; no destructive git or DB operations.

### Phase 1 — baseline (verified)

| Command | Location | Result |
|---|---|---|
| `./mvnw test` | `backend/` | **43 tests, 0 failures** (pre-change baseline) |
| `npm run build` | `frontend/` | success, bundle ≈609 kB (gzip ≈184 kB) |
| `git status --short` | root | 125 entries (69 modified, rest untracked) — all preserved |

### Phase 2 — database reliability (three real-MySQL scenarios)

Isolated throwaway `mysqld` instance on **port 3307** (separate datadir, test-only
credentials). The real `autoscientist_db` and all backups were never touched.

Ground truth: the real backup (`backups/autoscientist_db_20261020_174945.sql`) is a
**7-table pre-1.0 schema** (`users.password`, `datasets.file_path`, legacy `evidence` /
`statistical_test_results`, no `created_at`), NOT the 17-table reference schema.

Scenarios run against real MySQL 9.7 with `ddl-auto=validate` (fresh/restart) and `update`
(legacy):

| Scenario | Path exercised | Result |
|---|---|---|
| A. Fresh install | empty DB → Flyway **V1+V2** → Hibernate `validate` → register/login/upload/quality/EDA | **PASS** |
| B. Legacy pre-Flyway upgrade | real backup → baseline@1 + **V2** + Hibernate `update` → register/upload/ownership | **PASS** |
| C. Restart idempotency | migrated legacy DB restarted twice with `validate` | **PASS** (0 validation errors, data intact) |

### Defects found and fixed this session (all were production/fresh-install blockers)

1. **V1 failed on every real MySQL fresh install.** `V1__baseline.sql` ended with
   `CREATE INDEX IF NOT EXISTS …` — valid on H2, **invalid on MySQL** (error 1064). The
   existing `MysqlBaselineMigrationTest` runs on H2 only and therefore gave false
   confidence. Removed the statement (the index is created by V2's guarded PREPARE) and
   added **`MigrationSqlCompatibilityTest`**, which rejects H2-only SQL in `db/migration`.
2. **V2 failed on legacy databases that lack `data_quality_reports`.** MySQL resolves
   table references at PREPARE time, so the guard subquery on a missing table aborted the
   migration. Rewrote the section to defer all references into a conditional PREPARE.
3. **Legacy upgrade left a half-migrated, partly broken schema.** Hibernate
   `ddl-auto=update` cannot add a `datetime NOT NULL` column to a table with rows under
   MySQL strict mode (it tries `0000-00-00`, fails, **logs and continues**), so
   `users.created_at` / `datasets.created_at` were never created → registration and upload
   returned HTTP 500 on the migrated DB. V2 now adds the column NULL, backfills it, then
   tightens to NOT NULL. It also:
   - backfills `users.password_hash` from the legacy BCrypt `password` column (all 46
     accounts preserved) and relaxes the legacy `password` NOT NULL so new inserts work;
   - backfills `datasets.name`/`version` and `hypotheses` fields from legacy equivalents;
   - relaxes legacy-only NOT NULL columns the current entity no longer writes
     (`datasets.file_path`/`file_type`/`uploaded_at`) while keeping column + data.
   Every block is guarded on column existence **and table existence**, so it is safe when
   a legacy DB predates a table entirely. All changes remain additive/non-destructive.
4. **`JwtUtils` crashed on whitespace in `JWT_SECRET`** (`openssl rand -base64 64` wraps at
   64 chars → interior newline → `Illegal base64 character` → HTTP 500 on every auth call).
   Now strips all whitespace before decoding (`.replaceAll("\\s", "")`).
5. **The `mysql` profile silently fell back to `root/root` and the committed dev JWT
   secret.** Made `SPRING_DATASOURCE_URL/_USERNAME/_PASSWORD` and `JWT_SECRET` **required
   with no default** on the `mysql` profile; the app now fails fast at startup when they are
   missing. The dev default remains only for the `dev` profile.

### New/updated tests

- `MigrationSqlCompatibilityTest` (H2-only SQL guard) — runs in normal `./mvnw test`.
- `RealMysqlMigrationTest` — real-MySQL integration test for both fresh and legacy paths;
  gated on env `AS_TEST_MYSQL_URL`/`_USER`/`_PASSWORD`, **skipped otherwise** so CI needs no
  database. Both scenarios verified green on the isolated MySQL.
- Normal suite after changes: **46 tests, 0 failures, 0 errors, 2 skipped** (the MySQL IT).

### Docs/config

- `docs/DEPLOYMENT.md` revised: Flyway is real (§3.1), no literal secret, base64 generation
  command, required-env list, backup/recovery section, corrected `FILE_UPLOAD_DIR` name.
- `.gitignore`: now also ignores `uploads/`, `backups/` (data/secrets stay out of git).

### Phase 3 — security & correctness (verified against real MySQL)

| Check | Result |
|---|---|
| No auth on protected route | 401 |
| Cross-user dataset/quality/EDA/ask | 403 |
| Unknown dataset id | 404, generic message (no SQL/stack leak) |
| Bad file extension | 400, clean message |
| Uploaded filenames | stored as UUIDs (no path traversal) |
| Generic 500 handler | returns fixed string; only logged server-side |
| CORS | configurable via `APP_CORS_ALLOWED_ORIGINS`; dev defaults only |
| App log during full E2E | no unexpected ERROR/exception lines |

### Phase 4 — regression (verified)

- Backend: **46 tests, 0 failures, 0 errors** (`MigrationSqlCompatibilityTest` added).
- Frontend: **12/12 vitest tests pass**; production build succeeds (609.14 kB, 183.91 kB gzip).
- Scientific validation: CSV (height/weight/age) correlation matrix from the live API
  `[[1, 0.9795, 0.9337], [0.9795, 1, 0.9222], ...]` **matches independent Python exactly**.
- Full workflow E2E: register → upload → quality → reprofile → EDA (corr/hist/box/grouped/
  categorical/missingness) → associations → research question → hypothesis → execute
  (PEARSON stat 0.9795, r²=0.9594, p=0.0000, N=10) → findings → report → exports
  (PDF 3 pages / XLSX valid OOXML / CSV / Markdown) → AutoML (REGRESSION and AUTO) →
  what-if → drift. All succeed; cross-user report access returns 403.

### Phase 5 — packaging & handover (verified)

- Final `./mvnw clean package` jar rebuilt from all SQL/code changes; both migrations
  confirmed packaged inside the jar.
- Packaged artifact smoke test on a fresh empty MySQL database: Flyway applies **V1+V2**,
  Hibernate `validate` passes with 0 schema errors, register/login/upload/quality/EDA all
  succeed; `flyway_schema_history` shows exactly BASELINE@1 + SQL@2.
- No `/actuator/health` endpoint in this build → §8 of `DEPLOYMENT.md` documents a
  curl-based post-deploy smoke test instead.

## Staging-readiness verdict

**Level: STAGING-CANDIDATE (local-verified).** All functional, database, security and
packaging checks pass on this machine against a real MySQL 9.7 instance. Not yet proven in
a real staging environment (none was provided). Remaining gates before production:

1. Run the same artifact once against a real staging MySQL/HTTPS/domain (config-only change).
2. Set `APP_CORS_ALLOWED_ORIGINS` to the real frontend origin (§7).
3. Rotate in real `JWT_SECRET` / DB credentials; never reuse the dev secret.
4. Legacy-only tables `evidence`, `statistical_test_results` remain unmapped/orphaned;
   their data is preserved by V2 but they are not surfaced in the UI — this remains a known
   limitation, not a blocker for a fresh deployment (see audit notes).

---

## Analytical Regression Testing & Feature Completion Pass (2026-10-21, Session 4)

### 1. Backend Regression Suite Expansion
Added 5 dedicated regression test suites covering newly introduced analytical calculations, statistics, AutoML diagnostics, what-if counterfactuals, fairness, drift, and dashboard activity histograms:
1. **`EdaRegressionTest`** (5 tests):
   - Correlation matrix symmetry, Pearson $r$, analytical $p$-value matrix, complete-case sample sizes.
   - Empirical five-number summary box plots with IQR and outlier detection ($1.5 \times \text{IQR}$ rule).
   - Bivariate scatter plot with OLS linear regression fit (slope, intercept, $R^2$, $p$-value) and deterministic downsampling ($\le 2,000$ points).
   - Cohort/group distribution comparisons on aligned shared bin edges across categorical splits.
   - Row-level missingness presence matrix with bounded downsampling ($\le 200$ rows) and column index mapping.
2. **`AutoMlAndWhatIfRegressionTest`** (3 tests):
   - Classification diagnostics: holdout confusion matrix (`truePositives`, `falsePositives`, `trueNegatives`, `falseNegatives`, `rows`), per-class precision/recall/F1/support, ROC curve with trapezoidal AUC, Precision-Recall curve with average precision, probability calibration reliability diagram with Brier score, and seeded k-fold cross-validation.
   - Regression diagnostics: residual summary (`meanError` bias, `mae`, `maxAbsError`, `n`), residual scatter points (predicted minus actual against actual), prediction points, and seeded k-fold cross-validation.
   - `WhatIfEngine`: schema extraction (feature types, numeric ranges, unique categorical values, median/mode defaults), standard scaling, one-hot encoding, counterfactual prediction delta, feature contribution attributions, and out-of-range warnings.
3. **`FairnessAndDriftRegressionTest`** (2 tests):
   - EEOC 80% Four-Fifths rule disparate impact evaluation (`FLAGGED` status when demographic parity ratio $< 0.80$), group sample sizes, selection rates, and group positive rates.
   - `DataDriftEngine`: Kolmogorov-Smirnov two-sample test ($D$ and $p$-value), Welch's two-sample $t$-test, Population Stability Index (PSI) calculation on reference-aligned bin edges, and reference vs target proportion curves.
4. **`StatisticalAssumptionsAndCiTest`** (5 tests):
   - Fisher $z$-transform 95% confidence intervals for Pearson correlation ($r \in [\text{ciLower}, \text{ciUpper}]$).
   - Sample size bounds and graceful handling ($N < 4$ early return with informative diagnostic string).
   - Welch's $t$-test mean difference 95% CI (Satterthwaite-Welch df) and Cohen's $d$ confidence interval.
   - Informative `ciUnavailableReason` when confidence intervals are not defined for omnibus tests (ANOVA, Chi-Square).
   - `AssociationEngine` confidence interval field propagation into persisted DTOs.
5. **`DashboardRegressionTest`** (1 test):
   - True database-derived aggregate counts (datasets, observations, associations, findings, models, experiments, reports, quality score) without fabrication.
   - Daily research-activity timeline histogram generation (consecutive date buckets, 60-day capping, zero preservation, truncation flag).

- **Backend Test Result**: `./mvnw test` → **62 tests run, 0 failures, 0 errors, 2 skipped** (100% passing across 20 test classes).

### 2. Frontend Unit Test Suite Expansion
Added 10 new Vitest test files in `frontend/src/test/` to verify chart rendering, API clients, and page-level interactions:
1. `BoxPlotChart.test.jsx` (2 tests): Five-number summary rendering, IQR outliers, custom labels, empty/zero data handling.
2. `ModelEvaluationPanel.test.jsx` (5 tests): Empty/unavailable states, classification diagnostics (confusion matrix, per-class table, ROC/PR curves, probability calibration reliability curve and Brier score, cross-validation), regression diagnostics (residual summary stat tiles, residual scatter plot, cross-validation), no-continuous-scores fallback.
3. `EvidenceGraphPanel.test.jsx` (5 tests): Full 13-type node provenance network rendering, node interaction/selection and edge highlighting, empty fallback, weighted reliability scorecard (20/20/15/20/15/10) with grade and limitations.
4. `EdaGallery.test.jsx` (5 tests): Numeric histograms, scatter and OLS fit metrics, categorical distributions, missingness audit, row-level missingness presence matrix, empty/error state handling.
5. `analysisService.test.js` (8 tests): All 28 API client endpoints, param serialization, blob downloads.
6. `datasetService.test.js` (4 tests): Uploads, preview, reprofile, exclusions, CRUD.
7. `researchService.test.js` (3 tests): Research questions, hypothesis execution, findings, experiment comparisons.
8. `AssociationsPage.test.jsx` (5 tests): Summary counts, forest-plot CI explorer, significance toggle, recomputation, error handling.
9. `DashboardPage.test.jsx` (4 tests): Live metrics, 5-series stacked activity histogram, 60-day truncation badge, empty state fallback, provenance audit trail.
10. `DriftPage.test.jsx` (3 tests): Cohort selection, drift analysis execution, overall PSI, per-feature PSI bar chart, reference vs target distribution curve, error handling.
11. Existing suites: `DatasetsPage.test.jsx` (5 tests), `FindingsPage.test.jsx` (7 tests).

- **Frontend Test Result**: `npm test -- --run` → **56 tests passed across 12 test files, 0 failures** (1.39s execution time).
- **Production Build Result**: `npm run build` → **130 modules transformed, build succeeded in 4.01s** with zero errors.

### 3. Feature Vision Audit & Verification
- **EDA**: Histograms, box plots, scatter plots, linear regression fits, cohort distribution overlays, correlation matrices with p-values, missingness percentages, and row-level missingness presence matrix are completely implemented and backed by real backend computations.
- **AutoML**: Algorithmic benchmarking (Ridge, Decision Tree, Random Forest), ranking, holdout confusion matrix, ROC/PR curves, probability calibration curves, Brier score, and seeded k-fold cross-validation.
- **XAI**: Permutation feature importance, local explanations, and `WhatIfEngine` counterfactual simulation with schema guidance and feature contributions.
- **Fairness & Drift**: EEOC 80% Four-Fifths rule disparate impact evaluation, Kolmogorov-Smirnov $D$ and $p$-value, Welch $t$-test, PSI calculation, and cohort distribution comparisons.
- **Statistics**: Parametric and non-parametric bivariate tests, effect sizes, Fisher $z$-transform 95% CIs, Welch $t$-test mean difference CIs, and sample size safeguards ($N < 4$, $N < 5$).
- **Research Workflows**: Questions, hypotheses, findings, experiment versioning, 13-node interactive SVG evidence graph, and 6-factor weighted reliability scorecard (0-100 score + grade).
- **Ask Dataset**: Natural language data query engine over active analytic columns with statistical grounding, citation of specific columns/rows, and chart recommendations without fabricating data.
- **Dashboard**: Pure database-derived metrics, daily 60-day activity histogram, and provenance audit log.
- **Reports**: Multi-format exports (truthful Markdown, embedded-font DejaVu PDF, Apache POI XLSX, and CSV) with data quality audit, methodology, findings, and model benchmarks.

## Branding De-duplication and Dataset-First Access Control (2026-10-22)

### 1. Remove Duplicate Branding
- **Navbar & Public Landing Page**:
  - Removed the redundant inner `<nav>` and its duplicate brand bar and hamburger menu from `frontend/src/pages/LandingPage.jsx`.
  - The application now uses the single primary website header in `frontend/src/components/Navbar.jsx` across all views (public landing page, auth pages, and authenticated app workbench).
  - Resolved stacked duplicate footers on the landing page: `AppFrame` now conditionally renders its generic footer only when `location.pathname !== '/'`, allowing `LandingPage.jsx` to render its themed footer cleanly.
  - Verified responsive navigation across desktop and mobile screen sizes.

### 2. Dataset-First Access Control Implementation
- **Backend Dataset Ownership & Security**:
  - Hardened `DataDriftService.java` to validate that both `referenceDatasetId` and `targetDatasetId` are non-null before running comparisons, throwing `BadRequestException` (HTTP 400).
  - Added `backend/src/test/java/com/autoscientist/DatasetOwnershipSecurityTest.java` (5 tests):
    1. Authorized dataset access returns HTTP 200.
    2. Non-owner dataset access throws `UnauthorizedException` (HTTP 403).
    3. Nonexistent dataset access throws `ResourceNotFoundException` (HTTP 404).
    4. Data drift comparison requires non-null reference and target IDs (`BadRequestException` HTTP 400).
    5. Data drift comparison enforces ownership checks on both datasets.
- **Frontend WorkflowContext & Gating State**:
  - Updated `frontend/src/context/WorkflowContext.jsx` to manage `userDatasets`, `hasDatasets`, `datasetsLoading`, and `refreshUserDatasets()`.
  - Added robust null/context fallbacks in `useWorkflow()` to prevent test crashes in isolated component tests.
  - Automatic synchronization: when a user logs in, user datasets are retrieved; when a dataset is imported or deleted, access is immediately re-evaluated without page reloads.
- **RequireDatasetRoute Protection**:
  - Created `frontend/src/components/RequireDatasetRoute.jsx` to protect all analysis-only routes (`/associations`, `/research`, `/hypotheses`, `/statistics`, `/automl`, `/ml`, `/xai`, `/visualizations`, `/findings`, `/reports`, `/drift`, `/ask`, `/experiments`, and `/datasets/:id/*`).
  - Loading states prevent flashes of locked or unlocked screens (`datasetsLoading || authLoading`).
  - Displays an informative locked card: "Analysis Workspace Locked" with prompt "Upload a dataset to unlock scientific analysis.", plus working buttons linking to `/datasets` ("Upload Dataset") and `/dashboard` ("Go to Dashboard").
- **Sidebar & Navbar Gating**:
  - Updated `frontend/src/components/Sidebar.jsx` to disable analysis links when `hasDatasets === false`, displaying `is-disabled` styling, `aria-disabled="true"`, lock icons, and prompt tooltip.
  - Displays a dedicated sidebar callout card with a working "Upload Dataset" button when `!hasDatasets` and expanded.
  - Updated `frontend/src/components/Navbar.jsx` mobile drawer to disable gated analysis links and display a prominent "Upload Dataset to Unlock" call to action.
- **Dashboard & Datasets Flow**:
  - Updated `frontend/src/pages/DashboardPage.jsx` with an informative empty state card and "Upload First Dataset" button when `!hasDatasets`.
  - Updated `frontend/src/pages/DatasetsPage.jsx` to invoke `refreshUserDatasets()` on import and deletion, immediately updating access state.

### 3. Verification & Test Evidence
- **Backend Tests**: `./mvnw test` → **67 tests run, 0 failures, 0 errors, 2 skipped (100% passing)**.
- **Frontend Unit Tests**: `npm test -- --run` → **64 tests passed across 14 test files, 0 failures**.
- **Frontend Production Build**: `npm run build` → **131 modules transformed, built in 3.81s** with zero errors.

## Critical Bug Fixes: Visualizations Blank Screen Repair & ML Benchmark Graph (2026-10-22)

### 1. Visualizations Page Blank / Black Screen Resolution
- **Root Cause Diagnosis**:
  1. **Missing Chart.js v4 Controllers**: `chartSetup.js`, `EdaGallery.jsx`, and `VisualizationsPage.jsx` registered elements and scales (`PointElement`, `BarElement`, `LinearScale`) but omitted chart controllers (`BarController`, `LineController`, `ScatterController`). In Chart.js v4, rendering a scatter/bar/line canvas without its controller throws `"scatter" is not a registered controller`. In the browser, this unhandled exception crashed the React rendering tree into a black/blank page.
  2. **Dangerous Plugin Mutation in `BoxPlotChart.jsx`**: Lines 100–108 directly mutated `chart.plugins` on an object where `plugins` was undefined or sealed in Chart.js v4, throwing `TypeError`.
  3. **React Rules of Hooks Violation**: In `VisualizationsPage.jsx`, hooks (`useMemo` for `boxPlotChartData`, `correlationSignificance`, and `significantPairs`) were placed below early conditional returns (`if (loading)` and `if (!dataset && !activeId)`). When loading transitioned from `true` to `false`, React detected more hooks than during the previous render, triggering an unhandled React error.
  4. **Lack of React Error Boundary**: Any charting canvas error or missing data crashed the entire page with no recovery action.
  5. **Stale Asynchronous Overwrites**: Rapid switching of datasets or variables caused in-flight network responses from earlier requests to overwrite subsequent selections.
  6. **Missing No-Dataset Empty State**: Navigating directly to `/visualizations` without an active dataset produced broken half-rendered cards.

- **Implemented Fixes**:
  - `frontend/src/utils/chartSetup.js`: Registered all controllers (`BarController`, `LineController`, `ScatterController`, `DoughnutController`, `PieController`) alongside scales and plugins.
  - `frontend/src/components/BoxPlotChart.jsx`: Explicitly registered `BarController`, removed the dangerous `useEffect` that mutated `chart.plugins`, and preserved clean inline `plugins={[boxPlotPlugin]}`.
  - `frontend/src/components/EdaGallery.jsx`: Explicitly registered `BarController`, `LineController`, and `ScatterController`. Added active cancellation flags (`let isCurrent = true; return () => { isCurrent = false; }`) across all `useEffect` hooks. Added finite number guards (`Number.isFinite`) on scatter points and linear fit coordinates.
  - `frontend/src/pages/VisualizationsPage.jsx`:
    - Re-ordered all hooks (`useState`, `useCallback`, `useMemo`, `useEffect`) unconditionally above any early return statements, adhering strictly to the Rules of Hooks.
    - Added `VisualizationsErrorBoundary` wrapping the page content to catch any unexpected canvas or rendering exceptions, displaying an informative error card with a "Reload Visualizations" CTA.
    - Added request sequence counter (`requestSeq = useRef(0)`) to discard stale out-of-order API responses.
    - Added null-safe checks on `corrMatrix.matrix[rIdx]` and `bp.min`.
    - Added a dedicated `visualizations-no-dataset` empty state with clear CTAs linking to dataset upload and dashboard.
  - `frontend/src/test/setup.js`: Configured a robust `LocalStorageMock` for `globalThis.localStorage` and `window.localStorage` to prevent Node 25 JSDOM environment conflicts.
  - `frontend/src/test/VisualizationsPage.test.jsx`: Added 5 comprehensive automated tests covering initial loading, empty dataset states, API error handling/retry, grouped variable switching, and error boundary resilience.

### 2. ML Benchmark Feature Selection & Response Graph Architecture
- **Root Cause & Functional Gap**:
  - The AutoML Benchmarking section lacked a mechanism to evaluate and inspect how the benchmarked model responds to changes in individual features.
  - The UI only presented aggregate permutation feature importance without a feature selector or model prediction response curve.
  - Users selecting a feature could not see the model's learned response function.

- **Implemented Architecture & Full Data Path**:
  1. **Backend DTOs**:
     - `com.autoscientist.dto.ml.FeatureEffectPointDto`: Grid coordinate `{x, prediction, probability}`.
     - `com.autoscientist.dto.ml.FeatureEffectCategoryDto`: Categorical coordinate `{category, prediction, probability}`.
     - `com.autoscientist.dto.ml.FeatureEffectDto`: Encapsulates `featureName`, `kind` (`NUMERIC` | `CATEGORICAL`), `problemType`, `targetColumn`, `referenceValue`, `referencePrediction`, `referenceClass`, `points`, `categories`, `supported`, `unsupportedReason`, and `scientificNote`.
  2. **Backend Service (`AutoMlService.java`)**:
     - Implemented `computeFeatureEffect(Long datasetId, String featureName, User user)`:
       - Validates dataset ownership and retrieves the warm model from `MODEL_CACHE` (or reloads if evicted).
       - Evaluates against the model's recorded `WhatIfEngine` preprocessing schema, ensuring exact feature name alignment without data leakage.
       - Identifies unsupported features (e.g. identifiers or the target variable) and returns clear reasons.
       - For NUMERIC features: Evaluates a 15-point uniform grid across `[min, max]`, keeping all other features at training baseline (median for numeric, mode for categorical), running `model.predictSingle` (and `predictProba` for classification).
       - For CATEGORICAL features: Evaluates every observed training category.
       - Includes scientific footnote clarifying marginal conditional prediction curve (not causal intervention).
  3. **Backend Controller (`AutoMlController.java`)**:
     - Exposed `GET /api/datasets/{id}/automl/feature-effect?feature={featureName}`.
  4. **Frontend API Client (`analysisService.js`)**:
     - Added `getFeatureEffect(datasetId, feature)`.
  5. **Frontend UI & Interactive Graph (`AutoMlPage.jsx`)**:
     - Registered `BarController` and `LineController`.
     - Added `#benchmarkFeatureSelect` dropdown allowing selection across all candidate features.
     - Made rows in the Permutation Feature Importance table clickable, automatically selecting the clicked feature and highlighting the row.
     - Added dedicated card: "ML Benchmark: Feature Response & Effect Curve" rendering:
       - Smooth `Line` chart for numeric features with dual-axis class probability curve for classification models.
       - Clean `Bar` chart for categorical features with predicted class/value.
       - Responsive loading spinner, clear error banners, and empty/unsupported state fallbacks.

### 3. Master Test Verification Evidence
- **Backend Test Suite**:
  - Command: `cd backend && ./mvnw test`
  - Result: **69 tests run, 0 failures, 0 errors, 2 skipped (100% passing)** across 20 test classes.
  - Total execution time: ~6.5 seconds.
- **Frontend Test Suite**:
  - Command: `cd frontend && npm test -- --run`
  - Result: **77 tests passed across 16 test files, 0 failures**:
    1. `AutoMlFeatureEffect.test.jsx` (4 tests passing)
    2. `VisualizationsPage.test.jsx` (5 tests passing)
    3. `AssociationsPage.test.jsx` (9 tests passing)
    4. `DatasetGating.test.jsx` (6 tests passing)
    5. `LandingPage.test.jsx` (2 tests passing)
    6. `DatasetsPage.test.jsx` (5 tests passing)
    7. `DashboardPage.test.jsx` (4 tests passing)
    8. `FindingsPage.test.jsx` (7 tests passing)
    9. `DriftPage.test.jsx` (3 tests passing)
    10. `ModelEvaluationPanel.test.jsx` (5 tests passing)
    11. `EvidenceGraphPanel.test.jsx` (5 tests passing)
    12. `EdaGallery.test.jsx` (5 tests passing)
    13. `BoxPlotChart.test.jsx` (2 tests passing)
    14. `analysisService.test.js` (8 tests passing)
    15. `datasetService.test.js` (4 tests passing)
    16. `researchService.test.js` (3 tests passing)
  - Total execution time: 2.14 seconds.
- **Frontend Production Build**:
  - Command: `cd frontend && npm run build`
  - Result: **131 modules transformed, build succeeded in 3.92s** with clean Rollup chunking (`vendor-react`, `vendor-charts`, `index`), zero errors, and zero warnings.


### 1. Fix Blank Associations Page (Phase 2)
- **Root Cause Analysis**:
  1. `frontend/src/pages/AssociationsPage.jsx`: Line 158 called `navigate(...)` inside `handleInvestigate` without defining `const navigate = useNavigate();` at component scope. When clicking "Investigate" or if triggered, an uncaught `ReferenceError: navigate is not defined` crashed the entire component tree.
  2. Invalid DOM nesting: Filter `<select>` options contained `{getTestBadge(t)} {t}` which rendered `<span>` badges inside `<option>` tags, triggering React invalid DOM nesting warnings and crashes on certain browser engines.
  3. Silent error suppression: Initial API fetch swallowed errors via `.catch(() => null)`, setting no user-visible error state, causing the page to fall through to a false "No Associations Found Matching Filters" empty state with no retry action.
  4. Ambiguous empty states: When a dataset produced zero statistically eligible pairs, the UI told users to "disable filters" rather than explaining that no variable pairs met the threshold criteria.
  5. Missing React Error Boundary: Any Chart.js rendering issue crashed the page to a blank white screen.
  6. Backend `AssociationEngine.java`: Potential `NullPointerException` when unboxing `colA.getUniqueCount()`, checking null `dataType`, or retrieving unprofiled column vectors.
- **Implemented Fixes**:
  - `backend/src/main/java/com/autoscientist/analysis/association/AssociationEngine.java`:
    - Added null-safe checks on `uniqueCount`, `typeA`, `typeB`, `rawA`, `rawB`.
    - Guarded against datasets with `< 2` active columns.
    - Wrapped pairwise computations in per-pair `try/catch` blocks so a single pathological column pair cannot abort the entire discovery run.
  - `frontend/src/pages/AssociationsPage.jsx`:
    - Added `const navigate = useNavigate();`.
    - Extracted string-only test names via `getTestName()` for clean `<option>` tags without nested DOM elements.
    - Added React `AssociationsErrorBoundary` wrapping the view with a recovery card and "Try Again" CTA.
    - Implemented actionable error banner with "Retry Analysis" button on network or server failures.
    - Added dedicated empty states: `associations-zero-pairs` (no pairs met criteria + "Re-run Discovery" button), `associations-no-filter-match` (active filter matched 0 rows + "Reset Filters" button), and `associations-no-dataset` (no dataset active + "Go to Datasets" button).
    - Added finite number guards (`Number.isFinite`) on confidence interval scatter coordinates.
    - Expanded test suite in `frontend/src/test/AssociationsPage.test.jsx` from 5 to 9 comprehensive tests (all passing).

### 2. Visualization Performance Profiling & Optimization (Phase 3)
- **Bottlenecks Identified**:
  - `EdaService.getGroupedSummary` read and parsed the raw CSV from disk on *every single request* without any caching, causing noticeable lag when switching variables.
  - `analysisRunService.record` was executed outside the cache retrieval supplier for all EDA endpoints. Even on cache hits, every EDA call acquired a `REQUIRES_NEW` transaction, queried the database, serialized payloads, and performed database writes. Loading all visualization panels triggered 10+ unnecessary database transactions simultaneously.
- **Implemented Optimizations**:
  - `backend/src/main/java/com/autoscientist/util/BoundedCache.java`:
    - Added `invalidatePrefix(String prefix)` for targeted bulk cache eviction by dataset ID prefix (`datasetId + "@"`).
    - Added `clear()` for thread-safe cache flushing.
    - Added unit test `BoundedCacheTest.testInvalidatePrefixAndClear` (passing).
  - `backend/src/main/java/com/autoscientist/service/EdaService.java`:
    - Added caching to `getGroupedSummary` using `cacheKey(dataset, "grouped", numVar + "|" + catVar)`.
    - Moved `analysisRunService.record(...)` inside the computation supplier across all EDA methods (`getCorrelationMatrix`, `getBoxPlots`, `getGroupedSummary`, `getHistograms`, `getScatter`, `getGroupDistributions`, `getMissingnessMatrix`, `getMissingness`, `getCategoricalSummaries`, `getTimeSeries`).
    - Cache hits now return in **< 1 ms** with **0 database transactions** and **0 disk reads**.
    - Cache keys naturally invalidate when dataset version bumps on reprofiling or exclusion changes (`datasetId@version:op`).
    - Added `invalidateDataset(Long datasetId)` and `clearCache()`.
  - `frontend/vite.config.js`:
    - Added Rollup chunking strategy via `manualChunks`: separated `vendor-react` (`react`, `react-dom`, `react-router-dom`), `vendor-charts` (`chart.js`, `react-chartjs-2`), and general `vendor`.
    - Production bundle split into clean chunks (`vendor-react.js` 152 kB, `vendor-charts.js` 177 kB, `index.js` 250 kB), completely eliminating the Vite `Some chunks are larger than 500 kB` warning.

## Verification session (2026-10-22 — Cinematic Homepage Redesign + First-Visit Routing Fix)

### 1. Part A: First-Visit Routing Bug Fix

#### Root Cause Analysis
- **Global 401 Interceptor Redirect on Public Routes (`frontend/src/services/api.js`)**:
  Line 33 previously evaluated:
  `if (window.location.pathname !== '/login' && window.location.pathname !== '/register') window.location.href = '/login';`
  When a visitor opened `/`, `window.location.pathname === '/'`. If an expired or invalid token existed in `localStorage`, or if any background/optional request failed with HTTP 401 on `/`, the axios response interceptor immediately forced a hard browser navigation to `/login`.
- **Expired Token in `AuthContext.jsx`**:
  `initAuth()` invoked `authService.getCurrentUser()` (`/api/auth/me`). When the token had expired, the backend returned HTTP 401, which `api.js` intercepted, redirected the window to `/login`, and wiped the token. Opening a subsequent tab had `token = null`, which did not make an auth request, so the second tab displayed `/` correctly.
- **Unguarded `WorkflowContext.jsx` Fetch on Mount**:
  `WorkflowProvider` is mounted globally in `App.jsx`. On initial render, an effect invoked `fetchWorkflowData(datasetId)` if a `datasetId` was stored in `localStorage`, *without checking `isAuthenticated`*. The protected `/api/datasets/:id` call failed with HTTP 401, triggering `api.js`'s global redirect to `/login` and clearing the stored dataset ID.

#### Implemented Fixes
1. **`frontend/src/services/api.js`**:
   - Excluded all public paths (`/`, `/home`, `/login`, `/register`) and `/auth/me` from global 401 redirects.
   - For protected routes, captures intended destination URL (`encodeURIComponent(currentPath)`) and redirects with `?redirect=...`.
2. **`frontend/src/context/AuthContext.jsx`**:
   - Added request sequence counter (`authRequestId = useRef(0)`) to discard stale async responses in React StrictMode.
   - Added cross-tab `storage` event synchronization for `autoscientist_token`.
   - Handled expired tokens deliberately without page reloads.
3. **`frontend/src/context/WorkflowContext.jsx`**:
   - Strictly guarded `fetchWorkflowData` and dataset calls with `isAuthenticated && !authLoading`. Never fires protected calls when unauthenticated.
4. **`frontend/src/App.jsx`**:
   - Updated `publicRoutes` to include `/home`.
   - Updated catch-all route to `<Navigate to={isAuthenticated ? "/dashboard" : "/"} replace />`.
5. **`frontend/src/pages/LoginPage.jsx`**:
   - Added support for `?redirect=...` query parameter and `location.state?.from`.
   - Added auto-redirect to `/dashboard` if already authenticated.
   - Added `htmlFor`/`id` accessibility pairing for email and password inputs.
6. **`frontend/src/pages/RegisterPage.jsx`**:
   - Added auto-redirect to `/dashboard` if already authenticated.
7. **Regression Test Suite (`frontend/src/test/RoutingAndAuth.test.jsx`)**:
   - Created 10 comprehensive deterministic unit/integration test scenarios covering fresh unauthenticated visits, delayed auth init, failed optional API requests, direct login/register access, unauthenticated protected route redirection, loading spinner guards, destination URL preservation, expired token handling, stale request race conditions, and logout behavior.
   - **All 10/10 tests passing**.

---

### 2. Part B: Public Homepage Redesign (Dark-Cinematic Scroll Storytelling)

#### Concept & Visual Direction
- **Theme**: Dark-cinematic concept 6 (hybrid of Living Scientific Universe, From Data to Discovery, Interactive Research Lab, AI Command Center, Scientific Storybook).
- **Palette**: Deep obsidian (`#080B12`), secondary surface (`#111827`), card surface (`#151D2F`), primary electric cyan (`#22D3EE`), secondary indigo (`#818CF8`) and violet (`#A78BFA`), soft white text (`#E5E7EB`), and accessible cool gray (`#9CA3AF`).
- **Scoping**: All rules strictly scoped to `.cinematic-home` in `frontend/src/styles/home.css` to prevent leaking into authenticated workbench views.
- **Accessibility & Motion**: Full `prefers-reduced-motion: reduce` support, IntersectionObserver-based lifecycle pausing, and keyboard focus preservation.

#### Delivered Modular Components (`frontend/src/components/home/`)
1. **`HomeHero.jsx`**:
   - Headline: **Turn Data Into Discovery.**
   - Supporting Copy: "Explore patterns, evaluate models, investigate explanations, and turn your datasets into evidence-backed scientific insights."
   - Primary CTA: "Start a Discovery" (`to={isAuthenticated ? "/datasets" : "/register"}`).
   - Secondary CTA: "Explore the Science" (smooth scroll to `#chapter-raw-data`).
   - Living Scientific Network: 38-node canvas particle network with proximity connections, bounds collision, boundary dampening, offscreen pause via `IntersectionObserver`, and jsdom/headless bypass.
   - Live Scientific Command Console: Simulates real CLI pipeline execution (`import`, `audit-quality`, `associations`, `automl`, `explain`, `report`).
   - Highlight badges: 100-Point Quality Audit, Bivariate Associations & p-values, Seeded Reproducible AutoML, Provenance Evidence Graph.
2. **`DiscoveryProgress.jsx`**:
   - Floating chapter progress indicator tracking the 5 storytelling chapters using `IntersectionObserver`.
   - Allows one-click jumping to any chapter.
3. **`DatasetStoryVisual.jsx` (Chapter 1 — Raw Data)**:
   - Headline: "Every discovery starts with data."
   - Structured tabular preview of clinical cohort data.
   - Quarantined identifier badge: `patient_id` isolated from predictive model leakage.
   - 100-point data quality score gauge (98.4/100, High Quality).
   - Ingestion details: CSV, TSV, multi-sheet XLSX, non-destructive exclusions.
   - Explicit label: *"Illustrative benchmark dataset for scientific demonstration"*.
4. **`AnalyticsStoryVisual.jsx` (Chapter 2 — Patterns Emerge)**:
   - Headline: "Find the patterns hiding in plain sight."
   - Interactive 3-tab analytics suite:
     - Correlation Matrix Heatmap: Interactive cells with Pearson $r$ and exact two-tailed $p$-values.
     - Ranked Associations Leaderboard: Effect sizes (Cohen's $d$, $\eta^2$, Cramér's $V$) with 95% confidence intervals.
     - Group Box Plots: Five-number summary comparison across treatment groups (Min, Q1, Median, Q3, Max, sample $N$).
   - Explicit label: *"Illustrative benchmark dataset for scientific demonstration"*.
5. **`ModelStoryVisual.jsx` (Chapter 3 — Experiments and Models)**:
   - Headline: "Turn questions into experiments."
   - AutoML Leaderboard: Benchmarking Random Forest, Gradient Boosted Trees, Ridge, and Decision Tree baseline under reproducible seeded splits (Seed: 42, 5-Fold CV).
   - Interactive Feature Response Curve: Selected-feature curve with SVG area visualization.
   - Interactive What-If Simulator: Real-time slider recalculating predicted favorable outcome probability.
   - Explicit label: *"Illustrative model benchmark for scientific demonstration"*.
6. **`EvidenceStoryVisual.jsx` (Chapter 4 — Explain and Investigate)**:
   - Headline: "Understand what your models reveal."
   - 4 Investigation Quadrants:
     - Feature Contributions (directional breakdown: $+0.32$ Biomarker, $+0.24$ Treatment, $-0.18$ BP).
     - Fairness & Parity Audit (EEOC 80% Four-Fifths ratio: 0.88 > 0.80 Passed).
     - Distribution Drift Monitor (Kolmogorov-Smirnov test: $D = 0.042, p = 0.68$, No drift).
     - Evidence Graph Provenance (Question $\to$ Hypothesis $\to$ Statistical Test $\to$ Finding).
   - **Mandatory Scientific Integrity Callout**: *"Model explanations and feature-response curves are analytical diagnostic tools, not proof of causation. Assessment of fairness and distribution drift requires appropriate baseline data."*
7. **`ReportStoryVisual.jsx` (Chapter 5 — Findings and Reports)**:
   - Headline: "Make every result easier to examine."
   - Publication-Ready Report Preview: Executive summary, hypothesis testing evidence, and model findings.
   - Multi-Format Export Cards: PDF, Markdown, Excel (XLSX), CSV.
   - Concluding CTA Banner: **Your next discovery starts with a dataset.**
   - Public Footer: AutoScientist AI mission, integrity declaration, and copyright.
8. **`Navbar.jsx`**:
   - Single top header with AutoScientist AI brand, "How It Works" (`#how-it-works`), "Scientific Capabilities" (`#capabilities`), "Login", and "Register".
9. **`LandingPage.jsx`**:
   - Assembled all modular components into the unified scroll-storytelling orchestrator.

---

### 3. Verification & Test Evidence

#### Frontend Unit & Regression Tests
- **Command**: `cd frontend && npm test -- --run`
- **Result**: **88 tests passed across 17 test files (100% passing)**:
  1. `src/test/RoutingAndAuth.test.jsx` (10 tests passing)
  2. `src/test/LandingPage.test.jsx` (3 tests passing)
  3. `src/test/AssociationsPage.test.jsx` (9 tests passing)
  4. `src/test/AutoMlFeatureEffect.test.jsx` (4 tests passing)
  5. `src/test/DatasetGating.test.jsx` (6 tests passing)
  6. `src/test/DatasetsPage.test.jsx` (5 tests passing)
  7. `src/test/DriftPage.test.jsx` (3 tests passing)
  8. `src/test/EdaGallery.test.jsx` (5 tests passing)
  9. `src/test/FindingsPage.test.jsx` (7 tests passing)
  10. `src/test/VisualizationsPage.test.jsx` (5 tests passing)
  11. `src/test/BoxPlotChart.test.jsx` (2 tests passing)
  12. `src/test/DashboardPage.test.jsx` (4 tests passing)
  13. `src/test/EvidenceGraphPanel.test.jsx` (5 tests passing)
  14. `src/test/ModelEvaluationPanel.test.jsx` (5 tests passing)
  15. `src/test/analysisService.test.js` (8 tests passing)
  16. `src/test/datasetService.test.js` (4 tests passing)
  17. `src/test/researchService.test.js` (3 tests passing)
- **Duration**: ~1.9 seconds.

#### Frontend Production Build
- **Command**: `cd frontend && npm run build`
- **Result**: **139 modules transformed, built in 3.75s**:
  - `dist/index.html`: 0.84 kB (gzip: 0.42 kB)
  - `dist/assets/index-CG9t2bNH.css`: 15.26 kB (gzip: 3.70 kB)
  - `dist/assets/vendor-D_mTzRiS.css`: 307.88 kB (gzip: 44.58 kB)
  - `dist/assets/vendor-B1ac9mmj.js`: 72.54 kB (gzip: 28.31 kB)
  - `dist/assets/vendor-react-CBcz0jqk.js`: 152.12 kB (gzip: 48.98 kB)
  - `dist/assets/vendor-charts-CZQWH73Y.js`: 182.83 kB (gzip: 62.36 kB)
  - `dist/assets/index-BjFaSUiN.js`: 302.01 kB (gzip: 68.17 kB)
- Zero warnings, zero errors.

#### Backend Test Suite
- **Command**: `cd backend && ./mvnw test`
- **Result**: **69 tests run, 0 failures, 0 errors, 2 skipped (100% passing)** across 20 test classes. Total time: ~6.0s.

---

## Verification session (2026-10-22 — Authentication Diagnosis & Fix for "Invalid Credentials" During Login)

### 1. Root Cause Analysis

Tracing the entire login flow from the frontend form to database persistence revealed four interacting factors causing the "Invalid credentials." error:

1. **In-Memory Database Reset Without Development Seeding (`dev` profile)**:
   - In `backend/src/main/resources/application.yml` and `backend/.env`, `SPRING_PROFILES_ACTIVE=dev` activates an H2 in-memory database (`jdbc:h2:mem:autoscientist_dev;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;MODE=MySQL`) with `hibernate.ddl-auto: create-drop`.
   - The application previously had no `CommandLineRunner` or `ApplicationRunner` seeding users on startup.
   - When the backend restarted, all user accounts in the in-memory database were cleared. Any login attempt with an account registered prior to the restart failed with `UsernameNotFoundException` -> Spring Security `DaoAuthenticationProvider` `BadCredentialsException` -> HTTP 401 -> "Invalid credentials."
2. **Whitespace and Input Sanitization Inconsistencies**:
   - `LoginRequest.java` and `RegisterRequest.java` enforced `@Email` and `@NotBlank` annotations.
   - Browser autocomplete, copy-paste, and mobile keyboards frequently add trailing whitespace (e.g. `"jane@university.edu "`).
   - Bean validation rejected untrimmed emails with a 400 Bad Request (`Validation failed: Valid email is required`).
   - `LoginPage.jsx` did not trim emails prior to sending, causing validation failures to be misinterpreted.
3. **Email Normalization & Case-Sensitivity**:
   - `UserRepository.java` only provided case-sensitive `findByEmail(String email)`.
   - In `AuthService.java`, registration checked `userRepository.existsByEmail(request.getEmail())` without trimming or lowercasing, while saving `request.getEmail().trim().toLowerCase()`.
   - If a user registered with mixed-case email or logged in with differing casing, user lookup failed depending on database collation.
4. **Generic Frontend Fallback Masking Network and Server Errors**:
   - In `frontend/src/pages/LoginPage.jsx` (line 39), the catch block previously executed:
     `setError(err.response?.data?.message || err.message || 'Invalid credentials.');`
   - If the backend server was not running or offline (network error), or if a 400 validation error occurred with field-level details, or if an internal server error occurred without a message string, the catch block silently defaulted to displaying `"Invalid credentials."`.
5. **Legacy Database Schema Drift (`password` vs `password_hash`)**:
   - In historical databases (such as `backups/autoscientist_db_20261020_174945.sql`), the column storing BCrypt password hashes was named `password`. Modern entities map `password_hash`.
   - If restored without Flyway V2 executing, `password_hash` remained `NULL`, causing `passwordEncoder.matches(raw, null)` to fail even for valid passwords.

---

### 2. Implemented Fixes

1. **DTO Trimming & Sanitization (`LoginRequest.java` & `RegisterRequest.java`)**:
   - Added explicit Jackson-compatible setters (`setEmail(String email)`, `setName(String name)`) that automatically trim whitespace upon deserialization, ensuring `@Email` validation succeeds cleanly on trailing whitespace.
2. **Repository Case-Insensitive Queries (`UserRepository.java`)**:
   - Added `Optional<User> findByEmailIgnoreCase(String email)` and `boolean existsByEmailIgnoreCase(String email)`.
3. **User Details & Auth Service Email Normalization (`UserDetailsServiceImpl.java` & `AuthService.java`)**:
   - In `UserDetailsServiceImpl.loadUserByUsername`: trims and lowercases email, querying `findByEmail(email)`, `findByEmail(normalizedEmail)`, `findByEmailIgnoreCase(normalizedEmail)`, and `findByEmail(email.trim())`.
   - In `AuthService.java`: consistently normalizes emails across `register`, `login`, and `getCurrentUser`, checking existing emails and fallbacks without breaking existing unit tests.
4. **Data Compatibility & Development Seeding (`DataInitializer.java`)**:
   - Created `@Component` implementing `CommandLineRunner`.
   - Safe legacy password backfill: executes idempotent query `UPDATE users SET password_hash = password WHERE (password_hash IS NULL OR password_hash = '') AND password IS NOT NULL` (gracefully catches and ignores if column is absent).
   - In `dev` profile: if `userRepository.count() == 0`, seeds default researcher account `jane@university.edu` / `SecurePassword123!` (matching `docs/API_DOCUMENTATION.md`) and admin `admin@autoscientist.com` / `science123` with standard BCrypt password hashing.
   - Never modifies existing production accounts or touches databases when users exist.
5. **Frontend Diagnostics & Error Handling (`LoginPage.jsx` & `RegisterPage.jsx`)**:
   - Form submission automatically trims email (`email.trim()`).
   - Differentiates error types explicitly:
     - Unreachable backend / network errors: *"Unable to connect to AutoScientist server. Please verify the backend is running."*
     - HTTP 401 Unauthorized: *"Invalid email or password."*
     - HTTP 400 Bad Request: extracts and displays field-level validation errors (e.g. *"Valid email is required"*).
     - Other server errors: displays API message or general failure message without defaulting to "Invalid credentials."
   - Added accessible Demo Credentials card with "Quick Fill" button (`jane@university.edu` / `SecurePassword123!`).

---

### 3. Regression Test Evidence

#### Backend Regression Tests (`backend/src/test/java/com/autoscientist/AuthIntegrationTest.java`)
Added 9 comprehensive integration tests covering:
1. Fresh user registration followed by immediate login (200 OK, JWT token, Bearer type, user profile).
2. Login with incorrect password safely rejected with HTTP 401 and `"Invalid email or password."`.
3. Login with unknown email safely rejected with HTTP 401 (preventing user enumeration).
4. Password storage verification: confirmed passwords are saved as BCrypt hashes (`$2a$`), never plaintext, and match configured encoder.
5. Case-insensitive email login: verifies login works across lowercase and uppercase variations.
6. Leading/trailing whitespace trimming: verifies emails with spaces log in successfully.
7. Dev profile seeded demo account verification (`jane@university.edu` / `SecurePassword123!`).
8. Duplicate registration rejection with HTTP 400 Bad Request.
9. Malformed email validation rejection with HTTP 400 Bad Request and field-level error.

- **Backend Test Run**: `cd backend && ./mvnw test`
- **Result**: **78 tests run, 0 failures, 0 errors, 2 skipped (100% passing)** across 21 test classes.

#### Frontend Regression Tests (`frontend/src/test/LoginPage.test.jsx`)
Added 5 comprehensive frontend tests covering:
1. Quick Fill demo credentials populating email and password.
2. Email leading/trailing whitespace trimming before invoking login service.
3. Network error display when backend server is unreachable.
4. HTTP 401 Unauthorized invalid credentials error display.
5. HTTP 400 Bad Request validation error display from field errors.

- **Frontend Test Run**: `cd frontend && npm test -- --run`
- **Result**: **93 tests passed across 18 test files (100% passing)**.
- **Frontend Production Build**: `cd frontend && npm run build` — 139 modules transformed, built in 3.85s with 0 warnings.

---

## Persistent File-Backed H2 for Local Development (2026-10-22)

### 1. Motivation and Architectural Design
- **Problem**: In local development using the default H2 database, newly registered user accounts, uploaded datasets, and trained models were stored in an in-memory database (`jdbc:h2:mem:...`) that vanished every time the backend restarted. Developers had to re-register and re-upload datasets on every restart.
- **Architectural Solution**:
  1. Configured persistent, file-backed H2 storage exclusively for the `dev` profile.
  2. Maintained deterministic file location at `backend/data/h2/autoscientist_dev.mv.db`.
  3. Ensured directory creation safety across both startup shell scripts (`scripts/run_backend.sh`) and Java application boot (`AutoScientistApplication.java`).
  4. Preserved strict environment isolation: staging and production environments continue using MySQL with Flyway migration authority; automated unit and integration tests remain isolated using in-memory H2 (`jdbc:h2:mem:autoscientist_test`) with `create-drop` to avoid inter-test pollution.

---

### 2. Implemented Changes

1. **Repository Hygiene (`.gitignore`)**:
   - Added `backend/data/` and `data/` to prevent committing local H2 database files (`*.mv.db`, `*.trace.db`).
2. **Startup Script Safety (`scripts/run_backend.sh`)**:
   - Added pre-startup directory creation: `mkdir -p data/h2` and `mkdir -p uploads/datasets`.
3. **Application Lifecycle Safety (`AutoScientistApplication.java`)**:
   - Added a static initialization block ensuring `data/h2` and `uploads/datasets` exist before Spring Boot's `DataSourceAutoConfiguration` connects to the file path.
4. **Configuration Profiles (`application.yml` and `test/resources/application.yml`)**:
   - **`dev` profile in `backend/src/main/resources/application.yml`**:
     ```yaml
     ---
     spring:
       config:
         activate:
           on-profile: dev
       datasource:
         url: ${SPRING_DATASOURCE_URL:jdbc:h2:file:./data/h2/autoscientist_dev;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;MODE=MySQL}
         driver-class-name: org.h2.Driver
         username: sa
         password:
       jpa:
         hibernate:
           ddl-auto: update
         database-platform: org.hibernate.dialect.H2Dialect
       h2:
         console:
           enabled: true
           path: /h2-console
     ```
   - **Staging / Production profile (`mysql`)**: Untouched; strictly enforces Flyway migrations and external credentials.
   - **Automated test isolation (`backend/src/test/resources/application.yml`)**:
     Configured an isolated in-memory test database with `spring.profiles.active: ${SPRING_PROFILES_ACTIVE:dev}`, `url: ${SPRING_DATASOURCE_URL:jdbc:h2:mem:autoscientist_test;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;MODE=MySQL}`, and `hibernate.ddl-auto: create-drop`. This guarantees tests run cleanly in RAM and never depend on or contaminate persistent development database files.

---

### 3. Verification & Test Evidence

#### Automated Restart Integration Test (`backend/src/test/java/com/autoscientist/PersistentDevDatabaseRestartIntegrationTest.java`)
Added a rigorous end-to-end integration test simulating an entire backend process restart lifecycle using Spring Boot's `SpringApplicationBuilder`:
1. **Session 1 (Initial Startup)**:
   - Boots against a file-backed H2 database URL in a temporary directory.
   - Verifies `DataInitializer` seeds default development accounts (`jane@university.edu`).
   - Registers a new user account (`dr.curie@physics.org`) via `AuthService`.
   - Persists a new dataset entity ("Radionuclide Decay Curve") associated with the new user.
   - Completely closes and terminates the `ApplicationContext`, releasing all database locks and Hikari connection pools.
2. **Session 2 (Backend Restart)**:
   - Boots a completely new `ApplicationContext` against the identical file database URL.
   - Confirms `dr.curie@physics.org` and its BCrypt password hash survived the process restart.
   - Executes `authService.login(...)` with the user's raw password and verifies successful authentication and JWT generation.
   - Confirms the dataset record survived with its metadata and foreign-key user relation intact.
   - Verifies `DataInitializer` is idempotent and did not duplicate demo accounts.

#### Full Test Suite Execution
- **Backend Test Suite**: `cd backend && ./mvnw test`
  - **Result**: **85 tests run, 0 failures, 0 errors, 2 skipped (100% passing)** across 23 test classes.
- **Frontend Test Suite**: `cd frontend && npm test -- --run`
  - **Result**: **93 tests passed across 18 test files (100% passing)**.
- **Frontend Production Build**: `cd frontend && npm run build`
  - **Result**: **139 modules transformed, built in 4.11s with 0 errors**.

---

## Session 5 (2026-10-28) — production ML 500 fix + password reset

### Root cause fixed: AutoML cold-cache 500 on MySQL
- **Symptom**: after a backend restart, `GET /api/datasets/{id}/automl/whatif/schema`
  and `/api/datasets/{id}/automl/feature-effect` returned HTTP 500 in production.
- **Cause**: both methods were `@Transactional(readOnly = true)` but
  `loadModelContext` retrains and persists `experiment_versions` on a cold cache.
  MySQL rejects the insert with `Connection is read-only. Queries leading to data
  modification are not allowed`; H2 does not enforce this, so local H2 tests passed.
- **Fix**: both methods in `AutoMlService` are now normal `@Transactional` with an
  explanatory comment. Reproduced on local MySQL 9.7 and verified after a cold
  restart (both endpoints `200`).

### Password reset (backend + frontend)
- New `password_reset_tokens` table (`V4__password_reset_tokens.sql`), SHA-256
  token hashes only, 30-minute expiry, single use, per-user 60-second request
  interval, and invalidation of older tokens on a new request.
- Endpoints `POST /api/auth/forgot-password` and `POST /api/auth/reset-password`
  with identical responses for known/unknown emails (no enumeration). Rejects
  expired/used/unknown tokens, passwords shorter than 8 characters, and reuse of
  the current password.
- Delivery via SMTP (`spring-boot-starter-mail`), with a local `.eml` outbox
  fallback for development. Tokens are never logged; only hashes are stored.
- Frontend pages `/forgot-password` and `/reset-password`, public routes, 401
  interceptor exemptions, and a login-page link. Frontend tests cover both pages.
- `.env.example` and deployment docs document `FRONTEND_URL`, reset TTL/interval,
  and SMTP variables.

### Verification actually run
- Backend: `./mvnw -q test` → **101 tests, 0 failures, 0 errors, 2 skipped**
  across 34 test classes.
- Frontend: `npm test -- --run` → **126 tests passed across 21 test files**;
  `VITE_API_BASE_URL=https://autoscientist-ai-backend.onrender.com/api npm run build`
  → success (141 modules).
- MySQL 9.7 (local, `ddl-auto=validate`): Flyway applied V4, Hibernate validated
  the schema, cold-cache what-if/feature-effect returned `200`.
- Password-reset MySQL E2E: register → forgot (`.eml` outbox) → reset → old
  password `401`, new password `200`; reused token `400`; DB stored a 64-char
  SHA-256 hash, not the raw token.
- `MysqlBaselineMigrationTest` now creates the V4 table via an H2 `INIT` shim
  because V2/V3 are MySQL-only and the test intentionally targets V1.
- No production deploy, commit, or push was performed.
