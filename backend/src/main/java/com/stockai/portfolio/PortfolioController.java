package com.stockai.portfolio;

import com.stockai.analysis.SimulationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/portfolio")
public class PortfolioController {

    private static final Logger log = LoggerFactory.getLogger(PortfolioController.class);

    private final PortfolioService portfolioService;

    public PortfolioController(PortfolioService portfolioService) {
        this.portfolioService = portfolioService;
    }

    @GetMapping
    public ResponseEntity<List<PortfolioItemResponse>> getPortfolio(Authentication auth) {
        try {
            return ResponseEntity.ok(portfolioService.getPortfolio(auth.getName()));
        } catch (Exception e) {
            log.error("Erro ao obter carteira: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping
    public ResponseEntity<PortfolioItemSaved> addOrUpdate(Authentication auth,
                                                          @RequestBody AddPortfolioItemRequest request) {
        try {
            PortfolioItem saved = portfolioService.addOrUpdate(
                    auth.getName(),
                    request.ticker(),
                    request.quantity(),
                    request.averagePrice(),
                    request.purchaseDate()
            );
            return ResponseEntity.ok(new PortfolioItemSaved(
                    saved.getId(), saved.getTicker(),
                    saved.getQuantity(), saved.getAveragePrice(), saved.getPurchaseDate()
            ));
        } catch (Exception e) {
            log.error("Erro ao adicionar/atualizar posição: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    @DeleteMapping("/{ticker}")
    public ResponseEntity<Void> remove(Authentication auth, @PathVariable String ticker) {
        try {
            portfolioService.remove(auth.getName(), ticker);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            log.error("Erro ao remover posição {}: {}", ticker, e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/evaluation")
    public ResponseEntity<List<EvaluationItem>> evaluate(Authentication auth) {
        try {
            return ResponseEntity.ok(portfolioService.evaluate(auth.getName()));
        } catch (Exception e) {
            log.error("Erro ao avaliar carteira: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping("/suggest-allocation")
    public ResponseEntity<SimulationResult> suggestAllocation(Authentication auth,
                                                               @RequestBody SuggestAllocationRequest request) {
        try {
            return ResponseEntity.ok(portfolioService.suggestAllocation(auth.getName(), request.amount()));
        } catch (Exception e) {
            log.error("Erro ao sugerir alocação: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }
}
