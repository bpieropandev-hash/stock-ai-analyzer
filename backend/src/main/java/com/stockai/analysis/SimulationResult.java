package com.stockai.analysis;

import java.time.LocalDate;
import java.util.List;

public record SimulationResult(
        double totalAmount,
        List<Allocation> allocations,
        List<String> excludedTickers,
        LocalDate generatedAt
) {}
