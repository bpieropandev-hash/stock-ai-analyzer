package com.stockai.analysis;

import com.stockai.stock.Stock;
import com.stockai.stock.StockRepository;
import com.stockai.stock.TickerNormalizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

@Service
public class ScoreAlertService {

    private static final Logger log = LoggerFactory.getLogger(ScoreAlertService.class);
    private static final double ALERT_THRESHOLD = 1.5;

    private final ScoreHistoryService scoreHistoryService;
    private final StockAlertRepository alertRepository;
    private final StockRepository stockRepository;

    public ScoreAlertService(ScoreHistoryService scoreHistoryService,
                             StockAlertRepository alertRepository,
                             StockRepository stockRepository) {
        this.scoreHistoryService = scoreHistoryService;
        this.alertRepository = alertRepository;
        this.stockRepository = stockRepository;
    }

    public void checkAndAlert(StockAnalysis analysis) {
        try {
            List<ScoreSnapshot> history = scoreHistoryService.getScoreHistory(analysis.ticker(), 7);

            // Compara com o snapshot anterior (exclui o do dia atual, pois já foi salvo)
            LocalDate today = analysis.analysisDate();
            history.stream()
                    .filter(s -> s.date().isBefore(today))
                    .max(Comparator.comparing(ScoreSnapshot::date))
                    .ifPresent(previous -> {
                        double delta = analysis.scoreGeral() - previous.scoreGeral();
                        if (Math.abs(delta) > ALERT_THRESHOLD) {
                            String direction = delta > 0 ? "UP" : "DOWN";
                            double magnitude = Math.round(Math.abs(delta) * 100.0) / 100.0;
                            Stock stock = stockRepository.findOrCreate(analysis.ticker());
                            StockAlertEntity entity = new StockAlertEntity(
                                    stock,
                                    today,
                                    previous.scoreGeral(),
                                    analysis.scoreGeral(),
                                    direction,
                                    magnitude,
                                    LocalDateTime.now()
                            );
                            alertRepository.save(entity);
                            log.info("Alerta gerado — ticker={} direction={} magnitude={} before={} after={}",
                                    analysis.ticker(), direction, magnitude,
                                    previous.scoreGeral(), analysis.scoreGeral());
                        }
                    });
        } catch (Exception e) {
            log.warn("Falha ao verificar alertas para {}: {}", analysis.ticker(), e.getMessage());
        }
    }

    public List<StockAlert> getRecentAlerts(int days) {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(days);
        return alertRepository.findByCreatedAtAfter(cutoff)
                .stream()
                .map(this::toDto)
                .toList();
    }

    public List<StockAlert> getAlertsByTicker(String ticker) {
        return alertRepository.findByStock_Ticker(TickerNormalizer.canonical(ticker))
                .stream()
                .map(this::toDto)
                .toList();
    }

    private StockAlert toDto(StockAlertEntity e) {
        return new StockAlert(
                e.getTicker(),
                e.getCreatedAt(),
                e.getScoreBefore(),
                e.getScoreAfter(),
                e.getDirection(),
                e.getMagnitude()
        );
    }
}
