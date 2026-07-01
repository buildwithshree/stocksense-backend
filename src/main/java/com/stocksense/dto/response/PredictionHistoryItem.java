package com.stocksense.dto.response;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record PredictionHistoryItem(
        UUID id,
        String ticker,
        OffsetDateTime generatedAt,
        String modelName,
        BigDecimal predictedClose,
        BigDecimal actualClose,
        BigDecimal actualErrorPct,
        Integer riskScore,
        String riskLabel
) {}
