package com.stockai.analysis;

/**
 * Meta-score de confiança na qualidade dos dados que sustentam a análise —
 * não é a mesma coisa que scoreGeral (que mede a qualidade da empresa).
 * Escala 0-10, mesma convenção do resto do score.
 */
public record ScoreConfidence(
        double overall,
        double fundamentalsQuality,
        double sentimentQuality,
        boolean technicalDataAvailable,
        boolean sectorBenchmarkDynamic
) {}
