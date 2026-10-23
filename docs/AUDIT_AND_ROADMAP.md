# AutoScientist AI — Audit & Roadmap (M0–M8)

Status: audit retained as historical evidence; **all M0–M8 work has since been implemented** (see §11). Original text below describes the pre-implementation state.
Evidence labels used throughout:

- **[VERIFIED]** — reproduced by running the code in this environment (command + observed output recorded below).
- **[CODE]** — established by reading the source; the exact file:line is cited.
- **[ASSUMPTION]** — plausible, not yet proven; listed in Open Questions.

---

## 1. Method & Environment

Read-only sweep of the repo (structure, git, docs, frontend pages/services, backend controllers/services/entities/config, analysis engines), followed by an end-to-end runtime smoke test against a locally started backend.

Environment actually measured:

| Item | Result |
|---|---|
| Java | OpenJDK 21.0.12 (system `mvn` absent — use `./mvnw`) |
| Node / npm | v25.8.2 / 11.11.1 |
| Backend build | `./mvnw -q test` from `backend/` → exit 0 |
| Backend tests | 26 at audit time → **40 now**, 0 failures/errors, 16 classes |
| Frontend build | 515.75 kB at audit time → **607.23 kB now** (gzip 183.52 kB), success |
| Frontend tests | none (`package.json` has no `test` script, no lint script) |
| MySQL | running on :3306, but `root/root` is **rejected** on this machine (prod profile needs real creds) |

Test classes at audit time (26 tests): `AuthControllerTest` 3, `AuthServiceTest` 4, `AutoMlEngineTest` 2, `BiomedicalClassificationPipelineTest` 1, `DataDriftAndAskTest` 2, `DataQualityEngineTest` 1, `DatasetProfilerTest` 3, `EndToEndPipelineTest` 1, `QuestionAnalysisEngineTest` 2, `ReportAndPdfTest` 1, `StatisticalTestEngineTest` 5, `WorkflowServiceTest` 1.

Runtime smoke test (dev profile, H2 create-drop, temp upload dir, 60-row CSV `customer_id,age,salary,zip_code,region,churn`):

```
register → 200 | login → token | POST /datasets/upload → 200 (rows=60, cols=6)
GET  /datasets/1                     → 200
GET  /datasets/1/eda/correlations    → 200  variables: [age, salary, zip_code]
GET  /datasets/1/eda/boxplots        → 200  vars: age, salary, zip_code
POST /datasets/1/associations/refresh→ 200  10 associations
POST /datasets/1/automl/train        → 200  3 models, best LogisticRegression
GET  /datasets/1/automl/latest       → 200
GET  /datasets/1/workflow-status     → 200  xai: completed=true, prereq=true
POST /datasets/1/reports             → 200  report id 1, markdown 8013 chars
GET  /datasets/1/reports             → 200
GET  /reports/1                      → 200
GET  /reports/1/pdf                  → 200  application/pdf, 21,618 bytes
```

**Key finding:** all four reported failure areas *work* in a fresh, healthy state. The reported symptoms therefore originate from **state/environment divergence** (stale client state, missing-schema storage, wiped dev DB, empty in-memory caches, low-quality/short datasets) plus **silent-degradation code paths** — not from dead endpoints. This changes M0 from "add missing endpoints" to "remove silent-divergence paths and make failures explicit".

---

## 2. Architecture Snapshot [VERIFIED by reading + runtime]

- Backend: Spring Boot 3.2.4, `backend/pom.xml` sets `<java.version>17</java.version>` while README claims Java 21 → **drift** (runs fine on JDK 21).
- Layering: `controller → service → analysis/* engines → util`, DTOs in `dto/**`, entities in `entity/**`. Analysis engines (profiling, EDA, stats, quality, ML, XAI, report) are plain classes, mostly stateless except `AutoMlService.MODEL_CACHE`.
- Persistence: Spring Data JPA. **No Flyway/Liquibase**; `database/migrations/` is empty; schema comes from `ddl-auto`. Dev profile (H2) is **create-drop** → all dataset metadata is destroyed on every backend restart while `localStorage` on the client survives.
- Storage: `FileStorageService` accepts **only `.csv`**, stores a UUID filename under `app.upload.dir` (default `./uploads/datasets`, CWD-relative, gitignored). Every file-reading engine re-opens that path; a missing file throws `"File does not exist or access is restricted."`.
- Security: JWT (`AuthTokenFilter`), `anyRequest().authenticated()`; only `/api/auth/**` (+ H2 console in dev) are public. No `/api/public/**`.
- Frontend: React 18 + Vite, React Router, services return the axios body, i.e. the `ApiResponse {success,message,data,timestamp}` wrapper; pages read `res.data`. `/api` is proxied to `:8080`; the 401 interceptor clears storage and redirects to `/login`.
- Bootstrap: **CSS + Icons imported only** (`frontend/src/main.jsx`); Bootstrap **JS is never imported anywhere** [CODE] → `data-bs-toggle="collapse"` in `Navbar.jsx` is inert.
- Existing uploads on disk: `backend/uploads/datasets/` holds 3 legacy CSVs (UUID names). Gitignored → not reproducible in a fresh clone or ephemeral prod container.

---

## 3. Feature Status Matrix

| Area | Status | Evidence |
|---|---|---|
| Auth (register/login/me, JWT) | OK | 7 auth tests + runtime login |
| CSV upload + profiling | OK, single-format only | `FileStorageService` `.csv` check [CODE] |
| Excel ingest | Missing | no POI dependency in `pom.xml` [CODE] |
| Dataset list/detail | OK | runtime 200 |
| Quality assessment | OK, quality "issues" partly cosmetic | runtime: score 100 with "2 structural issues" |
| EDA (correlations/boxplots/grouped) | OK but includes ID-like numerics | runtime correlation vars `[age, salary, zip_code]` |
| Associations | OK | runtime 10 rows; DTO field is lowercase `pvalue` [CODE] |
| Research questions / hypotheses | OK | 4 tests, workflow-status summaries |
| Statistical tests | OK | 5 tests |
| AutoML training | OK on healthy state | runtime 3 models; `versionNumber` hardcoded `1` [CODE `AutoMlService.java:124`] |
| AutoML latest / recommender context | **fragile** | `featureImportances` served from static in-memory cache [CODE `AutoMlService.java:327`] |
| XAI | Works only while cache is warm | see Bug 2 |
| Drift | Works, additive to workflow | `DriftPage.jsx` + engine present |
| Ask-Dataset | Works (keyword intent) | 2 tests; `AskDatasetEngine` |
| Findings / evidence graph | Minimal (3 node types, flat UI) | `EvidenceEngine` |
| Reports + PDF | OK, degraded formatting | runtime 200; see Bug 4 |
| Public homepage | Missing (`/` → redirect to `/dashboard`) | `App.jsx` [CODE] |
| Responsive nav on mobile | **Broken** (no Bootstrap JS) | [CODE] |
| Experiments UI | Read-only list, not linked in Navbar | `ExperimentsPage.jsx`, `Navbar.jsx` [CODE] |
| Dashboard metrics | Partly fabricated | `DashboardService.java:89` `totalModelsTrained += 3` |

---

## 4. The Four Reported Bugs — Root Causes

### Bug 1 — "ML page: dataset loads but target column is empty / dataset-loading failure"

Failure modes, in order of likelihood:

1. **[CODE] Stale-bound dataset id with no fallback.** `AutoMlPage.jsx:27` `const effectiveId = id || workflowDatasetId` (the latter from `localStorage`). When `effectiveId` is truthy, `loadActiveDataset(effectiveId)` (`:125-126`, effect `:151`) is called and `listDatasets()` is **never** consulted. A stale/foreign id → `GET /datasets/{id}` 404 → error banner, `selectedDataset = null` → target `<select>` renders empty/disabled, "Train a Model First" appears even though the user *has* datasets. `ReportsPage.jsx:13` has the identical pattern (`activeDatasetId = id || workflowDatasetId`) → reports "appear to not load" while sibling endpoints keep working.
2. **[CODE] Dev DB is create-drop while client state persists.** After any backend restart, dataset rows vanish but `localStorage.autoscientist_dataset_id` remains → guaranteed 404 for exactly the id the page insists on using (mode 1).
3. **[CODE] File-backed engines depend on the CSV still existing.** `FileStorageService` throws for a missing file; `FeatureEngineeringEngine.prepareDataset`, `EdaService`, `ResearchService`, `DataDriftEngine` and report generation all stream that path. Ephemeral prod storage / different CWD / cleaned `uploads/` ⇒ "ML dataset-loading failure" **after** metadata loaded fine.
4. **[CODE] Silent empty target list.** `AutoMlPage.jsx:229-230` filters candidates with `c.dataType !== 'IDENTIFIER'`. If the profile is degenerate (all columns typed IDENTIFIER) the list is empty with no explanatory message. Related: `FeatureEngineeringEngine` resolves the target **case-sensitively** (`headers.indexOf(target)` / `equals`) while `AutoMlService` validates with `equalsIgnoreCase` → a case-only difference passes validation then fails in the engine.

Verified shape of the underlying error the UI swallows: `GET /api/datasets/999` → `404 {"success":false,"message":"Dataset not found with id: 999"}` [VERIFIED].

### Bug 2 — XAI prerequisite/recommendation inconsistency

Multiple disagreeing sources of truth [CODE]:

| Surface | Rule | Location |
|---|---|---|
| Backend workflow | `xai.completed = mlDone`, summary "Permutation importance, fairness & what-if simulator ready" | `WorkflowService.java:166-172` |
| Backend latest | `featureImportances` = `context != null ? … : emptyList()` | `AutoMlService.java:327,353` |
| Frontend recommender | requires `isStageCompleted('ml')` | `WorkflowContext.jsx:316-325` |
| `RecommendedNextStep` | "Current Stage Done" from `isStageCompleted('xai')` | `RecommendedNextStep.jsx` |
| `AutoMlPage` inline gate | `!results` from `GET /automl/latest` | `AutoMlPage.jsx` |

Consequences: XAI is declared **complete the instant ML finishes**, before any explanation exists; and `getLatestBenchmark` returns `featureImportances: []` whenever the process restarted (static `MODEL_CACHE` lost, DB rows still present) → the XAI page renders empty charts/tables and an empty What-If simulator *while every badge says ready*. Because the sources can diverge in both directions, the UI shows "Current Stage Done" and "Train a Model First" simultaneously.

### Bug 3 — Identifier columns treated as meaningful variables

[VERIFIED + CODE] On a 60-row dataset:

```
GET /datasets/1  →  customer_id | IDENTIFIER | conf=0.99 | uniq=60 | mean=30.50
                    zip_code    | NUMERIC    | conf=1.00 | uniq=60 | mean=10030.50
GET /eda/correlations → variables: [age, salary, zip_code]
GET /eda/boxplots     → vars: age, salary, zip_code
POST /automl/train    → top drivers: age, salary, zip_code, region_*
```

Root causes:

1. `DatasetProfiler` identifier gate is conjunctive and defeatable: name pattern **and** `(uniqueCount == n || distinctRatio > 0.40 || uniqueCount >= 20 || totalRows < 10)` **and** `isAllIntegerOrText`. A numeric id with duplicates and <20 distinct values, or a short-ish file, falls through to NUMERIC. The passing unit test only exercises 3 rows with a non-numeric `customer_id` (`DatasetProfilerTest.testIdentifierColumnDetection`) → the gate is effectively unmasked.
2. Name patterns miss `order_no`, `s_no`, `zip`, `zip_code`, `phone`, `ssn`, `email`, `account_number`, `invoice_number`, `sku`, etc.
3. Even correctly-typed IDENTIFIER columns keep numeric statistics, and those are displayed and reported (`customer_id` mean/median/std above).
4. `EdaEngine.java:101,134` contain dead filters: `.filter(c -> c.getDataType().equals("NUMERIC") && !c.getDataType().equals("IDENTIFIER"))` — a value cannot be both, so exclusion relies entirely on the type label being right.
5. Frontend duplicates the heuristic (`VisualizationsPage.jsx` `isIdentifierColumn`) → client/server divergence; `DatasetDetailPage.jsx:70-74` defaults to `numVars[0]/catVars[0]` with no ID filter; `QuestionAnalysisEngine.java:86-90` falls back to `columns.get(0)/(1)` untyped by identifier status.
6. Profiling runs **only at upload** (`DatasetService.java:69`) → existing datasets keep stale types; there is no re-profile path (`grep` shows a single call site).

Impact: inflated/collinear correlation matrices (above, `salary↔zip_code` r=1.0 from synthetic id leakage), misleading boxplots, and identifier "leak" features in AutoML.

### Bug 4 — Report loading / PDF reliability

Report endpoints are present and functional [VERIFIED]: `POST /datasets/{id}/reports`, `GET /datasets/{id}/reports`, `GET /reports/{id}`, `GET /reports/{id}/pdf` all 200 (PDF 21,618 bytes). `docs/FINAL_AUDIT.md`'s claim that the list endpoint was missing is stale.

Remaining real defects:

1. **[VERIFIED] PDF silently drops unsupported glyphs.** Extracted text of the produced PDF contains **0** occurrences of `★` although the markdown source has `★ **BEST MODEL**`; the extracted text shows `**BEST MODEL**` with literal asterisks. Only base-14 Helvetica is used (no embedded Unicode font), so any non-Latin-1 glyph disappears.
2. **[VERIFIED] Markdown is passed through literally.** Extraction shows `**bold**`, backticks, `---`, and `$H_0$` verbatim, and multi-column tables degrade to space-separated text with no borders/alignment.
3. **[CODE] Fairness/Drift report sections are hardcoded "Not performed"** in `ReportGeneratorService` even when XAI/fairness or drift data exists.
4. **[CODE] Raw JSON blobs in table cells** — validation/test metric maps are stringified into a single cell (visible in markdown excerpt), and the subsequent download UI failure modes trace back to the same stale/dataset-id coupling as Bug 1 (`ReportsPage.loadData` calls `getDataset(targetId)`; a fallback to `listReports()` masks partial failures).
5. **[CODE] No HTTP-level tests** for report endpoints/ownership (`ReportAndPdfTest` is a pure unit test).

---

## 5. Additional Verified Defects (not among the four)

- **[CODE] `GlobalExceptionHandler.java:65`** maps every unmapped exception — including client errors such as a missing `@RequestParam` — to **500**. Verified: `GET /eda/grouped?numVar=…` (wrong param name) → `500 "Required request parameter 'numericVar' … is not present"`; should be 400.
- **[CODE] Fabricated dashboard metric:** `DashboardService.java:89` `totalModelsTrained += 3; // Typically Ridge, Tree, Forest` — violates the project's zero-hallucination tenet and is also N+1 (per-dataset repository calls).
- **[CODE] Experiments are not versions:** `AutoMlService.java:124` records `versionNumber(1)` always, so re-training creates new Experiments rather than new versions. `ExperimentController` exposes only list/get (no comparison), and `ExperimentsPage.jsx` is a read-only list reachable only by URL (not in `Navbar.jsx`).
- **[CODE] Evidence graph is minimal:** `EvidenceEngine` emits only DATASET / HYPOTHESIS / STAT_TEST nodes; `FindingsPage.jsx` renders flat cards.
- **[CODE] Response bloat / duplicate fetches:** `DatasetDetailDto` embeds `distributionJson`/`categoriesJson` for every column, and many pages refetch `getDataset` concurrently with `WorkflowContext`.
- **[CODE] Performance shape to measure:** `DatasetProfiler` loads the whole CSV into `List<List<String>>` with per-column raw copies; upload reads the file ~3× (validator, profiler, quality); every EDA endpoint re-parses the CSV; `EdaEngine` correlation is O(k²·n) with per-pair string parsing; `AutoMlService.MODEL_CACHE` holds full `DatasetSplit`s in an unbounded static map (not multi-instance safe, stale after restart); report/PDF generation is synchronous on the request thread.
- **[CODE] Missing ownership/integration tests** for report + drift + automl endpoints.

Doc drift: `docs/TESTING.md` says 24 tests (actual 26); `docs/API_DOCUMENTATION.md` omits `/datasets/{id}/reports`, `/workflow-status`, `/findings`, `/experiments*`; `docs/TROUBLESHOOTING.md` references `APP_UPLOAD_DIR` while the config uses `FILE_UPLOAD_DIR`/`app.upload.dir`; README claims Java 21 while `pom.xml` targets 17.

---

## 6. Dependencies & Schema Changes Required

| Stage | Need | Type |
|---|---|---|
| M2 Excel | `org.apache.poi:poi-ooxml` (no POI today), plus `.xlsx/.xls` acceptance in `FileStorageService`, multi-sheet handling | new dependency |
| Bug 4 PDF | A Unicode-capable font (e.g. DejaVuSans/NotoSans TTF embedded via OpenPDF `BaseFont.createFont`) **or** a glyph-sanitizing step that maps `★ → *`, `→ → ->`, `↔ → <->`, `≥ → >=`, `² → ^2`, `• → -` | asset + code |
| Bug 3 | Additive `POST /datasets/{id}/reprofile` (recompute types/stats for existing rows); **no data dropped** | additive API |
| M4 | Persistent experiment **versions** + comparison endpoint; keep existing `experiments` rows valid | additive API; `versionNumber` fix |
| Perf | Optional caching of parsed datasets; DB index review | additive |
| Prod robustness | Durable object storage or explicit DB-stored dataset blobs; documented `ddl-auto` policy (currently `update` vs `create-drop` per profile) | decision + config |

No destructive migration is required for any M0 item.

---

## 7. Proposed New Visualizations (with engine requirements)

| Visualization | Requirement |
|---|---|
| Missing-value matrix / heatmap | `DatasetProfiler` already stores missing counts; needs per-row presence grid (streaming) |
| Correlation heatmap with significance mask + ID exclusion | reuse `EdaEngine` matrix; mask `p > α` (needs pairwise p-values, not just r) |
| Numeric-vs-numeric scatter (hexbin for large N) | needs downsampling to keep payloads bounded |
| Distribution overlay by group (violin/ridge) | `EdaEngine.computeGroupedSummary` + density estimation |
| Class-balance / target profile bar | `DatasetProfiler` target distribution |
| Confusion matrix + ROC/PR curves | **engine gap:** `AutoMlEngine` currently computes TP/FP/TN/FN and accuracy/precision/recall/F1 only — needs per-threshold scores for ROC/PR |
| Model comparison chart (parallel coordinates / ranked bars) | needs M4 versions + comparison endpoint |
| Feature-importance with direction + CIs | `ExplainabilityEngine` has rank/direction/relative %; add resampling CI |
| Drift timeline (PSI/KS per feature over versions) | needs persisted drift snapshots |
| Evidence graph (interactive) | `EvidenceEngine` must emit richer node/edge types (dataset → question → hypothesis → test → model → finding) |

---

## 8. M0 — Bug-Fix Plan (proposed, smallest safe change set)

Order chosen so each step is independently shippable and testable. All changes additive; nothing deletes user data, routes, or styling.

**M0.1 — Stop the stale-id dead end (Bug 1 / part of Bug 4).**
- `AutoMlPage.jsx`, `ReportsPage.jsx`, `VisualizationsPage.jsx`, `DatasetDetailPage.jsx`: when the id from props/localStorage is absent from `listDatasets()` (or its `getDataset` 404s), fall back to the first owned dataset, update the URL/context, and show a dismissible notice instead of an error banner.
- `WorkflowContext.jsx`: validate `autoscientist_dataset_id` against the fetched list on load; clear it if invalid.
- Accept "no datasets" with an explicit empty state ("Upload a CSV to begin") rather than a disabled form.

**M0.2 — Single source of truth for XAI (Bug 2).**
- `WorkflowService.java`: stop deriving `xai.completed` from `mlDone`; compute it from "an AutoML result exists **and** exposes non-empty `featureImportances`".
- `AutoMlService.getLatestBenchmark`: persist (or recompute) feature importances instead of serving an empty list when the cache is cold, so post-restart state is truthful.
- Frontend: make `RecommendedNextStep`/`PrerequisiteAlert`/`AutoMlPage` gate use the same `GET /automl/latest` non-null + non-empty-importances predicate.

**M0.3 — Identifier handling (Bug 3).**
- `DatasetProfiler`: broaden name patterns (word-boundary aware: `id`, `_id`, `no`, `num`, `code`, `zip`, `phone`, `ssn`, `email`, `account`, `invoice`, `sku`, `uuid`), relax the `uniqueCount >= 20` gate, and treat fully-unique/near-unique integer columns as IDENTIFIER; **skip numeric stat computation** for IDENTIFIER.
- `EdaEngine.java:101,134`: delete the dead conjunctive filter (rely on a single clear type predicate).
- Add `POST /datasets/{id}/reprofile` (additive) and call it from the dataset detail UI for legacy rows.
- Make `QuestionAnalysisEngine` fallbacks and `DatasetDetailPage` default variable picks identifier-aware; de-duplicate the frontend `isIdentifierColumn` heuristic against a single shared helper fed by backend types.
- Extend `DatasetProfilerTest` with the failing cases (numeric id with duplicates, `zip_code`, 500-row low-cardinality id).

**M0.4 — Report/PDF reliability (Bug 4).**
- `ReportGeneratorService`: strip/convert markdown markers when rendering PDF; replace `★`/`↔`/`→`/`≥`/`²`/`•` with ASCII-safe equivalents (or embed a Unicode TTF); render tables as real PDF tables; fill Fairness/Drift sections from real data when present, otherwise state "not run" with reason.
- Add HTTP-level integration tests: generate → list → get → pdf, plus a cross-user 403/404 ownership test.
- Ensure report generation surfaces a clear error (not a silent 200) when the backing CSV is missing.

**M0.5 — Error taxonomy (quick win).**
- `GlobalExceptionHandler`: map missing/invalid request params and validation errors to **400**, keep 500 for genuine server faults.

Acceptance criteria for M0 (all must be observable):
1. With `localStorage` pointing at a deleted dataset id, the ML page auto-recovers to a valid dataset and trains successfully (no "empty target" dead end).
2. Immediately after ML training, XAI is **not** reported complete until importances exist; after a backend restart the UI either shows real importances or clearly states "re-run training to explain".
3. Identifier-like columns (`customer_id`, `zip_code`, `order_no`, numeric ids with duplicates) never appear in correlation/boxplot/feature-importance outputs and show no numeric stats.
4. PDF download contains no literal `**`/backticks/`$H_0$`, contains a visible "BEST MODEL" marker, and renders tables aligned; report endpoints have integration tests.
5. `./mvnw -q test` (backend, ≥26 tests) and `npm run build` (frontend) still pass.

Risk notes: Bug 3 changes can reclassify live columns — mitigate by making type changes additive/idempotent and re-runnable, and by keeping the raw stored values untouched. Bug 2 changes could hide the XAI stage for users who never warmed the cache — mitigate by recomputing importances on demand rather than only reading the cache.

---

## 9. M1–M8 Roadmap (M1/M2/M4/M5/M6 grounded in repo evidence; M3/M7/M8 inferred — confirm labels)

Each stage: goal → key files → dependency/DB deltas → acceptance evidence.

**M1 — Public landing page + responsive nav.** `/` becomes a public marketing/home route (new `HomePage`, public route in `App.jsx`); import Bootstrap JS bundle (or migrate the toggle to React state) so the mobile hamburger works; keep authenticated routes unchanged; add `docs`-level note that only read-only/public endpoints may be used client-side (no `/api/public/**` today). Acceptance: hamburger toggles at <768px; logged-out visit to `/` shows the home page and `/dashboard` still redirects to `/login`.

**M2 — Excel upload.** Add `poi-ooxml`; extend `FileStorageService` to accept `.xlsx/.xls`; multi-sheet selection (default first sheet) with sheet metadata in the upload response; reuse the existing profiling pipeline (convert sheet → CSV stream) so downstream engines are untouched. DB: optional `sheet_name` column (additive). Acceptance: upload a 2-sheet workbook, confirm profile + EDA + AutoML on the chosen sheet; CSV path unchanged.

**M3 — Data understanding & quality depth (inferred).** Deeper profiling (types, ranges, outliers, missingness patterns), quality issues with actionable explanations, and the re-profile flow from M0.3 generalised. Additive endpoints only.

**M4 — Experiment versions & comparison.** Fix `versionNumber` to increment per (dataset, target, problemType); add a comparison endpoint returning aligned metrics across versions; promote `ExperimentsPage` into the nav with a comparison view. DB: index on `(dataset_id, version_number)`.

**M5 — Interactive evidence graph.** Extend `EvidenceEngine` to emit dataset → question → hypothesis → test → model → finding nodes/edges; render with an interactive graph component on `FindingsPage` (replace flat cards). No DB change required if graph is derived on read.

**M6 — Ask-Dataset (natural language).** Upgrade the keyword-intent engine to a typed intent + parameter extraction layer with citations to the underlying rows/statistics; every answer must link to its evidence (zero-hallucination tenet). Add `AskDatasetEngine` tests for paraphrase coverage.

**M7 — Model evaluation depth (inferred).** ROC/PR curves, calibration, cross-validation, per-class metrics, learning curves; surface the class-imbalance warning already implied by the 0.000 macro-F1 seen in the smoke test. Requires new engine outputs (not just DTOs).

**M8 — Performance & production hardening (inferred).** Parse-and-cache dataset reader; eliminate N+1 dashboard queries and the fabricated `+3` model count; bound/evict `MODEL_CACHE` (or move to persisted artifacts); async report/PDF generation; durable dataset storage decision (object store vs DB blobs); make production schema strategy explicit (`ddl-auto=validate` + versioned migrations, since `database/migrations/` is currently empty). Acceptance: p95 latencies before/after for upload, EDA, train, report; multi-instance smoke test.

---

## 10. Open Questions

1. Were the reported screenshots taken from a build that already included the `FINAL_AUDIT.md` fixes? (Behaviour differs from what we observe on a clean run.)
2. What is the actual production deployment + storage for `uploads/datasets`, and is it durable across deploys/restarts?
3. Does production run MySQL with `ddl-auto=update` or a hand-applied `database/schema.sql`? (No Flyway today; `database/migrations/` is empty.)
4. Confirm the intended M1–M8 stage names/scopes (M3/M7/M8 above are inferred).
5. Preferred PDF strategy: bundle a Unicode TTF font, or sanitize all non-Latin-1 glyphs to ASCII?
6. May M0 add integration tests plus the additive `/datasets/{id}/reprofile` endpoint, and is a new Maven dependency (POI) acceptable at M2?

---

## 11. Implementation outcome (session 3, 2026-10-19)

All roadmap stages were implemented and verified without destructive operations (no DB reset, no dataset deletion, no history rewrite). Evidence in `docs/IMPLEMENTATION_PROGRESS.md` and the app itself.

| Stage | Status | What shipped |
|---|---|---|
| M0 bug fixes | done | Error taxonomy (validation/params → 400); stale-id fallback; identifier policy centralized; truthful workflow/XAI; real PDF tables + embedded DejaVu font; report sections fill from stored evidence or state "not run" with reason |
| M1 public page/nav | done | Public `LandingPage` at `/`; React-driven hamburger; sidebar layout; Bootstrap JS dependency removed |
| M2 Excel | done | POI `.xlsx/.xls`, multi-sheet preview/selection, canonical CSV conversion; CSV path unchanged; test added |
| M3 quality depth | done | Re-profile + exclusions (version bump), identifier-safe stats, quality issues surfaced, EDA depth endpoints |
| M4 versions/comparison | done | Version increments per experiment, dataset version/seed/engine version persisted, comparison endpoint + UI |
| M5 evidence graph | done | Dataset→question→hypothesis→test→model→finding graph, interactive SVG panel, reliability scorecard |
| M6 Ask 2.0 | done | Typed intents, parameter extraction, normalized tokens, charts + citations, explicit causal refusal, paraphrase tests |
| M7 evaluation depth | done | ROC/PR, confusion matrix, per-class metrics, CV, calibration + Brier, residuals; UI panels; report diagnostics |
| M8 performance/prod | partial | EDA result cache (bounded LRU), repository `@EntityGraph` N+1 fixes, DB-derived dashboard; **remaining:** bound/relocate AutoML `MODEL_CACHE`, async reports, Flyway/prod schema strategy, durable upload storage decision |

Verification at completion: `./mvnw test` → 40/40; `npm run build` → 607.23 kB; runtime smoke on :8099 covering ingest, EDA, Ask, AutoML, versions/comparison, evidence, reports (PDF/CSV/XLSX) all 200 with expected payloads and magic bytes. The Open Questions in §10 that concerned *behaviour* are resolved; deployment/storage decisions remain operational choices.
