package com.stocksense.dto.response;

import java.math.BigDecimal;
import java.util.List;

public record AdminMetricsResponse(
        long totalUsers,
        long totalPredictions,
        long predictionsLast24h,
        long failedPredictionsLast24h,
        List<TickerStats> topTickers,
        List<ModelMetricsSummary> modelSummaries
) {}
