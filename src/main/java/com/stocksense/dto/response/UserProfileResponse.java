package com.stocksense.dto.response;

import com.stocksense.enums.UserRole;

import java.time.OffsetDateTime;
import java.util.UUID;

public record UserProfileResponse(
        UUID id,
        String email,
        String fullName,
        UserRole role,
        OffsetDateTime createdAt
) {}
