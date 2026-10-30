-- H2-only shim for MysqlBaselineMigrationTest.
-- That test pins Flyway to V1 to keep MySQL-only V2/V3 out of H2; the
-- password_reset_tokens table introduced by V4 is created here so Hibernate
-- schema validation can still check the entity mapping. The real V4 script is
-- exercised against MySQL (see docs/DEPLOYMENT.md).
CREATE TABLE IF NOT EXISTS password_reset_tokens (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    used_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT uk_password_reset_token_hash UNIQUE (token_hash)
);
