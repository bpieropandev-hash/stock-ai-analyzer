package com.stockai.analysis;

import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class SectorClassifier {

    private static final Map<String, SectorType> TICKER_MAP = Map.ofEntries(
            Map.entry("PETR4", SectorType.ENERGIA),
            Map.entry("ITUB4", SectorType.FINANCEIRO),
            Map.entry("BBDC4", SectorType.FINANCEIRO),
            Map.entry("B3SA3", SectorType.FINANCEIRO),
            Map.entry("MGLU3", SectorType.VAREJO),
            Map.entry("VALE3", SectorType.MINERACAO),
            Map.entry("ABEV3", SectorType.BEBIDAS),
            Map.entry("WEGE3", SectorType.INDUSTRIA),
            Map.entry("RENT3", SectorType.LOGISTICA),
            Map.entry("SUZB3", SectorType.PAPEL_CELULOSE)
    );

    private static final Map<String, SectorType> YFINANCE_SECTOR_MAP = Map.ofEntries(
            Map.entry("Energy", SectorType.ENERGIA),
            Map.entry("Utilities", SectorType.ENERGIA),
            Map.entry("Financial Services", SectorType.FINANCEIRO),
            Map.entry("Banks", SectorType.FINANCEIRO),
            Map.entry("Consumer Cyclical", SectorType.VAREJO),
            Map.entry("Consumer Defensive", SectorType.VAREJO),
            Map.entry("Retail", SectorType.VAREJO),
            Map.entry("Basic Materials", SectorType.MINERACAO),
            Map.entry("Industrials", SectorType.INDUSTRIA),
            Map.entry("Technology", SectorType.INDUSTRIA),
            Map.entry("Real Estate", SectorType.IMOBILIARIO),
            Map.entry("Healthcare", SectorType.SAUDE)
    );

    public SectorType classify(String ticker, String yfinanceSector) {
        String base = ticker.toUpperCase().replace(".SA", "");
        SectorType fromCache = TICKER_MAP.get(base);
        if (fromCache != null) return fromCache;
        if (yfinanceSector != null && !yfinanceSector.isBlank()) {
            return YFINANCE_SECTOR_MAP.getOrDefault(yfinanceSector, SectorType.OUTROS);
        }
        return SectorType.OUTROS;
    }
}
