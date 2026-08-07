package com.stockai.analysis;

import com.stockai.scheduler.PythonDataGateway;
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
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

@Service
public class StockAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(StockAnalysisService.class);

    private static final String CACHE_PREFIX = "analysis:";

    // Incrementar sempre que o prompt mudar — scores de versões diferentes não são comparáveis
    static final String PROMPT_VERSION = "v2.4";

    private static final String DISCLAIMER =
            "Esta análise é gerada por IA e não constitui recomendação de investimento. " +
            "Consulte um assessor financeiro credenciado.";

    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> embeddingStore;
    private final ChatModel geminiModel;
    private final ChatModel groqModel;
    private final ObjectMapper objectMapper;
    private final StockEmbeddingService embeddingService;
    private final ScoreHistoryService scoreHistoryService;
    private final ScoreAlertService scoreAlertService;
    private final AnalysisAuditService analysisAuditService;
    private final RedisTemplate<String, String> redisTemplate;
    private final SectorClassifier sectorClassifier;
    private final SectorPromptConfig sectorPromptConfig;
    private final SectorBenchmarks sectorBenchmarks;
    private final AnalysisParser parser;
    private final PythonDataGateway pythonGateway;

    // Single-flight: requisições simultâneas do mesmo ticker compartilham uma análise
    private final ConcurrentHashMap<String, ReentrantLock> tickerLocks = new ConcurrentHashMap<>();

    public StockAnalysisService(
            EmbeddingModel embeddingModel,
            EmbeddingStore<TextSegment> embeddingStore,
            @Qualifier("geminiChatModel") ChatModel geminiModel,
            @Qualifier("groqChatModel") ChatModel groqModel,
            ObjectMapper objectMapper,
            StockEmbeddingService embeddingService,
            ScoreHistoryService scoreHistoryService,
            ScoreAlertService scoreAlertService,
            AnalysisAuditService analysisAuditService,
            RedisTemplate<String, String> redisTemplate,
            SectorClassifier sectorClassifier,
            SectorPromptConfig sectorPromptConfig,
            SectorBenchmarks sectorBenchmarks,
            AnalysisParser parser,
            PythonDataGateway pythonGateway) {
        this.embeddingModel = embeddingModel;
        this.embeddingStore = embeddingStore;
        this.geminiModel = geminiModel;
        this.groqModel = groqModel;
        this.objectMapper = objectMapper;
        this.embeddingService = embeddingService;
        this.scoreHistoryService = scoreHistoryService;
        this.scoreAlertService = scoreAlertService;
        this.analysisAuditService = analysisAuditService;
        this.redisTemplate = redisTemplate;
        this.sectorClassifier = sectorClassifier;
        this.sectorPromptConfig = sectorPromptConfig;
        this.sectorBenchmarks = sectorBenchmarks;
        this.parser = parser;
        this.pythonGateway = pythonGateway;
    }

    public AnalysisResponse analyze(String ticker) throws Exception {
        // Normaliza para o formato B3 exigido pelo yfinance
        String normalized = normalizeTicker(ticker);
        String cacheKey = CACHE_PREFIX + normalized;

        AnalysisResponse cached = readCache(cacheKey, normalized);
        if (cached != null) return cached;

        // Single-flight: o segundo request do mesmo ticker espera e reaproveita o cache
        ReentrantLock lock = tickerLocks.computeIfAbsent(normalized, k -> new ReentrantLock());
        lock.lock();
        try {
            cached = readCache(cacheKey, normalized);
            if (cached != null) return cached;
            return doAnalyze(normalized, cacheKey);
        } finally {
            lock.unlock();
        }
    }

    public AnalysisResponse refreshAnalysis(String ticker) throws Exception {
        String normalized = normalizeTicker(ticker);
        redisTemplate.delete(CACHE_PREFIX + normalized);
        return analyze(normalized);
    }

    static String normalizeTicker(String ticker) {
        String upper = ticker.toUpperCase();
        return upper.endsWith(".SA") ? upper : upper + ".SA";
    }

    private AnalysisResponse readCache(String cacheKey, String ticker) {
        String cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached == null) return null;
        try {
            log.debug("Cache HIT para {}", ticker);
            return objectMapper.readValue(cached, AnalysisResponse.class);
        } catch (Exception e) {
            // cache com formato antigo — descarta e recomputa
            log.debug("Cache stale para {} — recomputando", ticker);
            return null;
        }
    }

    private AnalysisResponse doAnalyze(String ticker, String cacheKey) throws Exception {
        // Fase 1 — coletas independentes em paralelo (virtual threads)
        StockFundamentals fundamentals;
        MacroData macro;
        List<NewsItem> news;
        TechnicalIndicators technical;
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<StockFundamentals> fundamentalsF = executor.submit(() -> fetchFundamentals(ticker));
            Future<MacroData> macroF = executor.submit(this::fetchMacro);
            Future<List<NewsItem>> newsF = executor.submit(() -> fetchNews(ticker));
            Future<TechnicalIndicators> technicalF = executor.submit(() -> fetchTechnicalIndicators(ticker));

            fundamentals = fundamentalsF.get();   // obrigatório — propaga falha
            macro = safeGet(macroF, null);
            news = safeGet(newsF, List.of());
            technical = safeGet(technicalF, null);
        }

        // Fase 2 — dependem da fase 1
        SectorType sector = sectorClassifier.classify(ticker, fundamentals.sector());
        SentimentResult sentiment = fetchSentiment(news);
        String context = retrieveContext(fundamentals, sector);

        String prompt = buildPrompt(fundamentals, macro, context, sentiment, technical, sector);

        AnalysisParser.ParsedAnalysis parsed;
        String modelUsed;
        String rawResponse;
        try {
            rawResponse = geminiModel.chat(prompt);
            parsed = parser.parse(ticker, rawResponse);
            modelUsed = "gemini-2.5-flash";
            log.info("Análise gerada via Gemini para {}", ticker);
        } catch (Exception geminiEx) {
            log.warn("Gemini falhou para {} ({}), tentando Groq...", ticker, geminiEx.getMessage());
            try {
                rawResponse = groqModel.chat(prompt);
                parsed = parser.parse(ticker, rawResponse);
                modelUsed = "groq-qwen3-32b";
                log.info("Análise gerada via Groq (fallback) para {}", ticker);
            } catch (Exception groqEx) {
                log.error("Groq também falhou para {}: {}", ticker, groqEx.getMessage());
                throw new RuntimeException("Análise temporariamente indisponível, tente novamente");
            }
        }

        StockAnalysis analysis = parsed.analysis();
        indexAnalysis(analysis, fundamentals);
        ScoreHistoryEntity savedScore = scoreHistoryService.saveScore(analysis, modelUsed, PROMPT_VERSION);
        if (savedScore != null) {
            analysisAuditService.save(savedScore, prompt, rawResponse, analysis, parsed.reasoning());
        }
        scoreAlertService.checkAndAlert(analysis);

        AnalysisResponse response = new AnalysisResponse(
                analysis,
                sector.name(),
                parsed.simpleSummary(),
                parser.deriveRecommendation(analysis.scoreGeral()),
                DISCLAIMER,
                modelUsed,
                PROMPT_VERSION
        );

        try {
            redisTemplate.opsForValue().set(cacheKey, objectMapper.writeValueAsString(response), Duration.ofMinutes(30));
        } catch (Exception e) {
            log.warn("Falha ao salvar análise no cache Redis para {}: {}", ticker, e.getMessage());
        }

        return response;
    }

    private <T> T safeGet(Future<T> future, T fallback) {
        try {
            return future.get();
        } catch (Exception e) {
            log.warn("Coleta opcional falhou: {}", e.getMessage());
            return fallback;
        }
    }

    // -------------------------------------------------------------------------
    // Coleta de dados Python (sidecar HTTP com fallback para spawn)
    // -------------------------------------------------------------------------

    private StockFundamentals fetchFundamentals(String ticker) throws Exception {
        return objectMapper.readValue(pythonGateway.fundamentals(ticker), StockFundamentals.class);
    }

    /** Retorna null se a coleta falhar — a análise continua sem dados macro. */
    private MacroData fetchMacro() {
        try {
            return objectMapper.readValue(pythonGateway.macro(), MacroData.class);
        } catch (Exception e) {
            log.warn("Falha ao buscar dados macroeconômicos: {}", e.getMessage());
            return null;
        }
    }

    /** Retorna lista vazia se a coleta falhar — a análise continua sem notícias. */
    private List<NewsItem> fetchNews(String ticker) {
        try {
            return objectMapper.readValue(pythonGateway.news(ticker),
                    objectMapper.getTypeFactory().constructCollectionType(List.class, NewsItem.class));
        } catch (Exception e) {
            log.warn("Falha ao buscar notícias para {}: {}", ticker, e.getMessage());
            return List.of();
        }
    }

    /** Retorna score neutro (5.0) se a lista estiver vazia ou o script falhar. */
    private SentimentResult fetchSentiment(List<NewsItem> news) {
        if (news == null || news.isEmpty()) {
            return new SentimentResult(5.0, 0, 0, 0, 0.0, "unavailable");
        }
        try {
            List<String> titles = news.stream()
                    .map(NewsItem::title)
                    .filter(t -> t != null && !t.isBlank())
                    .collect(Collectors.toList());
            if (titles.isEmpty()) return new SentimentResult(5.0, 0, 0, 0, 0.0, "unavailable");

            // JSON como corpo (sidecar) ou stdin (script) — argv corrompe aspas no Windows
            String titlesJson = objectMapper.writeValueAsString(titles);
            String output = pythonGateway.sentiment(titlesJson);
            if (output.isBlank()) return new SentimentResult(5.0, 0, 0, news.size(), 0.0, "unavailable");

            JsonNode root = objectMapper.readTree(output);
            return new SentimentResult(
                    root.path("sentimentScore").asDouble(5.0),
                    root.path("distribution").path("positive").asInt(0),
                    root.path("distribution").path("negative").asInt(0),
                    root.path("distribution").path("neutral").asInt(0),
                    root.path("confidence").asDouble(0.0),
                    root.path("source").asText("lexical")
            );
        } catch (Exception e) {
            log.warn("Falha ao calcular sentimento: {}", e.getMessage());
            return new SentimentResult(5.0, 0, 0, 0, 0.0, "unavailable");
        }
    }

    /** Retorna null se a coleta falhar — a análise continua sem indicadores técnicos. */
    private TechnicalIndicators fetchTechnicalIndicators(String ticker) {
        try {
            String output = pythonGateway.technicalIndicators(ticker);
            if (output.isBlank()) return null;

            JsonNode root = objectMapper.readTree(output);
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

    private String retrieveContext(StockFundamentals fundamentals, SectorType sector) {
        try {
            Embedding queryEmbedding = embeddingModel
                    .embed(TextSegment.from(buildFundamentalsText(fundamentals, sector)))
                    .content();

            // Apenas fundamentos históricos — recuperar análises anteriores criaria
            // feedback loop: o LLM ancoraria no próprio score passado
            Filter filter = MetadataFilterBuilder.metadataKey("ticker")
                    .isEqualTo(fundamentals.ticker())
                    .and(MetadataFilterBuilder.metadataKey("type")
                            .isEqualTo("historical_fundamentals"));

            List<EmbeddingMatch<TextSegment>> matches = embeddingStore.search(
                    EmbeddingSearchRequest.builder()
                            .queryEmbedding(queryEmbedding)
                            .maxResults(3)
                            .filter(filter)
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
        String benchmarkSection = sectorBenchmarks.describe(sector);

        LocalDate today = LocalDate.now();
        // Eleições gerais brasileiras a cada 4 anos a partir de 2026
        boolean electionYear = (today.getYear() - 2026) % 4 == 0;

        return """
                Responda APENAS com JSON válido, sem markdown, sem ```json, sem texto antes ou depois. Baseie sua análise EXCLUSIVAMENTE nos dados fornecidos no contexto.

                Você é um analista de investimentos especializado em ações da B3.

                DATA DA ANÁLISE: %s%s

                DADOS FUNDAMENTALISTAS:
                %s

                CONTEXTO MACROECONÔMICO:
                %s

                %s

                %s

                FUNDAMENTOS HISTÓRICOS (trimestres anteriores):
                %s

                CONTEXTO SETORIAL (%s):
                %s

                BENCHMARKS DO SETOR:
                %s

                RUBRICA DE PONTUAÇÃO (use estas âncoras, não impressões gerais):
                - Fundamentos — 8-10: ROE acima da faixa setorial, margens estáveis ou crescentes, lucro crescente nos trimestres; 5-7: rentabilidade dentro da faixa setorial, resultados estáveis; 2-4: margens em queda ou lucro irregular; 0-1: prejuízo recorrente.
                - Valuation — 8-10: múltiplos claramente abaixo da faixa setorial com FCL positivo; 5-7: múltiplos dentro da faixa; 2-4: múltiplos acima da faixa sem crescimento que justifique; 0-1: múltiplos extremos ou P/L negativo sem perspectiva. Use P/VPA quando o P/L estiver distorcido por resultado negativo.
                - Regime/Momentum — use technicalSignal como base: STRONG_BUY≈8, BUY≈6.5, NEUTRAL≈5, SELL≈3.5, STRONG_SELL≈2; ajuste ±1 pelo contexto fundamentalista. Momentum ruim com fundamentos fortes não deve ficar abaixo de 3.5.
                - Sentimento Institucional — use o score lexical como base, ajustando por beta e amplitude 52 semanas. Com poucas notícias ou confiança baixa, mantenha próximo de 5 (neutro) — não invente sinal.
                - Retorno ao Acionista — 8-10: yield acima da faixa setorial com histórico consistente e FCL que o sustenta; 5-7: yield dentro da faixa; 0-4: sem dividendos e sem recompras. Se dividendYield = 0 mas o histórico mostra pagamentos reais, use o histórico — yield zero pode ser falha de coleta.
                - Gestão de Risco — 8-10: caixa líquido ou dívida/patrimônio baixo para o setor, exposição cambial administrada; 5-7: alavancagem típica do setor; 0-4: alavancagem alta E vulnerabilidade direta à Selic (varejo de crédito, imobiliário alavancado). A Selic afeta todas as empresas — penalize além de 1 ponto apenas as particularmente vulneráveis.

                CALIBRAÇÃO:
                - Score acima de 7.0: empresas com múltiplas dimensões fortes segundo a rubrica.
                - Score abaixo de 3.0: problemas graves e estruturais.
                - Use a escala inteira — empresas diferentes devem receber scores claramente diferentes.
                - Não deflacione scores por fatores macro que afetam o mercado inteiro.

                Responda SOMENTE com JSON válido. O campo "analise" vem PRIMEIRO: raciocine brevemente sobre os dados antes de pontuar.

                {
                  "analise": "<raciocínio em 3-4 frases: pontos fortes, pontos fracos e o que pesa em cada dimensão>",
                  "fundamentos": {"score": <0-10>, "explicacao": "<1 frase citando o dado que sustenta o score>"},
                  "valuation": {"score": <0-10>, "explicacao": "<1 frase citando o dado que sustenta o score>"},
                  "regimeMomentum": {"score": <0-10>, "explicacao": "<1 frase citando o dado que sustenta o score>"},
                  "sentimentoInstitucional": {"score": <0-10>, "explicacao": "<1 frase citando o dado que sustenta o score>"},
                  "retornoAcionista": {"score": <0-10>, "explicacao": "<1 frase citando o dado que sustenta o score>"},
                  "gestaoRisco": {"score": <0-10>, "explicacao": "<1 frase citando o dado que sustenta o score>"},
                  "resumo": "<síntese em 2 frases>",
                  "simpleSummary": "<explique em 2 frases simples como se falasse com alguém que nunca investiu, sem jargões financeiros>"
                }
                """.formatted(
                        today,
                        electionYear ? " (ano de eleições gerais no Brasil — considere risco político onde o contexto setorial indicar)" : "",
                        buildFundamentalsText(f, sector), macroSection, technicalSection, sentimentSection,
                        context, sector.name(), sectorSection, benchmarkSection);
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
                Sinal Técnico: %s"""
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
        // Honestidade com o modelo: a força do sinal depende de qual método classificou
        // as manchetes — FinBERT é um classificador treinado, léxico é heurística de
        // palavras-chave (mais fraco), "unavailable" não teve manchete nenhuma pra avaliar.
        String caveat = switch (s.source()) {
            case "finbert" -> "FinBERT-PT-BR, classificador treinado em notícias financeiras — sinal confiável";
            case "lexical" -> "análise lexical de palavras-chave — sinal de confiança limitada";
            default -> "sem notícias suficientes para avaliar — não usar como sinal";
        };
        return """
                SENTIMENTO DAS MANCHETES (%s):
                Score: %.2f/10 | Distribuição: %d positivas, %d negativas, %d neutras | Confiança média: %.2f"""
                .formatted(caveat, s.score(), s.positiveCount(), s.negativeCount(), s.neutralCount(), s.confidence());
    }

    private String buildFundamentalsText(StockFundamentals f, SectorType sector) {
        StringBuilder sb = new StringBuilder();

        sb.append("Ação: ").append(nvl(f.name())).append(" (").append(f.ticker()).append(")")
          .append(" | Setor: ").append(nvl(f.sector()))
          .append(" | Segmento: ").append(nvl(f.industry()))
          .append(" | Moeda: ").append(nvl(f.currency())).append("\n");

        if ("cvm+yfinance".equals(f.fundamentalsSource())) {
            sb.append("Fonte: demonstrativos oficiais CVM (ITR/DFP, balanço de ")
              .append(nvl(f.statementDate()))
              .append(", janela LTM) + dados de mercado yfinance\n");
        } else {
            sb.append("Fonte: yfinance — números contábeis podem estar defasados; ")
              .append("trate divergências fortes com ceticismo\n");
        }

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

        sb.append("\nBALANÇO PATRIMONIAL\n");
        if (sector == SectorType.FINANCEIRO && f.debtToEquity() == null) {
            // Banco/seguradora: captação e depósitos não são dívida corporativa —
            // exibir alavancagem bruta induziria o LLM a penalizar gestaoRisco
            sb.append("Dívida Total e Dívida/Patrimônio: não se aplicam (instituição financeira — ")
              .append("alavancagem é estrutural do negócio; avalie risco por ROE, P/VPA e inadimplência)\n")
              .append("Receita (intermediação financeira): ").append(fmtBrl(f.totalRevenue())).append("\n");
        } else {
            sb.append("Dívida Total: ").append(fmtBrl(f.totalDebt()))
              .append(" | Caixa: ").append(fmtBrl(f.totalCash()))
              .append(" | Receita Total: ").append(fmtBrl(f.totalRevenue())).append("\n")
              .append("FCO: ").append(fmtBrl(f.operatingCashflow()))
              .append(" | FCL: ").append(fmtBrl(f.freeCashflow()))
              .append(" | Dívida/Patrimônio: ").append(fmt(f.debtToEquity())).append("x").append("\n");
        }

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
        String corrente = "Selic: %s%% a.a. | IPCA 12m: %s%% | USD/BRL: %s | Brent: USD %s (%s%%) | WTI: USD %s (%s%%)"
                .formatted(
                        fmt(m.selicPct()), fmt(m.ipca12mPct()), fmt(m.usdBrl()),
                        fmt(m.brentPrice()), fmt(m.brentChangePct()),
                        fmt(m.wtiPrice()), fmt(m.wtiChangePct()));

        String focus = "Expectativas Focus (medianas) — Selic fim do ano: %s%% | Selic ano seguinte: %s%% | IPCA ano: %s%% | IPCA ano seguinte: %s%%"
                .formatted(
                        fmt(m.focusSelicCurrentYear()), fmt(m.focusSelicNextYear()),
                        fmt(m.focusIpcaCurrentYear()), fmt(m.focusIpcaNextYear()));

        return corrente + "\n" + focus +
                "\nUse as expectativas Focus para avaliar a trajetória dos juros — corte esperado de Selic favorece setores sensíveis a juros.";
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

    private void indexAnalysis(StockAnalysis analysis, StockFundamentals fundamentals) {
        try {
            embeddingService.embedAndStoreAnalysis(analysis, fundamentals);
        } catch (Exception e) {
            log.warn("Falha ao indexar análise para {} — RAG não afetado: {}", analysis.ticker(), e.getMessage());
        }
    }
}
