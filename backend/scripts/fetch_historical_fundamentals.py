#!/usr/bin/env python3
"""
Busca os últimos 4 trimestres de fundamentos de um ticker via yfinance.
Retorna uma lista JSON de snapshots históricos.
Uso: python fetch_historical_fundamentals.py PETR4
"""
import sys
import json
import math
import yfinance as yf


def _safe_float(v):
    if v is None:
        return None
    try:
        f = float(v)
        return None if (math.isnan(f) or math.isinf(f)) else f
    except (TypeError, ValueError):
        return None


def _quarter_label(ts):
    """Converte um Timestamp pandas para 'YYYY-QN'."""
    try:
        q = (ts.month - 1) // 3 + 1
        return f"{ts.year}-Q{q}"
    except Exception:
        return str(ts)[:10]


def _get(df, period, *row_keys):
    """Extrai o primeiro valor não-nulo de df para um dado período e lista de chaves."""
    if df is None or df.empty or period not in df.columns:
        return None
    for key in row_keys:
        if key in df.index:
            v = _safe_float(df.loc[key, period])
            if v is not None:
                return v
    return None


def _nearest_bs_period(qbs, target):
    """Retorna a coluna do balance sheet mais próxima do período alvo (≤ 100 dias)."""
    if qbs is None or qbs.empty:
        return None
    for p in qbs.columns:
        try:
            if abs((p - target).days) < 100:
                return p
        except Exception:
            pass
    return None


def fetch_historical(ticker_base: str) -> list:
    ticker_sa = ticker_base if ticker_base.endswith(".SA") else ticker_base + ".SA"
    stock = yf.Ticker(ticker_sa)

    qf = stock.quarterly_financials
    qbs = stock.quarterly_balance_sheet

    if qf is None or qf.empty:
        return []

    snapshots = []
    for period in qf.columns[:4]:
        bs_period = _nearest_bs_period(qbs, period)

        snapshot = {
            "ticker":          ticker_base,
            "period":          _quarter_label(period),
            "revenue":         _get(qf, period, "Total Revenue"),
            "netIncome":       _get(qf, period, "Net Income"),
            "grossProfit":     _get(qf, period, "Gross Profit"),
            "operatingIncome": _get(qf, period, "Operating Income", "EBIT"),
            "totalDebt":       _get(qbs, bs_period, "Total Debt", "Long Term Debt") if bs_period else None,
            "totalCash":       _get(qbs, bs_period,
                                    "Cash And Cash Equivalents",
                                    "Cash Cash Equivalents And Short Term Investments") if bs_period else None,
        }
        snapshots.append(snapshot)

    return snapshots


if __name__ == "__main__":
    if len(sys.argv) < 2:
        print(json.dumps({"error": "ticker requerido"}), file=sys.stderr)
        sys.exit(1)

    ticker = sys.argv[1].upper()
    try:
        result = fetch_historical(ticker)
        print(json.dumps(result))
    except Exception as exc:
        print(json.dumps({"error": str(exc)}), file=sys.stderr)
        sys.exit(1)
