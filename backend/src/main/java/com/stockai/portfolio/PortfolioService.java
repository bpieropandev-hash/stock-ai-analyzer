package com.stockai.portfolio;

import com.stockai.analysis.Allocation;
import com.stockai.analysis.AnalysisResponse;
import com.stockai.analysis.SimulationResult;
import com.stockai.analysis.StockAnalysisService;
import com.stockai.stock.Stock;
import com.stockai.stock.StockRepository;
import com.stockai.stock.TickerNormalizer;
import com.stockai.user.UserEntity;
import com.stockai.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class PortfolioService {

    private static final Logger log = LoggerFactory.getLogger(PortfolioService.class);

    private final PortfolioRepository portfolioRepository;
    private final UserRepository userRepository;
    private final StockAnalysisService analysisService;
    private final StockRepository stockRepository;

    public PortfolioService(PortfolioRepository portfolioRepository,
                            UserRepository userRepository,
                            StockAnalysisService analysisService,
                            StockRepository stockRepository) {
        this.portfolioRepository = portfolioRepository;
        this.userRepository = userRepository;
        this.analysisService = analysisService;
        this.stockRepository = stockRepository;
    }

    @Transactional(readOnly = true)
    public List<PortfolioItemResponse> getPortfolio(String userId) {
        UserEntity user = getUserOrThrow(userId);
        List<PortfolioItemResponse> result = new ArrayList<>();
        for (PortfolioItem item : portfolioRepository.findByUser(user)) {
            try {
                AnalysisResponse analysis = analysisService.analyze(item.getTicker());
                result.add(new PortfolioItemResponse(
                        item.getTicker(),
                        item.getQuantity(),
                        item.getAveragePrice(),
                        item.getPurchaseDate(),
                        analysis.analysis().scoreGeral(),
                        analysis.recommendation(),
                        analysis.simpleSummary(),
                        analysis.sector()
                ));
            } catch (Exception e) {
                log.warn("Falha ao obter análise para {} na carteira: {}", item.getTicker(), e.getMessage());
                result.add(new PortfolioItemResponse(
                        item.getTicker(), item.getQuantity(), item.getAveragePrice(),
                        item.getPurchaseDate(), 0.0, "N/D", "Análise indisponível.", "N/D"
                ));
            }
        }
        return result;
    }

    @Transactional
    public PortfolioItem addOrUpdate(String userId, String ticker, Double quantity,
                                     Double averagePrice, LocalDate purchaseDate) {
        UserEntity user = getUserOrThrow(userId);
        String normalizedTicker = TickerNormalizer.canonical(ticker);
        return portfolioRepository.findByUserAndStock_Ticker(user, normalizedTicker)
                .map(item -> {
                    item.setQuantity(quantity);
                    item.setAveragePrice(averagePrice);
                    if (purchaseDate != null) item.setPurchaseDate(purchaseDate);
                    return portfolioRepository.save(item);
                })
                .orElseGet(() -> {
                    Stock stock = stockRepository.findOrCreate(normalizedTicker);
                    return portfolioRepository.save(
                            new PortfolioItem(user, stock, quantity, averagePrice, purchaseDate)
                    );
                });
    }

    @Transactional
    public void remove(String userId, String ticker) {
        UserEntity user = getUserOrThrow(userId);
        portfolioRepository.findByUserAndStock_Ticker(user, TickerNormalizer.canonical(ticker))
                .ifPresent(portfolioRepository::delete);
    }

    @Transactional(readOnly = true)
    public List<EvaluationItem> evaluate(String userId) {
        UserEntity user = getUserOrThrow(userId);
        List<EvaluationItem> result = new ArrayList<>();
        for (PortfolioItem item : portfolioRepository.findByUser(user)) {
            try {
                AnalysisResponse analysis = analysisService.analyze(item.getTicker());
                double score = analysis.analysis().scoreGeral();
                // Linguagem descritiva (Res. CVM 20/2021) — sem verbos de ordem
                String action = score >= 7.0 ? "ATRATIVO" : score >= 5.0 ? "NEUTRO" : "DESFAVORÁVEL";
                result.add(new EvaluationItem(
                        item.getTicker(), item.getQuantity(), item.getAveragePrice(),
                        score, action, analysis.simpleSummary()
                ));
            } catch (Exception e) {
                log.warn("Falha ao avaliar {} na carteira: {}", item.getTicker(), e.getMessage());
            }
        }
        return result;
    }

    @Transactional(readOnly = true)
    public SimulationResult suggestAllocation(String userId, double amount) {
        UserEntity user = getUserOrThrow(userId);
        List<PortfolioItem> items = portfolioRepository.findByUser(user);

        record ItemWithAnalysis(PortfolioItem item, AnalysisResponse analysis) {}

        List<ItemWithAnalysis> eligible = new ArrayList<>();
        List<String> excluded = new ArrayList<>();
        double totalScore = 0;

        for (PortfolioItem item : items) {
            try {
                AnalysisResponse analysis = analysisService.analyze(item.getTicker());
                // Elegibilidade por score (piso do NEUTRO) — não compara rótulo, que
                // pode estar na grafia antiga em análises ainda cacheadas no Redis
                if (analysis.analysis().scoreGeral() >= 6.0) {
                    eligible.add(new ItemWithAnalysis(item, analysis));
                    totalScore += analysis.analysis().scoreGeral();
                } else {
                    excluded.add(item.getTicker());
                }
            } catch (Exception e) {
                log.warn("Falha ao analisar {} para sugestão de alocação: {}", item.getTicker(), e.getMessage());
                excluded.add(item.getTicker());
            }
        }

        if (eligible.isEmpty()) {
            return new SimulationResult(amount, List.of(), excluded, LocalDate.now());
        }

        double finalTotalScore = totalScore;
        List<Allocation> allocations = new ArrayList<>();
        for (ItemWithAnalysis ia : eligible) {
            double score = ia.analysis().analysis().scoreGeral();
            double weight = finalTotalScore > 0 ? score / finalTotalScore : 1.0 / eligible.size();
            allocations.add(new Allocation(
                    ia.item().getTicker(),
                    ia.analysis().sector(),
                    amount * weight,
                    weight * 100.0,
                    score,
                    ia.analysis().recommendation(),
                    ia.analysis().simpleSummary()
            ));
        }

        return new SimulationResult(amount, allocations, excluded, LocalDate.now());
    }

    private UserEntity getUserOrThrow(String userId) {
        return userRepository.findById(UUID.fromString(userId))
                .orElseThrow(() -> new IllegalArgumentException("Usuário não encontrado: " + userId));
    }
}
