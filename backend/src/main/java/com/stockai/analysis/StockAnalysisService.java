package com.stockai.analysis;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class StockAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(StockAnalysisService.class);

    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> embeddingStore;
    private final ChatModel chatModel;
    private final ObjectMapper objectMapper;

    @Value("${python.script.fundamentals-path:scripts/fetch_fundamentals.py}")
    private String fundamentalsScriptPath;

    public StockAnalysisService(
            EmbeddingModel embeddingModel,
            EmbeddingStore<TextSegment> embeddingStore,
            ChatModel chatModel,
            ObjectMapper objectMapper) {
        this.embeddingModel = embeddingModel;
        this.embeddingStore = embeddingStore;
        this.chatModel = chatModel;
        this.objectMapper = objectMapper;
    }

    public StockAnalysis analyze(String ticker) throws Exception {
        StockFundamentals fundamentals = fetchFundamentals(ticker);
        String context = retrieveContext(fundamentals);
        String prompt = buildPrompt(fundamentals, context);
        String rawResponse = chatModel.chat(prompt);
        log.debug("Resposta bruta do LLM para {}: {}", ticker, rawResponse);
        return parseAnalysis(ticker, sanitize(rawResponse));
    }

    private StockFundamentals fetchFundamentals(String ticker) throws Exception {
        Process process = new ProcessBuilder("python", fundamentalsScriptPath, ticker).start();

        String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        String stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
        int exitCode = process.waitFor();

        if (!stderr.isBlank()) {
            log.warn("fetch_fundamentals.py [{}]: {}", ticker, stderr.strip());
        }
        if (exitCode != 0) {
            throw new IllegalStateException(
                    "Script de fundamentals encerrou com código %d para %s".formatted(exitCode, ticker));
        }

        return objectMapper.readValue(stdout.strip(), StockFundamentals.class);
    }

    private String retrieveContext(StockFundamentals fundamentals) {
        try {
            Embedding queryEmbedding = embeddingModel
                    .embed(TextSegment.from(buildFundamentalsText(fundamentals)))
                    .content();

            List<EmbeddingMatch<TextSegment>> matches = embeddingStore.search(
                    EmbeddingSearchRequest.builder()
                            .queryEmbedding(queryEmbedding)
                            .maxResults(3)
                            .build()
            ).matches();

            if (matches.isEmpty()) return "Sem contexto histórico disponível.";

            return matches.stream()
                    .map(m -> m.embedded().text())
                    .collect(Collectors.joining("\n---\n"));
        } catch (Exception e) {
            log.warn("Falha ao recuperar contexto pgvector para {}: {}", fundamentals.ticker(), e.getMessage());
            return "Sem contexto histórico disponível.";
        }
    }

    private String buildPrompt(StockFundamentals f, String context) {
        return """
                Você é um analista de investimentos especializado em ações da B3.

                DADOS FUNDAMENTALISTAS:
                %s

                CONTEXTO HISTÓRICO:
                %s

                Com base nos dados acima, gere um JSON com scores de 0 a 10 para cada dimensão.
                Responda SOMENTE com JSON válido, sem texto adicional, sem markdown.

                {
                  "fundamentos": {"score": <número>, "explicacao": "<1 frase em português>"},
                  "valuation": {"score": <número>, "explicacao": "<1 frase em português>"},
                  "regimeMomentum": {"score": <número>, "explicacao": "<1 frase em português>"},
                  "sentimentoInstitucional": {"score": <número>, "explicacao": "<1 frase em português>"},
                  "retornoAcionista": {"score": <número>, "explicacao": "<1 frase em português>"},
                  "gestaoRisco": {"score": <número>, "explicacao": "<1 frase em português>"},
                  "scoreGeral": <média dos 6 scores>,
                  "resumo": "<síntese em 2 frases>"
                }
                """.formatted(buildFundamentalsText(f), context);
    }

    private String buildFundamentalsText(StockFundamentals f) {
        return "Ação: %s (%s) | Setor: %s | Segmento: %s\nP/L: %s | ROE: %s%% | Margem Líquida: %s%%\nDívida/Patrimônio: %s | Crescimento de Receita: %s%% | Dividend Yield: %s%%"
                .formatted(
                        nvl(f.name()), f.ticker(), nvl(f.sector()), nvl(f.industry()),
                        fmt(f.priceToEarnings()),
                        fmtPct(f.roe()), fmtPct(f.netMargin()),
                        fmt(f.debtToEquity()), fmt(f.revenueGrowth()), fmtPct(f.dividendYield()));
    }

    /** Remove blocos markdown que alguns modelos inserem ao redor do JSON. */
    private String sanitize(String raw) {
        String s = raw.strip();
        if (s.startsWith("```")) {
            s = s.replaceFirst("```[a-z]*\\n?", "").replaceFirst("(?s)```\\s*$", "").strip();
        }
        return s;
    }

    private StockAnalysis parseAnalysis(String ticker, String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            return new StockAnalysis(
                    ticker,
                    LocalDate.now(),
                    parseDimension(root.get("fundamentos")),
                    parseDimension(root.get("valuation")),
                    parseDimension(root.get("regimeMomentum")),
                    parseDimension(root.get("sentimentoInstitucional")),
                    parseDimension(root.get("retornoAcionista")),
                    parseDimension(root.get("gestaoRisco")),
                    root.path("scoreGeral").asDouble(0.0),
                    root.path("resumo").asText("N/D")
            );
        } catch (Exception e) {
            log.error("Falha ao parsear resposta do LLM para {}: {}", ticker, e.getMessage());
            return fallbackAnalysis(ticker);
        }
    }

    private DimensionScore parseDimension(JsonNode node) {
        if (node == null || node.isNull()) return new DimensionScore(0.0, "N/D");
        return new DimensionScore(
                node.path("score").asDouble(0.0),
                node.path("explicacao").asText("N/D"));
    }

    private StockAnalysis fallbackAnalysis(String ticker) {
        DimensionScore nd = new DimensionScore(0.0, "Análise indisponível");
        return new StockAnalysis(ticker, LocalDate.now(), nd, nd, nd, nd, nd, nd, 0.0,
                "Análise indisponível. Verifique se o Ollama está em execução com o modelo configurado.");
    }

    private String nvl(String v) { return v != null ? v : "N/D"; }

    private String fmt(BigDecimal v) {
        return v != null ? v.setScale(2, RoundingMode.HALF_UP).toPlainString() : "N/D";
    }

    private String fmtPct(BigDecimal v) {
        if (v == null) return "N/D";
        return v.multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
