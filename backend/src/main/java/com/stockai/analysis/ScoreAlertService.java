package com.stockai.analysis;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class ScoreAlertService {

    private static final Logger log = LoggerFactory.getLogger(ScoreAlertService.class);
    private static final double ALERT_THRESHOLD = 1.5;

    private final List<StockAlert> alerts = new CopyOnWriteArrayList<>();
    private final ScoreHistoryService scoreHistoryService;

    public ScoreAlertService(ScoreHistoryService scoreHistoryService) {
        this.scoreHistoryService = scoreHistoryService;
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
                            StockAlert alert = new StockAlert(
                                    analysis.ticker(),
                                    LocalDateTime.now(),
                                    previous.scoreGeral(),
                                    analysis.scoreGeral(),
                                    direction,
                                    Math.round(Math.abs(delta) * 100.0) / 100.0
                            );
                            alerts.add(alert);
                            log.info("Alerta gerado — ticker={} direction={} magnitude={:.2f} before={} after={}",
                                    analysis.ticker(), direction, Math.abs(delta),
                                    previous.scoreGeral(), analysis.scoreGeral());
                        }
                    });
        } catch (Exception e) {
            log.warn("Falha ao verificar alertas para {}: {}", analysis.ticker(), e.getMessage());
        }
    }

    public List<StockAlert> getRecentAlerts() {
        LocalDateTime cutoff = LocalDateTime.now().minusHours(24);
        return alerts.stream()
                .filter(a -> a.date().isAfter(cutoff))
                .toList();
    }
}
