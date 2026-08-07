package com.stockai.analysis;

import java.time.LocalDate;
import java.util.List;

/**
 * Compara o score atual com a análise anterior do mesmo ativo — "por que o
 * score mudou", não só "quanto mudou" (isso já existe em StockAlert). Nulo
 * quando não há análise anterior (primeira vez que o ticker é analisado).
 */
public record ScoreChangeExplanation(
        LocalDate previousAnalysisDate,
        double previousScoreGeral,
        double currentScoreGeral,
        double delta,
        List<DimensionChange> dimensions,
        DimensionChange topMover
) {
    public record DimensionChange(String label, double previousScore, double currentScore,
                                  double delta, String explicacao) {}
}
