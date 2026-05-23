package com.stockai.analysis;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/stocks")
public class AnalysisController {

    private static final Logger log = LoggerFactory.getLogger(AnalysisController.class);

    private final StockAnalysisService analysisService;
    private final ScoreHistoryService scoreHistoryService;

    public AnalysisController(StockAnalysisService analysisService,
                               ScoreHistoryService scoreHistoryService) {
        this.analysisService = analysisService;
        this.scoreHistoryService = scoreHistoryService;
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

    @PostMapping("/{ticker}/analysis/refresh")
    public ResponseEntity<StockAnalysis> refresh(@PathVariable String ticker) {
        try {
            return ResponseEntity.ok(analysisService.refreshAnalysis(ticker.toUpperCase()));
        } catch (Exception e) {
            log.error("Erro ao forçar nova análise para {}: {}", ticker, e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/{ticker}/score-history")
    public ResponseEntity<List<ScoreSnapshot>> scoreHistory(
            @PathVariable String ticker,
            @RequestParam(defaultValue = "30") int days) {
        return ResponseEntity.ok(scoreHistoryService.getScoreHistory(ticker.toUpperCase(), days));
    }
}
