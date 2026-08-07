package com.stockai.analysis;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AnalysisParserTest {

    private AnalysisParser parser;

    @BeforeEach
    void setUp() {
        parser = new AnalysisParser(new ObjectMapper());
    }

    private String validJson(String scoreGeralDoLlm) {
        return """
                {
                  "analise": "raciocínio",
                  "fundamentos": {"score": 8.0, "explicacao": "ROE alto"},
                  "valuation": {"score": 6.0, "explicacao": "P/L na faixa"},
                  "regimeMomentum": {"score": 5.0, "explicacao": "neutro"},
                  "sentimentoInstitucional": {"score": 5.0, "explicacao": "neutro"},
                  "retornoAcionista": {"score": 9.0, "explicacao": "yield alto"},
                  "gestaoRisco": {"score": 7.0, "explicacao": "caixa líquido"},
                  "scoreGeral": %s,
                  "resumo": "resumo",
                  "simpleSummary": "simples"
                }
                """.formatted(scoreGeralDoLlm);
    }

    @Test
    void scoreGeralEhCalculadoEmJavaIgnorandoValorDoLlm() {
        // o LLM afirma 9.9 — o valor correto é a média (8+6+5+5+9+7)/6 = 6.666... → 6.7
        AnalysisParser.ParsedAnalysis parsed = parser.parse("PETR4.SA", validJson("9.9"));
        assertThat(parsed.analysis().scoreGeral()).isEqualTo(6.7);
    }

    @Test
    void parseiaDimensoesEResumo() {
        AnalysisParser.ParsedAnalysis parsed = parser.parse("PETR4.SA", validJson("6.7"));
        assertThat(parsed.analysis().fundamentos().score()).isEqualTo(8.0);
        assertThat(parsed.analysis().fundamentos().explicacao()).isEqualTo("ROE alto");
        assertThat(parsed.analysis().resumo()).isEqualTo("resumo");
        assertThat(parsed.simpleSummary()).isEqualTo("simples");
        assertThat(parsed.analysis().ticker()).isEqualTo("PETR4.SA");
    }

    @Test
    void clampaScoresForaDoIntervalo() {
        String json = validJson("5.0")
                .replace("\"score\": 8.0", "\"score\": 15.0")
                .replace("\"score\": 9.0", "\"score\": -3.0");
        AnalysisParser.ParsedAnalysis parsed = parser.parse("VALE3.SA", json);
        assertThat(parsed.analysis().fundamentos().score()).isEqualTo(10.0);
        assertThat(parsed.analysis().retornoAcionista().score()).isEqualTo(0.0);
    }

    @Test
    void dimensaoAusenteLancaExcecaoEmVezDeZeroSilencioso() {
        String json = """
                {
                  "fundamentos": {"score": 8.0, "explicacao": "ok"},
                  "valuation": {"score": 6.0, "explicacao": "ok"}
                }
                """;
        assertThatThrownBy(() -> parser.parse("ITUB4.SA", json))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void sanitizeRemoveBlocosMarkdown() {
        String wrapped = "```json\n{\"a\": 1}\n```";
        assertThat(parser.sanitize(wrapped)).isEqualTo("{\"a\": 1}");
    }

    @Test
    void sanitizeMantemJsonLimpo() {
        assertThat(parser.sanitize("  {\"a\": 1}  ")).isEqualTo("{\"a\": 1}");
    }

    @Test
    void parseiaRespostaComMarkdownFence() {
        String wrapped = "```json\n" + validJson("6.7") + "\n```";
        AnalysisParser.ParsedAnalysis parsed = parser.parse("WEGE3.SA", wrapped);
        assertThat(parsed.analysis().scoreGeral()).isEqualTo(6.7);
    }

    @Test
    void deriveRecommendationCobreTodasAsFaixas() {
        assertThat(parser.deriveRecommendation(7.6)).isEqualTo("ATRATIVO");
        assertThat(parser.deriveRecommendation(7.5)).isEqualTo("NEUTRO");
        assertThat(parser.deriveRecommendation(6.0)).isEqualTo("NEUTRO");
        assertThat(parser.deriveRecommendation(5.9)).isEqualTo("CAUTELA");
        assertThat(parser.deriveRecommendation(4.5)).isEqualTo("CAUTELA");
        assertThat(parser.deriveRecommendation(4.4)).isEqualTo("DESFAVORÁVEL");
        assertThat(parser.deriveRecommendation(0.0)).isEqualTo("DESFAVORÁVEL");
    }

    @Test
    void computeScoreGeralArredondaParaUmaCasa() {
        DimensionScore d = new DimensionScore(5.0, "x");
        DimensionScore alto = new DimensionScore(6.0, "x");
        // (5+5+5+5+5+6)/6 = 5.1666... → 5.2
        assertThat(parser.computeScoreGeral(d, d, d, d, d, alto)).isEqualTo(5.2);
    }
}
