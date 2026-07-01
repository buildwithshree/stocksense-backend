-- V3__create_stock_cache.sql
-- Caches OHLCV data fetched from yfinance to avoid redundant API calls

CREATE TABLE stock_cache (
    ticker      VARCHAR(20)     NOT NULL,
    trade_date  DATE            NOT NULL,
    open_price  NUMERIC(12, 4)  NOT NULL,
    high_price  NUMERIC(12, 4)  NOT NULL,
    low_price   NUMERIC(12, 4)  NOT NULL,
    close_price NUMERIC(12, 4)  NOT NULL,
    volume      BIGINT          NOT NULL,
    currency    VARCHAR(3)      NOT NULL DEFAULT 'INR',
    fetched_at  TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_stock_cache PRIMARY KEY (ticker, trade_date),
    CONSTRAINT chk_sc_prices CHECK (
        open_price  > 0 AND
        high_price  >= open_price AND
        low_price   <= open_price AND
        close_price > 0 AND
        high_price  >= close_price AND
        low_price   <= close_price
    ),
    CONSTRAINT chk_sc_volume CHECK (volume >= 0)
);

CREATE INDEX idx_sc_ticker     ON stock_cache (ticker);
CREATE INDEX idx_sc_trade_date ON stock_cache (ticker, trade_date DESC);

COMMENT ON TABLE  stock_cache            IS 'OHLCV data cached from yfinance. Written by Python ML service.';
COMMENT ON COLUMN stock_cache.ticker     IS 'Yahoo Finance ticker symbol e.g. TCS.NS, AAPL';
COMMENT ON COLUMN stock_cache.trade_date IS 'Market date of this OHLCV record, not fetch timestamp';
