-- V6__create_model_artifacts.sql
-- Persists trained model + scaler (joblib-serialized) so models survive
-- Render free-tier restarts. Written exclusively by the Python ML service.
--
-- Schema below is a byte-for-byte match of the live Neon table (verified
-- via \d model_artifacts on 2026-07-05), which was originally created ad-hoc
-- before this migration existed. Uses IF NOT EXISTS so this migration is a
-- no-op there — its purpose is to make the schema reproducible from a clean
-- database going forward, not to alter what's already running.
--
-- Notable, deliberately preserved as-is even though a stricter schema was
-- considered: model_name is plain VARCHAR(50), NOT the model_name enum
-- type used by model_metrics — the Python ML service writes this table
-- directly via SQLAlchemy with plain strings, so there's no enum
-- constraint here. Metric/date columns are nullable with no CHECK
-- constraints, since a partially-failed training run may still need to
-- persist an artifact without every metric present.

CREATE TABLE IF NOT EXISTS model_artifacts (
    id                BIGSERIAL PRIMARY KEY,
    ticker            VARCHAR(20)  NOT NULL,
    model_name        VARCHAR(50)  NOT NULL,
    model_version     VARCHAR(20)  NOT NULL,
    artifact          BYTEA        NOT NULL,

    rmse              DOUBLE PRECISION,
    mae               DOUBLE PRECISION,
    r2_score          DOUBLE PRECISION,
    feature_cols      TEXT[],

    train_start_date  DATE,
    train_end_date    DATE,
    test_start_date   DATE,
    test_end_date     DATE,

    created_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT model_artifacts_ticker_unique UNIQUE (ticker)
);

CREATE INDEX IF NOT EXISTS idx_model_artifacts_ticker ON model_artifacts (ticker);

COMMENT ON TABLE model_artifacts IS 'Persisted trained model (joblib-serialized scaler+model) per ticker. One row per ticker (enforced via unique ticker constraint, upserted on retrain via ON CONFLICT). Survives Render restarts unlike the in-memory ModelCache.';
COMMENT ON COLUMN model_artifacts.artifact IS 'joblib.dump() bytes of the full TrainResult dataclass (model + scaler + metadata)';