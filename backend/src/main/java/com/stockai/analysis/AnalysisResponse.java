package com.stockai.analysis;

public record AnalysisResponse(
        StockAnalysis analysis,
        String sector,
        String simpleSummary,
        String recommendation,
        String disclaimer,
        // Auditoria — permite explicar por que um score mudou entre análises
        String modelUsed,
        String promptVersion,
        ScoreConfidence confidence,
        ScoreChangeExplanation scoreChange
) {}
