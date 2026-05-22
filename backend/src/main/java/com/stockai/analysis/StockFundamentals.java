package com.stockai.analysis;

import java.math.BigDecimal;

/**
 * Dados fundamentalistas de uma ação retornados pelo script fetch_fundamentals.py.
 * Campos de percentual (roe, netMargin, dividendYield) chegam como decimal do yfinance
 * — ex: 0.25 representa 25%.
 */
public record StockFundamentals(
        String ticker,
        String symbol,
        String name,
        String sector,
        String industry,
        BigDecimal priceToEarnings,
        BigDecimal roe,
        BigDecimal netMargin,
        BigDecimal debtToEquity,
        BigDecimal revenueGrowth,
        BigDecimal dividendYield,
        Long marketCap,
        String currency
) {}
