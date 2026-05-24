package com.stockai.portfolio;

import java.time.LocalDate;

public record AddPortfolioItemRequest(
        String ticker,
        Double quantity,
        Double averagePrice,
        LocalDate purchaseDate
) {}
