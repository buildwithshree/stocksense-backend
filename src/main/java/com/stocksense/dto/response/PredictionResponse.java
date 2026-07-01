package com.stocksense.dto.response;

import java.util.List;
import java.util.UUID;
import java.time.OffsetDateTime;
import java.math.BigDecimal;

public record PredictionResponse(
        UUID id,
        String ticker,
        String companyName,
        String currency,
        BigDecimal lastClose,
        BigDecimal predictedClose,
        BigDecimal expectedMovePercent,
        BigDecimal confidenceLower,
        BigDecimal confidenceUpper,
        BigDecimal directionProbability,
        Integer riskScore,
        String riskLabel,
        String modelName,
        String modelVersion,
        BigDecimal rmse,
        Integer inferenceTimeMs,
        List<String> topFeatures,
        OffsetDateTime generatedAt
) {}
