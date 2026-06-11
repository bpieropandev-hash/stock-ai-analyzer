package com.stockai.analysis;

import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;

/**
 * Parse e validação da resposta do LLM.
 *
 * Decisões deliberadas:
 * - scoreGeral é SEMPRE calculado aqui (média aritmética das 6 dimensões) — LLMs
 *   erram aritmética e o valor retornado pelo modelo é descartado.
 * - Scores fora de [0, 10] são clampados; dimensão ausente é erro, não 0.0
 *   silencioso (0.0 falso derrubaria o score geral e dispararia alertas).
 */
@Component
public class AnalysisParser {

    public record ParsedAnalysis(StockAnalysis analysis, String simpleSummary) {}

    private final ObjectMapper objectMapper;

    public AnalysisParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ParsedAnalysis parse(String ticker, String rawResponse) {
        String sanitized = sanitize(rawResponse);
        JsonNode root = objectMapper.readTree(sanitized);

        DimensionScore fundamentos = requireDimension(root, "fundamentos");
        DimensionScore valuation = requireDimension(root, "valuation");
        DimensionScore regimeMomentum = requireDimension(root, "regimeMomentum");
        DimensionScore sentimento = requireDimension(root, "sentimentoInstitucional");
        DimensionScore retorno = requireDimension(root, "retornoAcionista");
        DimensionScore gestaoRisco = requireDimension(root, "gestaoRisco");

        double scoreGeral = computeScoreGeral(
                fundamentos, valuation, regimeMomentum, sentimento, retorno, gestaoRisco);

        StockAnalysis analysis = new StockAnalysis(
                ticker,
                LocalDate.now(),
                fundamentos, valuation, regimeMomentum, sentimento, retorno, gestaoRisco,
                scoreGeral,
                root.path("resumo").asText("N/D")
        );
        return new ParsedAnalysis(analysis, root.path("simpleSummary").asText("Análise indisponível."));
    }

    /** Média aritmética das 6 dimensões, arredondada a 1 casa decimal. */
    public double computeScoreGeral(DimensionScore... dimensions) {
        double sum = 0;
        for (DimensionScore d : dimensions) {
            sum += d.score();
        }
        return Math.round(sum / dimensions.length * 10.0) / 10.0;
    }

    public String deriveRecommendation(double score) {
        if (score > 7.5)  return "COMPRAR";
        if (score >= 6.0) return "MANTER";
        if (score >= 4.5) return "AGUARDAR";
        return "EVITAR";
    }

    /** Remove blocos markdown que alguns modelos inserem ao redor do JSON. */
    public String sanitize(String raw) {
        String s = raw.strip();
        if (s.startsWith("```")) {
            s = s.replaceFirst("```[a-z]*\\n?", "").replaceFirst("(?s)```\\s*$", "").strip();
        }
        return s;
    }

    private DimensionScore requireDimension(JsonNode root, String field) {
        JsonNode node = root.get(field);
        if (node == null || node.isNull() || !node.has("score")) {
            throw new IllegalStateException("Resposta do LLM sem a dimensão obrigatória: " + field);
        }
        double score = clamp(node.path("score").asDouble());
        return new DimensionScore(score, node.path("explicacao").asText("N/D"));
    }

    private double clamp(double score) {
        return Math.max(0.0, Math.min(10.0, score));
    }
}
