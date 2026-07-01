package com.stocksense.dto.response;

import com.stocksense.enums.AuditAction;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

public record AuditLogResponse(
        UUID id,
        UUID userId,
        String action,
        Map<String, Object> metadata,
        String ipAddress,
        OffsetDateTime createdAt
) {}
