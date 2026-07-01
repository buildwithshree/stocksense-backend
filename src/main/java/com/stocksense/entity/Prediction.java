package com.stocksense.entity;

import com.stocksense.enums.ModelName;
import com.stocksense.enums.RiskLabel;
import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Type;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "predictions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Prediction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "ticker", nullable = false, length = 20)
    private String ticker;

    @Column(name = "company_name", length = 255)
    private String companyName;

    @Column(name = "currency", nullable = false, length = 3)
    @Builder.Default
    private String currency = "INR";

    @Column(name = "last_close", nullable = false, precision = 12, scale = 4)
    private BigDecimal lastClose;

    @Column(name = "predicted_close", nullable = false, precision = 12, scale = 4)
    private BigDecimal predictedClose;

    @Column(name = "expected_move_pct", nullable = false, precision = 6, scale = 4)
    private BigDecimal expectedMovePct;

    @Column(name = "confidence_lower", nullable = false, precision = 12, scale = 4)
    private BigDecimal confidenceLower;

    @Column(name = "confidence_upper", nullable = false, precision = 12, scale = 4)
    private BigDecimal confidenceUpper;

    @Column(name = "direction_probability", nullable = false, precision = 4, scale = 3)
    private BigDecimal directionProbability;

    @Column(name = "risk_score", nullable = false)
    private Short riskScore;

    @Enumerated(EnumType.STRING)
    @Column(name = "risk_label", nullable = false, columnDefinition = "risk_label")
    private RiskLabel riskLabel;

    @Enumerated(EnumType.STRING)
    @Column(name = "model_used", nullable = false, columnDefinition = "model_name")
    private ModelName modelUsed;

    @Column(name = "model_version", nullable = false, length = 20)
    private String modelVersion;

    @Column(name = "rmse", precision = 10, scale = 4)
    private BigDecimal rmse;

    @Column(name = "inference_time_ms", nullable = false)
    private Integer inferenceTimeMs;

    // JSONB list of feature name strings — e.g. ["RSI_14", "EMA_20", "MACD"]
    @Type(JsonBinaryType.class)
    @Column(name = "top_features", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private List<String> topFeatures = List.of();

    @Column(name = "actual_close", precision = 12, scale = 4)
    private BigDecimal actualClose;

    @Column(name = "actual_error_pct", precision = 6, scale = 4)
    private BigDecimal actualErrorPct;

    @CreationTimestamp
    @Column(name = "generated_at", nullable = false, updatable = false)
    private OffsetDateTime generatedAt;
}
