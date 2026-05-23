package com.stockai.analysis;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.filter.MetadataFilterBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class ScoreHistoryService {

    private static final Logger log = LoggerFactory.getLogger(ScoreHistoryService.class);
    private static final String TYPE = "score_history";

    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> embeddingStore;
    private final ObjectMapper objectMapper;

    public ScoreHistoryService(
            EmbeddingModel embeddingModel,
            EmbeddingStore<TextSegment> embeddingStore,
            ObjectMapper objectMapper) {
        this.embeddingModel = embeddingModel;
        this.embeddingStore = embeddingStore;
        this.objectMapper = objectMapper;
    }

    public void saveScore(StockAnalysis analysis) {
        try {
            ScoreSnapshot snapshot = toSnapshot(analysis);
            String jsonText = objectMapper.writeValueAsString(snapshot);

            // Embedding sobre texto descritivo; payload armazenado é o JSON do snapshot
            String descriptiveText = TYPE + " " + analysis.ticker() + " " + analysis.analysisDate();
            Embedding embedding = embeddingModel.embed(TextSegment.from(descriptiveText)).content();

            Metadata metadata = new Metadata();
            metadata.put("ticker", analysis.ticker());
            metadata.put("date", analysis.analysisDate().toString());
            metadata.put("type", TYPE);

            embeddingStore.add(embedding, TextSegment.from(jsonText, metadata));
            log.info("Score history salvo — ticker={} data={}", analysis.ticker(), analysis.analysisDate());
        } catch (Exception e) {
            log.warn("Falha ao salvar score history para {}: {}", analysis.ticker(), e.getMessage());
        }
    }

    public List<ScoreSnapshot> getScoreHistory(String ticker, int days) {
        try {
            String queryText = TYPE + " " + ticker;
            Embedding queryEmbedding = embeddingModel.embed(TextSegment.from(queryText)).content();

            Filter filter = MetadataFilterBuilder.metadataKey("ticker").isEqualTo(ticker);

            List<EmbeddingMatch<TextSegment>> matches = embeddingStore.search(
                    EmbeddingSearchRequest.builder()
                            .queryEmbedding(queryEmbedding)
                            .maxResults(200)
                            .filter(filter)
                            .build()
            ).matches();

            LocalDate cutoff = LocalDate.now().minusDays(days);
            List<ScoreSnapshot> result = new ArrayList<>();

            for (EmbeddingMatch<TextSegment> match : matches) {
                String type = match.embedded().metadata().getString("type");
                if (!TYPE.equals(type)) continue;
                try {
                    ScoreSnapshot snapshot = objectMapper.readValue(
                            match.embedded().text(), ScoreSnapshot.class);
                    if (!snapshot.date().isBefore(cutoff)) {
                        result.add(snapshot);
                    }
                } catch (Exception e) {
                    log.debug("Falha ao parsear snapshot de score: {}", e.getMessage());
                }
            }

            result.sort(Comparator.comparing(ScoreSnapshot::date));
            return result;
        } catch (Exception e) {
            log.warn("Falha ao buscar score history para {}: {}", ticker, e.getMessage());
            return List.of();
        }
    }

    public ScoreSnapshot getLatestScore(String ticker) {
        List<ScoreSnapshot> history = getScoreHistory(ticker, 365);
        return history.isEmpty() ? null : history.getLast();
    }

    private ScoreSnapshot toSnapshot(StockAnalysis a) {
        return new ScoreSnapshot(
                a.analysisDate(),
                a.scoreGeral(),
                a.fundamentos().score(),
                a.valuation().score(),
                a.regimeMomentum().score(),
                a.sentimentoInstitucional().score(),
                a.retornoAcionista().score(),
                a.gestaoRisco().score()
        );
    }
}
