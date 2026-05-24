package com.stockai.portfolio;

import java.time.LocalDate;

public record PortfolioItemResponse(
        String ticker,
        Double quantity,
        Double averagePrice,
        LocalDate purchaseDate,
        double currentScore,
        String recommendation,
        String simpleSummary,
        String sector
) {}
