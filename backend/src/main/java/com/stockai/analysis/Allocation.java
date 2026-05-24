package com.stockai.analysis;

public record Allocation(
        String ticker,
        String sector,
        double amount,
        double percentage,
        double scoreGeral,
        String recommendation,
        String simpleSummary
) {}
