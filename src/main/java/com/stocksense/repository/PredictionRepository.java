package com.stocksense.repository;

import com.stocksense.entity.Prediction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface PredictionRepository extends JpaRepository<Prediction, UUID> {
    Page<Prediction> findByUserIdOrderByGeneratedAtDesc(UUID userId, Pageable pageable);
    List<Prediction> findByTickerOrderByGeneratedAtDesc(String ticker, Pageable pageable);

    @Query("SELECT p.ticker, COUNT(p) as cnt FROM Prediction p GROUP BY p.ticker ORDER BY cnt DESC")
    List<Object[]> findTopTickers(Pageable pageable);

    @Query("SELECT p FROM Prediction p WHERE p.ticker = :ticker AND p.actualClose IS NULL AND p.generatedAt < :before")
    List<Prediction> findPendingActualClose(String ticker, OffsetDateTime before);
}
