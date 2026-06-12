package com.stockai.analysis;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
        assertEquals(6.7, parsed.analysis().scoreGeral());
    }

    @Test
    void parseiaDimensoesEResumo() {
        AnalysisParser.ParsedAnalysis parsed = parser.parse("PETR4.SA", validJson("6.7"));
        assertEquals(8.0, parsed.analysis().fundamentos().score());
        assertEquals("ROE alto", parsed.analysis().fundamentos().explicacao());
        assertEquals("resumo", parsed.analysis().resumo());
        assertEquals("simples", parsed.simpleSummary());
        assertEquals("PETR4.SA", parsed.analysis().ticker());
    }

    @Test
    void clampaScoresForaDoIntervalo() {
        String json = validJson("5.0")
                .replace("\"score\": 8.0", "\"score\": 15.0")
                .replace("\"score\": 9.0", "\"score\": -3.0");
        AnalysisParser.ParsedAnalysis parsed = parser.parse("VALE3.SA", json);
        assertEquals(10.0, parsed.analysis().fundamentos().score());
        assertEquals(0.0, parsed.analysis().retornoAcionista().score());
    }

    @Test
    void dimensaoAusenteLancaExcecaoEmVezDeZeroSilencioso() {
        String json = """
                {
                  "fundamentos": {"score": 8.0, "explicacao": "ok"},
                  "valuation": {"score": 6.0, "explicacao": "ok"}
                }
                """;
        assertThrows(IllegalStateException.class, () -> parser.parse("ITUB4.SA", json));
    }

    @Test
    void sanitizeRemoveBlocosMarkdown() {
        String wrapped = "```json\n{\"a\": 1}\n```";
        assertEquals("{\"a\": 1}", parser.sanitize(wrapped));
    }

    @Test
    void sanitizeMantemJsonLimpo() {
        assertEquals("{\"a\": 1}", parser.sanitize("  {\"a\": 1}  "));
    }

    @Test
    void parseiaRespostaComMarkdownFence() {
        String wrapped = "```json\n" + validJson("6.7") + "\n```";
        AnalysisParser.ParsedAnalysis parsed = parser.parse("WEGE3.SA", wrapped);
        assertEquals(6.7, parsed.analysis().scoreGeral());
    }

    @Test
    void deriveRecommendationCobreTodasAsFaixas() {
        assertEquals("ATRATIVO", parser.deriveRecommendation(7.6));
        assertEquals("NEUTRO", parser.deriveRecommendation(7.5));
        assertEquals("NEUTRO", parser.deriveRecommendation(6.0));
        assertEquals("CAUTELA", parser.deriveRecommendation(5.9));
        assertEquals("CAUTELA", parser.deriveRecommendation(4.5));
        assertEquals("DESFAVORÁVEL", parser.deriveRecommendation(4.4));
        assertEquals("DESFAVORÁVEL", parser.deriveRecommendation(0.0));
    }

    @Test
    void computeScoreGeralArredondaParaUmaCasa() {
        DimensionScore d = new DimensionScore(5.0, "x");
        DimensionScore alto = new DimensionScore(6.0, "x");
        // (5+5+5+5+5+6)/6 = 5.1666... → 5.2
        assertEquals(5.2, parser.computeScoreGeral(d, d, d, d, d, alto));
    }
}
