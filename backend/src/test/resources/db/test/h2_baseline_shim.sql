-- H2-only shim for MysqlBaselineMigrationTest.
--
-- The H2 proxy caps Flyway at V1 because V2/V3/V6/V7 use MySQL-only conditional
-- DDL (information_schema + PREPARE). This script creates the tables that V4
-- and V5 would create on a real MySQL server so Hibernate ddl-auto=validate can
-- still verify every JPA entity mapping against the migrated baseline.
--
-- Foreign keys are intentionally omitted: this INIT script runs when the
-- connection is opened, before Flyway creates users/experiment_versions. The
-- real MySQL migrations declare and enforce those constraints.
--
-- Column names/types mirror V5__jobs_and_model_artifacts.sql so the entity
-- mapping is validated against the same shape (CHAR(36) UUID, LONGBLOB payload).

CREATE TABLE IF NOT EXISTS password_reset_tokens (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    used_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT uk_password_reset_token_hash UNIQUE (token_hash)
);

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
    problem_type VARCHAR(50) NULL
);

CREATE TABLE IF NOT EXISTS model_artifacts (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    experiment_version_id BIGINT NOT NULL,
    model_name VARCHAR(255) NOT NULL,
    payload LONGBLOB NOT NULL,
    created_at TIMESTAMP NOT NULL
);
