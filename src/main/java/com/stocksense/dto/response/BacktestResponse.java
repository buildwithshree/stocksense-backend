package com.stocksense.dto.response;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record BacktestResponse(
        String ticker,
        String modelName,
        String modelVersion,
        BigDecimal averageError,
        BigDecimal directionAccuracy,
        BigDecimal maxError,
        Integer testDays,
        OffsetDateTime ranAt
) {}
