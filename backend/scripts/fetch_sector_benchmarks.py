#!/usr/bin/env python3
"""
Medianas reais de múltiplos de uma lista de pares setoriais da B3.

Para cada ticker: números contábeis dos demonstrativos CVM (cache local, ver
cvm_data.py) + market cap leve do yfinance (fast_info — sem o .info completo).
P/L, P/VPA e DY são calculados por empresa; a saída traz a mediana de cada
métrica entre os pares que tinham dado disponível.

Uso CLI: python fetch_sector_benchmarks.py PETR4,PRIO3,CSAN3
"""

import json
import statistics
import sys
from concurrent.futures import ThreadPoolExecutor
from datetime import date

import yfinance as yf

from cvm_data import fetch_cvm_fundamentals

_METRICS = ("priceToEarnings", "priceToBook", "dividendYield",
            "roe", "netMargin", "debtToEquity")
# Mediana com menos de 3 pontos não representa o setor
_MIN_SAMPLES = 3


def _market_cap(ticker: str) -> float | None:
    try:
        fast = yf.Ticker(ticker + ".SA").fast_info
        cap = getattr(fast, "market_cap", None)
        return float(cap) if cap else None
    except Exception:
        return None


def _peer_snapshot(ticker: str) -> dict | None:
    cvm = fetch_cvm_fundamentals(ticker)
    if cvm is None:
        return None
    snapshot = {
        "roe": cvm["roe"],
        "netMargin": cvm["netMargin"],
        "debtToEquity": cvm["debtToEquity"],
        "priceToEarnings": None,
        "priceToBook": None,
        "dividendYield": None,
    }
    market_cap = _market_cap(ticker)
    if market_cap:
        net_income, equity = cvm["netIncomeLtm"], cvm["equity"]
        dividends = cvm["dividendsPaidLtm"]
        if net_income and net_income > 0:
            snapshot["priceToEarnings"] = market_cap / net_income
        if equity and equity > 0:
            snapshot["priceToBook"] = market_cap / equity
        if dividends is not None:
            snapshot["dividendYield"] = dividends / market_cap
    return snapshot


def compute_benchmarks(tickers: list[str]) -> dict:
    tickers = [t.strip().upper().removesuffix(".SA") for t in tickers if t.strip()]

    with ThreadPoolExecutor(max_workers=8) as pool:
        snapshots = list(pool.map(_peer_snapshot, tickers))

    valid = [(t, s) for t, s in zip(tickers, snapshots) if s is not None]

    medians: dict[str, float | None] = {}
    samples: dict[str, int] = {}
    for metric in _METRICS:
        values = [s[metric] for _, s in valid if s[metric] is not None]
        samples[metric] = len(values)
        medians[metric] = (round(statistics.median(values), 4)
                           if len(values) >= _MIN_SAMPLES else None)

    return {
        "tickers": [t for t, _ in valid],
        "peerCount": len(valid),
        "computedAt": date.today().isoformat(),
        "medians": medians,
        "samples": samples,
    }


if __name__ == "__main__":
    if len(sys.argv) < 2:
        print("Uso: python fetch_sector_benchmarks.py <TICKER1,TICKER2,...>", file=sys.stderr)
        sys.exit(1)
    try:
        print(json.dumps(compute_benchmarks(sys.argv[1].split(","))))
    except Exception as exc:
        print(f"Erro ao calcular benchmarks setoriais: {exc}", file=sys.stderr)
        sys.exit(1)
