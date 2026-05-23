#!/usr/bin/env python3
"""Análise de sentimento financeiro via HuggingFace Inference API (ProsusAI/finbert)."""
import json
import os
import sys
import time
import urllib.error
import urllib.request

FINBERT_URL = "https://api-inference.huggingface.co/models/ProsusAI/finbert"
RETRY_WAIT_SECONDS = 20   # cold start do modelo na HuggingFace
MAX_RETRIES = 2


def _fallback(n_texts: int = 0, reason: str = "") -> dict:
    """Cria fallback neutro sem reutilizar o mesmo dict (evita mutação compartilhada)."""
    fb = {
        "sentimentScore": 5.0,
        "distribution": {"positive": 0, "negative": 0, "neutral": n_texts},
        "confidence": 0.0,
    }
    if reason:
        fb["fallbackReason"] = reason
    return fb


def _call_api(payload: bytes, token: str) -> list:
    """Chama a API com retry automático em caso de 503 (cold start do modelo)."""
    headers = {"Authorization": f"Bearer {token}", "Content-Type": "application/json"}

    for attempt in range(1, MAX_RETRIES + 1):
        req = urllib.request.Request(FINBERT_URL, data=payload, headers=headers)
        try:
            with urllib.request.urlopen(req, timeout=30) as resp:
                raw = resp.read().decode("utf-8")
                print(f"[DEBUG] resposta bruta da API (tentativa {attempt}): {raw[:600]}", file=sys.stderr)
                return json.loads(raw)
        except urllib.error.HTTPError as e:
            body = e.read().decode("utf-8", errors="replace")
            print(f"[DEBUG] HTTP {e.code} (tentativa {attempt}): {body[:300]}", file=sys.stderr)
            if e.code == 503 and attempt < MAX_RETRIES:
                print(f"Modelo em cold start — aguardando {RETRY_WAIT_SECONDS}s...", file=sys.stderr)
                time.sleep(RETRY_WAIT_SECONDS)
                continue
            raise

    raise RuntimeError("Todas as tentativas de chamar a API falharam")


def _normalize(results: list, n_texts: int) -> list:
    """
    A API retorna formatos diferentes dependendo do número de inputs:
    - 1 input  → lista plana:   [{label, score}, {label, score}, ...]
    - N inputs → lista aninhada: [[{...}, ...], [{...}, ...]]

    Normaliza sempre para lista aninhada.
    """
    if not results:
        return []
    # Primeiro elemento é dict → lista plana (único input) → envolve em lista
    if isinstance(results[0], dict):
        print("[DEBUG] formato plano detectado — envolvendo em lista aninhada", file=sys.stderr)
        return [results]
    return results


def _compute(results: list) -> dict:
    """Calcula score, distribuição e confiança a partir dos resultados normalizados."""
    pos_scores, neg_scores, neu_scores, confidences = [], [], [], []

    for item in results:
        if not item:
            continue
        best = max(item, key=lambda x: x["score"])
        label = best["label"].lower()
        score = best["score"]
        confidences.append(score)
        if label == "positive":
            pos_scores.append(score)
        elif label == "negative":
            neg_scores.append(score)
        else:
            neu_scores.append(score)

    total = len(results)
    pos_n, neg_n, neu_n = len(pos_scores), len(neg_scores), len(neu_scores)
    pos_avg = sum(pos_scores) / pos_n if pos_n else 0.0
    neg_avg = sum(neg_scores) / neg_n if neg_n else 0.0
    confidence = sum(confidences) / total if total else 0.0

    # Normaliza score de [-total, +total] para [0, 10]
    raw = pos_n * pos_avg - neg_n * neg_avg
    sentiment_score = round(max(0.0, min(10.0, ((raw / total) + 1) / 2 * 10)), 2) if total else 5.0

    return {
        "sentimentScore": sentiment_score,
        "distribution": {"positive": pos_n, "negative": neg_n, "neutral": neu_n},
        "confidence": round(confidence, 4),
    }


def analyze(texts: list[str]) -> dict:
    token = os.environ.get("HUGGINGFACE_TOKEN", "")
    if not token:
        print("HUGGINGFACE_TOKEN não configurado — retornando neutro", file=sys.stderr)
        return _fallback(len(texts), "token_missing")

    payload = json.dumps({"inputs": texts}).encode("utf-8")
    raw_results = _call_api(payload, token)
    normalized = _normalize(raw_results, len(texts))
    print(f"[DEBUG] {len(normalized)} itens após normalização", file=sys.stderr)
    return _compute(normalized)


if __name__ == "__main__":
    if len(sys.argv) < 2:
        print("Uso: analyze_sentiment.py '<json-array-de-textos>'", file=sys.stderr)
        print(json.dumps(_fallback(0, "no_args")))
        sys.exit(0)

    try:
        texts = json.loads(sys.argv[1])
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
        # Sempre imprime JSON válido — o serviço Java não pode receber stdout vazio
        print(f"Erro na análise de sentimento: {e}", file=sys.stderr)
        print(json.dumps(_fallback(len(texts), str(e)[:120])))
        sys.exit(0)
