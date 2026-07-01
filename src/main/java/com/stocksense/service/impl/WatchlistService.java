package com.stocksense.service.impl;

import com.stocksense.dto.response.WatchlistItemResponse;
import com.stocksense.entity.User;
import com.stocksense.entity.Watchlist;
import com.stocksense.enums.AuditAction;
import com.stocksense.exception.ConflictException;
import com.stocksense.exception.ResourceNotFoundException;
import com.stocksense.repository.UserRepository;
import com.stocksense.repository.WatchlistRepository;
import com.stocksense.service.AuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WatchlistService {

    private final WatchlistRepository watchlistRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public List<WatchlistItemResponse> getWatchlist(UUID userId) {
        return watchlistRepository.findByUserIdOrderByAddedAtDesc(userId).stream()
                .map(w -> new WatchlistItemResponse(w.getId(), w.getTicker(), w.getAddedAt()))
                .toList();
    }

    @Transactional
    public WatchlistItemResponse add(UUID userId, String ticker) {
        if (watchlistRepository.existsByUserIdAndTicker(userId, ticker.toUpperCase())) {
            throw new ConflictException("Ticker already in watchlist: " + ticker);
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Watchlist item = Watchlist.builder()
                .user(user)
                .ticker(ticker.toUpperCase())
                .build();
        watchlistRepository.save(item);

        auditService.log(userId, AuditAction.WATCHLIST_ADDED, Map.of("ticker", ticker), null);
        return new WatchlistItemResponse(item.getId(), item.getTicker(), item.getAddedAt());
    }

    @Transactional
    public void remove(UUID userId, String ticker) {
        int deleted = watchlistRepository.deleteByUserIdAndTicker(userId, ticker.toUpperCase());
        if (deleted == 0) throw new ResourceNotFoundException("Ticker not in watchlist: " + ticker);
        auditService.log(userId, AuditAction.WATCHLIST_REMOVED, Map.of("ticker", ticker), null);
    }
}
