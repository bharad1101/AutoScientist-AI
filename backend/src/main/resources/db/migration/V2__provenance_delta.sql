-- ============================================================================
-- V2__provenance_delta.sql
--
-- Additive delta that brings databases created BEFORE Flyway (schema owned by
-- Hibernate ddl-auto=update, or hand-applied database/schema.sql) up to the
-- current entity model. Every statement is guarded so that it is a no-op when
-- the object already exists — this makes the migration safe on databases that
-- Hibernate has already updated, and idempotent under re-runs.
--
-- Adds:
--   1. datasets.source_stored_filename            (original Excel upload file)
--   2. analysis_runs                              (analysis provenance)
--   3. drift_snapshots + index                    (persisted drift evidence)
--   4. data_quality_reports.uq_quality_dataset    (one report per dataset)
--
-- NON-DESTRUCTIVE: nothing here drops tables, columns or rows. The unique
-- constraint in (4) is only added when no duplicate dataset_id rows exist; if
-- duplicates are present the constraint is skipped so no data is deleted —
-- operators can deduplicate manually and re-apply (see docs/DEPLOYMENT.md).
--
-- MySQL-specific by design (uses information_schema + PREPARE). It runs only
-- under the mysql profile; dev/test H2 uses ddl-auto=create-drop and never
-- executes Flyway. Validated against MySQL 8.0 (see docs/IMPLEMENTATION_PROGRESS.md).
-- ============================================================================

-- 1) datasets.source_stored_filename -----------------------------------------
SET @ddl := IF(
    (SELECT COUNT(*)
       FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'datasets'
        AND COLUMN_NAME = 'source_stored_filename') = 0,
    'ALTER TABLE datasets ADD COLUMN source_stored_filename VARCHAR(255) NULL',
    'SELECT ''skip: datasets.source_stored_filename already present'' AS note'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 2) analysis_runs ------------------------------------------------------------
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

-- 3) drift_snapshots ----------------------------------------------------------
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

SET @ddl := IF(
    (SELECT COUNT(*)
       FROM information_schema.STATISTICS
      WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'drift_snapshots'
        AND INDEX_NAME = 'idx_drift_snapshot_dataset') = 0,
    'CREATE INDEX idx_drift_snapshot_dataset ON drift_snapshots (dataset_id, created_at)',
    'SELECT ''skip: idx_drift_snapshot_dataset already present'' AS note'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 4) data_quality_reports unique constraint ------------------------------------
-- Legacy pre-1.0 databases may lack the table entirely; Hibernate ddl-auto=update
-- creates it later together with the constraint. MySQL resolves table references
-- at PREPARE time, so the table must never be named in a directly-executed
-- statement when it might be absent — every reference below is deferred into a
-- PREPARE'd string that is only executed when the table exists.
SET @dqr_exists := (
    SELECT COUNT(*)
      FROM information_schema.TABLES
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 'data_quality_reports');

SET @ddl := IF(@dqr_exists = 0,
    'SELECT ''skip: uq_quality_dataset (data_quality_reports table not present yet; Hibernate ddl-auto=update will create it with the constraint)'' AS note',
    'SET @dqr_duplicates := (SELECT COUNT(*) FROM (SELECT dataset_id FROM data_quality_reports GROUP BY dataset_id HAVING COUNT(*) > 1) d)');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl := IF(
    @dqr_exists = 1
    AND @dqr_duplicates = 0
    AND (SELECT COUNT(*)
           FROM information_schema.STATISTICS
          WHERE TABLE_SCHEMA = DATABASE()
            AND TABLE_NAME = 'data_quality_reports'
            AND NON_UNIQUE = 0
            AND COLUMN_NAME = 'dataset_id') = 0,
    'ALTER TABLE data_quality_reports ADD CONSTRAINT uq_quality_dataset UNIQUE (dataset_id)',
    'SELECT ''skip: uq_quality_dataset not added (table missing, constraint already present, or duplicate dataset_id rows exist — deduplicate manually then re-apply)'' AS note'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ============================================================================
-- 5) Legacy pre-1.0 schema upgrade (users / datasets / hypotheses)
--
-- Databases created before Flyway may carry an older, narrower schema than the
-- current entities. Hibernate ddl-auto=update adds *nullable* columns and
-- varchar columns, but it CANNOT add a `datetime NOT NULL` column to a table
-- that already has rows under MySQL strict mode (it tries to fill the existing
-- rows with '0000-00-00', which is rejected, and Hibernate only logs the error
-- and continues — leaving a half-migrated schema). This section performs those
-- additive changes safely and idempotently:
--   * add the column NULL, backfill existing rows, then tighten to NOT NULL;
--   * backfill user password hashes from the legacy `password` column
--     (same BCrypt format), so existing accounts can still sign in;
--   * backfill dataset name/version and hypothesis columns from their legacy
--     equivalents so no existing data is silently orphaned.
-- All statements are guarded and non-destructive; no column is dropped and no
-- row is deleted. Every block also checks that its table exists, because a
-- legacy database may predate one or more of these tables entirely (Hibernate
-- ddl-auto=update creates the missing ones afterwards).
-- ============================================================================

SET @users_tbl := (SELECT COUNT(*) FROM information_schema.TABLES
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'users');
SET @datasets_tbl := (SELECT COUNT(*) FROM information_schema.TABLES
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'datasets');
SET @hypotheses_tbl := (SELECT COUNT(*) FROM information_schema.TABLES
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'hypotheses');

-- 5.1) users.created_at -------------------------------------------------------
SET @ddl := IF(@users_tbl = 1
    AND (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'users' AND COLUMN_NAME = 'created_at') = 0,
    'ALTER TABLE users ADD COLUMN created_at datetime(6) NULL',
    'SELECT ''skip: users.created_at already present'' AS note');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl := IF(@users_tbl = 1,
    'UPDATE users SET created_at = NOW() WHERE created_at IS NULL',
    'SELECT ''skip: users table absent'' AS note');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl := IF(@users_tbl = 1
    AND (SELECT IS_NULLABLE FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'users' AND COLUMN_NAME = 'created_at') = 'YES',
    'ALTER TABLE users MODIFY COLUMN created_at datetime(6) NOT NULL',
    'SELECT ''skip: users.created_at already NOT NULL'' AS note');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 5.2) users.password_hash backfill from legacy `password` --------------------
-- Ensure the target column exists first: on a pristine legacy database
-- Hibernate has not run yet when this migration executes.
SET @ddl := IF(@users_tbl = 1
    AND (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'users' AND COLUMN_NAME = 'password_hash') = 0,
    'ALTER TABLE users ADD COLUMN password_hash VARCHAR(255) NULL',
    'SELECT ''skip: users.password_hash already present'' AS note');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- Only when a legacy `password` column exists and the target is empty, so a
-- database that already stores modern hashes is never overwritten.
SET @has_legacy_password := IF(@users_tbl = 1,
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'users' AND COLUMN_NAME = 'password'), 0);
SET @ddl := IF(@has_legacy_password = 1,
    'UPDATE users SET password_hash = password WHERE (password_hash IS NULL OR password_hash = '''')',
    'SELECT ''skip: no legacy users.password column'' AS note');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- The legacy `password` column is NOT NULL with no default; once its values
-- live in password_hash, new inserts (which only set password_hash) would fail
-- under MySQL strict mode. Drop the NOT NULL constraint but KEEP the column and
-- its data (non-destructive) so the old value remains available for audit.
SET @ddl := IF(@has_legacy_password = 1
    AND (SELECT IS_NULLABLE FROM information_schema.COLUMNS
          WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'users' AND COLUMN_NAME = 'password') = 'NO',
    'ALTER TABLE users MODIFY COLUMN password VARCHAR(255) NULL',
    'SELECT ''skip: users.password already nullable (or absent)'' AS note');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 5.3) datasets.created_at / name / version -----------------------------------
SET @ddl := IF(@datasets_tbl = 1
    AND (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'datasets' AND COLUMN_NAME = 'created_at') = 0,
    'ALTER TABLE datasets ADD COLUMN created_at datetime(6) NULL',
    'SELECT ''skip: datasets.created_at already present'' AS note');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- backfill from legacy uploaded_at where present, else now
SET @has_uploaded_at := IF(@datasets_tbl = 1,
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'datasets' AND COLUMN_NAME = 'uploaded_at'), 0);
SET @ddl := IF(@datasets_tbl = 1 AND @has_uploaded_at = 1,
    'UPDATE datasets SET created_at = COALESCE(created_at, uploaded_at, NOW()) WHERE created_at IS NULL',
    'SELECT ''skip: datasets.created_at backfill (table or uploaded_at absent)'' AS note');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl := IF(@datasets_tbl = 1 AND @has_uploaded_at = 0,
    'UPDATE datasets SET created_at = COALESCE(created_at, NOW()) WHERE created_at IS NULL',
    'SELECT ''skip: datasets.created_at backfill (uploaded_at path)'' AS note');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl := IF(@datasets_tbl = 1
    AND (SELECT IS_NULLABLE FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'datasets' AND COLUMN_NAME = 'created_at') = 'YES',
    'ALTER TABLE datasets MODIFY COLUMN created_at datetime(6) NOT NULL',
    'SELECT ''skip: datasets.created_at already NOT NULL'' AS note');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- name: current entity requires NOT NULL; Hibernate may have added it empty.
SET @ddl := IF(@datasets_tbl = 1
    AND (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'datasets' AND COLUMN_NAME = 'name') = 0,
    'ALTER TABLE datasets ADD COLUMN name VARCHAR(255) NULL',
    'SELECT ''skip: datasets.name already present'' AS note');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl := IF(@datasets_tbl = 1
    AND (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'datasets' AND COLUMN_NAME = 'version') = 0,
    'ALTER TABLE datasets ADD COLUMN version INT NULL',
    'SELECT ''skip: datasets.version already present'' AS note');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl := IF(@datasets_tbl = 1,
    'UPDATE datasets SET name = COALESCE(NULLIF(name, ''''), original_filename, CONCAT(''Dataset '', id)) WHERE name IS NULL OR name = ''''',
    'SELECT ''skip: datasets table absent'' AS note');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- version: current entity expects >= 1 (legacy rows defaulted to 0).
SET @ddl := IF(@datasets_tbl = 1,
    'UPDATE datasets SET version = 1 WHERE version IS NULL OR version = 0',
    'SELECT ''skip: datasets table absent'' AS note');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- Legacy-only NOT NULL columns that the current entity no longer writes
-- (e.g. datasets.file_path/file_type/uploaded_at, users.password) would reject
-- new inserts under strict mode once the constraint is unsatisfied. Relax each
-- to NULL but KEEP the column and its data. Enumerated explicitly (rather than
-- a cursor) to stay readable and reviewable; add new entries as the legacy
-- schema is understood further.

-- datasets.file_path
SET @ddl := IF(@datasets_tbl = 1
    AND (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'datasets'
        AND COLUMN_NAME = 'file_path' AND IS_NULLABLE = 'NO') = 1,
    'ALTER TABLE datasets MODIFY COLUMN file_path VARCHAR(1024) NULL',
    'SELECT ''skip: datasets.file_path already nullable (or absent)'' AS note');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- datasets.file_type
SET @ddl := IF(@datasets_tbl = 1
    AND (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'datasets'
        AND COLUMN_NAME = 'file_type' AND IS_NULLABLE = 'NO') = 1,
    'ALTER TABLE datasets MODIFY COLUMN file_type VARCHAR(255) NULL',
    'SELECT ''skip: datasets.file_type already nullable (or absent)'' AS note');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- datasets.uploaded_at
SET @ddl := IF(@datasets_tbl = 1
    AND (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'datasets'
        AND COLUMN_NAME = 'uploaded_at' AND IS_NULLABLE = 'NO') = 1,
    'ALTER TABLE datasets MODIFY COLUMN uploaded_at DATETIME(6) NULL',
    'SELECT ''skip: datasets.uploaded_at already nullable (or absent)'' AS note');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 5.4) hypotheses legacy columns ----------------------------------------------
-- current entity columns: null_hypothesis, alt_hypothesis, rationale,
-- suggested_test, var1 (NOT NULL), var2. Legacy schema used hypothesis_text /
-- alternative_hypothesis / variables. Ensure the current columns exist first.
SET @ddl := IF(@hypotheses_tbl = 1
    AND (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'hypotheses' AND COLUMN_NAME = 'alt_hypothesis') = 0,
    'ALTER TABLE hypotheses ADD COLUMN alt_hypothesis TEXT NULL',
    'SELECT ''skip: hypotheses.alt_hypothesis already present'' AS note');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl := IF(@hypotheses_tbl = 1
    AND (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'hypotheses' AND COLUMN_NAME = 'null_hypothesis') = 0,
    'ALTER TABLE hypotheses ADD COLUMN null_hypothesis TEXT NULL',
    'SELECT ''skip: hypotheses.null_hypothesis already present'' AS note');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl := IF(@hypotheses_tbl = 1
    AND (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'hypotheses' AND COLUMN_NAME = 'var1') = 0,
    'ALTER TABLE hypotheses ADD COLUMN var1 VARCHAR(255) NULL',
    'SELECT ''skip: hypotheses.var1 already present'' AS note');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has_alt_legacy := IF(@hypotheses_tbl = 1,
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'hypotheses' AND COLUMN_NAME = 'alternative_hypothesis'), 0);
SET @ddl := IF(@has_alt_legacy = 1,
    'UPDATE hypotheses SET alt_hypothesis = alternative_hypothesis WHERE (alt_hypothesis IS NULL OR alt_hypothesis = '''')',
    'SELECT ''skip: no legacy hypotheses.alternative_hypothesis column'' AS note');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has_hyp_text := IF(@hypotheses_tbl = 1,
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'hypotheses' AND COLUMN_NAME = 'hypothesis_text'), 0);
SET @ddl := IF(@has_hyp_text = 1,
    'UPDATE hypotheses SET null_hypothesis = hypothesis_text WHERE (null_hypothesis IS NULL OR null_hypothesis = '''')',
    'SELECT ''skip: no legacy hypotheses.hypothesis_text column'' AS note');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- avoid leaving var1 (NOT NULL in the entity) empty: parse legacy `variables`
SET @has_variables := IF(@hypotheses_tbl = 1,
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'hypotheses' AND COLUMN_NAME = 'variables'), 0);
SET @ddl := IF(@has_variables = 1,
    'UPDATE hypotheses SET var1 = COALESCE(NULLIF(var1, ''''), SUBSTRING_INDEX(variables, '','', 1), ''unspecified'') WHERE var1 IS NULL OR var1 = ''''',
    'SELECT ''skip: hypotheses.var1 backfill (no legacy variables)'' AS note');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl := IF(@hypotheses_tbl = 1 AND @has_variables = 0,
    'UPDATE hypotheses SET var1 = ''unspecified'' WHERE var1 IS NULL OR var1 = ''''',
    'SELECT ''skip: hypotheses.var1 default (variables path)'' AS note');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
