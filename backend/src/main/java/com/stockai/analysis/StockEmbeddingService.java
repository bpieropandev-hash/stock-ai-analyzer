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
        String text = buildDescriptiveText(fundamentals);

        Metadata metadata = new Metadata();
        metadata.put("ticker", fundamentals.ticker());
        metadata.put("date", LocalDate.now().toString());
        metadata.put("type", "fundamentals");

        TextSegment segment = TextSegment.from(text, metadata);
        Embedding embedding = embeddingModel.embed(segment).content();

        embeddingStore.add(embedding, segment);
        log.info("Embedding fundamentalista salvo — ticker={} data={}", fundamentals.ticker(), LocalDate.now());
    }

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

    private String nvl(String value) {
        return value != null ? value : "N/D";
    }

    private String fmt(BigDecimal value) {
        return value != null ? value.setScale(2, RoundingMode.HALF_UP).toPlainString() : "N/D";
    }

    /** yfinance retorna roe, netMargin e dividendYield como decimal (0.25 = 25%). */
    private String fmtPct(BigDecimal value) {
        if (value == null) return "N/D";
        return value.multiply(BigDecimal.valueOf(100))
                    .setScale(2, RoundingMode.HALF_UP)
                    .toPlainString();
    }
}
