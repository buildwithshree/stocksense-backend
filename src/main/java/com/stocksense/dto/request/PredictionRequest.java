package com.stocksense.dto.request;

import jakarta.validation.constraints.*;

public record PredictionRequest(
        @NotBlank(message = "Ticker is required")
        @Size(max = 20)
        @Pattern(regexp = "^[A-Z0-9.^-]{1,20}$", message = "Invalid ticker format")
        String ticker
) {}
