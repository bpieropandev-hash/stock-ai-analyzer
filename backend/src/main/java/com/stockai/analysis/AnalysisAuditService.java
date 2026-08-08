package com.stockai.analysis;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Persistência best-effort do snapshot de auditoria — nunca deve derrubar uma
 * análise que já foi gerada e servida ao usuário (mesmo padrão defensivo de
 * ScoreHistoryService/ScoreAlertService: try/catch + log, sem propagar).
 */
@Service
public class AnalysisAuditService {

    private static final Logger log = LoggerFactory.getLogger(AnalysisAuditService.class);

    private final AnalysisAuditRepository repository;

    public AnalysisAuditService(AnalysisAuditRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void save(ScoreHistoryEntity scoreHistory, String prompt, String rawResponse,
                     StockAnalysis analysis, String reasoning, List<PlausibilitySignal> plausibilitySignals) {
        try {
            repository.save(new AnalysisAudit(
                    scoreHistory.getId(),
                    prompt,
                    rawResponse,
                    reasoning,
                    analysis.resumo(),
                    analysis.fundamentos().explicacao(),
                    analysis.valuation().explicacao(),
                    analysis.regimeMomentum().explicacao(),
                    analysis.sentimentoInstitucional().explicacao(),
                    analysis.retornoAcionista().explicacao(),
                    analysis.gestaoRisco().explicacao(),
                    plausibilitySignals.isEmpty() ? null
                            : plausibilitySignals.stream().map(Enum::name).collect(Collectors.joining(","))
            ));
        } catch (Exception e) {
            log.warn("Falha ao salvar auditoria de análise para {}: {}", analysis.ticker(), e.getMessage());
        }
    }
}
