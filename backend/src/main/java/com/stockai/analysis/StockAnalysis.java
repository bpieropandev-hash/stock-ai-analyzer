package com.stockai.analysis;

import java.time.LocalDate;

public record StockAnalysis(
        String ticker,
        LocalDate analysisDate,
        DimensionScore fundamentos,
        DimensionScore valuation,
        DimensionScore regimeMomentum,
        DimensionScore sentimentoInstitucional,
        DimensionScore retornoAcionista,
        DimensionScore gestaoRisco,
        double scoreGeral,
        String resumo
) {}
