package com.stocksense.controller;

import com.stocksense.dto.request.PredictionRequest;
import com.stocksense.dto.request.WatchlistAddRequest;
import com.stocksense.dto.response.*;
import com.stocksense.enums.AuditAction;
import com.stocksense.repository.AuditLogRepository;
import com.stocksense.repository.PredictionRepository;
import com.stocksense.repository.UserRepository;
import com.stocksense.service.AuditService;
import com.stocksense.service.impl.PredictionService;
import com.stocksense.service.impl.WatchlistService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// ─── Prediction Controller ────────────────────────────────────────────────────

@RestController
@RequestMapping("/api/predictions")
@RequiredArgsConstructor
class PredictionController {

    private final PredictionService predictionService;

    @PostMapping
    public ResponseEntity<?> predict(
            @Valid @RequestBody PredictionRequest req,
            @AuthenticationPrincipal String userId
    ) {
        var outcome = predictionService.predict(req.ticker(), UUID.fromString(userId));
        return switch (outcome) {
            case com.stocksense.service.PredictionOutcome.PredictionReady ready ->
                    ResponseEntity.status(HttpStatus.CREATED).body(ready.response());
            case com.stocksense.service.PredictionOutcome.PredictionTraining training ->
                    ResponseEntity.status(HttpStatus.ACCEPTED)
                            .header("Retry-After", String.valueOf(training.retryAfterSeconds()))
                            .body(new TrainingStatusResponse(
                                    training.ticker(), "training", training.message(), training.retryAfterSeconds()));
        };
    }

    @GetMapping("/history")
    public ResponseEntity<PagedResponse<PredictionHistoryItem>> history(
            @AuthenticationPrincipal String userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        size = Math.min(size, 50); // hard cap
        return ResponseEntity.ok(predictionService.getHistory(UUID.fromString(userId), page, size));
    }

    @GetMapping("/{ticker}/backtest")
    public ResponseEntity<BacktestResponse> backtest(
            @PathVariable String ticker,
            @AuthenticationPrincipal String userId
    ) {
        return ResponseEntity.ok(predictionService.getBacktest(ticker));
    }
}

// ─── Watchlist Controller ─────────────────────────────────────────────────────

@RestController
@RequestMapping("/api/watchlist")
@RequiredArgsConstructor
class WatchlistController {

    private final WatchlistService watchlistService;

    @GetMapping
    public ResponseEntity<List<WatchlistItemResponse>> getWatchlist(
            @AuthenticationPrincipal String userId
    ) {
        return ResponseEntity.ok(watchlistService.getWatchlist(UUID.fromString(userId)));
    }

    @PostMapping
    public ResponseEntity<WatchlistItemResponse> add(
            @Valid @RequestBody WatchlistAddRequest req,
            @AuthenticationPrincipal String userId
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(watchlistService.add(UUID.fromString(userId), req.ticker()));
    }

    @DeleteMapping("/{ticker}")
    public ResponseEntity<Void> remove(
            @PathVariable String ticker,
            @AuthenticationPrincipal String userId
    ) {
        watchlistService.remove(UUID.fromString(userId), ticker);
        return ResponseEntity.noContent().build();
    }
}

// ─── Admin Controller ─────────────────────────────────────────────────────────

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
class AdminController {

    private final UserRepository userRepository;
    private final PredictionRepository predictionRepository;
    private final AuditLogRepository auditLogRepository;
    private final AuditService auditService;

    @GetMapping("/metrics")
    public ResponseEntity<AdminMetricsResponse> metrics(@AuthenticationPrincipal String userId) {
        auditService.log(UUID.fromString(userId), AuditAction.ADMIN_VIEWED_METRICS, Map.of(), null);

        long totalUsers       = userRepository.count();
        long totalPredictions = predictionRepository.count();
        OffsetDateTime since24h = OffsetDateTime.now().minusHours(24);
        long preds24h         = auditLogRepository.countByActionSince(AuditAction.PREDICTION_REQUESTED, since24h);

        List<Object[]> rawTickers = predictionRepository.findTopTickers(PageRequest.of(0, 10));
        List<TickerStats> topTickers = rawTickers.stream()
                .map(row -> new TickerStats((String) row[0], (Long) row[1]))
                .toList();

        return ResponseEntity.ok(new AdminMetricsResponse(
                totalUsers, totalPredictions, preds24h, 0L, topTickers, List.of()
        ));
    }

    @GetMapping("/audit-logs")
    public ResponseEntity<PagedResponse<AuditLogResponse>> auditLogs(
            @AuthenticationPrincipal String userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        auditService.log(UUID.fromString(userId), AuditAction.ADMIN_VIEWED_AUDIT_LOGS, Map.of(), null);
        size = Math.min(size, 100);
        var results = auditLogRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(page, size));
        List<AuditLogResponse> items = results.stream()
                .map(a -> new AuditLogResponse(a.getId(), a.getUserId(), a.getAction().name(),
                        a.getMetadata(), a.getIpAddress(), a.getCreatedAt()))
                .toList();
        return ResponseEntity.ok(new PagedResponse<>(items, page, size,
                results.getTotalElements(), results.getTotalPages()));
    }
}

// ─── AuditLog response DTO (admin-only, not in public Responses.java) ─────────

record AuditLogResponse(
        java.util.UUID id,
        java.util.UUID userId,
        String action,
        java.util.Map<String, Object> metadata,
        String ipAddress,
        java.time.OffsetDateTime createdAt
) {}