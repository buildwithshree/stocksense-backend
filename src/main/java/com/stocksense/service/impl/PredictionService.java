package com.stocksense.service.impl;

import com.stocksense.dto.response.*;
import com.stocksense.entity.Prediction;
import com.stocksense.entity.User;
import com.stocksense.enums.AuditAction;
import com.stocksense.enums.ModelName;
import com.stocksense.enums.RiskLabel;
import com.stocksense.exception.MlServiceException;
import com.stocksense.exception.ResourceNotFoundException;
import com.stocksense.repository.PredictionRepository;
import com.stocksense.repository.UserRepository;
import com.stocksense.service.AuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PredictionService {

    private final PredictionRepository predictionRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;
    private final RestTemplate restTemplate;

    @Value("${ml.service.base-url}")
    private String mlBaseUrl;

    @Transactional
    public PredictionResponse predict(String ticker, UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        // Call Python ML service
        MlPredictResponse mlResponse = callMlService(ticker);

        // Persist to DB
        Prediction prediction = Prediction.builder()
                .user(user)
                .ticker(ticker.toUpperCase())
                .companyName(mlResponse.companyName())
                .currency(mlResponse.currency() != null ? mlResponse.currency() : "INR")
                .lastClose(mlResponse.lastClose())
                .predictedClose(mlResponse.predictedClose())
                .expectedMovePct(mlResponse.expectedMovePercent())
                .confidenceLower(mlResponse.confidenceLower())
                .confidenceUpper(mlResponse.confidenceUpper())
                .directionProbability(mlResponse.directionProbability())
                .riskScore(mlResponse.riskScore() != null ? mlResponse.riskScore().shortValue() : null)
                .riskLabel(RiskLabel.valueOf(mlResponse.riskLabel().replace(" ", "_")))
                .modelUsed(ModelName.valueOf(mlResponse.modelName()))
                .modelVersion(mlResponse.modelVersion())
                .rmse(mlResponse.rmse())
                .inferenceTimeMs(mlResponse.inferenceTimeMs())
                .topFeatures(mlResponse.topFeatures() != null ? mlResponse.topFeatures() : List.of())
                .build();
        predictionRepository.save(prediction);

        auditService.log(userId, AuditAction.PREDICTION_REQUESTED,
                Map.of("ticker", ticker, "model", mlResponse.modelName()), null);

        return toResponse(prediction);
    }

    @Transactional(readOnly = true)
    public PagedResponse<PredictionHistoryItem> getHistory(UUID userId, int page, int size) {
        Page<Prediction> results = predictionRepository
                .findByUserIdOrderByGeneratedAtDesc(userId, PageRequest.of(page, size));
        List<PredictionHistoryItem> items = results.stream().map(p ->
                new PredictionHistoryItem(
                        p.getId(), p.getTicker(), p.getGeneratedAt(),
                        p.getModelUsed().name(), p.getPredictedClose(),
                        p.getActualClose(), p.getActualErrorPct(),
                        p.getRiskScore() != null ? p.getRiskScore().intValue() : null,
                        p.getRiskLabel().display()
                )
        ).toList();
        return new PagedResponse<>(items, page, size, results.getTotalElements(), results.getTotalPages());
    }

    @Transactional(readOnly = true)
    public BacktestResponse getBacktest(String ticker) {
        String url = mlBaseUrl + "/backtest/" + ticker.toUpperCase();
        try {
            MlBacktestResponse resp = restTemplate.getForObject(url, MlBacktestResponse.class);
            if (resp == null) throw new MlServiceException("Empty backtest response for " + ticker);
            return new BacktestResponse(
                    ticker, resp.modelName(), resp.modelVersion(),
                    resp.averageError(), resp.directionAccuracy(),
                    resp.maxError(), resp.testDays(), resp.ranAt()
            );
        } catch (RestClientException e) {
            throw new MlServiceException("Backtest request failed", e);
        }
    }

    // ─── Private ──────────────────────────────────────────────────────────────

    private MlPredictResponse callMlService(String ticker) {
        String url = mlBaseUrl + "/predict/" + ticker.toUpperCase();
        try {
            MlPredictResponse resp = restTemplate.getForObject(url, MlPredictResponse.class);
            if (resp == null) throw new MlServiceException("Null response from ML service for " + ticker);
            return resp;
        } catch (RestClientException e) {
            log.error("ML service call failed for ticker {}: {}", ticker, e.getMessage());
            throw new MlServiceException("ML service unavailable", e);
        }
    }

    private PredictionResponse toResponse(Prediction p) {
        return new PredictionResponse(
                p.getId(), p.getTicker(), p.getCompanyName(), p.getCurrency(),
                p.getLastClose(), p.getPredictedClose(), p.getExpectedMovePct(),
                p.getConfidenceLower(), p.getConfidenceUpper(), p.getDirectionProbability(),
                p.getRiskScore() != null ? p.getRiskScore().intValue() : null,
                p.getRiskLabel().display(),
                p.getModelUsed().name(), p.getModelVersion(),
                p.getRmse(), p.getInferenceTimeMs(),
                p.getTopFeatures(), p.getGeneratedAt()
        );
    }

    // Internal records mirroring Python ML API JSON response shapes
    // These are parsing-only — never exposed to frontend directly.

    private record MlPredictResponse(
            String ticker, String companyName, String currency,
            java.math.BigDecimal lastClose, java.math.BigDecimal predictedClose,
            java.math.BigDecimal expectedMovePercent,
            java.math.BigDecimal confidenceLower, java.math.BigDecimal confidenceUpper,
            java.math.BigDecimal directionProbability,
            Integer riskScore, String riskLabel,
            String modelName, String modelVersion,
            java.math.BigDecimal rmse, Integer inferenceTimeMs,
            List<String> topFeatures
    ) {}

    private record MlBacktestResponse(
            String ticker, String modelName, String modelVersion,
            java.math.BigDecimal averageError, java.math.BigDecimal directionAccuracy,
            java.math.BigDecimal maxError, Integer testDays,
            java.time.OffsetDateTime ranAt
    ) {}
}
