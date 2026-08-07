#!/usr/bin/env python3
"""
Sentimento financeiro via FinBERT-PT-BR (lucas-leme/FinBERT-PT-BR) na Hugging
Face Inference API — classificador real treinado em 1,4M notícias financeiras
PT-BR, em vez do léxico de palavras-chave de analyze_sentiment.py.

Opcional por natureza (invariante: funcionalidade opcional nunca bloqueia uma
análise) — classify() retorna None em qualquer falha (sem token, timeout, erro
HTTP, resposta inesperada), e o chamador (sidecar_app.py) cai pro léxico.

HUGGINGFACE_TOKEN precisa estar no ambiente do processo que sobe o sidecar —
diferente do Java, o .env não é carregado automaticamente aqui (dois runtimes,
dois mecanismos de config, ver decisions.md).
"""
import os

import requests

_MODEL_ID = "lucas-leme/FinBERT-PT-BR"
_API_URL = f"https://router.huggingface.co/hf-inference/models/{_MODEL_ID}"
_TIMEOUT_S = 8
_LABEL_MAP = {"POSITIVE": "positive", "NEGATIVE": "negative", "NEUTRAL": "neutral"}


def _classify_one(text: str, token: str) -> tuple[str, float]:
    resp = requests.post(
        _API_URL,
        headers={"Authorization": f"Bearer {token}"},
        json={"inputs": text},
        timeout=_TIMEOUT_S,
    )
    resp.raise_for_status()
    data = resp.json()
    scores = data[0] if data and isinstance(data[0], list) else data
    top = max(scores, key=lambda x: x["score"])
    label = _LABEL_MAP.get(str(top["label"]).upper(), "neutral")
    return label, float(top["score"])


def classify(texts: list[str]) -> tuple[list[str], list[float]] | None:
    """(labels, confidences) via FinBERT, ou None se indisponível — nunca lança."""
    token = os.environ.get("HUGGINGFACE_TOKEN", "").strip()
    if not token or not texts:
        return None
    try:
        results = [_classify_one(t, token) for t in texts]
        return [r[0] for r in results], [r[1] for r in results]
    except Exception:
        return None
