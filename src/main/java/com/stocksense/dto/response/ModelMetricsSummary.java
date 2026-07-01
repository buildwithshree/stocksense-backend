package com.stocksense.dto.response;

import java.math.BigDecimal;

public record ModelMetricsSummary(
        String modelName,
        BigDecimal averageRmse,
        BigDecimal averageInferenceTimeMs,
        long trainingRuns
) {}
