-- V5__create_watchlists_metrics_backtest_audit.sql

-- ─── Watchlists ──────────────────────────────────────────────────────────────
CREATE TABLE watchlists (
    id         UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID        NOT NULL,
    ticker     VARCHAR(20) NOT NULL,
    added_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_wl_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT uq_wl_user_ticker UNIQUE (user_id, ticker)
);

CREATE INDEX idx_wl_user_id ON watchlists (user_id);
CREATE INDEX idx_wl_ticker  ON watchlists (ticker);

COMMENT ON TABLE watchlists IS 'User-saved tickers. One row per user+ticker pair, unique constraint prevents duplicates.';


-- ─── Model Metrics ────────────────────────────────────────────────────────────
-- Written exclusively by Python ML service after every training run
CREATE TABLE model_metrics (
    id                UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    model_name        model_name  NOT NULL,
    model_version     VARCHAR(20) NOT NULL,
    ticker            VARCHAR(20) NOT NULL,

    -- Accuracy
    rmse              NUMERIC(10, 4) NOT NULL,
    mae               NUMERIC(10, 4) NOT NULL,
    r2_score          NUMERIC(6, 4)  NOT NULL,

    -- Efficiency (critical for model comparison)
    training_time_ms  INTEGER        NOT NULL,
    inference_time_ms INTEGER        NOT NULL,
    model_size_mb     NUMERIC(6, 3)  NOT NULL,
    feature_count     SMALLINT       NOT NULL,

    -- Time-series split metadata
    train_start_date  DATE           NOT NULL,
    train_end_date    DATE           NOT NULL,
    test_start_date   DATE           NOT NULL,
    test_end_date     DATE           NOT NULL,

    trained_at        TIMESTAMPTZ    NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_mm_rmse       CHECK (rmse >= 0),
    CONSTRAINT chk_mm_mae        CHECK (mae >= 0),
    CONSTRAINT chk_mm_r2         CHECK (r2_score BETWEEN -1.0 AND 1.0),
    CONSTRAINT chk_mm_train_time CHECK (training_time_ms > 0),
    CONSTRAINT chk_mm_inf_time   CHECK (inference_time_ms > 0),
    CONSTRAINT chk_mm_size       CHECK (model_size_mb > 0),
    CONSTRAINT chk_mm_features   CHECK (feature_count > 0),
    CONSTRAINT chk_mm_dates      CHECK (train_end_date < test_start_date)
);

CREATE INDEX idx_mm_model_ticker ON model_metrics (model_name, ticker);
CREATE INDEX idx_mm_trained_at   ON model_metrics (trained_at DESC);
CREATE INDEX idx_mm_ticker       ON model_metrics (ticker);

COMMENT ON TABLE model_metrics IS 'Per-training-run ML model performance. Enables comparing accuracy vs latency vs size across models.';


-- ─── Backtest Results ────────────────────────────────────────────────────────
CREATE TABLE backtest_results (
    id                 UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    ticker             VARCHAR(20) NOT NULL,
    model_name         model_name  NOT NULL,
    model_version      VARCHAR(20) NOT NULL,

    average_error      NUMERIC(10, 4) NOT NULL,
    direction_accuracy NUMERIC(5, 2)  NOT NULL,  -- percentage e.g. 58.70
    max_error          NUMERIC(10, 4) NOT NULL,
    test_days          INTEGER        NOT NULL,

    ran_at             TIMESTAMPTZ    NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_bt_dir_acc  CHECK (direction_accuracy BETWEEN 0 AND 100),
    CONSTRAINT chk_bt_test_days CHECK (test_days > 0),
    CONSTRAINT chk_bt_avg_err  CHECK (average_error >= 0)
);

CREATE INDEX idx_bt_ticker     ON backtest_results (ticker, ran_at DESC);
CREATE INDEX idx_bt_model      ON backtest_results (model_name);

COMMENT ON TABLE backtest_results IS 'Backtest walk-forward results. Proves model performance on unseen past data.';


-- ─── Audit Log ───────────────────────────────────────────────────────────────
CREATE TABLE audit_log (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID,                    -- NULL for unauthenticated events (register)
    action      audit_action NOT NULL,
    metadata    JSONB        NOT NULL DEFAULT '{}',
    ip_address  VARCHAR(45),             -- supports IPv6
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_al_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE SET NULL
);

CREATE INDEX idx_al_user_id   ON audit_log (user_id, created_at DESC);
CREATE INDEX idx_al_action    ON audit_log (action);
CREATE INDEX idx_al_created   ON audit_log (created_at DESC);
-- GIN index for metadata JSONB queries (admin filtering)
CREATE INDEX idx_al_metadata  ON audit_log USING GIN (metadata);

COMMENT ON TABLE  audit_log          IS 'Immutable event log. Rows are never updated or deleted.';
COMMENT ON COLUMN audit_log.metadata IS 'Contextual data e.g. {"ticker":"TCS.NS","model":"XGBoost"}';
COMMENT ON COLUMN audit_log.user_id  IS 'NULL for pre-auth events like failed logins';
