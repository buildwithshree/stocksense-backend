package com.stocksense.config;

import com.stocksense.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.time.OffsetDateTime;

// ─── RestTemplate Config ──────────────────────────────────────────────────────
@Configuration
public class AppConfig {

    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder.build();
    }
}

// ─── Scheduled Cleanup ────────────────────────────────────────────────────────
// Runs nightly at 02:00 UTC to purge expired/revoked refresh tokens.
// Keeps the refresh_tokens table lean on Neon's free tier.
@Component
@RequiredArgsConstructor
@Slf4j
class TokenCleanupJob {

    private final RefreshTokenRepository refreshTokenRepository;

    @Scheduled(cron = "0 0 2 * * *", zone = "UTC")
    @Transactional
    public void purgeExpiredTokens() {
        int deleted = refreshTokenRepository.deleteExpiredAndRevoked(OffsetDateTime.now());
        log.info("Token cleanup: {} expired/revoked tokens removed", deleted);
    }
}
