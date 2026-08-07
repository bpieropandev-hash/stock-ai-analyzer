package com.stockai.analysis;

import java.util.Comparator;
import java.util.List;

/**
 * Função pura — cruza a análise atual com o registro anterior em score_history
 * pra apontar qual dimensão mais moveu o score e por quê, reusando a
 * `explicacao` que o LLM já dá pra cada dimensão (nenhum dado novo coletado).
 */
public final class ScoreChangeCalculator {

    private ScoreChangeCalculator() {}

    public static ScoreChangeExplanation compute(ScoreHistoryEntity previous, StockAnalysis current) {
        List<ScoreChangeExplanation.DimensionChange> dims = List.of(
                dim("Fundamentos", previous.getFundamentos(), current.fundamentos()),
                dim("Valuation", previous.getValuation(), current.valuation()),
                dim("Regime/Momentum", previous.getRegimeMomentum(), current.regimeMomentum()),
                dim("Sentimento Institucional", previous.getSentimentoInstitucional(), current.sentimentoInstitucional()),
                dim("Retorno ao Acionista", previous.getRetornoAcionista(), current.retornoAcionista()),
                dim("Gestão de Risco", previous.getGestaoRisco(), current.gestaoRisco())
        );

        ScoreChangeExplanation.DimensionChange topMover = dims.stream()
                .max(Comparator.comparingDouble(d -> Math.abs(d.delta())))
                .orElseThrow();

        return new ScoreChangeExplanation(
                previous.getAnalysisDate(),
                previous.getScoreGeral(),
                current.scoreGeral(),
                round1(current.scoreGeral() - previous.getScoreGeral()),
                dims,
                topMover
        );
    }

    private static ScoreChangeExplanation.DimensionChange dim(String label, double previousScore, DimensionScore curr) {
        return new ScoreChangeExplanation.DimensionChange(
                label, previousScore, curr.score(), round1(curr.score() - previousScore), curr.explicacao());
    }

    private static double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }
}
