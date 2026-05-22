package com.stockai.analysis;

import java.math.BigDecimal;

public record HistoricalSnapshot(
        String ticker,
        String period,
        BigDecimal revenue,
        BigDecimal netIncome,
        BigDecimal grossProfit,
        BigDecimal operatingIncome,
        BigDecimal totalDebt,
        BigDecimal totalCash
) {}
