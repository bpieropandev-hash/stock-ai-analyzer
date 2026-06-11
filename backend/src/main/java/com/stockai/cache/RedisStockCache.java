package com.stockai.cache;

import com.stockai.stock.StockQuote;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
public class RedisStockCache {

    private static final Logger log = LoggerFactory.getLogger(RedisStockCache.class);

    static final String KEY_PREFIX = "stock:";
    private static final Duration TTL = Duration.ofSeconds(120);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public RedisStockCache(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public void save(StockQuote quote) {
        try {
            String json = objectMapper.writeValueAsString(quote);
            redisTemplate.opsForValue().set(KEY_PREFIX + quote.symbol(), json, TTL);
        } catch (Exception e) {
            log.error("Erro ao salvar cotação no Redis para {}: {}", quote.symbol(), e.getMessage());
        }
    }

    public List<StockQuote> findAll() {
        // SCAN em vez de KEYS — KEYS é O(N) e bloqueia o Redis inteiro
        Set<String> keys = new HashSet<>();
        ScanOptions options = ScanOptions.scanOptions().match(KEY_PREFIX + "*").count(100).build();
        try (Cursor<String> cursor = redisTemplate.scan(options)) {
            cursor.forEachRemaining(keys::add);
        }
        if (keys.isEmpty()) {
            return List.of();
        }

        List<StockQuote> quotes = new ArrayList<>();
        for (String key : keys) {
            try {
                String json = redisTemplate.opsForValue().get(key);
                if (json != null) {
                    quotes.add(objectMapper.readValue(json, StockQuote.class));
                }
            } catch (Exception e) {
                log.error("Erro ao desserializar cotação do Redis para chave {}: {}", key, e.getMessage());
            }
        }
        return quotes;
    }
}
