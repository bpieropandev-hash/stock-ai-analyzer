package com.stockai.analysis;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.filter.MetadataFilterBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class StockAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(StockAnalysisService.class);

    private static final String CACHE_PREFIX = "analysis:";
    private static final String DISCLAIMER =
            "Esta análise é gerada por IA e não constitui recomendação de investimento. " +
            "Consulte um assessor financeiro credenciado.";

    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> embeddingStore;
    private final ChatModel chatModel;
    private final ObjectMapper objectMapper;
    private final StockEmbeddingService embeddingService;
    private final ScoreHistoryService scoreHistoryService;
    private final ScoreAlertService scoreAlertService;
    private final RedisTemplate<String, String> redisTemplate;
    private final SectorClassifier sectorClassifier;
    private final SectorPromptConfig sectorPromptConfig;

    @Value("${python.script.fundamentals-path:scripts/fetch_fundamentals.py}")
    private String fundamentalsScriptPath;

    @Value("${python.script.macro-path:scripts/fetch_macro.py}")
    private String macroScriptPath;

    @Value("${python.script.news-path:scripts/fetch_news.py}")
    private String newsScriptPath;

    @Value("${python.script.sentiment-path:scripts/analyze_sentiment.py}")
    private String sentimentScriptPath;

    @Value("${python.script.technical-indicators-path:scripts/fetch_technical_indicators.py}")
    private String technicalScriptPath;

    @Value("${huggingface.token:}")
    private String huggingfaceToken;

    public StockAnalysisService(
            EmbeddingModel embeddingModel,
            EmbeddingStore<TextSegment> embeddingStore,
            ChatModel chatModel,
            ObjectMapper objectMapper,
            StockEmbeddingService embeddingService,
            ScoreHistoryService scoreHistoryService,
            ScoreAlertService scoreAlertService,
            RedisTemplate<String, String> redisTemplate,
            SectorClassifier sectorClassifier,
            SectorPromptConfig sectorPromptConfig) {
        this.embeddingModel = embeddingModel;
        this.embeddingStore = embeddingStore;
        this.chatModel = chatModel;
        this.objectMapper = objectMapper;
        this.embeddingService = embeddingService;
        this.scoreHistoryService = scoreHistoryService;
        this.scoreAlertService = scoreAlertService;
        this.redisTemplate = redisTemplate;
        this.sectorClassifier = sectorClassifier;
        this.sectorPromptConfig = sectorPromptConfig;
    }

    public AnalysisResponse analyze(String ticker) throws Exception {
        String cacheKey = CACHE_PREFIX + ticker;
        String cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            try {
                log.debug("Cache HIT para {}", ticker);
                return objectMapper.readValue(cached, AnalysisResponse.class);
            } catch (Exception e) {
                // cache com formato antigo (StockAnalysis) — descarta e recomputa
                log.debug("Cache stale para {} — recomputando", ticker);
            }
        }

        SectorType sector = sectorClassifier.classify(ticker);
        StockFundamentals fundamentals = fetchFundamentals(ticker);
        MacroData macro = fetchMacro();
        List<NewsItem> news = fetchNews(ticker);
        String context = retrieveContext(fundamentals);
        SentimentResult sentiment = fetchSentiment(news);
        TechnicalIndicators technical = fetchTechnicalIndicators(ticker);
        String prompt = buildPrompt(fundamentals, macro, context, sentiment, technical, sector);
        String rawResponse = chatModel.chat(prompt);
        String modelUsed = EmbeddingStoreConfig.ACTIVE_MODEL.get();
        EmbeddingStoreConfig.ACTIVE_MODEL.remove();
        log.info("Análise gerada via {} para {}", modelUsed, ticker);
        log.debug("Resposta bruta do LLM para {}: {}", ticker, rawResponse);
        String sanitized = sanitize(rawResponse);
        StockAnalysis analysis = parseAnalysis(ticker, sanitized);
        String simpleSummary = parseSimpleSummary(sanitized);
        indexAnalysis(analysis, fundamentals);
        scoreHistoryService.saveScore(analysis);
        scoreAlertService.checkAndAlert(analysis);

        AnalysisResponse response = new AnalysisResponse(
                analysis,
                sector.name(),
                simpleSummary,
                deriveRecommendation(analysis.scoreGeral()),
                DISCLAIMER
        );

        try {
            redisTemplate.opsForValue().set(cacheKey, objectMapper.writeValueAsString(response), Duration.ofMinutes(30));
        } catch (Exception e) {
            log.warn("Falha ao salvar análise no cache Redis para {}: {}", ticker, e.getMessage());
        }

        return response;
    }

    public AnalysisResponse refreshAnalysis(String ticker) throws Exception {
        redisTemplate.delete(CACHE_PREFIX + ticker);
        return analyze(ticker);
    }

    private String deriveRecommendation(double score) {
        if (score > 7.5)  return "COMPRAR";
        if (score >= 6.0) return "MANTER";
        if (score >= 4.5) return "AGUARDAR";
        return "EVITAR";
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

    /** Retorna lista vazia se o script falhar — a análise continua sem notícias. */
    private List<NewsItem> fetchNews(String ticker) {
        try {
            Process process = new ProcessBuilder("python", newsScriptPath, ticker).start();
            String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            String stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
            int exitCode = process.waitFor();

            if (!stderr.isBlank()) log.warn("fetch_news.py [{}]: {}", ticker, stderr.strip());
            if (exitCode != 0 || stdout.isBlank()) return List.of();

            return objectMapper.readValue(stdout.strip(),
                    objectMapper.getTypeFactory().constructCollectionType(List.class, NewsItem.class));
        } catch (Exception e) {
            log.warn("Falha ao buscar notícias para {}: {}", ticker, e.getMessage());
            return List.of();
        }
    }

    /** Retorna score neutro (5.0) se a lista estiver vazia ou o script falhar. */
    private SentimentResult fetchSentiment(List<NewsItem> news) {
        if (news == null || news.isEmpty()) {
            return new SentimentResult(5.0, 0, 0, 0, 0.0);
        }
        try {
            List<String> titles = news.stream()
                    .map(NewsItem::title)
                    .filter(t -> t != null && !t.isBlank())
                    .collect(Collectors.toList());
            if (titles.isEmpty()) return new SentimentResult(5.0, 0, 0, 0, 0.0);

            String titlesJson = objectMapper.writeValueAsString(titles);
            ProcessBuilder pb = new ProcessBuilder("python", sentimentScriptPath, titlesJson);
            pb.environment().put("HUGGINGFACE_TOKEN", huggingfaceToken);
            Process process = pb.start();
            String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            String stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
            process.waitFor();

            if (!stderr.isBlank()) log.warn("analyze_sentiment.py: {}", stderr.strip());
            if (stdout.isBlank()) return new SentimentResult(5.0, 0, 0, news.size(), 0.0);

            JsonNode root = objectMapper.readTree(stdout.strip());
            return new SentimentResult(
                    root.path("sentimentScore").asDouble(5.0),
                    root.path("distribution").path("positive").asInt(0),
                    root.path("distribution").path("negative").asInt(0),
                    root.path("distribution").path("neutral").asInt(0),
                    root.path("confidence").asDouble(0.0)
            );
        } catch (Exception e) {
            log.warn("Falha ao calcular sentimento FinBERT: {}", e.getMessage());
            return new SentimentResult(5.0, 0, 0, 0, 0.0);
        }
    }

    /** Retorna null se o script falhar — a análise continua sem indicadores técnicos. */
    private TechnicalIndicators fetchTechnicalIndicators(String ticker) {
        try {
            Process process = new ProcessBuilder("python", technicalScriptPath, ticker).start();
            String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            String stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
            process.waitFor();

            if (!stderr.isBlank()) log.warn("fetch_technical_indicators.py [{}]: {}", ticker, stderr.strip());
            if (stdout.isBlank()) return null;

            JsonNode root = objectMapper.readTree(stdout.strip());
            return new TechnicalIndicators(
                    root.path("currentPrice").isNull() ? null : root.path("currentPrice").asDouble(),
                    root.path("rsi").isNull() ? null : root.path("rsi").asDouble(),
                    root.path("macdLine").isNull() ? null : root.path("macdLine").asDouble(),
                    root.path("macdSignal").isNull() ? null : root.path("macdSignal").asDouble(),
                    root.path("macdHistogram").isNull() ? null : root.path("macdHistogram").asDouble(),
                    root.path("sma20").isNull() ? null : root.path("sma20").asDouble(),
                    root.path("sma50").isNull() ? null : root.path("sma50").asDouble(),
                    root.path("bollingerUpper").isNull() ? null : root.path("bollingerUpper").asDouble(),
                    root.path("bollingerMiddle").isNull() ? null : root.path("bollingerMiddle").asDouble(),
                    root.path("bollingerLower").isNull() ? null : root.path("bollingerLower").asDouble(),
                    root.path("volumeRatio").isNull() ? null : root.path("volumeRatio").asDouble(),
                    root.path("technicalSignal").asText(null)
            );
        } catch (Exception e) {
            log.warn("Falha ao calcular indicadores técnicos para {}: {}", ticker, e.getMessage());
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

            Filter tickerFilter = MetadataFilterBuilder.metadataKey("ticker")
                    .isEqualTo(fundamentals.ticker());

            List<EmbeddingMatch<TextSegment>> matches = embeddingStore.search(
                    EmbeddingSearchRequest.builder()
                            .queryEmbedding(queryEmbedding)
                            .maxResults(3)
                            .filter(tickerFilter)
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

    private String buildPrompt(StockFundamentals f, MacroData macro, String context,
                               SentimentResult sentiment, TechnicalIndicators technical, SectorType sector) {
        String macroSection = macro != null ? buildMacroText(macro) : "Dados macroeconômicos indisponíveis.";
        String technicalSection = buildTechnicalText(technical);
        String sentimentSection = buildSentimentText(sentiment);
        String sectorSection = sectorPromptConfig.getInstructions(sector);
        return """
                Responda APENAS com JSON válido, sem markdown, sem ```json, sem texto antes ou depois. Baseie sua análise EXCLUSIVAMENTE nos dados fornecidos no contexto.

                Você é um analista de investimentos especializado em ações da B3.

                DADOS FUNDAMENTALISTAS:
                %s

                CONTEXTO MACROECONÔMICO:
                %s

                %s

                %s

                CONTEXTO HISTÓRICO (RAG):
                %s

                CONTEXTO SETORIAL (%s):
                %s

                INSTRUÇÕES DE ANÁLISE:
                - Fundamentos: qualidade dos resultados, margens, ROE/ROA e crescimento
                - Valuation: P/L e P/VPA vs setor; FCL como suporte ao preço justo
                - Regime/Momentum: use technicalSignal como base objetiva, ajustando pelo contexto fundamentalista e câmbio USD/BRL
                - Sentimento Institucional: use o score FinBERT como base objetiva, ajustando por beta e amplitude 52 semanas
                - Retorno ao Acionista: dividend yield, consistência do histórico de dividendos e FCL
                - Gestão de Risco: Selic eleva custo de capital; dívida/patrimônio e exposição cambial
                - Aplique as particularidades do CONTEXTO SETORIAL ao calibrar cada dimensão

                Responda SOMENTE com JSON válido, sem texto adicional, sem markdown.

                {
                  "fundamentos": {"score": <0-10>, "explicacao": "<1 frase em português>"},
                  "valuation": {"score": <0-10>, "explicacao": "<1 frase em português>"},
                  "regimeMomentum": {"score": <0-10>, "explicacao": "<1 frase em português>"},
                  "sentimentoInstitucional": {"score": <0-10>, "explicacao": "<1 frase em português>"},
                  "retornoAcionista": {"score": <0-10>, "explicacao": "<1 frase em português>"},
                  "gestaoRisco": {"score": <0-10>, "explicacao": "<1 frase em português>"},
                  "scoreGeral": <média dos 6 scores>,
                  "resumo": "<síntese em 2 frases>",
                  "simpleSummary": "<explique em 2 frases simples como se falasse com alguém que nunca investiu, sem jargões financeiros>"
                }
                """.formatted(buildFundamentalsText(f), macroSection, technicalSection, sentimentSection,
                              context, sector.name(), sectorSection);
    }

    private String buildTechnicalText(TechnicalIndicators t) {
        if (t == null) return "INDICADORES TÉCNICOS:\nIndisponíveis.";

        String rsiLabel = t.rsi() != null
                ? (t.rsi() < 30 ? " (sobrevendido)" : t.rsi() > 70 ? " (sobrecomprado)" : "")
                : "";
        String macdDir = t.macdHistogram() != null
                ? (t.macdHistogram() > 0 ? " ↑ bullish" : " ↓ bearish")
                : "";

        return """
                INDICADORES TÉCNICOS:
                RSI (14): %s%s | MACD Linha: %s | Signal: %s | Hist: %s%s
                SMA20: %s | SMA50: %s | %s
                Bollinger: Superior %s / Médio %s / Inferior %s
                Volume Ratio (20d): %sx
                Sinal Técnico: %s

                Use technicalSignal como base objetiva para regimeMomentum, ajustando pelo contexto fundamentalista."""
                .formatted(
                        fmtD(t.rsi()), rsiLabel,
                        fmtD(t.macdLine()), fmtD(t.macdSignal()), fmtD(t.macdHistogram()), macdDir,
                        fmtD(t.sma20()), fmtD(t.sma50()), priceVsMA(t.currentPrice(), t.sma20(), t.sma50()),
                        fmtD(t.bollingerUpper()), fmtD(t.bollingerMiddle()), fmtD(t.bollingerLower()),
                        fmtD(t.volumeRatio()),
                        t.technicalSignal() != null ? t.technicalSignal() : "N/D"
                );
    }

    private String priceVsMA(Double price, Double sma20, Double sma50) {
        if (price == null) return "Preço indisponível";
        boolean aboveSma20 = sma20 != null && price > sma20;
        boolean aboveSma50 = sma50 != null && price > sma50;
        if (aboveSma20 && aboveSma50) return "Preço acima de SMA20 e SMA50";
        if (aboveSma20)               return "Preço acima de SMA20, abaixo de SMA50";
        if (aboveSma50)               return "Preço abaixo de SMA20, acima de SMA50";
        return "Preço abaixo de SMA20 e SMA50";
    }

    private String fmtD(Double v) {
        return v != null ? String.format("%.2f", v) : "N/D";
    }

    private String buildSentimentText(SentimentResult s) {
        return """
                ANÁLISE DE SENTIMENTO DAS NOTÍCIAS (FinBERT):
                Score: %.2f/10
                Distribuição: %d positivas, %d negativas, %d neutras
                Confiança média: %.2f

                Use este score como base objetiva para sentimentoInstitucional, ajustando pelo contexto macro e risco político."""
                .formatted(s.score(), s.positiveCount(), s.negativeCount(), s.neutralCount(), s.confidence());
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
        return "Selic: %s%% a.a. | IPCA 12m: %s%% | USD/BRL: %s | Brent: USD %s (%s%%) | WTI: USD %s (%s%%)"
                .formatted(
                        fmt(m.selicPct()), fmt(m.ipca12mPct()), fmt(m.usdBrl()),
                        fmt(m.brentPrice()), fmt(m.brentChangePct()),
                        fmt(m.wtiPrice()), fmt(m.wtiChangePct()));
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

    private String parseSimpleSummary(String json) {
        try {
            return objectMapper.readTree(json).path("simpleSummary").asText("Análise indisponível.");
        } catch (Exception e) {
            return "Análise indisponível.";
        }
    }

    private DimensionScore parseDimension(JsonNode node) {
        if (node == null || node.isNull()) return new DimensionScore(0.0, "N/D");
        return new DimensionScore(
                node.path("score").asDouble(0.0),
                node.path("explicacao").asString("N/D"));
    }

    private void indexAnalysis(StockAnalysis analysis, StockFundamentals fundamentals) {
        try {
            embeddingService.embedAndStoreAnalysis(analysis, fundamentals);
        } catch (Exception e) {
            log.warn("Falha ao indexar análise para {} — RAG não afetado: {}", analysis.ticker(), e.getMessage());
        }
    }

    private StockAnalysis fallbackAnalysis(String ticker) {
        DimensionScore nd = new DimensionScore(0.0, "Análise indisponível");
        return new StockAnalysis(ticker, LocalDate.now(), nd, nd, nd, nd, nd, nd, 0.0,
                "Análise indisponível. Verifique se o Ollama está em execução com o modelo configurado.");
    }
}
