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
    private final BacktestService backtestService;

    public AnalysisController(StockAnalysisService analysisService,
                               ScoreHistoryService scoreHistoryService,
                               BacktestService backtestService) {
        this.analysisService = analysisService;
        this.scoreHistoryService = scoreHistoryService;
        this.backtestService = backtestService;
    }

    @GetMapping("/{ticker}/analysis")
    public ResponseEntity<AnalysisResponse> analyze(@PathVariable String ticker) {
        try {
            return ResponseEntity.ok(analysisService.analyze(ticker.toUpperCase()));
        } catch (Exception e) {
            log.error("Erro ao analisar ticker {}: {}", ticker, e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping("/{ticker}/analysis/refresh")
    public ResponseEntity<AnalysisResponse> refresh(@PathVariable String ticker) {
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
        // histórico é gravado com o ticker normalizado (.SA)
        String normalized = StockAnalysisService.normalizeTicker(ticker);
        return ResponseEntity.ok(scoreHistoryService.getScoreHistory(normalized, days));
    }

    @GetMapping("/{ticker}/backtest")
    public ResponseEntity<BacktestService.BacktestResult> backtest(@PathVariable String ticker) {
        try {
            String normalized = StockAnalysisService.normalizeTicker(ticker);
            return ResponseEntity.ok(backtestService.backtest(normalized));
        } catch (Exception e) {
            log.error("Erro no backtest de {}: {}", ticker, e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }
}
