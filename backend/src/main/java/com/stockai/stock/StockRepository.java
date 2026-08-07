package com.stockai.stock;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface StockRepository extends JpaRepository<Stock, Long> {

    Optional<Stock> findByTicker(String ticker);

    /**
     * Único ponto de criação de {@link Stock} — garante que todo ticker persistido
     * passa por {@link TickerNormalizer#canonical}, mesmo vindo de chamadores que
     * ainda passam formato com sufixo .SA.
     */
    default Stock findOrCreate(String rawTicker) {
        String canonical = TickerNormalizer.canonical(rawTicker);
        return findByTicker(canonical).orElseGet(() -> save(new Stock(canonical)));
    }
}
