package com.stockai.analysis;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface ScoreHistoryRepository extends JpaRepository<ScoreHistoryEntity, Long> {

    List<ScoreHistoryEntity> findByTickerAndAnalysisDateAfterOrderByAnalysisDateAsc(String ticker, LocalDate cutoff);

    List<ScoreHistoryEntity> findByTickerOrderByAnalysisDateAsc(String ticker);
}
