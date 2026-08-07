package com.stockai.analysis;

import com.stockai.stock.Stock;
import com.stockai.stock.StockRepository;
import com.stockai.stock.TickerNormalizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
public class ScoreHistoryService {

    private static final Logger log = LoggerFactory.getLogger(ScoreHistoryService.class);

    private final ScoreHistoryRepository repository;
    private final StockRepository stockRepository;

    public ScoreHistoryService(ScoreHistoryRepository repository, StockRepository stockRepository) {
        this.repository = repository;
        this.stockRepository = stockRepository;
    }

    @Transactional
    public void saveScore(StockAnalysis analysis, String modelUsed, String promptVersion) {
        try {
            Stock stock = stockRepository.findOrCreate(analysis.ticker());
            repository.save(new ScoreHistoryEntity(
                    stock,
                    analysis.analysisDate(),
                    analysis.scoreGeral(),
                    analysis.fundamentos().score(),
                    analysis.valuation().score(),
                    analysis.regimeMomentum().score(),
                    analysis.sentimentoInstitucional().score(),
                    analysis.retornoAcionista().score(),
                    analysis.gestaoRisco().score(),
                    modelUsed,
                    promptVersion
            ));
            log.info("Score history salvo — ticker={} data={} modelo={}",
                    analysis.ticker(), analysis.analysisDate(), modelUsed);
        } catch (Exception e) {
            log.warn("Falha ao salvar score history para {}: {}", analysis.ticker(), e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public List<ScoreSnapshot> getScoreHistory(String ticker, int days) {
        LocalDate cutoff = LocalDate.now().minusDays(days);
        String canonical = TickerNormalizer.canonical(ticker);
        return repository.findByStock_TickerAndAnalysisDateAfterOrderByAnalysisDateAsc(canonical, cutoff)
                .stream()
                .map(this::toSnapshot)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ScoreHistoryEntity> getFullHistory(String ticker) {
        return repository.findByStock_TickerOrderByAnalysisDateAsc(TickerNormalizer.canonical(ticker));
    }

    public ScoreSnapshot getLatestScore(String ticker) {
        List<ScoreSnapshot> history = getScoreHistory(ticker, 365);
        return history.isEmpty() ? null : history.getLast();
    }

    private ScoreSnapshot toSnapshot(ScoreHistoryEntity e) {
        return new ScoreSnapshot(
                e.getAnalysisDate(),
                e.getScoreGeral(),
                e.getFundamentos(),
                e.getValuation(),
                e.getRegimeMomentum(),
                e.getSentimentoInstitucional(),
                e.getRetornoAcionista(),
                e.getGestaoRisco()
        );
    }
}
