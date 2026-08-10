package com.stockai.analysis;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Gate de plausibilidade pós-score — roda depois do parse da resposta do LLM
 * e do cálculo de score, só SINALIZA inconsistência numérica entre dimensões/
 * scoreGeral e os dados que as sustentam. Nunca corrige, nunca bloqueia,
 * nunca é lido de volta pelo LLM. Função pura, mesmo estilo de
 * ScoreConfidenceCalculator/ScoreChangeCalculator.
 *
 * As 6 regras espelham travas/instruções que já existem como texto na rubrica
 * do prompt (ver prompts.md) — não são regras novas inventadas, são as mesmas
 * regras verificadas em vez de só pedidas. Thresholds validados por
 * financial-analyst; ponto de integração e tipo de saída validados por
 * backend-architect (ver decisions.md).
 */
public final class ScorePlausibilityGate {

    private ScorePlausibilityGate() {}

    public static List<PlausibilitySignal> check(StockAnalysis analysis, StockFundamentals fundamentals,
                                                   ScoreConfidence confidence, SectorType sector) {
        List<PlausibilitySignal> signals = new ArrayList<>();

        if (analysis.fundamentos().score() >= 7 && analysis.regimeMomentum().score() < 3.5) {
            signals.add(PlausibilitySignal.FUNDAMENTOS_ALTO_MOMENTUM_BAIXO);
        }

        // Ajuste do financial-analyst: P/L negativo sozinho é falso positivo comum (write-off pontual
        // com P/VPA saudável é o caso ESPERADO pela rubrica, não uma inconsistência). Só sinaliza quando
        // não há P/VPA disponível pra justificar a substituição que a própria rubrica instrui.
        BigDecimal pe = fundamentals.priceToEarnings();
        if (pe != null && pe.signum() < 0 && fundamentals.priceToBook() == null
                && analysis.valuation().score() >= 7) {
            signals.add(PlausibilitySignal.PE_NEGATIVO_VALUATION_ALTO_SEM_PVPA);
        }

        BigDecimal yield = fundamentals.dividendYield();
        if (yield != null && yield.signum() == 0
                && fundamentals.dividendHistory() != null && !fundamentals.dividendHistory().isEmpty()
                && analysis.retornoAcionista().score() <= 2) {
            signals.add(PlausibilitySignal.DIVIDENDO_ZERO_APESAR_DE_HISTORICO_RETORNO_BAIXO);
        }

        // Threshold alinhado ao piso real de ATRATIVO (financial-rules.md, >7.5) em vez de um corte
        // arbitrário — cobre exatamente a população cujo rótulo exposto ao usuário está assentado em
        // dado fraco (sugestão do financial-analyst).
        if (analysis.scoreGeral() > 7.5 && confidence.overall() < 4) {
            signals.add(PlausibilitySignal.SCORE_ALTO_CONFIANCA_BAIXA);
        }

        if (sector == SectorType.VAREJO && analysis.retornoAcionista().score() > 4
                && lucroInconsistente(fundamentals)) {
            signals.add(PlausibilitySignal.VAREJO_RETORNO_ALTO_SEM_LUCRO_CONSISTENTE);
        }

        // Bucket 8-10 de Fundamentos exige "lucro crescente nos trimestres" (prompts.md) — genérica,
        // não setorial (achado real: WEGE3, fundamentos=8 com crescimento negativo no próprio texto).
        // Reusa o mesmo helper da Regra 5, sem sector gate.
        if (analysis.fundamentos().score() >= 8 && lucroInconsistente(fundamentals)) {
            signals.add(PlausibilitySignal.FUNDAMENTOS_ALTO_LUCRO_INCONSISTENTE);
        }

        return List.copyOf(signals);
    }

    /**
     * "Lucro consistente" não é definido numericamente em lugar nenhum do sistema — proxy determinístico:
     * algum dos últimos 4 trimestres com lucro <= 0, ou crescimento de lucro negativo. Dado insuficiente
     * (sem trimestres e sem earningsGrowth) nunca sinaliza — o gate não especula sobre lucro que não viu.
     */
    private static boolean lucroInconsistente(StockFundamentals fundamentals) {
        List<QuarterlyResult> quarters = fundamentals.quarterlyResults();
        boolean prejuizoTrimestral = quarters != null && quarters.stream()
                .anyMatch(q -> q.earnings() != null && q.earnings().signum() <= 0);

        BigDecimal earningsGrowth = fundamentals.earningsGrowth();
        boolean lucroEmQueda = earningsGrowth != null && earningsGrowth.signum() < 0;

        return prejuizoTrimestral || lucroEmQueda;
    }
}
