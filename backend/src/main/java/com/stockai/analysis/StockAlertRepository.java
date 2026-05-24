package com.stockai.analysis;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface StockAlertRepository extends JpaRepository<StockAlertEntity, UUID> {
    List<StockAlertEntity> findByCreatedAtAfter(LocalDateTime dateTime);
    List<StockAlertEntity> findByTicker(String ticker);
}
