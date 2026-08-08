package com.stockai.analysis;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ScorePlausibilityGateTest {

    private static final ScoreConfidence CONFIANCA_ALTA =
            new ScoreConfidence(10.0, 10.0, 10.0, true, true);
    private static final ScoreConfidence CONFIANCA_BAIXA =
            new ScoreConfidence(2.0, 0.0, 0.0, false, false);

    @Test
    void naoSinalizaAnaliseCoerente() {
        StockAnalysis analysis = analysis(7.0, 7.0, 6.5, 5.0, 5.0, 7.0, 6.4);
        StockFundamentals fundamentals = fundamentals(BigDecimal.TEN, BigDecimal.ONE,
                BigDecimal.valueOf(0.05), List.of(dividendo()), List.of(), null);

        List<PlausibilitySignal> signals =
                ScorePlausibilityGate.check(analysis, fundamentals, CONFIANCA_ALTA, SectorType.INDUSTRIA);

        assertThat(signals).isEmpty();
    }

    @Test
    void sinalizaFundamentosAltoComMomentumBaixo() {
        StockAnalysis analysis = analysis(8.0, 5.0, 2.0, 5.0, 5.0, 5.0, 5.0);
        StockFundamentals fundamentals = fundamentals(BigDecimal.TEN, BigDecimal.ONE, null, List.of(), List.of(), null);

        List<PlausibilitySignal> signals =
                ScorePlausibilityGate.check(analysis, fundamentals, CONFIANCA_ALTA, SectorType.INDUSTRIA);

        assertThat(signals).containsExactly(PlausibilitySignal.FUNDAMENTOS_ALTO_MOMENTUM_BAIXO);
    }

    @Test
    void naoSinalizaMomentumBaixoQuandoFundamentosNaoEstaAlto() {
        StockAnalysis analysis = analysis(5.0, 6.9, 2.0, 5.0, 5.0, 5.0, 5.0);
        StockFundamentals fundamentals = fundamentals(BigDecimal.TEN, BigDecimal.ONE, null, List.of(), List.of(), null);

        List<PlausibilitySignal> signals =
                ScorePlausibilityGate.check(analysis, fundamentals, CONFIANCA_ALTA, SectorType.INDUSTRIA);

        assertThat(signals).isEmpty();
    }

    @Test
    void sinalizaPLNegativoSemPVPAComValuationAlto() {
        StockAnalysis analysis = analysis(5.0, 8.0, 5.0, 5.0, 5.0, 5.0, 5.0);
        StockFundamentals fundamentals = fundamentals(
                BigDecimal.valueOf(-3.5), null, null, List.of(), List.of(), null);

        List<PlausibilitySignal> signals =
                ScorePlausibilityGate.check(analysis, fundamentals, CONFIANCA_ALTA, SectorType.INDUSTRIA);

        assertThat(signals).containsExactly(PlausibilitySignal.PE_NEGATIVO_VALUATION_ALTO_SEM_PVPA);
    }

    @Test
    void naoSinalizaPLNegativoQuandoPVPADisponivel() {
        // P/VPA disponível justifica a substituição que a própria rubrica instrui — não é inconsistência
        StockAnalysis analysis = analysis(5.0, 8.0, 5.0, 5.0, 5.0, 5.0, 5.0);
        StockFundamentals fundamentals = fundamentals(
                BigDecimal.valueOf(-3.5), BigDecimal.valueOf(0.8), null, List.of(), List.of(), null);

        List<PlausibilitySignal> signals =
                ScorePlausibilityGate.check(analysis, fundamentals, CONFIANCA_ALTA, SectorType.INDUSTRIA);

        assertThat(signals).isEmpty();
    }

    @Test
    void sinalizaDividendoZeradoComHistoricoEretornoBaixo() {
        StockAnalysis analysis = analysis(5.0, 5.0, 5.0, 5.0, 1.0, 5.0, 4.3);
        StockFundamentals fundamentals = fundamentals(
                BigDecimal.TEN, BigDecimal.ONE, BigDecimal.ZERO, List.of(dividendo()), List.of(), null);

        List<PlausibilitySignal> signals =
                ScorePlausibilityGate.check(analysis, fundamentals, CONFIANCA_ALTA, SectorType.INDUSTRIA);

        assertThat(signals).containsExactly(PlausibilitySignal.DIVIDENDO_ZERO_APESAR_DE_HISTORICO_RETORNO_BAIXO);
    }

    @Test
    void naoSinalizaDividendoZeradoSemHistorico() {
        StockAnalysis analysis = analysis(5.0, 5.0, 5.0, 5.0, 1.0, 5.0, 3.7);
        StockFundamentals fundamentals = fundamentals(
                BigDecimal.TEN, BigDecimal.ONE, BigDecimal.ZERO, List.of(), List.of(), null);

        List<PlausibilitySignal> signals =
                ScorePlausibilityGate.check(analysis, fundamentals, CONFIANCA_ALTA, SectorType.INDUSTRIA);

        assertThat(signals).isEmpty();
    }

    @Test
    void sinalizaScoreAltoComConfiancaBaixa() {
        StockAnalysis analysis = analysis(8.0, 8.0, 8.0, 8.0, 8.0, 8.0, 8.0);
        StockFundamentals fundamentals = fundamentals(BigDecimal.TEN, BigDecimal.ONE, null, List.of(), List.of(), null);

        List<PlausibilitySignal> signals =
                ScorePlausibilityGate.check(analysis, fundamentals, CONFIANCA_BAIXA, SectorType.INDUSTRIA);

        assertThat(signals).containsExactly(PlausibilitySignal.SCORE_ALTO_CONFIANCA_BAIXA);
    }

    @Test
    void naoSinalizaScoreNoPisoDeAtrativoComConfiancaBaixa() {
        // 7.5 é o piso, não "acima de" — regra usa > 7.5 pra alinhar com o threshold real de ATRATIVO
        StockAnalysis analysis = analysis(7.5, 7.5, 7.5, 7.5, 7.5, 7.5, 7.5);
        StockFundamentals fundamentals = fundamentals(BigDecimal.TEN, BigDecimal.ONE, null, List.of(), List.of(), null);

        List<PlausibilitySignal> signals =
                ScorePlausibilityGate.check(analysis, fundamentals, CONFIANCA_BAIXA, SectorType.INDUSTRIA);

        assertThat(signals).isEmpty();
    }

    @Test
    void sinalizaVarejoComRetornoAltoELucroTrimestralNegativo() {
        StockAnalysis analysis = analysis(5.0, 5.0, 5.0, 5.0, 6.0, 5.0, 5.2);
        StockFundamentals fundamentals = fundamentals(BigDecimal.TEN, BigDecimal.ONE, null, List.of(),
                List.of(new QuarterlyResult("2026-06-30", BigDecimal.valueOf(1_000_000), BigDecimal.valueOf(-50_000))),
                null);

        List<PlausibilitySignal> signals =
                ScorePlausibilityGate.check(analysis, fundamentals, CONFIANCA_ALTA, SectorType.VAREJO);

        assertThat(signals).containsExactly(PlausibilitySignal.VAREJO_RETORNO_ALTO_SEM_LUCRO_CONSISTENTE);
    }

    @Test
    void sinalizaVarejoComRetornoAltoECrescimentoDeLucroNegativo() {
        StockAnalysis analysis = analysis(5.0, 5.0, 5.0, 5.0, 6.0, 5.0, 5.2);
        StockFundamentals fundamentals = fundamentals(BigDecimal.TEN, BigDecimal.ONE, null, List.of(), List.of(),
                BigDecimal.valueOf(-0.1));

        List<PlausibilitySignal> signals =
                ScorePlausibilityGate.check(analysis, fundamentals, CONFIANCA_ALTA, SectorType.VAREJO);

        assertThat(signals).containsExactly(PlausibilitySignal.VAREJO_RETORNO_ALTO_SEM_LUCRO_CONSISTENTE);
    }

    @Test
    void naoSinalizaVarejoQuandoNaoHaDadoDeLucroParaAvaliar() {
        // Sem trimestres e sem earningsGrowth — o gate não especula sobre lucro que não viu
        StockAnalysis analysis = analysis(5.0, 5.0, 5.0, 5.0, 6.0, 5.0, 5.2);
        StockFundamentals fundamentals = fundamentals(BigDecimal.TEN, BigDecimal.ONE, null, List.of(), List.of(), null);

        List<PlausibilitySignal> signals =
                ScorePlausibilityGate.check(analysis, fundamentals, CONFIANCA_ALTA, SectorType.VAREJO);

        assertThat(signals).isEmpty();
    }

    @Test
    void naoSinalizaHardCapDeVarejoForaDoSetorVarejo() {
        StockAnalysis analysis = analysis(5.0, 5.0, 5.0, 5.0, 6.0, 5.0, 5.2);
        StockFundamentals fundamentals = fundamentals(BigDecimal.TEN, BigDecimal.ONE, null, List.of(),
                List.of(new QuarterlyResult("2026-06-30", BigDecimal.valueOf(1_000_000), BigDecimal.valueOf(-50_000))),
                null);

        List<PlausibilitySignal> signals =
                ScorePlausibilityGate.check(analysis, fundamentals, CONFIANCA_ALTA, SectorType.FINANCEIRO);

        assertThat(signals).isEmpty();
    }

    @Test
    void acumulaMultiplosSinaisNaMesmaAnalise() {
        StockAnalysis analysis = analysis(8.0, 8.0, 2.0, 5.0, 5.0, 5.0, 5.0);
        StockFundamentals fundamentals = fundamentals(
                BigDecimal.valueOf(-3.5), null, null, List.of(), List.of(), null);

        List<PlausibilitySignal> signals =
                ScorePlausibilityGate.check(analysis, fundamentals, CONFIANCA_ALTA, SectorType.INDUSTRIA);

        assertThat(signals).containsExactlyInAnyOrder(
                PlausibilitySignal.FUNDAMENTOS_ALTO_MOMENTUM_BAIXO,
                PlausibilitySignal.PE_NEGATIVO_VALUATION_ALTO_SEM_PVPA);
    }

    // -------------------------------------------------------------------------

    private StockAnalysis analysis(double fundamentos, double valuation, double regimeMomentum,
                                    double sentimento, double retorno, double gestaoRisco, double scoreGeral) {
        return new StockAnalysis(
                "TEST4", LocalDate.now(),
                new DimensionScore(fundamentos, "exp"),
                new DimensionScore(valuation, "exp"),
                new DimensionScore(regimeMomentum, "exp"),
                new DimensionScore(sentimento, "exp"),
                new DimensionScore(retorno, "exp"),
                new DimensionScore(gestaoRisco, "exp"),
                scoreGeral,
                "resumo"
        );
    }

    private StockFundamentals fundamentals(BigDecimal priceToEarnings, BigDecimal priceToBook,
                                            BigDecimal dividendYield, List<DividendEntry> dividendHistory,
                                            List<QuarterlyResult> quarterlyResults, BigDecimal earningsGrowth) {
        return new StockFundamentals(
                "TEST4", "TEST4.SA", "Teste S.A.", "Industrials", "Industrial", "BRL",
                "yfinance", null,
                priceToEarnings, priceToBook, 1_000_000_000L,
                BigDecimal.valueOf(0.15), BigDecimal.valueOf(0.08), BigDecimal.valueOf(0.1),
                BigDecimal.valueOf(0.12), earningsGrowth,
                BigDecimal.valueOf(0.5), 500_000_000L, 200_000_000L, 1_000_000_000L, 100_000_000L, 80_000_000L,
                BigDecimal.valueOf(5.0),
                dividendYield, dividendHistory,
                quarterlyResults,
                BigDecimal.ONE, BigDecimal.TEN, BigDecimal.ONE, 1_000_000L
        );
    }

    private DividendEntry dividendo() {
        return new DividendEntry("2026-03-01", BigDecimal.valueOf(0.5));
    }
}
