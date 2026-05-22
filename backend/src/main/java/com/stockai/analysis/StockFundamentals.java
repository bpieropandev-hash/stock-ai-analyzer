package com.stockai.analysis;

import java.math.BigDecimal;
import java.util.List;

/**
 * Dados fundamentalistas enriquecidos retornados pelo script fetch_fundamentals.py.
 *
 * Convenção yfinance: campos de percentual (roe, roa, margens, earningsGrowth,
 * dividendYield) chegam como decimal — ex: 0.25 representa 25%.
 * Exceção: revenueGrowth é calculado pelo script e já vem em %.
 */
public record StockFundamentals(
        // Identificação
        String ticker,
        String symbol,
        String name,
        String sector,
        String industry,
        String currency,

        // Valuation
        BigDecimal priceToEarnings,
        BigDecimal priceToBook,
        Long marketCap,

        // Rentabilidade
        BigDecimal roe,
        BigDecimal roa,
        BigDecimal netMargin,
        BigDecimal operatingMargin,
        BigDecimal earningsGrowth,

        // Endividamento / Balanço patrimonial
        BigDecimal debtToEquity,
        Long totalDebt,
        Long totalCash,
        Long totalRevenue,
        Long operatingCashflow,
        Long freeCashflow,

        // Crescimento
        BigDecimal revenueGrowth,

        // Dividendos
        BigDecimal dividendYield,
        List<DividendEntry> dividendHistory,

        // Resultados trimestrais
        List<QuarterlyResult> quarterlyResults,

        // Dados de mercado
        BigDecimal beta,
        BigDecimal fiftyTwoWeekHigh,
        BigDecimal fiftyTwoWeekLow,
        Long averageVolume
) {}
