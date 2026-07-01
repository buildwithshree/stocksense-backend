package com.stocksense.dto.request;

import jakarta.validation.constraints.*;

public record RefreshRequest(
        @NotBlank(message = "Refresh token is required")
        String refreshToken
) {}
