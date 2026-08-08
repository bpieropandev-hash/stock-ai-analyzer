package com.stockai.analysis;

/**
 * Sinais de inconsistência numérica entre dimensões/scoreGeral e os dados que
 * as sustentam — produzidos por {@link ScorePlausibilityGate}. Nunca corrige
 * nem bloqueia o score, só sinaliza para auditoria.
 */
public enum PlausibilitySignal {

    FUNDAMENTOS_ALTO_MOMENTUM_BAIXO(
            "Fundamentos forte (>=7) mas Regime/Momentum muito fraco (<3.5) — contradiz a trava da rubrica " +
            "('momentum ruim com fundamentos fortes não deve ficar abaixo de 3.5')."),
    PE_NEGATIVO_VALUATION_ALTO_SEM_PVPA(
            "P/L negativo e sem P/VPA disponível para justificar a substituição que a rubrica instrui, " +
            "mas Valuation pontuado como atrativo (>=7)."),
    DIVIDENDO_ZERO_APESAR_DE_HISTORICO_RETORNO_BAIXO(
            "Dividend yield zerado com histórico de dividendos existente, e Retorno ao Acionista pontuado " +
            "muito baixo (<=2) — a rubrica instrui usar o histórico quando o yield parece falha de coleta."),
    SCORE_ALTO_CONFIANCA_BAIXA(
            "scoreGeral acima do piso de ATRATIVO (>7.5) mas confiança na qualidade do dado é baixa (<4)."),
    VAREJO_RETORNO_ALTO_SEM_LUCRO_CONSISTENTE(
            "Setor VAREJO com Retorno ao Acionista acima de 4 apesar de lucro trimestral inconsistente — " +
            "viola o hard cap já escrito na instrução setorial ('sem lucro consistente, retornoAcionista não pode passar de 4').");

    private final String description;

    PlausibilitySignal(String description) {
        this.description = description;
    }

    public String description() {
        return description;
    }
}
