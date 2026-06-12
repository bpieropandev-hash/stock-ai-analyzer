package com.stockai.scheduler;

import com.stockai.analysis.HistoricalSnapshot;
import com.stockai.analysis.StockEmbeddingService;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.MetadataFilterBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

@Component
public class HistoricalIndexingJob {

    private static final Logger log = LoggerFactory.getLogger(HistoricalIndexingJob.class);

    private final StockEmbeddingService embeddingService;
    private final EmbeddingStore<TextSegment> embeddingStore;
    private final ObjectMapper objectMapper;
    private final PythonDataGateway pythonGateway;

    public HistoricalIndexingJob(StockEmbeddingService embeddingService,
                                 EmbeddingStore<TextSegment> embeddingStore,
                                 ObjectMapper objectMapper,
                                 PythonDataGateway pythonGateway) {
        this.embeddingService = embeddingService;
        this.embeddingStore = embeddingStore;
        this.objectMapper = objectMapper;
        this.pythonGateway = pythonGateway;
    }

    @Scheduled(initialDelay = 300_000, fixedDelay = 86_400_000)
    public void indexHistoricalFundamentals() {
        log.info("Iniciando indexação de fundamentos históricos — {} tickers", StockFetchJob.TICKERS.size());
        int total = 0;
        for (String ticker : StockFetchJob.TICKERS) {
            try {
                total += indexTicker(ticker);
                Thread.sleep(500);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                log.warn("Indexação histórica interrompida em {}", ticker);
                break;
            } catch (Exception e) {
                log.warn("Falha ao indexar histórico de {}: {}", ticker, e.getMessage());
            }
        }
        log.info("Indexação histórica concluída — {} snapshots indexados", total);
    }

    private int indexTicker(String ticker) throws Exception {
        String json = pythonGateway.historicalFundamentals(ticker);

        List<HistoricalSnapshot> snapshots = objectMapper.readValue(json, new TypeReference<>() {});
        if (snapshots.isEmpty()) return 0;

        // Remove os embeddings anteriores do ticker antes de re-indexar — o job é
        // diário e sem isso o store acumula duplicatas que poluem o RAG
        embeddingStore.removeAll(
                MetadataFilterBuilder.metadataKey("ticker").isEqualTo(ticker)
                        .and(MetadataFilterBuilder.metadataKey("type").isEqualTo("historical_fundamentals")));

        for (HistoricalSnapshot snapshot : snapshots) {
            embeddingService.embedAndStoreSnapshot(snapshot);
        }
        log.info("Indexados {} snapshots para {}", snapshots.size(), ticker);
        return snapshots.size();
    }
}
