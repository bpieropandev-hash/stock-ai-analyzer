#!/usr/bin/env python3
"""
Sidecar HTTP persistente (FastAPI) — elimina o custo de 2–5s de import do
yfinance/pandas pago em cada spawn de processo Python.

Reaproveita as funções dos scripts existentes (que continuam funcionando
standalone como fallback). Endpoints síncronos rodam no threadpool do
Starlette, então chamadas bloqueantes do yfinance não travam o event loop.

Execução:
    cd backend/scripts
    python -m uvicorn sidecar_app:app --host 127.0.0.1 --port 8001
"""

import threading
from contextlib import asynccontextmanager

from fastapi import FastAPI, HTTPException
from fastapi.responses import JSONResponse

import cvm_data
from analyze_sentiment import _aggregate, _fallback, analyze
from finbert_sentiment import classify as finbert_classify
from fetch_fundamentals import fetch_fundamentals
from fetch_historical_fundamentals import fetch_historical
from fetch_macro import fetch_macro
from fetch_news import fetch_news
from fetch_price_history import fetch_price_history
from fetch_sector_benchmarks import compute_benchmarks
from fetch_stock import fetch_quotes
from fetch_technical_indicators import fetch_technical_indicators

@asynccontextmanager
async def _lifespan(_: FastAPI):
    # Datasets da CVM são dezenas de MB — baixa em background para não atrasar
    # o boot; até concluir, fundamentals responde só com yfinance
    threading.Thread(target=cvm_data.warm_cache, daemon=True).start()
    yield


app = FastAPI(title="stock-ai-analyzer sidecar", docs_url=None, redoc_url=None,
              lifespan=_lifespan)


def _base_ticker(ticker: str) -> str:
    """Normaliza para o formato base B3 (sem sufixo .SA), como os scripts CLI fazem."""
    return ticker.upper().removesuffix(".SA")


@app.get("/health")
def health() -> dict:
    return {"status": "ok"}


@app.get("/quotes")
def quotes() -> JSONResponse:
    return JSONResponse(fetch_quotes())


@app.get("/fundamentals/{ticker}")
def fundamentals(ticker: str) -> JSONResponse:
    try:
        return JSONResponse(fetch_fundamentals(_base_ticker(ticker)))
    except Exception as exc:
        raise HTTPException(status_code=502, detail=f"fundamentals {ticker}: {exc}")


@app.get("/macro")
def macro() -> JSONResponse:
    try:
        return JSONResponse(fetch_macro())
    except Exception as exc:
        raise HTTPException(status_code=502, detail=f"macro: {exc}")


@app.get("/news/{ticker}")
def news(ticker: str) -> JSONResponse:
    # Mesma semântica do CLI: uppercase sem strip de .SA (afeta a query de busca)
    return JSONResponse(fetch_news(ticker.upper()))


@app.post("/sentiment")
def sentiment(texts: list[str]) -> JSONResponse:
    if not texts:
        return JSONResponse(_fallback(0, "no_input"))

    # FinBERT real primeiro (classificador treinado); sem HUGGINGFACE_TOKEN ou
    # em qualquer falha (timeout, erro HTTP), finbert_classify retorna None e
    # cai pro léxico — nunca derruba a análise por causa do sentimento.
    finbert_result = finbert_classify(texts)
    if finbert_result is not None:
        labels, confidences = finbert_result
        result = _aggregate(labels, confidences)
        result["source"] = "finbert"
        return JSONResponse(result)

    try:
        return JSONResponse(analyze(texts))
    except Exception as exc:
        return JSONResponse(_fallback(len(texts), str(exc)[:120]))


@app.get("/sector-benchmarks")
def sector_benchmarks(tickers: str) -> JSONResponse:
    try:
        return JSONResponse(compute_benchmarks(tickers.split(",")))
    except Exception as exc:
        raise HTTPException(status_code=502, detail=f"sector-benchmarks: {exc}")


@app.get("/technical/{ticker}")
def technical(ticker: str) -> JSONResponse:
    try:
        return JSONResponse(fetch_technical_indicators(_base_ticker(ticker)))
    except Exception as exc:
        raise HTTPException(status_code=502, detail=f"technical {ticker}: {exc}")


@app.get("/price-history/{ticker}")
def price_history(ticker: str, period: str = "1y") -> JSONResponse:
    try:
        return JSONResponse(fetch_price_history(_base_ticker(ticker), period))
    except Exception as exc:
        raise HTTPException(status_code=502, detail=f"price-history {ticker}: {exc}")


@app.get("/historical-fundamentals/{ticker}")
def historical_fundamentals(ticker: str) -> JSONResponse:
    try:
        return JSONResponse(fetch_historical(ticker.upper()))
    except Exception as exc:
        raise HTTPException(status_code=502, detail=f"historical-fundamentals {ticker}: {exc}")
