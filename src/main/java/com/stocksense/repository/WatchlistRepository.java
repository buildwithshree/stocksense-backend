package com.stocksense.repository;

import com.stocksense.entity.Watchlist;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WatchlistRepository extends JpaRepository<Watchlist, UUID> {
    List<Watchlist> findByUserIdOrderByAddedAtDesc(UUID userId);
    Optional<Watchlist> findByUserIdAndTicker(UUID userId, String ticker);
    boolean existsByUserIdAndTicker(UUID userId, String ticker);

    @Modifying
    @Query("DELETE FROM Watchlist w WHERE w.user.id = :userId AND w.ticker = :ticker")
    int deleteByUserIdAndTicker(@Param("userId") UUID userId, @Param("ticker") String ticker);
}
