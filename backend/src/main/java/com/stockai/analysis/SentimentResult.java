package com.stockai.analysis;

/** source: "finbert" (classificador real), "lexical" (léxico de palavras-chave, fallback padrão) ou "unavailable". */
public record SentimentResult(
        double score,
        int positiveCount,
        int negativeCount,
        int neutralCount,
        double confidence,
        String source
) {}
