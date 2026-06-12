#!/usr/bin/env python3
"""
Busca dados fundamentalistas enriquecidos de uma ação B3.

Fonte primária dos números contábeis: demonstrativos oficiais da CVM
(ITR/DFP via cvm_data) — o yfinance para B3 tem P/L, P/VPA e DY
frequentemente errados ou defasados. O yfinance segue como fonte dos dados
de mercado (preço, market cap, beta, 52s) e fallback integral quando a CVM
não cobre o ticker ou o cache ainda não baixou.

Recebe o ticker como argumento (ex: PETR4) e retorna JSON no stdout.
"""

import json
import math
import sys

import yfinance as yf

from cvm_data import fetch_cvm_fundamentals


# ---------------------------------------------------------------------------
# Helpers de sanitização
# ---------------------------------------------------------------------------

def _safe_float(value) -> float | None:
    """Converte para float descartando NaN e Inf não-serializáveis em JSON."""
    try:
        f = float(value)
        return None if (math.isnan(f) or math.isinf(f)) else f
    except (TypeError, ValueError):
        return None


def _safe_int(value) -> int | None:
    """Converte para int de forma segura, descartando NaN/Inf."""
    try:
        f = float(value)
        return None if (math.isnan(f) or math.isinf(f)) else int(f)
    except (TypeError, ValueError):
        return None


# ---------------------------------------------------------------------------
# Extratores específicos
# ---------------------------------------------------------------------------

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


def _dividend_history(stock: yf.Ticker) -> list[dict]:
    """Retorna os últimos 4 dividendos pagos com data (YYYY-MM-DD) e valor."""
    try:
        dividends = stock.dividends
        if dividends is None or dividends.empty:
            return []
        return [
            {"date": idx.strftime("%Y-%m-%d"), "value": _safe_float(val)}
            for idx, val in dividends.tail(4).items()
        ]
    except Exception:
        return []


def _quarterly_results(stock: yf.Ticker) -> list[dict]:
    """Retorna os últimos 4 quarters com período, revenue e earnings."""
    try:
        q = stock.quarterly_financials
        if q is None or q.empty:
            return []
        results = []
        for col in q.columns[:4]:  # colunas ordenadas do mais recente para o mais antigo
            results.append({
                "period": col.strftime("%Y-%m-%d") if hasattr(col, "strftime") else str(col),
                "revenue": _safe_float(q.loc["Total Revenue", col]) if "Total Revenue" in q.index else None,
                "earnings": _safe_float(q.loc["Net Income", col]) if "Net Income" in q.index else None,
            })
        return results
    except Exception:
        return []


# ---------------------------------------------------------------------------
# Função principal
# ---------------------------------------------------------------------------

def _cvm_overlay(data: dict) -> None:
    """
    Sobrepõe os campos contábeis com os demonstrativos oficiais da CVM e
    recalcula os ratios de valuation com o market cap do yfinance. Sobrepõe
    apenas o que a CVM fornece — campo ausente mantém o valor do yfinance.
    """
    try:
        cvm = fetch_cvm_fundamentals(data["ticker"])
    except Exception as exc:
        print(f"Overlay CVM indisponível para {data['ticker']}: {exc}", file=sys.stderr)
        return
    if cvm is None:
        return

    overrides = {
        "roe": cvm["roe"],
        "roa": cvm["roa"],
        "netMargin": cvm["netMargin"],
        "operatingMargin": cvm["operatingMargin"],
        "debtToEquity": cvm["debtToEquity"],
        "totalDebt": _safe_int(cvm["totalDebt"]),
        "totalCash": _safe_int(cvm["totalCash"]),
        "totalRevenue": _safe_int(cvm["revenueLtm"]),
        "earningsGrowth": cvm["earningsGrowth"],
        # convenção do script: revenueGrowth já em % (demais razões em decimal)
        "revenueGrowth": (round(cvm["revenueGrowth"] * 100, 4)
                          if cvm["revenueGrowth"] is not None else None),
    }

    market_cap = data.get("marketCap")
    if market_cap:
        net_income, equity = cvm["netIncomeLtm"], cvm["equity"]
        dividends = cvm["dividendsPaidLtm"]
        # P/L negativo não tem leitura — empresa no prejuízo fica sem o múltiplo
        if net_income is not None:
            overrides["priceToEarnings"] = (round(market_cap / net_income, 4)
                                            if net_income > 0 else None)
        if equity and equity > 0:
            overrides["priceToBook"] = round(market_cap / equity, 4)
        if dividends is not None:
            overrides["dividendYield"] = round(dividends / market_cap, 4)

    for field, value in overrides.items():
        if value is not None or field == "priceToEarnings":
            data[field] = value

    data["fundamentalsSource"] = "cvm+yfinance"
    data["statementDate"] = cvm["statementDate"]


def fetch_fundamentals(ticker: str) -> dict:
    symbol = ticker + ".SA"
    stock = yf.Ticker(symbol)
    info = stock.info

    data = {
        # Identificação
        "ticker": ticker,
        "symbol": symbol,
        "name": info.get("longName"),
        "sector": info.get("sector", ""),
        "industry": info.get("industry", ""),
        "currency": info.get("currency"),

        # Valuation
        "priceToEarnings": _safe_float(info.get("trailingPE")),
        "priceToBook": _safe_float(info.get("priceToBook")),
        "marketCap": _safe_int(info.get("marketCap")),

        # Rentabilidade
        # ROE, ROA, margens retornados como decimal pelo yfinance (0.25 = 25%)
        "roe": _safe_float(info.get("returnOnEquity")),
        "roa": _safe_float(info.get("returnOnAssets")),
        "netMargin": _safe_float(info.get("profitMargins")),
        "operatingMargin": _safe_float(info.get("operatingMargins")),

        # Endividamento e balanço patrimonial (valores absolutos em BRL)
        # yfinance retorna debtToEquity como percentual (84.5 = 0.845x) — normaliza para razão
        "debtToEquity": (lambda v: round(v / 100, 4) if v is not None else None)(
            _safe_float(info.get("debtToEquity"))),
        "totalDebt": _safe_int(info.get("totalDebt")),
        "totalCash": _safe_int(info.get("totalCash")),
        "totalRevenue": _safe_int(info.get("totalRevenue")),
        "operatingCashflow": _safe_int(info.get("operatingCashflow")),
        "freeCashflow": _safe_int(info.get("freeCashflow")),

        # Crescimento
        "revenueGrowth": _revenue_growth(stock),
        # earningsGrowth retornado como decimal pelo yfinance
        "earningsGrowth": _safe_float(info.get("earningsGrowth")),

        # Dividendos
        # trailingAnnualDividendYield usa os pagamentos reais dos últimos 12 meses,
        # ao contrário de dividendYield que é calculado sobre o preço atual e pode
        # distorcer para cima quando a ação cai bruscamente.
        "dividendYield": _safe_float(info.get("trailingAnnualDividendYield")),
        "dividendHistory": _dividend_history(stock),

        # Resultados trimestrais
        "quarterlyResults": _quarterly_results(stock),

        # Dados de mercado
        "beta": _safe_float(info.get("beta")),
        "fiftyTwoWeekHigh": _safe_float(info.get("fiftyTwoWeekHigh")),
        "fiftyTwoWeekLow": _safe_float(info.get("fiftyTwoWeekLow")),
        "averageVolume": _safe_int(info.get("averageVolume")),

        # Proveniência — sobrescrita pelo overlay CVM quando disponível
        "fundamentalsSource": "yfinance",
        "statementDate": None,
    }

    _cvm_overlay(data)
    return data


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
