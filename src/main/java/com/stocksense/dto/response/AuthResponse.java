package com.stocksense.dto.response;

import com.stocksense.enums.UserRole;

import java.util.UUID;

public record AuthResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresInMs,
        UserProfileResponse user
) {
    public static final String TOKEN_TYPE = "Bearer";
}
