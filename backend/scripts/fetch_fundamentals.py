#!/usr/bin/env python3
"""
Busca dados fundamentalistas de uma ação B3 via yfinance.
Recebe o ticker como argumento (ex: PETR4) e retorna JSON no stdout.
"""

import json
import math
import sys

import yfinance as yf


def _safe_float(value) -> float | None:
    """Converte para float descartando NaN e Inf que não são serializáveis em JSON."""
    try:
        f = float(value)
        return None if (math.isnan(f) or math.isinf(f)) else f
    except (TypeError, ValueError):
        return None


def _revenue_growth(stock: yf.Ticker) -> float | None:
    """Calcula crescimento de receita YoY a partir dos demonstrativos anuais."""
    try:
        financials = stock.financials
        if financials is None or financials.empty:
            return None
        if "Total Revenue" not in financials.index:
            return None
        revenues = financials.loc["Total Revenue"].dropna()
        if len(revenues) < 2:
            return None
        current, previous = float(revenues.iloc[0]), float(revenues.iloc[1])
        if previous == 0:
            return None
        return round((current - previous) / abs(previous) * 100, 4)
    except Exception:
        return None


def fetch_fundamentals(ticker: str) -> dict:
    symbol = ticker + ".SA"
    stock = yf.Ticker(symbol)
    info = stock.info

    roe = _safe_float(info.get("returnOnEquity"))
    net_margin = _safe_float(info.get("profitMargins"))
    dividend_yield = _safe_float(info.get("dividendYield"))

    return {
        "ticker": ticker,
        "symbol": symbol,
        "name": info.get("longName"),
        "sector": info.get("sector"),
        "industry": info.get("industry"),
        # P/L (Price-to-Earnings) — múltiplo de valuation
        "priceToEarnings": _safe_float(info.get("trailingPE")),
        # ROE retornado como decimal pelo yfinance (ex: 0.25 = 25%)
        "roe": roe,
        # Margem líquida retornada como decimal pelo yfinance (ex: 0.12 = 12%)
        "netMargin": net_margin,
        "debtToEquity": _safe_float(info.get("debtToEquity")),
        "revenueGrowth": _revenue_growth(stock),
        # Dividend yield retornado como decimal pelo yfinance (ex: 0.06 = 6%)
        "dividendYield": dividend_yield,
        "marketCap": info.get("marketCap"),
        "currency": info.get("currency"),
    }


if __name__ == "__main__":
    if len(sys.argv) < 2:
        print("Uso: python fetch_fundamentals.py <TICKER>", file=sys.stderr)
        sys.exit(1)

    raw_ticker = sys.argv[1].upper().removesuffix(".SA")

    try:
        result = fetch_fundamentals(raw_ticker)
        print(json.dumps(result))
    except Exception as exc:
        print(f"Erro ao buscar fundamentals de {raw_ticker}: {exc}", file=sys.stderr)
        sys.exit(1)
