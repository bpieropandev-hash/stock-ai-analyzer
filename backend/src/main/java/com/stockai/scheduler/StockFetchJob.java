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

import java.time.Duration;
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
    private final PythonScriptRunner scriptRunner;

    @Value("${python.script.path:scripts/fetch_stock.py}")
    private String scriptPath;

    public StockFetchJob(RedisStockCache cache, ObjectMapper objectMapper, PythonScriptRunner scriptRunner) {
        this.cache = cache;
        this.objectMapper = objectMapper;
        this.scriptRunner = scriptRunner;
    }

    @Scheduled(fixedRate = 60_000)
    public void fetchQuotes() {
        try {
            PythonScriptRunner.ScriptResult result = scriptRunner.run(scriptPath, Duration.ofSeconds(55));

            if (result.failed()) {
                log.error("Script de cotações falhou (código {}): {}", result.exitCode(), result.stderr());
                return;
            }

            List<StockQuote> quotes = objectMapper.readValue(result.stdout(), new TypeReference<>() {});

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
