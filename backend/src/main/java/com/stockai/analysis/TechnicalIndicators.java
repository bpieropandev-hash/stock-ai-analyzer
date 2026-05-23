package com.stockai.analysis;

public record TechnicalIndicators(
        Double currentPrice,
        Double rsi,
        Double macdLine,
        Double macdSignal,
        Double macdHistogram,
        Double sma20,
        Double sma50,
        Double bollingerUpper,
        Double bollingerMiddle,
        Double bollingerLower,
        Double volumeRatio,
        String technicalSignal
) {}
