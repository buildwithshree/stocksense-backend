package com.stocksense.service;

import com.stocksense.dto.response.PredictionResponse;

/**
 * The ML API can respond to /predict/{ticker} in exactly two shapes:
 *   - 200: a full prediction is ready
 *   - 202: no model/data is cached yet; training was kicked off in the
 *     background and the caller should retry shortly
 *
 * Modelling this as a sealed interface (rather than one DTO with nullable
 * fields depending on status) means the compiler forces every consumer —
 * today's controller, and anything added later — to handle BOTH cases
 * explicitly via a pattern-matched switch. The original bug (NullPointer
 * on RiskLabel.valueOf(null) when a 202 body was silently deserialized
 * into the success DTO) is structurally impossible with this shape: there
 * is no single type with optional fields to misuse.
 */
public sealed interface PredictionOutcome {

    record PredictionReady(PredictionResponse response) implements PredictionOutcome {}

    record PredictionTraining(String ticker, String message, int retryAfterSeconds)
            implements PredictionOutcome {}
}