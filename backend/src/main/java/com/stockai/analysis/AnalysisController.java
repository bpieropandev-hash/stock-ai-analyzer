package com.stockai.analysis;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/stocks")
public class AnalysisController {

    private static final Logger log = LoggerFactory.getLogger(AnalysisController.class);

    private final StockAnalysisService analysisService;

    public AnalysisController(StockAnalysisService analysisService) {
        this.analysisService = analysisService;
    }

    @GetMapping("/{ticker}/analysis")
    public ResponseEntity<StockAnalysis> analyze(@PathVariable String ticker) {
        try {
            return ResponseEntity.ok(analysisService.analyze(ticker.toUpperCase()));
        } catch (Exception e) {
            log.error("Erro ao analisar ticker {}: {}", ticker, e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }
}
