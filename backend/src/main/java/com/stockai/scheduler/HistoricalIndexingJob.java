package com.stockai.scheduler;

import com.stockai.analysis.HistoricalSnapshot;
import com.stockai.analysis.StockEmbeddingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Component
public class HistoricalIndexingJob {

    private static final Logger log = LoggerFactory.getLogger(HistoricalIndexingJob.class);

    private final StockEmbeddingService embeddingService;
    private final ObjectMapper objectMapper;

    @Value("${python.script.historical-fundamentals-path:scripts/fetch_historical_fundamentals.py}")
    private String scriptPath;

    public HistoricalIndexingJob(StockEmbeddingService embeddingService, ObjectMapper objectMapper) {
        this.embeddingService = embeddingService;
        this.objectMapper = objectMapper;
    }

    @Scheduled(fixedDelay = 86_400_000)
    public void indexHistoricalFundamentals() {
        log.info("Iniciando indexação de fundamentos históricos — {} tickers", StockFetchJob.TICKERS.size());
        int total = 0;
        for (String ticker : StockFetchJob.TICKERS) {
            try {
                total += indexTicker(ticker);
            } catch (Exception e) {
                log.warn("Falha ao indexar histórico de {}: {}", ticker, e.getMessage());
            }
        }
        log.info("Indexação histórica concluída — {} snapshots indexados", total);
    }

    private int indexTicker(String ticker) throws Exception {
        Process process = new ProcessBuilder("python", scriptPath, ticker).start();
        String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        String stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
        int exitCode = process.waitFor();

        if (!stderr.isBlank()) log.warn("fetch_historical_fundamentals.py [{}]: {}", ticker, stderr.strip());
        if (exitCode != 0 || stdout.isBlank()) return 0;

        List<HistoricalSnapshot> snapshots = objectMapper.readValue(stdout.strip(), new TypeReference<>() {});
        for (HistoricalSnapshot snapshot : snapshots) {
            embeddingService.embedAndStoreSnapshot(snapshot);
        }
        log.info("Indexados {} snapshots para {}", snapshots.size(), ticker);
        return snapshots.size();
    }
}
