package com.stocksense.dto.response;

/**
 * Returned with HTTP 202 when the ML API has no cached model/data yet for
 * a ticker. The frontend is expected to poll POST /api/predictions again
 * after retryAfterSeconds — see hooks/usePrediction.ts on the frontend.
 */
public record TrainingStatusResponse(
        String ticker,
        String status,          // always "training"
        String message,
        int retryAfterSeconds
) {}