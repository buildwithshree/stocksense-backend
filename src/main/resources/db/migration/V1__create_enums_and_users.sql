-- V1__create_enums_and_users.sql
-- Enums defined at DB level for strict constraint enforcement

CREATE TYPE user_role AS ENUM ('USER', 'ADMIN');

CREATE TYPE audit_action AS ENUM (
    'USER_REGISTERED',
    'USER_LOGIN',
    'USER_LOGOUT',
    'TOKEN_REFRESHED',
    'PREDICTION_REQUESTED',
    'WATCHLIST_ADDED',
    'WATCHLIST_REMOVED',
    'BACKTEST_REQUESTED',
    'ADMIN_VIEWED_METRICS',
    'ADMIN_VIEWED_AUDIT_LOGS',
    'MODEL_TRAINED'
);

CREATE TYPE risk_label AS ENUM ('Low', 'Moderate', 'High', 'Very High');

CREATE TYPE model_name AS ENUM ('LinearRegression', 'Ridge', 'RandomForest', 'XGBoost', 'LSTM');

-- Users table
CREATE TABLE users (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email         VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    full_name     VARCHAR(100) NOT NULL,
    role          user_role    NOT NULL DEFAULT 'USER',
    is_active     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_users_email UNIQUE (email),
    CONSTRAINT chk_users_email CHECK (email ~* '^[^@\s]+@[^@\s]+\.[^@\s]+$')
);

CREATE INDEX idx_users_email ON users (email);
CREATE INDEX idx_users_role  ON users (role);

COMMENT ON TABLE  users               IS 'Registered user accounts';
COMMENT ON COLUMN users.password_hash IS 'BCrypt hashed password, never plain text';
COMMENT ON COLUMN users.role          IS 'USER = standard access, ADMIN = full metrics access';
