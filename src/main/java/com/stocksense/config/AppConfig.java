package com.stocksense.config;

import com.stocksense.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.time.OffsetDateTime;

// ─── RestTemplate Config ──────────────────────────────────────────────────────
@Configuration
public class AppConfig {

    /**
     * Previously built with zero timeout configuration despite
     * ml.service.connect-timeout-ms / read-timeout-ms being declared in
     * application.yml — those values were never actually wired in, meaning
     * a hung ML API call could block a Spring Boot request thread
     * indefinitely. Now genuinely enforced.
     */
    @Bean
    public RestTemplate restTemplate(
            RestTemplateBuilder builder,
            @Value("${ml.service.connect-timeout-ms}") long connectTimeoutMs,
            @Value("${ml.service.read-timeout-ms}") long readTimeoutMs
    ) {
        return builder
                .setConnectTimeout(Duration.ofMillis(connectTimeoutMs))
                .setReadTimeout(Duration.ofMillis(readTimeoutMs))
                .build();
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