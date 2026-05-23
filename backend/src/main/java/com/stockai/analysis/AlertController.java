package com.stockai.analysis;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/alerts")
public class AlertController {

    private final ScoreAlertService alertService;

    public AlertController(ScoreAlertService alertService) {
        this.alertService = alertService;
    }

    @GetMapping
    public ResponseEntity<List<StockAlert>> getAlerts() {
        return ResponseEntity.ok(alertService.getRecentAlerts());
    }
}
