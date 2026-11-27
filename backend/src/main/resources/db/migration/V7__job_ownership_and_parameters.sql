-- ============================================================================
-- V7__job_ownership_and_parameters.sql
--
-- Adds the job ownership / parameter columns to a PRE-EXISTING jobs table.
--
-- V5 creates the final shape on fresh installs and on legacy installs where the
-- jobs table is absent. This migration exists for legacy databases whose jobs
-- table was created by Hibernate ddl-auto=update before ownership was tracked;
-- there the columns may be absent (or partially present) and the table already
-- contains rows.
--
-- Every statement is guarded against information_schema so re-running on a
-- database that already received the columns (the fresh path) is a no-op.
-- MySQL-specific by design; H2 tests cap the Flyway target at V1 and execute
-- the portable V5 DDL separately (see FlywayMigrationTest and
-- MysqlBaselineMigrationTest). V2, V3, V6 and V7 are validated on disposable
-- MySQL only.
-- ============================================================================

SET @jobs_tbl := (SELECT COUNT(*) FROM information_schema.TABLES
                  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'jobs');

-- owner_id -------------------------------------------------------------------
SET @ddl := IF(@jobs_tbl = 1
    AND (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'jobs' AND COLUMN_NAME = 'owner_id') = 0,
    'ALTER TABLE jobs ADD COLUMN owner_id BIGINT NULL',
    'SELECT ''skip: jobs.owner_id already present (or jobs table absent)'' AS note');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- dataset_id -----------------------------------------------------------------
SET @ddl := IF(@jobs_tbl = 1
    AND (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'jobs' AND COLUMN_NAME = 'dataset_id') = 0,
    'ALTER TABLE jobs ADD COLUMN dataset_id BIGINT NULL',
    'SELECT ''skip: jobs.dataset_id already present (or jobs table absent)'' AS note');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- target_column --------------------------------------------------------------
SET @ddl := IF(@jobs_tbl = 1
    AND (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'jobs' AND COLUMN_NAME = 'target_column') = 0,
    'ALTER TABLE jobs ADD COLUMN target_column VARCHAR(255) NULL',
    'SELECT ''skip: jobs.target_column already present (or jobs table absent)'' AS note');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- problem_type ---------------------------------------------------------------
SET @ddl := IF(@jobs_tbl = 1
    AND (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'jobs' AND COLUMN_NAME = 'problem_type') = 0,
    'ALTER TABLE jobs ADD COLUMN problem_type VARCHAR(50) NULL',
    'SELECT ''skip: jobs.problem_type already present (or jobs table absent)'' AS note');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- fk_jobs_owner --------------------------------------------------------------
SET @ddl := IF(@jobs_tbl = 1
    AND (SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'jobs'
        AND CONSTRAINT_NAME = 'fk_jobs_owner' AND CONSTRAINT_TYPE = 'FOREIGN KEY') = 0
    AND (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'jobs' AND COLUMN_NAME = 'owner_id') = 1,
    'ALTER TABLE jobs ADD CONSTRAINT fk_jobs_owner FOREIGN KEY (owner_id) REFERENCES users (id)',
    'SELECT ''skip: fk_jobs_owner already present (or owner_id/jobs absent)'' AS note');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
