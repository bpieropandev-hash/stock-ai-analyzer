package com.stockai.analysis;

public record RankedStock(
        int position,
        String ticker,
        double scoreGeral,
        String recommendation,
        String simpleSummary,
        String sector
) {}
