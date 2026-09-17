// Mede deriva numérica dos 6 scores de dimensão contra o baseline real
// capturado em analysis_audit (10 tickers, 2026-08-08, gemini-2.5-flash).
// Não reimplementa nenhuma regra financeira (cap setorial etc.) — isso é
// exclusividade do ScorePlausibilityGate em Java. Aqui só se verifica se o
// modelo/prompt atual ainda produz números na mesma faixa do baseline
// conhecido, pra pegar regressão de prompt/modelo, não violação de regra.

const DIMENSIONS = [
  "fundamentos",
  "valuation",
  "regimeMomentum",
  "sentimentoInstitucional",
  "retornoAcionista",
  "gestaoRisco",
];

const DIMENSION_TOLERANCE = 2.5;
const SCORE_GERAL_TOLERANCE = 1.5;

function sanitize(raw) {
  let s = raw.trim();
  if (s.startsWith("```")) {
    s = s.replace(/^```[a-z]*\n?/i, "").replace(/```\s*$/, "").trim();
  }
  return s;
}

function deriveRecommendation(score) {
  if (score > 7.5) return "ATRATIVO";
  if (score >= 6.0) return "NEUTRO";
  if (score >= 4.5) return "CAUTELA";
  return "DESFAVORÁVEL";
}

module.exports = (output, context) => {
  const vars = context.vars;
  let parsed;
  try {
    parsed = JSON.parse(sanitize(output));
  } catch (e) {
    return { pass: false, score: 0, reason: `resposta não é JSON válido: ${e.message}` };
  }

  const drifts = [];
  let sum = 0;
  for (const dim of DIMENSIONS) {
    const node = parsed[dim];
    if (!node || typeof node.score !== "number") {
      return { pass: false, score: 0, reason: `dimensão ausente ou sem score numérico: ${dim}` };
    }
    const score = Math.max(0, Math.min(10, node.score));
    sum += score;
    const baselineKey = `baseline${dim.charAt(0).toUpperCase()}${dim.slice(1)}`;
    const baseline = vars[baselineKey];
    const delta = Math.abs(score - baseline);
    drifts.push({ dim, score, baseline, delta });
    if (delta > DIMENSION_TOLERANCE) {
      return {
        pass: false,
        score: 0,
        reason: `${dim} desviou ${delta.toFixed(1)} pontos do baseline (atual=${score}, baseline=${baseline}, tolerância=${DIMENSION_TOLERANCE})`,
      };
    }
  }

  const scoreGeral = Math.round((sum / DIMENSIONS.length) * 10) / 10;
  const scoreGeralDelta = Math.abs(scoreGeral - vars.baselineScoreGeral);
  if (scoreGeralDelta > SCORE_GERAL_TOLERANCE) {
    return {
      pass: false,
      score: 0,
      reason: `scoreGeral desviou ${scoreGeralDelta.toFixed(1)} pontos do baseline (atual=${scoreGeral}, baseline=${vars.baselineScoreGeral})`,
    };
  }

  const currentRec = deriveRecommendation(scoreGeral);
  const baselineRec = deriveRecommendation(vars.baselineScoreGeral);
  const recNote = currentRec !== baselineRec
    ? ` | ATENÇÃO: recomendação mudaria de ${baselineRec} para ${currentRec}`
    : "";

  return {
    pass: true,
    score: 1,
    reason: `dentro da tolerância (scoreGeral atual=${scoreGeral}, baseline=${vars.baselineScoreGeral})${recNote}`,
  };
};
