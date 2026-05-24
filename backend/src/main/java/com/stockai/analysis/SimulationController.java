package com.stockai.analysis;

import com.stockai.scheduler.StockFetchJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/simulate")
public class SimulationController {

    private static final Logger log = LoggerFactory.getLogger(SimulationController.class);

    private final PortfolioSimulator portfolioSimulator;

    public SimulationController(PortfolioSimulator portfolioSimulator) {
        this.portfolioSimulator = portfolioSimulator;
    }

    @PostMapping
    public ResponseEntity<?> simulate(@RequestBody SimulateRequest request) {
        List<String> tickers = (request.tickers() == null || request.tickers().isEmpty())
                ? StockFetchJob.TICKERS
                : request.tickers().stream().map(String::toUpperCase).toList();

        try {
            return ResponseEntity.ok(portfolioSimulator.simulate(request.amount(), tickers));
        } catch (Exception e) {
            log.error("Erro ao simular portfólio: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }
}
