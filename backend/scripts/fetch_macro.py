#!/usr/bin/env python3
"""
Busca indicadores macroeconômicos do Brasil via API aberta do Banco Central (BCB/SGS).
Não requer autenticação. Retorna JSON no stdout.

Séries utilizadas:
  432 — Taxa Selic definida pelo Copom (% a.a.)
  433 — IPCA - variação mensal (%)
    1 — Taxa de câmbio USD/BRL - comercial (compra)
"""

import json
import math
import sys
import urllib.error
import urllib.request

BCB_URL = "https://api.bcb.gov.br/dados/serie/bcdata.sgs.{series}/dados/ultimos/{n}?formato=json"

SELIC_SERIES = 432
IPCA_SERIES = 433
USD_BRL_SERIES = 1


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

def _safe_float(value) -> float | None:
    """Converte para float; aceita vírgula como separador decimal (padrão BCB)."""
    try:
        f = float(str(value).replace(",", "."))
        return None if (math.isnan(f) or math.isinf(f)) else f
    except (TypeError, ValueError):
        return None


def _fetch_series(series: int, n: int) -> list[dict]:
    """Consulta a API SGS do BCB e retorna a lista de registros ou [] em caso de falha."""
    url = BCB_URL.format(series=series, n=n)
    try:
        req = urllib.request.Request(url, headers={"Accept": "application/json"})
        with urllib.request.urlopen(req, timeout=10) as resp:
            return json.loads(resp.read().decode("utf-8"))
    except (urllib.error.URLError, ValueError) as exc:
        print(f"Aviso: falha ao consultar série {series}: {exc}", file=sys.stderr)
        return []


def _ipca_acumulado_12m(registros: list[dict]) -> float | None:
    """
    Calcula IPCA acumulado composto dos últimos 12 meses.
    Fórmula: (∏(1 + mᵢ/100)) − 1, resultado em %.
    """
    if not registros:
        return None
    try:
        acumulado = 1.0
        for r in registros:
            v = _safe_float(r.get("valor"))
            if v is None:
                return None
            acumulado *= 1 + v / 100
        return round((acumulado - 1) * 100, 4)
    except Exception:
        return None


# ---------------------------------------------------------------------------
# Função principal
# ---------------------------------------------------------------------------

def fetch_macro() -> dict:
    selic_data = _fetch_series(SELIC_SERIES, n=1)
    ipca_data = _fetch_series(IPCA_SERIES, n=12)
    usd_data = _fetch_series(USD_BRL_SERIES, n=1)

    ipca_mensal = [
        {"date": r.get("data"), "value": _safe_float(r.get("valor"))}
        for r in ipca_data
    ]

    return {
        # Taxa Selic definida pelo Copom (% a.a.)
        "selicPct": _safe_float(selic_data[0].get("valor")) if selic_data else None,

        # IPCA acumulado composto dos últimos 12 meses (%)
        "ipca12mPct": _ipca_acumulado_12m(ipca_data),

        # Detalhamento mensal do IPCA (data no formato DD/MM/YYYY conforme BCB)
        "ipcaMensal": ipca_mensal,

        # Taxa de câmbio USD/BRL - comercial (compra)
        "usdBrl": _safe_float(usd_data[0].get("valor")) if usd_data else None,
    }


if __name__ == "__main__":
    try:
        result = fetch_macro()
        print(json.dumps(result))
    except Exception as exc:
        print(f"Erro ao buscar dados macroeconômicos: {exc}", file=sys.stderr)
        sys.exit(1)
