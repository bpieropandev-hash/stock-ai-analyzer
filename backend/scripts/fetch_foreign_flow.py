#!/usr/bin/env python3
"""
Busca fluxo de capital estrangeiro na B3.
A página é renderizada por JavaScript, então o parse direto via urllib retorna HTML vazio.
O fallback retorna dados simulados consistentes com o cenário macro atual.
"""
import json
import sys
import urllib.error
import urllib.request
from datetime import datetime

B3_URL = (
    "https://www.b3.com.br/pt_br/market-data-e-indices/servicos-de-dados/"
    "market-data/consultas/mercado-a-vista/estrangeiros/"
)

# Premissas macro usadas para calibrar o fallback simulado
_SELIC_PCT = 14.75
_USD_BRL = 5.80


def _simulated() -> dict:
    """
    Com Selic a {selic}% a.a. e dólar a {usd}, juros reais elevados
    reduzem o apetite de capital estrangeiro por renda variável brasileira.
    Simula saída líquida moderada consistente com esse cenário.
    """
    return {
        "netFlow": -420_000_000,   # saída líquida de ~R$ 420 mi
        "trend": "OUTFLOW",
        "lastUpdate": datetime.now().strftime("%Y-%m-%d"),
        "source": "simulated",
        "note": f"Dados simulados — Selic {_SELIC_PCT}% a.a., USD/BRL {_USD_BRL}",
    }


def fetch_foreign_flow() -> dict:
    try:
        req = urllib.request.Request(
            B3_URL,
            headers={"User-Agent": "Mozilla/5.0 (compatible; stock-ai-analyzer/1.0)"},
        )
        with urllib.request.urlopen(req, timeout=10) as resp:
            html = resp.read().decode("utf-8", errors="replace")

        # Página é SPA (React) — o HTML bruto não contém dados tabulares
        if len(html) < 5_000 or "ng-version" in html or "__NEXT_DATA__" in html:
            raise ValueError("Conteúdo é SPA/JS-rendered — dados não acessíveis via HTTP simples")

        # Placeholder para implementação futura de parse quando/se a B3 expuser API REST
        raise NotImplementedError("Parse de HTML dinâmico não implementado")

    except Exception as e:
        print(f"Fallback ativado ({type(e).__name__}): {e}", file=sys.stderr)
        return _simulated()


if __name__ == "__main__":
    try:
        print(json.dumps(fetch_foreign_flow()))
    except Exception as exc:
        print(f"Erro ao buscar fluxo estrangeiro: {exc}", file=sys.stderr)
        sys.exit(1)
