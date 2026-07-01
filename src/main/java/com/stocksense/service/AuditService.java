package com.stocksense.service;

import com.stocksense.entity.AuditLog;
import com.stocksense.enums.AuditAction;
import com.stocksense.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    // Fire-and-forget: runs in a separate thread so audit writes
    // never slow down the main request path.
    // Propagation.REQUIRES_NEW: audit always commits even if the
    // calling transaction rolls back (e.g. failed prediction).
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(UUID userId, AuditAction action, Map<String, Object> metadata, String ipAddress) {
        try {
            AuditLog entry = AuditLog.builder()
                    .userId(userId)
                    .action(action)
                    .metadata(metadata != null ? metadata : Map.of())
                    .ipAddress(ipAddress)
                    .build();
            auditLogRepository.save(entry);
        } catch (Exception e) {
            // Audit failure must never crash the main request
            log.error("Audit log write failed for action {}: {}", action, e.getMessage());
        }
    }
}
