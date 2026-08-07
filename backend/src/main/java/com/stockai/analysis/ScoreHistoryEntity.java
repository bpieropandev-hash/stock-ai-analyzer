package com.stockai.analysis;

import com.stockai.stock.Stock;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Histórico de scores em tabela relacional.
 * Substitui o armazenamento anterior em pgvector — dado tabular consultado por
 * ticker/data não deve viver atrás de busca por similaridade vetorial.
 */
@Entity
@Table(name = "score_history", indexes = {
        @Index(name = "idx_score_history_stock_date", columnList = "stock_id, analysis_date")
})
public class ScoreHistoryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // EAGER: Stock é uma tabela pequena (id+ticker) e todo consumidor precisa do
    // ticker imediatamente — LAZY exigiria @Transactional em todo call site que
    // hoje não tem (ex.: ScoreAlertService.getAlertsByTicker).
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "stock_id", nullable = false)
    private Stock stock;

    @Column(nullable = false)
    private LocalDate analysisDate;

    private double scoreGeral;
    private double fundamentos;
    private double valuation;
    private double regimeMomentum;
    private double sentimentoInstitucional;
    private double retornoAcionista;
    private double gestaoRisco;

    // Auditoria — sem isso é impossível explicar por que um score mudou
    private String modelUsed;
    private String promptVersion;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    protected ScoreHistoryEntity() {
        // exigido pelo JPA
    }

    public ScoreHistoryEntity(Stock stock, LocalDate analysisDate, double scoreGeral,
                              double fundamentos, double valuation, double regimeMomentum,
                              double sentimentoInstitucional, double retornoAcionista,
                              double gestaoRisco, String modelUsed, String promptVersion) {
        this.stock = stock;
        this.analysisDate = analysisDate;
        this.scoreGeral = scoreGeral;
        this.fundamentos = fundamentos;
        this.valuation = valuation;
        this.regimeMomentum = regimeMomentum;
        this.sentimentoInstitucional = sentimentoInstitucional;
        this.retornoAcionista = retornoAcionista;
        this.gestaoRisco = gestaoRisco;
        this.modelUsed = modelUsed;
        this.promptVersion = promptVersion;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Stock getStock() { return stock; }
    public String getTicker() { return stock.getTicker(); }
    public LocalDate getAnalysisDate() { return analysisDate; }
    public double getScoreGeral() { return scoreGeral; }
    public double getFundamentos() { return fundamentos; }
    public double getValuation() { return valuation; }
    public double getRegimeMomentum() { return regimeMomentum; }
    public double getSentimentoInstitucional() { return sentimentoInstitucional; }
    public double getRetornoAcionista() { return retornoAcionista; }
    public double getGestaoRisco() { return gestaoRisco; }
    public String getModelUsed() { return modelUsed; }
    public String getPromptVersion() { return promptVersion; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
