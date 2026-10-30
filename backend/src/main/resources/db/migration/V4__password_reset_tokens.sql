-- ============================================================================
-- V4__password_reset_tokens.sql
--
-- Single-use, time-limited password reset tokens. Only the SHA-256 hash of a
-- token is stored; the raw token exists solely in the delivered email link.
--
-- Additive and non-destructive.
-- ============================================================================

CREATE TABLE IF NOT EXISTS password_reset_tokens (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    used_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT uk_password_reset_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_password_reset_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);
