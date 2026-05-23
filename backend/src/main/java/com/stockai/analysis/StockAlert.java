package com.stockai.analysis;

import java.time.LocalDateTime;

public record StockAlert(
        String ticker,
        LocalDateTime date,
        double scoreBefore,
        double scoreAfter,
        String direction,
        double magnitude
) {}
