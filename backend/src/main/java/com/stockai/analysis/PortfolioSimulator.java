package com.stockai.analysis;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
public class PortfolioSimulator {

    private static final Logger log = LoggerFactory.getLogger(PortfolioSimulator.class);
    private static final String CACHE_PREFIX = "analysis:";

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;
    private final StockAnalysisService analysisService;

    public PortfolioSimulator(RedisTemplate<String, String> redisTemplate,
                              ObjectMapper objectMapper,
                              StockAnalysisService analysisService) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.analysisService = analysisService;
    }

    public SimulationResult simulate(double amount, List<String> tickers) throws Exception {
        List<AnalysisResponse> eligible = new ArrayList<>();
        List<String> excluded = new ArrayList<>();

        for (String ticker : tickers) {
            AnalysisResponse response = getOrAnalyze(ticker);
            String rec = response.recommendation();
            if ("COMPRAR".equals(rec) || "MANTER".equals(rec)) {
                eligible.add(response);
            } else {
                excluded.add(ticker.toUpperCase());
            }
        }

        if (eligible.isEmpty()) {
            return new SimulationResult(amount, List.of(), excluded, LocalDate.now());
        }

        double totalScore = eligible.stream()
                .mapToDouble(a -> a.analysis().scoreGeral())
                .sum();

        List<Allocation> allocations = new ArrayList<>();
        for (AnalysisResponse r : eligible) {
            double score = r.analysis().scoreGeral();
            double weight = totalScore > 0 ? score / totalScore : 1.0 / eligible.size();
            double allocated = amount * weight;
            double percentage = weight * 100.0;

            allocations.add(new Allocation(
                    r.analysis().ticker(),
                    r.sector(),
                    allocated,
                    percentage,
                    score,
                    r.recommendation(),
                    r.simpleSummary()
            ));
        }

        return new SimulationResult(amount, allocations, excluded, LocalDate.now());
    }

    private AnalysisResponse getOrAnalyze(String ticker) throws Exception {
        String cached = redisTemplate.opsForValue().get(CACHE_PREFIX + ticker);
        if (cached != null) {
            try {
                log.debug("Cache HIT para {} na simulação", ticker);
                return objectMapper.readValue(cached, AnalysisResponse.class);
            } catch (Exception e) {
                log.debug("Cache stale para {} — recomputando", ticker);
            }
        }
        return analysisService.analyze(ticker);
    }
}
