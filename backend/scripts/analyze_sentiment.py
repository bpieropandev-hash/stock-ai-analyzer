#!/usr/bin/env python3
"""Análise de sentimento financeiro por léxico especializado (PT/EN) — sem dependência de rede."""
import json
import re
import sys

# Léxico financeiro positivo — PT e EN
_POSITIVE = {
    # Resultados
    "lucro", "lucros", "profit", "profits", "superávit", "surplus",
    "receita recorde", "resultado positivo", "record revenue",
    # Crescimento
    "crescimento", "cresceu", "crescendo", "growth", "grew", "growing",
    "expansão", "expandiu", "expansion", "expanded",
    "alta", "subiu", "subindo", "valorização", "valorizou",
    "rise", "rising", "rose", "gain", "gains", "gained",
    "avanço", "avançou", "advance", "advanced",
    "aumento", "aumentou", "increase", "increased",
    "aceleração", "acelerou", "acceleration", "accelerated",
    # Dividendos e retorno
    "dividendo", "dividendos", "dividend", "dividends",
    "jcp", "juros sobre capital", "buyback", "recompra",
    "rendimento", "yield", "retorno", "return",
    # Margem e eficiência
    "margem", "margin", "ebitda positivo", "geração de caixa", "cash generation",
    "fluxo de caixa positivo", "positive cash flow", "free cash flow positivo",
    "eficiência", "efficiency",
    # Guidance e expectativa
    "guidance elevado", "guidance positivo", "raised guidance", "upgraded",
    "superou expectativas", "supera expectativas", "beat expectations", "acima do esperado", "above estimates",
    "revisão para cima", "upward revision", "upgrade",
    "recomendação de compra", "buy rating", "outperform", "overweight",
    # Mercado e setor
    "demanda forte", "strong demand", "mercado aquecido", "bull market",
    "recuperação", "recovery", "recovered", "retomada",
    "oportunidade", "opportunity", "potencial", "potential",
    "inovação", "innovation", "disruptivo", "disruptive",
    "parceria estratégica", "strategic partnership", "fusão positiva",
    "liderança de mercado", "market leadership",
    # Financeiro
    "dívida reduzida", "redução da dívida", "debt reduced", "debt reduction",
    "desalavancagem", "deleveraging", "desalavancou", "deleverage",
    "caixa robusto", "strong cash", "liquidez saudável", "liquidity",
    "rating elevado", "rating upgrade", "investment grade",
}

# Léxico financeiro negativo — PT e EN
_NEGATIVE = {
    # Resultados ruins
    "prejuízo", "prejuizos", "loss", "losses", "deficit", "déficit",
    "resultado negativo", "negative result", "queda de lucro", "profit decline",
    # Queda
    "queda de receita", "queda de lucro", "queda de resultado",
    "caiu", "caindo", "decline", "fell", "falling",
    "desvalorização", "desvalorizou", "depreciation", "depreciated",
    "baixa", "recuo", "recuou", "drop", "dropped", "slump", "slumped",
    "retração", "retraiu", "contraction", "contracted",
    "redução de receita", "redução de lucro", "redução de margem",
    "reduziu receita", "revenue reduction", "revenue decline",
    "deterioração", "deteriorou", "deterioration", "deteriorated",
    # Dívida e caixa
    "endividamento", "dívida alta", "high debt", "alavancagem excessiva",
    "caixa negativo", "negative cash", "queima de caixa", "cash burn",
    "inadimplência", "default", "calote", "insolvência", "insolvency",
    "falência", "bankruptcy", "recuperação judicial", "chapter 11",
    # Guidance e expectativa negativa
    "guidance reduzido", "lowered guidance", "guidance cortado",
    "abaixo do esperado", "below estimates", "missed expectations",
    "decepcionou", "disappointed", "revisão para baixo", "downward revision",
    "downgrade", "rebaixamento", "underperform", "underweight", "sell rating",
    # Risco e incerteza
    "risco elevado", "high risk", "incerteza", "uncertainty",
    "crise", "crisis", "recessão", "recession",
    "inflação alta", "high inflation", "juros altos", "high rates",
    "aperto monetário", "monetary tightening", "aperto fiscal",
    "investigação", "investigation", "fraude", "fraud", "escândalo", "scandal",
    "processo", "lawsuit", "multa", "fine", "penalidade", "penalty",
    # Operacional
    "greve", "strike", "paralisação", "shutdown",
    "recall", "problema operacional", "operational issue",
    "perda de contrato", "lost contract", "churn elevado", "high churn",
    "perda de mercado", "market share loss", "concorrência intensa",
    # Gestão
    "troca de gestão", "management change", "CEO saiu", "CEO resigned",
    "reestruturação forçada", "forced restructuring",
}

# Modificadores de intensidade
_INTENSIFIERS = {"muito", "forte", "significativo", "expressivo", "grande",
                 "forte", "very", "highly", "significantly", "strongly", "major"}
_NEGATORS = {"não", "sem", "nenhum", "nunca", "jamais", "no", "not", "without", "never"}

_TOKEN_RE = re.compile(r"\b[\w]+\b", re.UNICODE | re.IGNORECASE)


def _tokenize(text: str) -> list[str]:
    return _TOKEN_RE.findall(text.lower())


def _score_text(text: str) -> tuple[str, float]:
    """
    Retorna (label, confidence) para um único texto.
    Lógica: conta matches positivos e negativos ponderados por intensificadores,
    descontados por negadores no contexto de janela de 3 tokens.
    """
    tokens = _tokenize(text)
    text_lower = text.lower()

    pos_score = 0.0
    neg_score = 0.0

    # Verifica frases compostas (bigramas e trigramas)
    for phrase in _POSITIVE:
        if phrase in text_lower:
            pos_score += 1.5 if " " in phrase else 1.0

    for phrase in _NEGATIVE:
        if phrase in text_lower:
            neg_score += 1.5 if " " in phrase else 1.0

    # Ajuste por janela de contexto (negadores e intensificadores em tokens)
    window = 3
    for i, token in enumerate(tokens):
        ctx_start = max(0, i - window)
        ctx = tokens[ctx_start:i]

        has_negator = any(t in _NEGATORS for t in ctx)
        has_intensifier = any(t in _INTENSIFIERS for t in ctx)
        multiplier = 0.0 if has_negator else (1.5 if has_intensifier else 1.0)

        if token in _POSITIVE:
            pos_score += multiplier * 0.5  # tokens já contados nas frases; peso menor
        elif token in _NEGATIVE:
            neg_score += multiplier * 0.5

    total = pos_score + neg_score
    if total == 0:
        return "neutral", 0.5

    pos_ratio = pos_score / total
    neg_ratio = neg_score / total

    if pos_ratio > neg_ratio:
        confidence = min(0.95, 0.5 + (pos_ratio - 0.5) * 1.5)
        return "positive", round(confidence, 4)
    elif neg_ratio > pos_ratio:
        confidence = min(0.95, 0.5 + (neg_ratio - 0.5) * 1.5)
        return "negative", round(confidence, 4)
    else:
        return "neutral", 0.5


def _fallback(n_texts: int = 0, reason: str = "") -> dict:
    fb = {
        "sentimentScore": 5.0,
        "distribution": {"positive": 0, "negative": 0, "neutral": n_texts},
        "confidence": 0.0,
        "source": "unavailable",
    }
    if reason:
        fb["fallbackReason"] = reason
    return fb


def _aggregate(labels: list[str], confidences: list[float]) -> dict:
    """
    Combina (label, confidence) por texto num resultado agregado. Compartilhada
    entre o caminho léxico e o FinBERT (finbert_sentiment.py) — mesma fórmula,
    mesma escala 0-10, pra o score continuar comparável independente da fonte.
    """
    pos_n = labels.count("positive")
    neg_n = labels.count("negative")
    neu_n = labels.count("neutral")
    total = len(labels)

    pos_conf = [confidences[i] for i, l in enumerate(labels) if l == "positive"]
    neg_conf = [confidences[i] for i, l in enumerate(labels) if l == "negative"]

    pos_avg = sum(pos_conf) / len(pos_conf) if pos_conf else 0.0
    neg_avg = sum(neg_conf) / len(neg_conf) if neg_conf else 0.0

    raw = pos_n * pos_avg - neg_n * neg_avg
    sentiment_score = round(max(0.0, min(10.0, ((raw / total) + 1) / 2 * 10)), 2)
    avg_confidence = round(sum(confidences) / total, 4)

    return {
        "sentimentScore": sentiment_score,
        "distribution": {"positive": pos_n, "negative": neg_n, "neutral": neu_n},
        "confidence": avg_confidence,
    }


def analyze(texts: list[str]) -> dict:
    if not texts:
        return _fallback(0, "no_input")

    results = [_score_text(t) for t in texts]
    labels = [r[0] for r in results]
    confidences = [r[1] for r in results]

    result = _aggregate(labels, confidences)
    result["source"] = "lexical"
    return result


if __name__ == "__main__":
    raw = sys.stdin.read().strip()
    if not raw:
        print(json.dumps(_fallback(0, "no_input")))
        sys.exit(0)

    try:
        texts = json.loads(raw)
    except json.JSONDecodeError as e:
        print(f"JSON de entrada inválido: {e}", file=sys.stderr)
        print(json.dumps(_fallback(0, "invalid_input_json")))
        sys.exit(0)

    if not texts:
        print(json.dumps(_fallback(0)))
        sys.exit(0)

    try:
        print(json.dumps(analyze(texts)))
    except Exception as e:
        print(f"Erro na análise de sentimento: {e}", file=sys.stderr)
        print(json.dumps(_fallback(len(texts), str(e)[:120])))
        sys.exit(0)
