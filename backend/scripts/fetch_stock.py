#!/usr/bin/env python3
"""
Busca cotações das ações B3 via yfinance e retorna JSON no stdout.
Cada ticker recebe o sufixo .SA exigido pelo Yahoo Finance para ações brasileiras.
"""

import json
import sys

import yfinance as yf

TICKERS = [
    "PETR4", "VALE3", "ITUB4", "BBDC4", "WEGE3",
    "MGLU3", "ABEV3", "B3SA3", "RENT3", "SUZB3",
]


def fetch_quotes() -> list[dict]:
    results = []
    for ticker in TICKERS:
        symbol = ticker + ".SA"
        try:
            info = yf.Ticker(symbol).fast_info

            price = info.last_price
            prev = info.previous_close
            # calcula variação percentual em relação ao fechamento anterior
            change_pct = ((price - prev) / prev * 100) if (price and prev) else 0.0

            results.append({
                "symbol": ticker,
                "price": round(float(price), 2) if price is not None else None,
                "changePercent": round(float(change_pct), 4),
                "volume": int(info.last_volume) if info.last_volume else None,
                "marketCap": int(info.market_cap) if info.market_cap else None,
            })
        except Exception as exc:
            print(f"Erro ao buscar {symbol}: {exc}", file=sys.stderr)

    return results


if __name__ == "__main__":
    quotes = fetch_quotes()
    print(json.dumps(quotes))
