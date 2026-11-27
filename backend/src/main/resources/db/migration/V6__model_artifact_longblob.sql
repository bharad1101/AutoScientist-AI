-- ============================================================================
-- V6__model_artifact_longblob.sql
--
-- Trained model payloads (gzip-compressed Java serialization) can exceed the
-- 64 KiB limit of BLOB. Widen legacy BLOB columns to LONGBLOB so larger tree
-- ensembles remain servable. MODIFY COLUMN preserves existing rows.
--
-- MySQL-specific by design: information_schema + PREPARE is not portable to
-- H2, which is why the H2 migration tests cap Flyway at V1 and apply the
-- portable V5 DDL separately. Guarded so a database without model_artifacts
-- (for example a partially migrated legacy install) is a no-op instead of a
-- failed, non-transactional MySQL DDL statement.
-- ============================================================================

SET @artifacts_tbl := (SELECT COUNT(*) FROM information_schema.TABLES
                       WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'model_artifacts');

SET @payload_not_longblob := (SELECT COUNT(*) FROM information_schema.COLUMNS
                              WHERE TABLE_SCHEMA = DATABASE()
                                AND TABLE_NAME = 'model_artifacts'
                                AND COLUMN_NAME = 'payload'
                                AND COLUMN_TYPE <> 'longblob');

SET @ddl := IF(@artifacts_tbl = 1 AND @payload_not_longblob > 0,
    'ALTER TABLE model_artifacts MODIFY COLUMN payload LONGBLOB NOT NULL',
    'SELECT ''skip: model_artifacts.payload absent or already LONGBLOB'' AS note');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
