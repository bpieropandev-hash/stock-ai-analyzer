package com.stockai.analysis;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * Snapshot completo de uma análise: prompt exato enviado, resposta bruta do LLM
 * e os campos de raciocínio/explicação que o prompt já pede mas o fluxo
 * principal (AnalysisResponse/score_history) não guarda. Ligado a score_history
 * por FK simples (não @OneToOne mapeado) — tabela de escrita/consulta rara,
 * não faz sentido carregar o grafo de objetos pra isso.
 */
@Entity
@Table(name = "analysis_audit")
public class AnalysisAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "score_history_id", nullable = false, unique = true)
    private Long scoreHistoryId;

    @Column(name = "prompt_full", nullable = false, columnDefinition = "TEXT")
    private String promptFull;

    @Column(name = "raw_response", nullable = false, columnDefinition = "TEXT")
    private String rawResponse;

    @Column(columnDefinition = "TEXT")
    private String reasoning;

    @Column(columnDefinition = "TEXT")
    private String resumo;

    @Column(name = "explicacao_fundamentos", columnDefinition = "TEXT")
    private String explicacaoFundamentos;

    @Column(name = "explicacao_valuation", columnDefinition = "TEXT")
    private String explicacaoValuation;

    @Column(name = "explicacao_regime_momentum", columnDefinition = "TEXT")
    private String explicacaoRegimeMomentum;

    @Column(name = "explicacao_sentimento_institucional", columnDefinition = "TEXT")
    private String explicacaoSentimentoInstitucional;

    @Column(name = "explicacao_retorno_acionista", columnDefinition = "TEXT")
    private String explicacaoRetornoAcionista;

    @Column(name = "explicacao_gestao_risco", columnDefinition = "TEXT")
    private String explicacaoGestaoRisco;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected AnalysisAudit() {
        // exigido pelo JPA
    }

    public AnalysisAudit(Long scoreHistoryId, String promptFull, String rawResponse, String reasoning,
                         String resumo, String explicacaoFundamentos, String explicacaoValuation,
                         String explicacaoRegimeMomentum, String explicacaoSentimentoInstitucional,
                         String explicacaoRetornoAcionista, String explicacaoGestaoRisco) {
        this.scoreHistoryId = scoreHistoryId;
        this.promptFull = promptFull;
        this.rawResponse = rawResponse;
        this.reasoning = reasoning;
        this.resumo = resumo;
        this.explicacaoFundamentos = explicacaoFundamentos;
        this.explicacaoValuation = explicacaoValuation;
        this.explicacaoRegimeMomentum = explicacaoRegimeMomentum;
        this.explicacaoSentimentoInstitucional = explicacaoSentimentoInstitucional;
        this.explicacaoRetornoAcionista = explicacaoRetornoAcionista;
        this.explicacaoGestaoRisco = explicacaoGestaoRisco;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Long getScoreHistoryId() { return scoreHistoryId; }
    public String getPromptFull() { return promptFull; }
    public String getRawResponse() { return rawResponse; }
    public String getReasoning() { return reasoning; }
    public String getResumo() { return resumo; }
    public String getExplicacaoFundamentos() { return explicacaoFundamentos; }
    public String getExplicacaoValuation() { return explicacaoValuation; }
    public String getExplicacaoRegimeMomentum() { return explicacaoRegimeMomentum; }
    public String getExplicacaoSentimentoInstitucional() { return explicacaoSentimentoInstitucional; }
    public String getExplicacaoRetornoAcionista() { return explicacaoRetornoAcionista; }
    public String getExplicacaoGestaoRisco() { return explicacaoGestaoRisco; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
