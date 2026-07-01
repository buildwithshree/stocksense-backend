package com.stocksense.dto.response;

import java.time.OffsetDateTime;
import java.util.UUID;

public record WatchlistItemResponse(
        UUID id,
        String ticker,
        OffsetDateTime addedAt
) {}
