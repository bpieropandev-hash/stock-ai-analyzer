package com.stockai.analysis;

public record SentimentResult(
        double score,
        int positiveCount,
        int negativeCount,
        int neutralCount,
        double confidence
) {}
