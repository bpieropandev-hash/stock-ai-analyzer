package com.stockai.analysis;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/alerts")
public class AlertController {

    private final ScoreAlertService alertService;

    public AlertController(ScoreAlertService alertService) {
        this.alertService = alertService;
    }

    @GetMapping
    public ResponseEntity<List<StockAlert>> getAlerts(@RequestParam(defaultValue = "7") int days) {
        return ResponseEntity.ok(alertService.getRecentAlerts(days));
    }

    @GetMapping("/{ticker}")
    public ResponseEntity<List<StockAlert>> getAlertsByTicker(@PathVariable String ticker) {
        return ResponseEntity.ok(alertService.getAlertsByTicker(ticker.toUpperCase()));
    }
}
