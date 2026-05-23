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

    public SectorType classify(String ticker) {
        return TICKER_MAP.getOrDefault(ticker.toUpperCase(), SectorType.OUTROS);
    }
}
