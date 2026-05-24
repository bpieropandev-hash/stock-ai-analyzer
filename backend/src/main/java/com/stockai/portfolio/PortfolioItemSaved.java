package com.stockai.portfolio;

import java.time.LocalDate;
import java.util.UUID;

public record PortfolioItemSaved(
        UUID id,
        String ticker,
        Double quantity,
        Double averagePrice,
        LocalDate purchaseDate
) {}
