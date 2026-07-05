package com.stocksense.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import com.stocksense.service.PredictionOutcome;
import com.stocksense.service.PredictionOutcome.PredictionReady;
import com.stocksense.service.PredictionOutcome.PredictionTraining;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
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
    private final ObjectMapper objectMapper;

    @Value("${ml.service.base-url}")
    private String mlBaseUrl;

    /**
     * Returns a PredictionOutcome — either PredictionReady (persisted to DB,
     * fully hydrated PredictionResponse) or PredictionTraining (nothing
     * persisted; the ML API is training in the background). The controller
     * pattern-matches on this to pick the correct HTTP status/body.
     *
     * A prediction row is ONLY ever written when the ML call succeeds — a
     * "still training" response is not a completed prediction and must
     * never pollute prediction history or per-user stats.
     */
    @Transactional
    public PredictionOutcome predict(String ticker, UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        MlCallResult mlResult = callMlService(ticker);

        return switch (mlResult) {
            case MlSuccess success -> {
                MlPredictResponse mlResponse = success.response();

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
                        Map.of("ticker", ticker, "model", mlResponse.modelName(), "status", "ready"), null);

                yield new PredictionReady(toResponse(prediction));
            }
            case MlTraining training -> {
                // Deliberately NOT persisted to `predictions` — this is not
                // a completed prediction. Still audit-logged so admin
                // metrics can see cold-start volume if useful later.
                auditService.log(userId, AuditAction.PREDICTION_REQUESTED,
                        Map.of("ticker", ticker, "status", "training"), null);
                yield new PredictionTraining(training.ticker(), training.message(), training.retryAfterSeconds());
            }
        };
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

    /**
     * Uses exchange() rather than getForObject() specifically so the actual
     * HTTP status can be inspected BEFORE deciding which JSON shape to
     * deserialize into. getForObject() (the original code) deserialized
     * blindly regardless of status — 202 is a 2xx and doesn't throw, so it
     * silently produced a half-null MlPredictResponse and crashed later on
     * a field the training-status body never had (riskLabel).
     */
    private MlCallResult callMlService(String ticker) {
        String url = mlBaseUrl + "/predict/" + ticker.toUpperCase();
        ResponseEntity<JsonNode> resp;
        try {
            resp = restTemplate.exchange(url, HttpMethod.GET, null, JsonNode.class);
        } catch (RestClientException e) {
            log.error("ML service call failed for ticker {}: {}", ticker, e.getMessage());
            throw new MlServiceException("ML service unavailable", e);
        }

        JsonNode body = resp.getBody();
        if (body == null) {
            throw new MlServiceException("Empty response from ML service for " + ticker);
        }

        if (resp.getStatusCode().value() == 202) {
            return new MlTraining(
                    body.path("ticker").asText(ticker),
                    body.path("message").asText("Model is training. Please retry shortly."),
                    body.path("check_again_in_seconds").asInt(30)
            );
        }

        return new MlSuccess(objectMapper.convertValue(body, MlPredictResponse.class));
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

    // ─── Internal result type for callMlService — kept separate from the
    // public PredictionOutcome because at this point we don't have the
    // User loaded / Prediction persisted yet; predict() does that and
    // constructs the real PredictionOutcome afterward. ───────────────────────

    private sealed interface MlCallResult permits MlSuccess, MlTraining {}

    private record MlSuccess(MlPredictResponse response) implements MlCallResult {}

    private record MlTraining(String ticker, String message, int retryAfterSeconds) implements MlCallResult {}

    // Internal record mirroring Python ML API's 200 JSON response shape
    // (the 202 shape is handled entirely via JsonNode path lookups above
    // since it's a completely different structure). Parsing-only — never
    // exposed to frontend directly.

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