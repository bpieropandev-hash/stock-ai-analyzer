package com.stockai.analysis;

import java.time.LocalDate;
import java.util.List;

public record ComparisonResult(
        List<String> tickers,
        List<AnalysisResponse> analyses,
        List<RankedStock> ranking,
        String bestForDividends,
        String bestMomentum,
        String lowestRisk,
        LocalDate generatedAt
) {}
