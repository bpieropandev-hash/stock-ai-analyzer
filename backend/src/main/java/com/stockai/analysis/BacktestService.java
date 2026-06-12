package com.stockai.analysis;

import com.stockai.scheduler.PythonDataGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

/**
 * Backtesting do score: cruza o histórico de scores com os retornos realizados
 * 30 e 90 dias depois de cada análise.
 *
 * Sem isso, não há evidência de que o score correlaciona com retorno futuro —
 * o sistema seria só uma opinião bem formatada.
 */
@Service
public class BacktestService {

    private static final Logger log = LoggerFactory.getLogger(BacktestService.class);

    public record BacktestEntry(
            LocalDate analysisDate,
            double scoreGeral,
            String modelUsed,
            String promptVersion,
            Double priceAtAnalysis,
            Double return30dPct,
            Double return90dPct
    ) {}

    public record BacktestResult(
            String ticker,
            int totalAnalyses,
            int analysesWithForwardData,
            Double correlationScore30d,
            Double correlationScore90d,
            List<BacktestEntry> entries
    ) {}

    private final ScoreHistoryService scoreHistoryService;
    private final PythonDataGateway pythonGateway;
    private final ObjectMapper objectMapper;

    public BacktestService(ScoreHistoryService scoreHistoryService,
                           PythonDataGateway pythonGateway,
                           ObjectMapper objectMapper) {
        this.scoreHistoryService = scoreHistoryService;
        this.pythonGateway = pythonGateway;
        this.objectMapper = objectMapper;
    }

    public BacktestResult backtest(String ticker) throws Exception {
        List<ScoreHistoryEntity> history = scoreHistoryService.getFullHistory(ticker);
        if (history.isEmpty()) {
            return new BacktestResult(ticker, 0, 0, null, null, List.of());
        }

        TreeMap<LocalDate, Double> prices = fetchPrices(ticker);
        if (prices.isEmpty()) {
            throw new IllegalStateException("Sem histórico de preços para " + ticker);
        }

        List<BacktestEntry> entries = new ArrayList<>();
        for (ScoreHistoryEntity h : history) {
            Double priceAt = closeOnOrAfter(prices, h.getAnalysisDate());
            Double return30 = forwardReturn(prices, h.getAnalysisDate(), 30, priceAt);
            Double return90 = forwardReturn(prices, h.getAnalysisDate(), 90, priceAt);
            entries.add(new BacktestEntry(
                    h.getAnalysisDate(), h.getScoreGeral(), h.getModelUsed(), h.getPromptVersion(),
                    priceAt, return30, return90));
        }

        int withForward = (int) entries.stream().filter(e -> e.return30dPct() != null).count();

        return new BacktestResult(
                ticker,
                entries.size(),
                withForward,
                correlation(entries, BacktestEntry::return30dPct),
                correlation(entries, BacktestEntry::return90dPct),
                entries
        );
    }

    // -------------------------------------------------------------------------
    // Preços
    // -------------------------------------------------------------------------

    private TreeMap<LocalDate, Double> fetchPrices(String ticker) throws Exception {
        String json = pythonGateway.priceHistory(ticker.replace(".SA", ""), "2y");
        TreeMap<LocalDate, Double> prices = new TreeMap<>();

        for (JsonNode node : objectMapper.readTree(json)) {
            if (!node.path("close").isNull()) {
                prices.put(LocalDate.parse(node.path("date").asText()), node.path("close").asDouble());
            }
        }
        return prices;
    }

    /** Fechamento no dia ou no primeiro pregão seguinte (análises podem cair em fim de semana). */
    private Double closeOnOrAfter(TreeMap<LocalDate, Double> prices, LocalDate date) {
        var entry = prices.ceilingEntry(date);
        // tolerância de 5 dias — além disso o dado não representa a data da análise
        if (entry == null || entry.getKey().isAfter(date.plusDays(5))) return null;
        return entry.getValue();
    }

    private Double forwardReturn(TreeMap<LocalDate, Double> prices, LocalDate date, int days, Double priceAt) {
        if (priceAt == null || priceAt == 0.0) return null;
        LocalDate target = date.plusDays(days);
        // exige que a janela futura já tenha fechado — senão o retorno é parcial
        if (target.isAfter(LocalDate.now())) return null;
        var entry = prices.ceilingEntry(target);
        if (entry == null || entry.getKey().isAfter(target.plusDays(5))) return null;
        return Math.round((entry.getValue() - priceAt) / priceAt * 100 * 100.0) / 100.0;
    }

    // -------------------------------------------------------------------------
    // Correlação de Pearson entre scoreGeral e retorno forward
    // -------------------------------------------------------------------------

    private Double correlation(List<BacktestEntry> entries, java.util.function.Function<BacktestEntry, Double> returnFn) {
        List<double[]> pairs = entries.stream()
                .filter(e -> returnFn.apply(e) != null)
                .map(e -> new double[]{e.scoreGeral(), returnFn.apply(e)})
                .toList();
        // menos de 3 pares não tem significado estatístico nenhum
        if (pairs.size() < 3) return null;

        double meanX = pairs.stream().mapToDouble(p -> p[0]).average().orElse(0);
        double meanY = pairs.stream().mapToDouble(p -> p[1]).average().orElse(0);

        double cov = 0, varX = 0, varY = 0;
        for (double[] p : pairs) {
            double dx = p[0] - meanX;
            double dy = p[1] - meanY;
            cov += dx * dy;
            varX += dx * dx;
            varY += dy * dy;
        }
        if (varX == 0 || varY == 0) return null;
        return Math.round(cov / Math.sqrt(varX * varY) * 1000.0) / 1000.0;
    }
}
