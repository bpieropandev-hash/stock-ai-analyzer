#!/usr/bin/env python3
"""
Busca o histórico de preços de fechamento de uma ação B3 via yfinance.
Recebe o ticker e opcionalmente o período (default 1y).
Retorna JSON no stdout: [{"date": "YYYY-MM-DD", "close": float}, ...]
"""

import json
import math
import sys

import yfinance as yf


def _safe_float(value) -> float | None:
    try:
        f = float(value)
        return None if (math.isnan(f) or math.isinf(f)) else round(f, 4)
    except (TypeError, ValueError):
        return None


def fetch_price_history(ticker: str, period: str = "1y") -> list[dict]:
    symbol = ticker + ".SA"
    hist = yf.Ticker(symbol).history(period=period)
    if hist.empty:
        raise ValueError(f"Sem dados históricos para {symbol}")

    return [
        {"date": idx.strftime("%Y-%m-%d"), "close": _safe_float(row["Close"])}
        for idx, row in hist.iterrows()
        if _safe_float(row["Close"]) is not None
    ]


if __name__ == "__main__":
    if len(sys.argv) < 2:
        print("Uso: python fetch_price_history.py <TICKER> [period]", file=sys.stderr)
        sys.exit(1)

    raw_ticker = sys.argv[1].upper().removesuffix(".SA")
    period_arg = sys.argv[2] if len(sys.argv) > 2 else "1y"

    try:
        print(json.dumps(fetch_price_history(raw_ticker, period_arg)))
    except Exception as exc:
        print(f"Erro ao buscar histórico de preços de {raw_ticker}: {exc}", file=sys.stderr)
        sys.exit(1)
