-- V4__create_predictions.sql
-- Stores every ML prediction result with full context and explainability fields

CREATE TABLE predictions (
    id                    UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id               UUID           NOT NULL,
    ticker                VARCHAR(20)    NOT NULL,
    company_name          VARCHAR(255),
    currency              VARCHAR(3)     NOT NULL DEFAULT 'INR',

    -- Price output
    last_close            NUMERIC(12, 4) NOT NULL,
    predicted_close       NUMERIC(12, 4) NOT NULL,
    expected_move_pct     NUMERIC(6, 4)  NOT NULL,
    confidence_lower      NUMERIC(12, 4) NOT NULL,
    confidence_upper      NUMERIC(12, 4) NOT NULL,
    direction_probability NUMERIC(4, 3)  NOT NULL,  -- 0.000 to 1.000

    -- Risk output
    risk_score            SMALLINT       NOT NULL,   -- 0 to 100
    risk_label            risk_label     NOT NULL,

    -- Model metadata
    model_used            model_name     NOT NULL,
    model_version         VARCHAR(20)    NOT NULL,
    rmse                  NUMERIC(10, 4),
    inference_time_ms     INTEGER        NOT NULL,

    -- Explainability — stored as JSONB for flexible feature list
    top_features          JSONB          NOT NULL DEFAULT '[]',

    -- Actual close (populated later for accuracy tracking)
    actual_close          NUMERIC(12, 4),
    actual_error_pct      NUMERIC(6, 4),

    generated_at          TIMESTAMPTZ    NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_pred_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT chk_pred_risk_score
        CHECK (risk_score BETWEEN 0 AND 100),
    CONSTRAINT chk_pred_direction_prob
        CHECK (direction_probability BETWEEN 0.000 AND 1.000),
    CONSTRAINT chk_pred_confidence_band
        CHECK (confidence_lower <= predicted_close AND predicted_close <= confidence_upper),
    CONSTRAINT chk_pred_inference_time
        CHECK (inference_time_ms > 0)
);

CREATE INDEX idx_pred_user_id     ON predictions (user_id, generated_at DESC);
CREATE INDEX idx_pred_ticker      ON predictions (ticker, generated_at DESC);
CREATE INDEX idx_pred_generated   ON predictions (generated_at DESC);
CREATE INDEX idx_pred_model_used  ON predictions (model_used);
-- Partial index for predictions missing actual close (pending accuracy fill)
CREATE INDEX idx_pred_pending_actual
    ON predictions (ticker, generated_at)
    WHERE actual_close IS NULL;

COMMENT ON TABLE  predictions                    IS 'All prediction results, one row per user request';
COMMENT ON COLUMN predictions.direction_probability IS '0.5 = neutral, >0.5 = bullish, <0.5 = bearish';
COMMENT ON COLUMN predictions.top_features       IS 'JSON array of feature name strings in importance order';
COMMENT ON COLUMN predictions.actual_close       IS 'Filled by a background job after market close the next day';
