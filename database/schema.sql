-- AutoScientist AI Database Schema (MySQL 8.0+)
-- Database: autoscientist_db
--
-- This file is the human-readable REFERENCE schema, kept in sync with the JPA
-- entities in backend/src/main/java/com/autoscientist/entity/.
-- The authoritative, versioned migration path is Flyway:
--   backend/src/main/resources/db/migration/V1__baseline.sql       (full baseline)
--   backend/src/main/resources/db/migration/V2__provenance_delta.sql (additive delta)
-- See docs/DEPLOYMENT.md §3.1.

CREATE DATABASE IF NOT EXISTS autoscientist_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE autoscientist_db;

-- 1. Users Table
CREATE TABLE IF NOT EXISTS users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    email VARCHAR(150) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(50) NOT NULL DEFAULT 'ROLE_USER',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB;

-- 2. Datasets Table
CREATE TABLE IF NOT EXISTS datasets (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    name VARCHAR(255) NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    stored_filename VARCHAR(255) NOT NULL,
    source_stored_filename VARCHAR(255) NULL,
    file_size BIGINT NOT NULL,
    row_count INT NOT NULL DEFAULT 0,
    column_count INT NOT NULL DEFAULT 0,
    dataset_type VARCHAR(50) DEFAULT 'MIXED',
    description TEXT,
    source_format VARCHAR(16) DEFAULT 'CSV',
    sheet_name VARCHAR(128) NULL,
    header_row INT NULL,
    version INT NOT NULL DEFAULT 1,
    parent_dataset_id BIGINT NULL,
    excluded_columns_json TEXT NULL,
    import_options_json TEXT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_datasets_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- 3. Dataset Columns Metadata
CREATE TABLE IF NOT EXISTS dataset_columns (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    dataset_id BIGINT NOT NULL,
    column_index INT NOT NULL,
    column_name VARCHAR(255) NOT NULL,
    data_type VARCHAR(50) NOT NULL,
    type_confidence DOUBLE DEFAULT 1.0,
    missing_count INT DEFAULT 0,
    missing_pct DOUBLE DEFAULT 0.0,
    unique_count INT DEFAULT 0,
    min_val DOUBLE NULL,
    max_val DOUBLE NULL,
    mean_val DOUBLE NULL,
    median_val DOUBLE NULL,
    std_dev DOUBLE NULL,
    variance DOUBLE NULL,
    q1 DOUBLE NULL,
    q3 DOUBLE NULL,
    skewness DOUBLE NULL,
    categories_json LONGTEXT NULL,
    distribution_json LONGTEXT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_columns_dataset FOREIGN KEY (dataset_id) REFERENCES datasets(id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- 4. Data Quality Reports
CREATE TABLE IF NOT EXISTS data_quality_reports (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    dataset_id BIGINT NOT NULL,
    overall_score DOUBLE NOT NULL,
    missing_value_score DOUBLE NOT NULL,
    duplicate_score DOUBLE NOT NULL,
    outlier_score DOUBLE NOT NULL,
    consistency_score DOUBLE NOT NULL,
    total_issues INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_quality_dataset FOREIGN KEY (dataset_id) REFERENCES datasets(id) ON DELETE CASCADE,
    CONSTRAINT uq_quality_dataset UNIQUE (dataset_id)
) ENGINE=InnoDB;

-- 5. Data Quality Issues
CREATE TABLE IF NOT EXISTS data_quality_issues (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    report_id BIGINT NOT NULL,
    column_name VARCHAR(255) NULL,
    issue_type VARCHAR(100) NOT NULL,
    severity VARCHAR(20) NOT NULL, -- INFO, WARNING, ERROR
    description TEXT NOT NULL,
    recommendation TEXT NOT NULL,
    affected_rows INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_issues_report FOREIGN KEY (report_id) REFERENCES data_quality_reports(id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- 6. Associations Table
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
    degrees_of_freedom DOUBLE NULL,
    is_significant BOOLEAN NOT NULL DEFAULT FALSE,
    interpretation TEXT NOT NULL,
    rank_score DOUBLE NOT NULL DEFAULT 0.0,
    ci_lower DOUBLE NULL,
    ci_upper DOUBLE NULL,
    ci_level DOUBLE NULL,
    ci_method VARCHAR(120) NULL,
    ci_target VARCHAR(160) NULL,
    ci_unavailable_reason VARCHAR(400) NULL,
    effect_size_ci_lower DOUBLE NULL,
    effect_size_ci_upper DOUBLE NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_associations_dataset FOREIGN KEY (dataset_id) REFERENCES datasets(id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- 7. Research Questions
CREATE TABLE IF NOT EXISTS research_questions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    dataset_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    question_text TEXT NOT NULL,
    target_variable VARCHAR(255) NULL,
    predictor_variable VARCHAR(255) NULL,
    question_type VARCHAR(50) NOT NULL,
    suggested_test VARCHAR(100) NULL,
    status VARCHAR(50) DEFAULT 'ACTIVE',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_rq_dataset FOREIGN KEY (dataset_id) REFERENCES datasets(id) ON DELETE CASCADE,
    CONSTRAINT fk_rq_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- 8. Hypotheses
CREATE TABLE IF NOT EXISTS hypotheses (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    research_question_id BIGINT NOT NULL,
    null_hypothesis TEXT NOT NULL,
    alt_hypothesis TEXT NOT NULL,
    rationale TEXT NOT NULL,
    suggested_test VARCHAR(100) NOT NULL,
    var1 VARCHAR(255) NOT NULL,
    var2 VARCHAR(255) NULL,
    confidence DOUBLE DEFAULT 0.95,
    rank_order INT DEFAULT 1,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_hypotheses_rq FOREIGN KEY (research_question_id) REFERENCES research_questions(id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- 9. Statistical Tests
CREATE TABLE IF NOT EXISTS statistical_tests (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    hypothesis_id BIGINT NOT NULL,
    dataset_id BIGINT NOT NULL,
    test_type VARCHAR(100) NOT NULL,
    var1 VARCHAR(255) NOT NULL,
    var2 VARCHAR(255) NULL,
    statistic DOUBLE NOT NULL,
    p_value DOUBLE NOT NULL,
    alpha DOUBLE DEFAULT 0.05,
    degrees_of_freedom DOUBLE NULL,
    effect_size DOUBLE NOT NULL,
    effect_size_type VARCHAR(50) NOT NULL,
    sample_size INT NOT NULL,
    is_statistically_significant BOOLEAN NOT NULL DEFAULT FALSE,
    status VARCHAR(50) NOT NULL,
    interpretation LONGTEXT NOT NULL,
    ci_lower DOUBLE NULL,
    ci_upper DOUBLE NULL,
    ci_level DOUBLE NULL,
    ci_method VARCHAR(120) NULL,
    ci_target VARCHAR(160) NULL,
    ci_unavailable_reason VARCHAR(400) NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_stat_tests_hypo FOREIGN KEY (hypothesis_id) REFERENCES hypotheses(id) ON DELETE CASCADE,
    CONSTRAINT fk_stat_tests_dataset FOREIGN KEY (dataset_id) REFERENCES datasets(id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- 10. Experiments
CREATE TABLE IF NOT EXISTS experiments (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    dataset_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    research_question_id BIGINT NULL,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    target_column VARCHAR(255) NOT NULL,
    problem_type VARCHAR(50) NOT NULL, -- REGRESSION, CLASSIFICATION, CLUSTERING
    status VARCHAR(50) DEFAULT 'COMPLETED',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_experiments_dataset FOREIGN KEY (dataset_id) REFERENCES datasets(id) ON DELETE CASCADE,
    CONSTRAINT fk_experiments_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- 11. Experiment Versions
CREATE TABLE IF NOT EXISTS experiment_versions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    experiment_id BIGINT NOT NULL,
    version_number INT NOT NULL DEFAULT 1,
    preprocessing_json LONGTEXT NULL,
    feature_selection_json LONGTEXT NULL,
    models_summary_json LONGTEXT NULL,
    best_model_name VARCHAR(100) NULL,
    best_metric_value DOUBLE NULL,
    dataset_version INT NULL,
    random_seed BIGINT NULL,
    engine_version VARCHAR(64) NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_exp_versions_exp FOREIGN KEY (experiment_id) REFERENCES experiments(id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- 12. ML Models
CREATE TABLE IF NOT EXISTS models (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    experiment_version_id BIGINT NOT NULL,
    model_name VARCHAR(100) NOT NULL,
    algorithm VARCHAR(100) NOT NULL,
    hyperparameters_json LONGTEXT NULL,
    train_metrics_json LONGTEXT NULL,
    val_metrics_json LONGTEXT NULL,
    test_metrics_json LONGTEXT NULL,
    is_best_model BOOLEAN DEFAULT FALSE,
    fairness_metrics_json LONGTEXT NULL,
    feature_importances_json LONGTEXT NULL,
    evaluation_json LONGTEXT NULL,
    random_seed BIGINT NULL,
    train_rows INT NULL,
    val_rows INT NULL,
    test_rows INT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_models_version FOREIGN KEY (experiment_version_id) REFERENCES experiment_versions(id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- 13. Findings
CREATE TABLE IF NOT EXISTS findings (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    dataset_id BIGINT NOT NULL,
    research_question_id BIGINT NULL,
    hypothesis_id BIGINT NULL,
    statistical_test_id BIGINT NULL,
    model_id BIGINT NULL,
    title VARCHAR(255) NOT NULL,
    summary LONGTEXT NOT NULL,
    evidence_text LONGTEXT NOT NULL,
    confidence_level VARCHAR(50) NOT NULL,
    causality_disclaimer LONGTEXT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_findings_dataset FOREIGN KEY (dataset_id) REFERENCES datasets(id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- 14. Evidence Nodes (Provenance Graph)
CREATE TABLE IF NOT EXISTS evidence_nodes (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    finding_id BIGINT NOT NULL,
    node_type VARCHAR(100) NOT NULL, -- DATASET, STAT_TEST, ML_MODEL, FEATURE
    reference_id BIGINT NULL,
    description TEXT NOT NULL,
    data_payload_json LONGTEXT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_evidence_finding FOREIGN KEY (finding_id) REFERENCES findings(id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- 15. Scientific Reports
CREATE TABLE IF NOT EXISTS reports (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    dataset_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    research_question_id BIGINT NULL,
    title VARCHAR(255) NOT NULL,
    summary TEXT,
    markdown_content LONGTEXT NOT NULL,
    pdf_filename VARCHAR(255) NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_reports_dataset FOREIGN KEY (dataset_id) REFERENCES datasets(id) ON DELETE CASCADE,
    CONSTRAINT fk_reports_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- 16. Analysis Runs (provenance of every analysis operation actually executed)
CREATE TABLE IF NOT EXISTS analysis_runs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    dataset_id BIGINT NOT NULL,
    user_id BIGINT NULL,
    operation VARCHAR(64) NOT NULL,
    params_hash VARCHAR(64) NOT NULL,
    summary TEXT NULL,
    payload_json LONGTEXT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'COMPLETED',
    duration_ms BIGINT NULL,
    run_count BIGINT NOT NULL DEFAULT 1,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_analysis_runs_dataset FOREIGN KEY (dataset_id) REFERENCES datasets(id) ON DELETE CASCADE,
    CONSTRAINT fk_analysis_runs_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL,
    CONSTRAINT uk_analysis_run_key UNIQUE (dataset_id, operation, params_hash)
) ENGINE=InnoDB;

-- 17. Drift Snapshots (persisted drift-comparison evidence)
CREATE TABLE IF NOT EXISTS drift_snapshots (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    dataset_id BIGINT NOT NULL,
    reference_dataset_id BIGINT NULL,
    reference_dataset_name VARCHAR(255) NULL,
    overall_status VARCHAR(32) NULL,
    overall_drift_index DOUBLE NULL,
    report_json LONGTEXT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_drift_dataset FOREIGN KEY (dataset_id) REFERENCES datasets(id) ON DELETE CASCADE,
    INDEX idx_drift_snapshot_dataset (dataset_id, created_at)
) ENGINE=InnoDB;

-- 18. Password Reset Tokens (single-use, hashed tokens)
CREATE TABLE IF NOT EXISTS password_reset_tokens (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    used_at TIMESTAMP NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_password_reset_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_password_reset_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB;
