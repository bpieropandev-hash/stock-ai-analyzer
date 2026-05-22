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

    @Value("${python.script.macro-path:scripts/fetch_macro.py}")
    private String macroScriptPath;

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
        MacroData macro = fetchMacro();
        String context = retrieveContext(fundamentals);
        String prompt = buildPrompt(fundamentals, macro, context);
        String rawResponse = chatModel.chat(prompt);
        log.debug("Resposta bruta do LLM para {}: {}", ticker, rawResponse);
        return parseAnalysis(ticker, sanitize(rawResponse));
    }

    // -------------------------------------------------------------------------
    // Execução dos scripts Python
    // -------------------------------------------------------------------------

    private StockFundamentals fetchFundamentals(String ticker) throws Exception {
        Process process = new ProcessBuilder("python", fundamentalsScriptPath, ticker).start();
        String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        String stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
        int exitCode = process.waitFor();

        if (!stderr.isBlank()) log.warn("fetch_fundamentals.py [{}]: {}", ticker, stderr.strip());
        if (exitCode != 0) throw new IllegalStateException(
                "Script de fundamentals encerrou com código %d para %s".formatted(exitCode, ticker));

        return objectMapper.readValue(stdout.strip(), StockFundamentals.class);
    }

    /** Retorna null se o script falhar — a análise continua sem dados macro. */
    private MacroData fetchMacro() {
        try {
            Process process = new ProcessBuilder("python", macroScriptPath).start();
            String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            String stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
            int exitCode = process.waitFor();

            if (!stderr.isBlank()) log.warn("fetch_macro.py: {}", stderr.strip());
            if (exitCode != 0 || stdout.isBlank()) return null;

            return objectMapper.readValue(stdout.strip(), MacroData.class);
        } catch (Exception e) {
            log.warn("Falha ao buscar dados macroeconômicos: {}", e.getMessage());
            return null;
        }
    }

    // -------------------------------------------------------------------------
    // RAG — recuperação de contexto histórico
    // -------------------------------------------------------------------------

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

    // -------------------------------------------------------------------------
    // Construção do prompt
    // -------------------------------------------------------------------------

    private String buildPrompt(StockFundamentals f, MacroData macro, String context) {
        String macroSection = macro != null ? buildMacroText(macro) : "Dados macroeconômicos indisponíveis.";
        return """
                Responda APENAS com JSON válido, sem markdown, sem ```json, sem texto antes ou depois. Baseie sua análise EXCLUSIVAMENTE nos dados fornecidos no contexto.

                Você é um analista de investimentos especializado em ações da B3.

                DADOS FUNDAMENTALISTAS:
                %s

                CONTEXTO MACROECONÔMICO:
                %s

                CONTEXTO HISTÓRICO (RAG):
                %s

                INSTRUÇÕES DE ANÁLISE:
                - Fundamentos: qualidade dos resultados, margens, ROE/ROA e crescimento
                - Valuation: P/L e P/VPA vs setor; FCL como suporte ao preço justo
                - Regime/Momentum: tendência trimestral de receita/lucro e impacto do câmbio USD/BRL
                - Sentimento Institucional: beta, amplitude 52 semanas e volume médio
                - Retorno ao Acionista: dividend yield, consistência do histórico de dividendos e FCL
                - Gestão de Risco: Selic eleva custo de capital; dívida/patrimônio e exposição cambial

                Responda SOMENTE com JSON válido, sem texto adicional, sem markdown.

                {
                  "fundamentos": {"score": <0-10>, "explicacao": "<1 frase em português>"},
                  "valuation": {"score": <0-10>, "explicacao": "<1 frase em português>"},
                  "regimeMomentum": {"score": <0-10>, "explicacao": "<1 frase em português>"},
                  "sentimentoInstitucional": {"score": <0-10>, "explicacao": "<1 frase em português>"},
                  "retornoAcionista": {"score": <0-10>, "explicacao": "<1 frase em português>"},
                  "gestaoRisco": {"score": <0-10>, "explicacao": "<1 frase em português>"},
                  "scoreGeral": <média dos 6 scores>,
                  "resumo": "<síntese em 2 frases>"
                }
                """.formatted(buildFundamentalsText(f), macroSection, context);
    }

    private String buildFundamentalsText(StockFundamentals f) {
        StringBuilder sb = new StringBuilder();

        sb.append("Ação: ").append(nvl(f.name())).append(" (").append(f.ticker()).append(")")
          .append(" | Setor: ").append(nvl(f.sector()))
          .append(" | Segmento: ").append(nvl(f.industry()))
          .append(" | Moeda: ").append(nvl(f.currency())).append("\n");

        sb.append("\nVALUATION\n")
          .append("P/L: ").append(fmt(f.priceToEarnings()))
          .append(" | P/VPA: ").append(fmt(f.priceToBook()))
          .append(" | Market Cap: ").append(fmtBrl(f.marketCap()))
          .append(" | Beta: ").append(fmt(f.beta())).append("\n");

        sb.append("\nRENTABILIDADE\n")
          .append("ROE: ").append(fmtPct(f.roe())).append("%")
          .append(" | ROA: ").append(fmtPct(f.roa())).append("%")
          .append(" | Margem Líquida: ").append(fmtPct(f.netMargin())).append("%")
          .append(" | Margem Operacional: ").append(fmtPct(f.operatingMargin())).append("%").append("\n")
          .append("Cresc. Receita (YoY): ").append(fmt(f.revenueGrowth())).append("%")
          .append(" | Cresc. Lucros: ").append(fmtPct(f.earningsGrowth())).append("%").append("\n");

        sb.append("\nBALANÇO PATRIMONIAL\n")
          .append("Dívida Total: ").append(fmtBrl(f.totalDebt()))
          .append(" | Caixa: ").append(fmtBrl(f.totalCash()))
          .append(" | Receita Total: ").append(fmtBrl(f.totalRevenue())).append("\n")
          .append("FCO: ").append(fmtBrl(f.operatingCashflow()))
          .append(" | FCL: ").append(fmtBrl(f.freeCashflow()))
          .append(" | Dívida/Patrimônio: ").append(fmt(f.debtToEquity())).append("\n");

        sb.append("\nDIVIDENDOS\n")
          .append("Dividend Yield: ").append(fmtPct(f.dividendYield())).append("%")
          .append(" | Últimos pagamentos: ").append(fmtDividendHistory(f.dividendHistory())).append("\n");

        sb.append("\nDADOS DE MERCADO\n")
          .append("Máx. 52s: ").append(fmt(f.fiftyTwoWeekHigh()))
          .append(" | Mín. 52s: ").append(fmt(f.fiftyTwoWeekLow()))
          .append(" | Volume Médio: ").append(f.averageVolume() != null
                  ? String.format("%,d", f.averageVolume()) : "N/D").append("\n");

        sb.append("\nRESULTADOS TRIMESTRAIS\n")
          .append(fmtQuarterly(f.quarterlyResults()));

        return sb.toString();
    }

    private String buildMacroText(MacroData m) {
        return "Selic: %s%% a.a. | IPCA 12m: %s%% | USD/BRL: %s"
                .formatted(fmt(m.selicPct()), fmt(m.ipca12mPct()), fmt(m.usdBrl()));
    }

    // -------------------------------------------------------------------------
    // Helpers de formatação
    // -------------------------------------------------------------------------

    private String fmtDividendHistory(List<DividendEntry> list) {
        if (list == null || list.isEmpty()) return "N/D";
        return list.stream()
                .map(d -> "%s: R$%s".formatted(
                        d.date(),
                        d.value() != null ? d.value().setScale(4, RoundingMode.HALF_UP).toPlainString() : "N/D"))
                .collect(Collectors.joining(" | "));
    }

    private String fmtQuarterly(List<QuarterlyResult> list) {
        if (list == null || list.isEmpty()) return "N/D";
        return list.stream()
                .map(q -> "%s: Receita %s, Lucro %s".formatted(
                        q.period(), fmtBrlBd(q.revenue()), fmtBrlBd(q.earnings())))
                .collect(Collectors.joining("\n"));
    }

    private String fmtBrl(Long v) {
        if (v == null) return "N/D";
        return "R$ %.2f bi".formatted(v / 1_000_000_000.0);
    }

    private String fmtBrlBd(BigDecimal v) {
        if (v == null) return "N/D";
        return "R$ %.2f bi".formatted(v.doubleValue() / 1_000_000_000.0);
    }

    private String nvl(String v) { return v != null ? v : "N/D"; }

    private String fmt(BigDecimal v) {
        return v != null ? v.setScale(2, RoundingMode.HALF_UP).toPlainString() : "N/D";
    }

    /** yfinance retorna roe, roa, margens, earningsGrowth e dividendYield como decimal (0.25 = 25%). */
    private String fmtPct(BigDecimal v) {
        if (v == null) return "N/D";
        return v.multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    // -------------------------------------------------------------------------
    // Parse da resposta do LLM
    // -------------------------------------------------------------------------

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
                    root.path("resumo").asString("N/D")
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
                node.path("explicacao").asString("N/D"));
    }

    private StockAnalysis fallbackAnalysis(String ticker) {
        DimensionScore nd = new DimensionScore(0.0, "Análise indisponível");
        return new StockAnalysis(ticker, LocalDate.now(), nd, nd, nd, nd, nd, nd, 0.0,
                "Análise indisponível. Verifique se o Ollama está em execução com o modelo configurado.");
    }
}
