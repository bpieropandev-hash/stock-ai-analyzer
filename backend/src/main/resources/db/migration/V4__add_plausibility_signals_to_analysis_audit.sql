-- Sinais de implausibilidade pós-score (com.stockai.analysis.ScorePlausibilityGate).
-- Nunca corrige/bloqueia score — só sinaliza inconsistência numérica entre dimensões/
-- scoreGeral e os dados que as sustentam. NULL = nenhum sinal disparado nessa análise.
-- Nomes do enum PlausibilitySignal separados por vírgula (conjunto de sinais é aberto,
-- coluna única texto evita migration nova a cada regra futura).
ALTER TABLE analysis_audit
    ADD COLUMN plausibility_signals TEXT;
