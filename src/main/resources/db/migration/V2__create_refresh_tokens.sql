-- V2__create_refresh_tokens.sql
-- Stores hashed refresh tokens for secure rotation strategy

CREATE TABLE refresh_tokens (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID        NOT NULL,
    token_hash  VARCHAR(64) NOT NULL,   -- SHA-256 hash of the raw token
    expires_at  TIMESTAMPTZ NOT NULL,
    is_revoked  BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_rt_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT uq_rt_token_hash UNIQUE (token_hash)
);

CREATE INDEX idx_rt_user_id    ON refresh_tokens (user_id);
CREATE INDEX idx_rt_token_hash ON refresh_tokens (token_hash);
CREATE INDEX idx_rt_expires_at ON refresh_tokens (expires_at)
    WHERE is_revoked = FALSE;

COMMENT ON TABLE  refresh_tokens            IS 'Hashed refresh tokens with expiry and revocation flag';
COMMENT ON COLUMN refresh_tokens.token_hash IS 'SHA-256 of the raw token sent to client. Raw token is never stored.';
