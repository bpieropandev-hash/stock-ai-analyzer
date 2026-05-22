package com.stockai.stock;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;

@JsonIgnoreProperties(ignoreUnknown = true)
public record StockQuote(
        String symbol,
        BigDecimal price,
        BigDecimal changePercent,
        Long volume,
        Long marketCap
) {}
