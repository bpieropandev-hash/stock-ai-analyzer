package com.stockai.scheduler;

import com.stockai.cache.RedisStockCache;
import com.stockai.stock.StockQuote;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

@Component
public class StockFetchJob {

    private static final Logger log = LoggerFactory.getLogger(StockFetchJob.class);

    public static final List<String> TICKERS = List.of(
            "PETR4", "VALE3", "ITUB4", "BBDC4", "WEGE3",
            "MGLU3", "ABEV3", "B3SA3", "RENT3", "SUZB3"
    );

    private final RedisStockCache cache;
    private final ObjectMapper objectMapper;

    @Value("${python.script.path:scripts/fetch_stock.py}")
    private String scriptPath;

    public StockFetchJob(RedisStockCache cache, ObjectMapper objectMapper) {
        this.cache = cache;
        this.objectMapper = objectMapper;
    }

    @Scheduled(fixedRate = 60_000)
    public void fetchQuotes() {
        try {
            Process process = new ProcessBuilder("python", scriptPath)
                    .start();

            String stdout = new String(process.getInputStream().readAllBytes());
            String stderr = new String(process.getErrorStream().readAllBytes());
            int exitCode = process.waitFor();

            if (!stderr.isBlank()) {
                log.warn("Script Python reportou: {}", stderr.strip());
            }

            if (exitCode != 0) {
                log.error("Script encerrou com código {}: {}", exitCode, stderr.strip());
                return;
            }

            if (stdout.isBlank()) {
                log.warn("Script Python retornou saída vazia");
                return;
            }

            List<StockQuote> quotes = objectMapper.readValue(stdout, new TypeReference<>() {});

            int saved = 0;
            for (StockQuote quote : quotes) {
                cache.save(quote);
                saved++;
            }

            log.info("Cotações atualizadas com sucesso: {}/{}", saved, TICKERS.size());
        } catch (Exception e) {
            log.error("Falha ao executar script de cotações: {}", e.getMessage());
        }
    }
}
