package com.stockai.analysis;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class ComparisonService {

    private static final Logger log = LoggerFactory.getLogger(ComparisonService.class);
    private static final String CACHE_PREFIX = "analysis:";

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;
    private final StockAnalysisService analysisService;

    public ComparisonService(RedisTemplate<String, String> redisTemplate,
                             ObjectMapper objectMapper,
                             StockAnalysisService analysisService) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.analysisService = analysisService;
    }

    public ComparisonResult compare(List<String> tickers) throws Exception {
        List<AnalysisResponse> analyses = new ArrayList<>();
        for (String ticker : tickers) {
            analyses.add(getOrAnalyze(ticker));
        }

        List<RankedStock> ranking = buildRanking(analyses);

        String bestForDividends = analyses.stream()
                .max(Comparator.comparingDouble(a -> a.analysis().retornoAcionista().score()))
                .map(a -> a.analysis().ticker())
                .orElse(null);

        String bestMomentum = analyses.stream()
                .max(Comparator.comparingDouble(a -> a.analysis().regimeMomentum().score()))
                .map(a -> a.analysis().ticker())
                .orElse(null);

        // menor risco = maior score de gestaoRisco (gestão de risco melhor = risco menor)
        String lowestRisk = analyses.stream()
                .max(Comparator.comparingDouble(a -> a.analysis().gestaoRisco().score()))
                .map(a -> a.analysis().ticker())
                .orElse(null);

        return new ComparisonResult(tickers, analyses, ranking, bestForDividends, bestMomentum, lowestRisk, LocalDate.now());
    }

    private AnalysisResponse getOrAnalyze(String ticker) throws Exception {
        String cached = redisTemplate.opsForValue().get(CACHE_PREFIX + ticker);
        if (cached != null) {
            try {
                log.debug("Cache HIT para {} na comparação", ticker);
                return objectMapper.readValue(cached, AnalysisResponse.class);
            } catch (Exception e) {
                log.debug("Cache stale para {} — recomputando", ticker);
            }
        }
        return analysisService.analyze(ticker);
    }

    private List<RankedStock> buildRanking(List<AnalysisResponse> analyses) {
        List<AnalysisResponse> sorted = analyses.stream()
                .sorted(Comparator.comparingDouble((AnalysisResponse a) -> a.analysis().scoreGeral()).reversed())
                .toList();

        List<RankedStock> ranking = new ArrayList<>();
        for (int i = 0; i < sorted.size(); i++) {
            AnalysisResponse r = sorted.get(i);
            ranking.add(new RankedStock(
                    i + 1,
                    r.analysis().ticker(),
                    r.analysis().scoreGeral(),
                    r.recommendation(),
                    r.simpleSummary(),
                    r.sector()
            ));
        }
        return ranking;
    }
}
