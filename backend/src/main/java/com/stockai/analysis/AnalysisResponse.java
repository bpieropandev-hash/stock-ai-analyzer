package com.stockai.analysis;

public record AnalysisResponse(
        StockAnalysis analysis,
        String sector,
        String simpleSummary,
        String recommendation,
        String disclaimer
) {}
