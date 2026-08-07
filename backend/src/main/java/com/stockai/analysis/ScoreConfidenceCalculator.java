package com.stockai.analysis;

/**
 * Agrega sinais de qualidade de dado já existentes no sistema (nenhum novo
 * dado coletado) numa métrica única exposta ao usuário — roadmap "Score
 * Confidence". Função pura, sem estado, mesmo estilo de TickerNormalizer.
 */
public final class ScoreConfidenceCalculator {

    private ScoreConfidenceCalculator() {}

    public static ScoreConfidence calculate(StockFundamentals fundamentals, SentimentResult sentiment,
                                             TechnicalIndicators technical, boolean sectorBenchmarkDynamic) {
        double fundamentalsQuality = "cvm+yfinance".equals(fundamentals.fundamentalsSource()) ? 10.0 : 5.0;

        double sentimentBase = switch (sentiment.source()) {
            case "finbert" -> 10.0;
            case "lexical" -> 5.0;
            default -> 0.0;
        };
        double sentimentQuality = round1(sentimentBase * sentiment.confidence());

        boolean technicalDataAvailable = technical != null;

        double overall = round1((fundamentalsQuality
                + sentimentQuality
                + (technicalDataAvailable ? 10.0 : 0.0)
                + (sectorBenchmarkDynamic ? 10.0 : 4.0)) / 4.0);

        return new ScoreConfidence(overall, fundamentalsQuality, sentimentQuality,
                technicalDataAvailable, sectorBenchmarkDynamic);
    }

    private static double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }
}
