-- ============================================================================
-- V1__baseline.sql
--
-- Complete AutoScientist AI baseline schema (all 17 tables, current entities).
--
-- Purpose:
--   * Fresh installations: applied on an empty database and yields the full,
--     current schema in one step.
--   * Existing installations (created before Flyway was introduced): these are
--     baselined at version 1 via `spring.flyway.baseline-on-migrate=true`, so
--     this file is NOT executed against them; the additive delta in
--     V2__provenance_delta.sql brings them up to date.
--
-- Design rules (all migrations in this directory):
--   * Additive and non-destructive: never DROP, never rewrite existing rows.
--   * No data statements; DDL only.
--   * Portable DDL subset: valid on MySQL 8.0+ (server-default engine/charset,
--     create the database with utf8mb4 — see database/schema.sql) and on H2 in
--     MODE=MySQL (used by MysqlBaselineMigrationTest as a CI-safe proxy).
--     MySQL-specific decorations (ENGINE=, DEFAULT CHARSET=) are intentionally
--     omitted here; the human-readable reference with them lives in
--     database/schema.sql.
-- ============================================================================

-- 1. Users
CREATE TABLE IF NOT EXISTS users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    email VARCHAR(150) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(50) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP,
    CONSTRAINT uk_users_email UNIQUE (email)
);

-- 2. Datasets
CREATE TABLE IF NOT EXISTS datasets (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    name VARCHAR(255) NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    stored_filename VARCHAR(255) NOT NULL,
    source_stored_filename VARCHAR(255),
    file_size BIGINT NOT NULL,
    row_count INT NOT NULL,
    column_count INT NOT NULL,
    dataset_type VARCHAR(50),
    description TEXT,
    source_format VARCHAR(16),
    sheet_name VARCHAR(128),
    header_row INT,
    version INT NOT NULL,
    parent_dataset_id BIGINT,
    excluded_columns_json TEXT,
    import_options_json TEXT,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP,
    CONSTRAINT fk_datasets_user FOREIGN KEY (user_id) REFERENCES users (id)
);

-- 3. Dataset column metadata
CREATE TABLE IF NOT EXISTS dataset_columns (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    dataset_id BIGINT NOT NULL,
    column_index INT NOT NULL,
    column_name VARCHAR(255) NOT NULL,
    data_type VARCHAR(50) NOT NULL,
    type_confidence DOUBLE,
    missing_count INT,
    missing_pct DOUBLE,
    unique_count INT,
    min_val DOUBLE,
    max_val DOUBLE,
    mean_val DOUBLE,
    median_val DOUBLE,
    std_dev DOUBLE,
    variance DOUBLE,
    q1 DOUBLE,
    q3 DOUBLE,
    skewness DOUBLE,
    categories_json LONGTEXT,
    distribution_json LONGTEXT,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_columns_dataset FOREIGN KEY (dataset_id) REFERENCES datasets (id)
);

-- 4. Data quality reports (one per dataset)
CREATE TABLE IF NOT EXISTS data_quality_reports (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    dataset_id BIGINT NOT NULL,
    overall_score DOUBLE NOT NULL,
    missing_value_score DOUBLE NOT NULL,
    duplicate_score DOUBLE NOT NULL,
    outlier_score DOUBLE NOT NULL,
    consistency_score DOUBLE NOT NULL,
    total_issues INT NOT NULL,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_quality_dataset FOREIGN KEY (dataset_id) REFERENCES datasets (id),
    CONSTRAINT uq_quality_dataset UNIQUE (dataset_id)
);

-- 5. Data quality issues
CREATE TABLE IF NOT EXISTS data_quality_issues (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    report_id BIGINT NOT NULL,
    column_name VARCHAR(255),
    issue_type VARCHAR(100) NOT NULL,
    severity VARCHAR(20) NOT NULL,
    description TEXT NOT NULL,
    recommendation TEXT NOT NULL,
    affected_rows INT,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_issues_report FOREIGN KEY (report_id) REFERENCES data_quality_reports (id)
);

-- 6. Associations
CREATE TABLE IF NOT EXISTS associations (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    dataset_id BIGINT NOT NULL,
    var1 VARCHAR(255) NOT NULL,
    var2 VARCHAR(255) NOT NULL,
    var1_type VARCHAR(50) NOT NULL,
    var2_type VARCHAR(50) NOT NULL,
    test_type VARCHAR(50) NOT NULL,
    statistic DOUBLE NOT NULL,
    p_value DOUBLE NOT NULL,
    effect_size DOUBLE NOT NULL,
    sample_size INT NOT NULL,
    degrees_of_freedom DOUBLE,
    is_significant BOOLEAN NOT NULL,
    interpretation TEXT NOT NULL,
    rank_score DOUBLE NOT NULL,
    ci_lower DOUBLE,
    ci_upper DOUBLE,
    ci_level DOUBLE,
    ci_method VARCHAR(120),
    ci_target VARCHAR(160),
    ci_unavailable_reason VARCHAR(400),
    effect_size_ci_lower DOUBLE,
    effect_size_ci_upper DOUBLE,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_associations_dataset FOREIGN KEY (dataset_id) REFERENCES datasets (id)
);

-- 7. Research questions
CREATE TABLE IF NOT EXISTS research_questions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    dataset_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    question_text TEXT NOT NULL,
    target_variable VARCHAR(255),
    predictor_variable VARCHAR(255),
    question_type VARCHAR(50) NOT NULL,
    suggested_test VARCHAR(100),
    status VARCHAR(50),
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_rq_dataset FOREIGN KEY (dataset_id) REFERENCES datasets (id),
    CONSTRAINT fk_rq_user FOREIGN KEY (user_id) REFERENCES users (id)
);

-- 8. Hypotheses
CREATE TABLE IF NOT EXISTS hypotheses (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    research_question_id BIGINT NOT NULL,
    null_hypothesis TEXT NOT NULL,
    alt_hypothesis TEXT NOT NULL,
    rationale TEXT NOT NULL,
    suggested_test VARCHAR(100) NOT NULL,
    var1 VARCHAR(255) NOT NULL,
    var2 VARCHAR(255),
    confidence DOUBLE,
    rank_order INT,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_hypotheses_rq FOREIGN KEY (research_question_id) REFERENCES research_questions (id)
);

-- 9. Statistical tests
CREATE TABLE IF NOT EXISTS statistical_tests (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    hypothesis_id BIGINT NOT NULL,
    dataset_id BIGINT NOT NULL,
    test_type VARCHAR(100) NOT NULL,
    var1 VARCHAR(255) NOT NULL,
    var2 VARCHAR(255),
    statistic DOUBLE NOT NULL,
    p_value DOUBLE NOT NULL,
    alpha DOUBLE,
    degrees_of_freedom DOUBLE,
    effect_size DOUBLE NOT NULL,
    effect_size_type VARCHAR(50) NOT NULL,
    sample_size INT NOT NULL,
    is_statistically_significant BOOLEAN NOT NULL,
    status VARCHAR(50) NOT NULL,
    interpretation LONGTEXT NOT NULL,
    ci_lower DOUBLE,
    ci_upper DOUBLE,
    ci_level DOUBLE,
    ci_method VARCHAR(120),
    ci_target VARCHAR(160),
    ci_unavailable_reason VARCHAR(400),
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_stat_tests_hypo FOREIGN KEY (hypothesis_id) REFERENCES hypotheses (id),
    CONSTRAINT fk_stat_tests_dataset FOREIGN KEY (dataset_id) REFERENCES datasets (id)
);

-- 10. Experiments
CREATE TABLE IF NOT EXISTS experiments (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    dataset_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    research_question_id BIGINT,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    target_column VARCHAR(255) NOT NULL,
    problem_type VARCHAR(50) NOT NULL,
    status VARCHAR(50),
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_experiments_dataset FOREIGN KEY (dataset_id) REFERENCES datasets (id),
    CONSTRAINT fk_experiments_user FOREIGN KEY (user_id) REFERENCES users (id)
);

-- 11. Experiment versions
CREATE TABLE IF NOT EXISTS experiment_versions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    experiment_id BIGINT NOT NULL,
    version_number INT NOT NULL,
    preprocessing_json LONGTEXT,
    feature_selection_json LONGTEXT,
    models_summary_json LONGTEXT,
    best_model_name VARCHAR(100),
    best_metric_value DOUBLE,
    dataset_version INT,
    random_seed BIGINT,
    engine_version VARCHAR(64),
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_exp_versions_exp FOREIGN KEY (experiment_id) REFERENCES experiments (id)
);

-- 12. ML models
CREATE TABLE IF NOT EXISTS models (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    experiment_version_id BIGINT NOT NULL,
    model_name VARCHAR(100) NOT NULL,
    algorithm VARCHAR(100) NOT NULL,
    hyperparameters_json LONGTEXT,
    train_metrics_json LONGTEXT,
    val_metrics_json LONGTEXT,
    test_metrics_json LONGTEXT,
    is_best_model BOOLEAN,
    fairness_metrics_json LONGTEXT,
    feature_importances_json LONGTEXT,
    evaluation_json LONGTEXT,
    random_seed BIGINT,
    train_rows INT,
    val_rows INT,
    test_rows INT,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_models_version FOREIGN KEY (experiment_version_id) REFERENCES experiment_versions (id)
);

-- 13. Findings
CREATE TABLE IF NOT EXISTS findings (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    dataset_id BIGINT NOT NULL,
    research_question_id BIGINT,
    hypothesis_id BIGINT,
    statistical_test_id BIGINT,
    model_id BIGINT,
    title VARCHAR(255) NOT NULL,
    summary LONGTEXT NOT NULL,
    evidence_text LONGTEXT NOT NULL,
    confidence_level VARCHAR(50) NOT NULL,
    causality_disclaimer LONGTEXT NOT NULL,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_findings_dataset FOREIGN KEY (dataset_id) REFERENCES datasets (id)
);

-- 14. Evidence nodes (provenance graph)
CREATE TABLE IF NOT EXISTS evidence_nodes (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    finding_id BIGINT NOT NULL,
    node_type VARCHAR(100) NOT NULL,
    reference_id BIGINT,
    description TEXT NOT NULL,
    data_payload_json LONGTEXT,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_evidence_finding FOREIGN KEY (finding_id) REFERENCES findings (id)
);

-- 15. Scientific reports
CREATE TABLE IF NOT EXISTS reports (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    dataset_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    research_question_id BIGINT,
    title VARCHAR(255) NOT NULL,
    summary TEXT,
    markdown_content LONGTEXT NOT NULL,
    pdf_filename VARCHAR(255),
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_reports_dataset FOREIGN KEY (dataset_id) REFERENCES datasets (id),
    CONSTRAINT fk_reports_user FOREIGN KEY (user_id) REFERENCES users (id)
);

-- 16. Analysis runs (provenance of every analysis operation actually executed)
CREATE TABLE IF NOT EXISTS analysis_runs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    dataset_id BIGINT NOT NULL,
    user_id BIGINT,
    operation VARCHAR(64) NOT NULL,
    params_hash VARCHAR(64) NOT NULL,
    summary TEXT,
    payload_json LONGTEXT,
    status VARCHAR(16) NOT NULL,
    duration_ms BIGINT,
    run_count BIGINT,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP,
    CONSTRAINT fk_analysis_runs_dataset FOREIGN KEY (dataset_id) REFERENCES datasets (id),
    CONSTRAINT fk_analysis_runs_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT uk_analysis_run_key UNIQUE (dataset_id, operation, params_hash)
);

-- 17. Drift snapshots (persisted drift-comparison evidence)
CREATE TABLE IF NOT EXISTS drift_snapshots (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    dataset_id BIGINT NOT NULL,
    reference_dataset_id BIGINT,
    reference_dataset_name VARCHAR(255),
    overall_status VARCHAR(32),
    overall_drift_index DOUBLE,
    report_json LONGTEXT NOT NULL,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_drift_dataset FOREIGN KEY (dataset_id) REFERENCES datasets (id)
);

-- NOTE: the idx_drift_snapshot_dataset index is intentionally NOT created here.
-- MySQL does not support `CREATE INDEX IF NOT EXISTS` (H2 does, which let the
-- H2-based proxy test pass while real MySQL failed with error 1064). The index
-- is created by V2__provenance_delta.sql using an information_schema-guarded
-- PREPARE, which runs on both the fresh-install path (V1 -> V2) and the
-- legacy-baseline path (baseline@1 -> V2).
