-- ============================================================================
-- V5__jobs_and_model_artifacts.sql
--
-- Introduces the two tables required by the asynchronous-job and model-serving
-- features:
--
--   * jobs             — bounded background job records (EDA, ASSOCIATION, AUTOML)
--   * model_artifacts  — serialized trained model payloads used for real inference
--
-- Portable DDL (valid on MySQL 8.0+ and on H2 in MODE=MySQL) because it is
-- exercised by the H2 baseline test. `jobs.id` is CHAR(36) rather than the
-- non-standard UUID type: MySQL has no UUID column type, and Hibernate maps the
-- entity's UUID to this column via @JdbcTypeCode(SqlTypes.CHAR).
--
-- Idempotent: IF NOT EXISTS makes this a no-op on databases where Hibernate
-- ddl-auto=update already created the tables. V6 widens payload to LONGBLOB on
-- legacy installs where it was created as BLOB; V7 adds any missing job-owner
-- columns on legacy job tables.
-- ============================================================================

CREATE TABLE IF NOT EXISTS jobs (
    id CHAR(36) PRIMARY KEY,
    type VARCHAR(255) NOT NULL,
    status VARCHAR(20) NOT NULL,
    progress INT NOT NULL,
    error_message TEXT,
    created_at TIMESTAMP NOT NULL,
    started_at TIMESTAMP NULL,
    completed_at TIMESTAMP NULL,
    transient_file_path VARCHAR(255),
    owner_id BIGINT NULL,
    dataset_id BIGINT NULL,
    target_column VARCHAR(255) NULL,
    problem_type VARCHAR(50) NULL,
    CONSTRAINT fk_jobs_owner FOREIGN KEY (owner_id) REFERENCES users (id)
);

CREATE TABLE IF NOT EXISTS model_artifacts (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    experiment_version_id BIGINT NOT NULL,
    model_name VARCHAR(255) NOT NULL,
    payload LONGBLOB NOT NULL,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_model_artifact_experiment FOREIGN KEY (experiment_version_id) REFERENCES experiment_versions (id)
);
