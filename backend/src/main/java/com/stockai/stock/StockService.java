package com.stockai.stock;

import com.stockai.cache.RedisStockCache;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class StockService {

    private final RedisStockCache cache;

    public StockService(RedisStockCache cache) {
        this.cache = cache;
    }

    public List<StockQuote> getAllQuotes() {
        return cache.findAll();
    }
}
