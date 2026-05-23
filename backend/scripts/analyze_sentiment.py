#!/usr/bin/env python3
"""Análise de sentimento financeiro via HuggingFace Inference API (ProsusAI/finbert)."""
import json
import os
import sys
import urllib.error
import urllib.request

FINBERT_URL = "https://api-inference.huggingface.co/models/ProsusAI/finbert"
NEUTRAL_FALLBACK = {"sentimentScore": 5.0, "distribution": {"positive": 0, "negative": 0, "neutral": 0}, "confidence": 0.0}


def analyze(texts: list[str]) -> dict:
    token = os.environ.get("HUGGINGFACE_TOKEN", "")
    if not token:
        print("HUGGINGFACE_TOKEN não configurado — retornando neutro", file=sys.stderr)
        fallback = dict(NEUTRAL_FALLBACK)
        fallback["distribution"]["neutral"] = len(texts)
        return fallback

    payload = json.dumps({"inputs": texts}).encode("utf-8")
    req = urllib.request.Request(
        FINBERT_URL,
        data=payload,
        headers={"Authorization": f"Bearer {token}", "Content-Type": "application/json"},
    )

    with urllib.request.urlopen(req, timeout=30) as resp:
        results = json.loads(resp.read().decode("utf-8"))

    positive_scores, negative_scores, neutral_scores, all_confidences = [], [], [], []

    for item_results in results:
        best = max(item_results, key=lambda x: x["score"])
        label = best["label"].lower()
        score = best["score"]
        all_confidences.append(score)
        if label == "positive":
            positive_scores.append(score)
        elif label == "negative":
            negative_scores.append(score)
        else:
            neutral_scores.append(score)

    total = len(results)
    pos_count = len(positive_scores)
    neg_count = len(negative_scores)
    neu_count = len(neutral_scores)

    pos_avg = sum(positive_scores) / pos_count if pos_count else 0.0
    neg_avg = sum(negative_scores) / neg_count if neg_count else 0.0
    confidence = sum(all_confidences) / total if total else 0.0

    # Normaliza de [-total, +total] para [0, 10]
    raw = pos_count * pos_avg - neg_count * neg_avg
    sentiment_score = round(max(0.0, min(10.0, ((raw / total) + 1) / 2 * 10)), 2) if total else 5.0

    return {
        "sentimentScore": sentiment_score,
        "distribution": {"positive": pos_count, "negative": neg_count, "neutral": neu_count},
        "confidence": round(confidence, 4),
    }


if __name__ == "__main__":
    if len(sys.argv) < 2:
        print("Uso: analyze_sentiment.py '<json-array-de-textos>'", file=sys.stderr)
        sys.exit(1)

    try:
        texts = json.loads(sys.argv[1])
    except json.JSONDecodeError as e:
        print(f"JSON inválido: {e}", file=sys.stderr)
        sys.exit(1)

    if not texts:
        print(json.dumps(NEUTRAL_FALLBACK))
        sys.exit(0)

    try:
        print(json.dumps(analyze(texts)))
    except urllib.error.HTTPError as e:
        body = e.read().decode("utf-8", errors="replace")
        print(f"HTTP {e.code}: {body}", file=sys.stderr)
        fallback = dict(NEUTRAL_FALLBACK)
        fallback["distribution"]["neutral"] = len(texts)
        print(json.dumps(fallback))
    except Exception as e:
        print(f"Erro inesperado: {e}", file=sys.stderr)
        sys.exit(1)
