package com.stockai.scheduler;

import com.stockai.cache.RedisStockCache;
import com.stockai.stock.StockQuote;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    private final PythonDataGateway pythonGateway;

    public StockFetchJob(RedisStockCache cache, ObjectMapper objectMapper, PythonDataGateway pythonGateway) {
        this.cache = cache;
        this.objectMapper = objectMapper;
        this.pythonGateway = pythonGateway;
    }

    @Scheduled(fixedRate = 60_000)
    public void fetchQuotes() {
        try {
            List<StockQuote> quotes = objectMapper.readValue(pythonGateway.quotes(), new TypeReference<>() {});

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
