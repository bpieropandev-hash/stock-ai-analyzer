package com.stockai.portfolio;

public record EvaluationItem(
        String ticker,
        Double quantity,
        Double averagePrice,
        double currentScore,
        String action,
        String simpleSummary
) {}
