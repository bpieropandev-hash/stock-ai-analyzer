package com.stockai.analysis;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/compare")
public class ComparisonController {

    private static final Logger log = LoggerFactory.getLogger(ComparisonController.class);
    private static final int MAX_TICKERS = 5;

    private final ComparisonService comparisonService;

    public ComparisonController(ComparisonService comparisonService) {
        this.comparisonService = comparisonService;
    }

    @GetMapping
    public ResponseEntity<?> compare(@RequestParam List<String> tickers) {
        if (tickers.size() > MAX_TICKERS) {
            return ResponseEntity.badRequest()
                    .body("Máximo de %d tickers por requisição.".formatted(MAX_TICKERS));
        }
        if (tickers.isEmpty()) {
            return ResponseEntity.badRequest().body("Informe ao menos um ticker.");
        }
        try {
            List<String> upper = tickers.stream().map(String::toUpperCase).toList();
            return ResponseEntity.ok(comparisonService.compare(upper));
        } catch (Exception e) {
            log.error("Erro ao comparar tickers {}: {}", tickers, e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }
}
