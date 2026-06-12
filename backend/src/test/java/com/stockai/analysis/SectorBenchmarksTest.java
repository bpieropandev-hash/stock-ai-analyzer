package com.stockai.analysis;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

class SectorBenchmarksTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    // formatDynamic é pura — não toca gateway nem Redis
    private final SectorBenchmarks benchmarks = new SectorBenchmarks(null, null, null);

    @Test
    void formataMedianasComPercentuaisEDecimais() {
        String json = """
                {"peerCount": 9, "computedAt": "2026-06-12",
                 "medians": {"priceToEarnings": 8.9133, "priceToBook": 1.5233,
                             "dividendYield": 0.077, "roe": 0.1376,
                             "netMargin": 0.0995, "debtToEquity": 1.0649}}""";
        String text = benchmarks.formatDynamic(objectMapper.readTree(json));

        assertTrue(text.contains("9 empresas"));
        assertTrue(text.contains("P/L: 8.9"));
        assertTrue(text.contains("ROE: 13.8%"));
        assertTrue(text.contains("Dividend Yield: 7.7%"));
        assertTrue(text.contains("Dívida/PL: 1.1"));
        assertTrue(text.contains("calculadas em 2026-06-12"));
    }

    @Test
    void omiteMetricaSemAmostrasSuficientes() {
        String json = """
                {"peerCount": 5, "computedAt": "2026-06-12",
                 "medians": {"priceToEarnings": null, "priceToBook": 1.2,
                             "dividendYield": 0.05, "roe": 0.15,
                             "netMargin": null, "debtToEquity": 0.8}}""";
        String text = benchmarks.formatDynamic(objectMapper.readTree(json));

        assertFalse(text.contains("P/L"));
        assertFalse(text.contains("Margem líquida"));
        assertTrue(text.contains("P/VPA: 1.2"));
    }

    @Test
    void rejeitaQuandoHaParesDeMenos() {
        String json = """
                {"peerCount": 2, "computedAt": "2026-06-12",
                 "medians": {"priceToEarnings": 8.0}}""";
        assertNull(benchmarks.formatDynamic(objectMapper.readTree(json)));
    }

    @Test
    void rejeitaJsonSemPeerCount() {
        assertNull(benchmarks.formatDynamic(objectMapper.readTree("{}")));
    }
}
