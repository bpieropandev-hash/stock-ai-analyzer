package com.stockai.analysis;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

@Service
public class StockEmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(StockEmbeddingService.class);

    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> embeddingStore;

    public StockEmbeddingService(EmbeddingModel embeddingModel, EmbeddingStore<TextSegment> embeddingStore) {
        this.embeddingModel = embeddingModel;
        this.embeddingStore = embeddingStore;
    }

    /**
     * Converte os fundamentais em texto descritivo, gera o embedding e persiste no pgvector.
     */
    public void embedAndStore(StockFundamentals fundamentals) {
        store(buildDescriptiveText(fundamentals),
              fundamentals.ticker(), LocalDate.now().toString(), "fundamentals");
        log.info("Embedding fundamentalista salvo — ticker={} data={}", fundamentals.ticker(), LocalDate.now());
    }

    /** Indexa o resultado da análise LLM com scores e explicações por dimensão. */
    public void embedAndStoreAnalysis(StockAnalysis analysis, StockFundamentals fundamentals) {
        store(buildAnalysisText(analysis, fundamentals),
              analysis.ticker(), analysis.analysisDate().toString(), "analysis");
        log.info("Embedding de análise salvo — ticker={} data={}", analysis.ticker(), analysis.analysisDate());
    }

    /** Indexa um snapshot trimestral de fundamentos históricos. */
    public void embedAndStoreSnapshot(HistoricalSnapshot snapshot) {
        store(buildSnapshotText(snapshot),
              snapshot.ticker(), snapshot.period(), "historical_fundamentals");
        log.info("Embedding histórico salvo — ticker={} período={}", snapshot.ticker(), snapshot.period());
    }

    // -------------------------------------------------------------------------
    // Construtores de texto
    // -------------------------------------------------------------------------

    private String buildDescriptiveText(StockFundamentals f) {
        return """
                Ação: %s (%s)
                Setor: %s | Segmento: %s
                P/L (Price-to-Earnings): %s
                ROE (Retorno sobre Patrimônio): %s%%
                Margem Líquida: %s%%
                Dívida/Patrimônio: %s
                Crescimento de Receita (YoY): %s%%
                Dividend Yield: %s%%
                """.formatted(
                nvl(f.name()), f.ticker(),
                nvl(f.sector()), nvl(f.industry()),
                fmt(f.priceToEarnings()),
                fmtPct(f.roe()),
                fmtPct(f.netMargin()),
                fmt(f.debtToEquity()),
                fmt(f.revenueGrowth()),
                fmtPct(f.dividendYield())
        );
    }

    private String buildAnalysisText(StockAnalysis a, StockFundamentals f) {
        return """
                Análise: %s (%s) — %s
                Score Geral: %.1f/10
                Fundamentos: %.1f — %s
                Valuation: %.1f — %s
                Regime/Momentum: %.1f — %s
                Sentimento Institucional: %.1f — %s
                Retorno ao Acionista: %.1f — %s
                Gestão de Risco: %.1f — %s
                Resumo: %s
                P/L: %s | ROE: %s%% | Margem Líquida: %s%% | Dívida/Patrimônio: %s
                """.formatted(
                nvl(f.name()), f.ticker(), a.analysisDate(),
                a.scoreGeral(),
                a.fundamentos().score(), a.fundamentos().explicacao(),
                a.valuation().score(), a.valuation().explicacao(),
                a.regimeMomentum().score(), a.regimeMomentum().explicacao(),
                a.sentimentoInstitucional().score(), a.sentimentoInstitucional().explicacao(),
                a.retornoAcionista().score(), a.retornoAcionista().explicacao(),
                a.gestaoRisco().score(), a.gestaoRisco().explicacao(),
                a.resumo(),
                fmt(f.priceToEarnings()), fmtPct(f.roe()), fmtPct(f.netMargin()), fmt(f.debtToEquity())
        );
    }

    private String buildSnapshotText(HistoricalSnapshot s) {
        return """
                Fundamentos históricos: %s — %s
                Receita: %s | Lucro Líquido: %s | Lucro Bruto: %s
                Resultado Operacional: %s | Dívida Total: %s | Caixa: %s
                """.formatted(
                s.ticker(), s.period(),
                fmtBd(s.revenue()), fmtBd(s.netIncome()), fmtBd(s.grossProfit()),
                fmtBd(s.operatingIncome()), fmtBd(s.totalDebt()), fmtBd(s.totalCash())
        );
    }

    // -------------------------------------------------------------------------
    // Persistência
    // -------------------------------------------------------------------------

    private void store(String text, String ticker, String date, String type) {
        Metadata metadata = new Metadata();
        metadata.put("ticker", ticker);
        metadata.put("date", date);
        metadata.put("type", type);
        TextSegment segment = TextSegment.from(text, metadata);
        Embedding embedding = embeddingModel.embed(segment).content();
        embeddingStore.add(embedding, segment);
    }

    // -------------------------------------------------------------------------
    // Helpers de formatação
    // -------------------------------------------------------------------------

    private String nvl(String value) {
        return value != null ? value : "N/D";
    }

    private String fmt(BigDecimal value) {
        return value != null ? value.setScale(2, RoundingMode.HALF_UP).toPlainString() : "N/D";
    }

    private String fmtBd(BigDecimal value) {
        if (value == null) return "N/D";
        return "R$ %.2f bi".formatted(value.doubleValue() / 1_000_000_000.0);
    }

    /** yfinance retorna roe, netMargin e dividendYield como decimal (0.25 = 25%). */
    private String fmtPct(BigDecimal value) {
        if (value == null) return "N/D";
        return value.multiply(BigDecimal.valueOf(100))
                    .setScale(2, RoundingMode.HALF_UP)
                    .toPlainString();
    }
}
