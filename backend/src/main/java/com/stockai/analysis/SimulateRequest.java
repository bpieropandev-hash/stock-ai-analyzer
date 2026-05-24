package com.stockai.analysis;

import java.util.List;

public record SimulateRequest(
        double amount,
        List<String> tickers
) {}
