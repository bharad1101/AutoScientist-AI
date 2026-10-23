-- ============================================================================
-- V3__association_confidence_intervals.sql
--
-- Additive delta that stores the confidence intervals computed for every
-- statistical association so the UI can display uncertainty instead of a bare
-- point estimate.
--
-- Adds to `associations`:
--   ci_lower, ci_upper, ci_level, ci_method, ci_target, ci_unavailable_reason
--   effect_size_ci_lower, effect_size_ci_upper
--
-- NON-DESTRUCTIVE: only ADD COLUMN statements, guarded so they are a no-op
-- when the column already exists (e.g. databases maintained by Hibernate
-- ddl-auto=update). No data is dropped or rewritten.
--
-- MySQL-specific by design (information_schema + PREPARE). It runs only under
-- the mysql profile; dev/test H2 uses ddl-auto=create-drop and never executes
-- Flyway.
-- ============================================================================

-- 0) associations table may not exist on legacy databases --------------------
SET @assoc_tbl := (SELECT COUNT(*) FROM information_schema.TABLES
                   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'associations');

-- 1) ci_lower / ci_upper -----------------------------------------------------
SET @ddl := IF(@assoc_tbl = 1
    AND (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'associations' AND COLUMN_NAME = 'ci_lower') = 0,
    'ALTER TABLE associations ADD COLUMN ci_lower DOUBLE NULL',
    'SELECT ''skip: associations.ci_lower already present'' AS note');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl := IF(@assoc_tbl = 1
    AND (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'associations' AND COLUMN_NAME = 'ci_upper') = 0,
    'ALTER TABLE associations ADD COLUMN ci_upper DOUBLE NULL',
    'SELECT ''skip: associations.ci_upper already present'' AS note');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl := IF(@assoc_tbl = 1
    AND (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'associations' AND COLUMN_NAME = 'ci_level') = 0,
    'ALTER TABLE associations ADD COLUMN ci_level DOUBLE NULL',
    'SELECT ''skip: associations.ci_level already present'' AS note');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 2) ci_method / ci_target / ci_unavailable_reason --------------------------
SET @ddl := IF(@assoc_tbl = 1
    AND (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'associations' AND COLUMN_NAME = 'ci_method') = 0,
    'ALTER TABLE associations ADD COLUMN ci_method VARCHAR(120) NULL',
    'SELECT ''skip: associations.ci_method already present'' AS note');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl := IF(@assoc_tbl = 1
    AND (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'associations' AND COLUMN_NAME = 'ci_target') = 0,
    'ALTER TABLE associations ADD COLUMN ci_target VARCHAR(160) NULL',
    'SELECT ''skip: associations.ci_target already present'' AS note');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl := IF(@assoc_tbl = 1
    AND (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'associations' AND COLUMN_NAME = 'ci_unavailable_reason') = 0,
    'ALTER TABLE associations ADD COLUMN ci_unavailable_reason VARCHAR(400) NULL',
    'SELECT ''skip: associations.ci_unavailable_reason already present'' AS note');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 3) effect size interval ----------------------------------------------------
SET @ddl := IF(@assoc_tbl = 1
    AND (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'associations' AND COLUMN_NAME = 'effect_size_ci_lower') = 0,
    'ALTER TABLE associations ADD COLUMN effect_size_ci_lower DOUBLE NULL',
    'SELECT ''skip: associations.effect_size_ci_lower already present'' AS note');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl := IF(@assoc_tbl = 1
    AND (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'associations' AND COLUMN_NAME = 'effect_size_ci_upper') = 0,
    'ALTER TABLE associations ADD COLUMN effect_size_ci_upper DOUBLE NULL',
    'SELECT ''skip: associations.effect_size_ci_upper already present'' AS note');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 4) statistical_tests (hypothesis testing) --------------------------------
SET @st_tbl := (SELECT COUNT(*) FROM information_schema.TABLES
                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'statistical_tests');

SET @ddl := IF(@st_tbl = 1
    AND (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'statistical_tests' AND COLUMN_NAME = 'ci_lower') = 0,
    'ALTER TABLE statistical_tests ADD COLUMN ci_lower DOUBLE NULL',
    'SELECT ''skip: statistical_tests.ci_lower already present'' AS note');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl := IF(@st_tbl = 1
    AND (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'statistical_tests' AND COLUMN_NAME = 'ci_upper') = 0,
    'ALTER TABLE statistical_tests ADD COLUMN ci_upper DOUBLE NULL',
    'SELECT ''skip: statistical_tests.ci_upper already present'' AS note');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl := IF(@st_tbl = 1
    AND (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'statistical_tests' AND COLUMN_NAME = 'ci_level') = 0,
    'ALTER TABLE statistical_tests ADD COLUMN ci_level DOUBLE NULL',
    'SELECT ''skip: statistical_tests.ci_level already present'' AS note');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl := IF(@st_tbl = 1
    AND (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'statistical_tests' AND COLUMN_NAME = 'ci_method') = 0,
    'ALTER TABLE statistical_tests ADD COLUMN ci_method VARCHAR(120) NULL',
    'SELECT ''skip: statistical_tests.ci_method already present'' AS note');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl := IF(@st_tbl = 1
    AND (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'statistical_tests' AND COLUMN_NAME = 'ci_target') = 0,
    'ALTER TABLE statistical_tests ADD COLUMN ci_target VARCHAR(160) NULL',
    'SELECT ''skip: statistical_tests.ci_target already present'' AS note');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl := IF(@st_tbl = 1
    AND (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'statistical_tests' AND COLUMN_NAME = 'ci_unavailable_reason') = 0,
    'ALTER TABLE statistical_tests ADD COLUMN ci_unavailable_reason VARCHAR(400) NULL',
    'SELECT ''skip: statistical_tests.ci_unavailable_reason already present'' AS note');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
