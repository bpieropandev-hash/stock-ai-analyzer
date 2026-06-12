#!/usr/bin/env python3
"""
Fundamentos oficiais da CVM (dados abertos — ITR/DFP/FCA).

O yfinance para B3 tem P/L, P/VPA e DY frequentemente errados ou defasados.
Este módulo calcula os números contábeis direto dos demonstrativos entregues
à CVM (consolidados, com fallback para individuais):

- DRE LTM = YTD do ITR mais recente + exercício anual (DFP) − YTD do mesmo
  período do ano anterior (linhas PENÚLTIMO do próprio ITR)
- Balanço (BPA/BPP) do demonstrativo mais recente disponível
- Dividendos+JCP pagos (DFC, grupo 6.03) em janela LTM

Os zips anuais da CVM são cacheados em `.cvm_cache/` ao lado deste arquivo;
arquivos do ano corrente são renovados a cada 24h, anos fechados a cada 30
dias. O sidecar mantém os DataFrames em memória entre chamadas.
"""

import io
import re
import threading
import time
import zipfile
from pathlib import Path

import pandas as pd
import requests

CACHE_DIR = Path(__file__).resolve().parent / ".cvm_cache"
BASE_URL = "https://dados.cvm.gov.br/dados/CIA_ABERTA/DOC"

# Idade máxima do cache por tipo de arquivo
MAX_AGE_CURRENT_YEAR = 24 * 3600
MAX_AGE_CLOSED_YEAR = 30 * 24 * 3600
# Após uma falha de download, espera antes de tentar de novo — evita que o
# caminho de spawn (CLI) fique re-baixando dezenas de MB a cada análise
RETRY_COOLDOWN = 10 * 60

_DOWNLOAD_TIMEOUT = (10, 120)  # (connect, read) em segundos

# Memoização de DataFrames por (zip, membro) — vale a pena no sidecar
_frames: dict[tuple[str, str], pd.DataFrame] = {}
_frames_lock = threading.Lock()


# ---------------------------------------------------------------------------
# Download e cache dos zips anuais
# ---------------------------------------------------------------------------

def _zip_url(kind: str, year: int) -> str:
    return f"{BASE_URL}/{kind.upper()}/DADOS/{kind.lower()}_cia_aberta_{year}.zip"


def _zip_path(kind: str, year: int) -> Path:
    return CACHE_DIR / f"{kind.lower()}_cia_aberta_{year}.zip"


def _max_age(year: int) -> int:
    from datetime import date
    return MAX_AGE_CURRENT_YEAR if year >= date.today().year else MAX_AGE_CLOSED_YEAR


def _ensure_zip(kind: str, year: int) -> Path | None:
    """Garante o zip no cache; retorna None se indisponível (ex.: 404 do ano corrente)."""
    path = _zip_path(kind, year)
    if path.exists() and time.time() - path.stat().st_mtime < _max_age(year):
        return path

    marker = path.with_suffix(".failed")
    if marker.exists() and time.time() - marker.stat().st_mtime < RETRY_COOLDOWN:
        return path if path.exists() else None

    CACHE_DIR.mkdir(exist_ok=True)
    tmp = path.with_suffix(".tmp")
    try:
        resp = requests.get(_zip_url(kind, year), timeout=_DOWNLOAD_TIMEOUT, stream=True)
        if resp.status_code == 404:
            marker.touch()
            return path if path.exists() else None
        resp.raise_for_status()
        with open(tmp, "wb") as f:
            for chunk in resp.iter_content(chunk_size=1 << 20):
                f.write(chunk)
        tmp.replace(path)  # escrita atômica — nunca deixa zip truncado no cache
        marker.unlink(missing_ok=True)
        return path
    except Exception:
        tmp.unlink(missing_ok=True)
        marker.touch()
        # cache vencido ainda é melhor que nada
        return path if path.exists() else None


def _load_member(zip_path: Path, member: str, usecols: list[str]) -> pd.DataFrame | None:
    key = (str(zip_path), member)
    with _frames_lock:
        cached = _frames.get(key)
    if cached is not None:
        return cached
    try:
        with zipfile.ZipFile(zip_path) as zf:
            if member not in zf.namelist():
                return None
            with zf.open(member) as f:
                df = pd.read_csv(
                    io.TextIOWrapper(f, encoding="latin-1"),
                    sep=";", usecols=lambda c: c in usecols, dtype=str)
    except Exception:
        return None
    with _frames_lock:
        _frames[key] = df
    return df


_STMT_COLS = ["CNPJ_CIA", "DT_REFER", "VERSAO", "ORDEM_EXERC", "DT_INI_EXERC",
              "DT_FIM_EXERC", "ESCALA_MOEDA", "CD_CONTA", "DS_CONTA", "VL_CONTA"]


def _statement(kind: str, year: int, doc: str, cnpj: str) -> pd.DataFrame | None:
    """Linhas do demonstrativo (ex.: DRE_con) de uma companhia, na versão mais recente."""
    zip_path = _ensure_zip(kind, year)
    if zip_path is None:
        return None
    member = f"{kind.lower()}_cia_aberta_{doc}_{year}.csv"
    df = _load_member(zip_path, member, _STMT_COLS)
    if df is None:
        return None
    df = df[df["CNPJ_CIA"] == cnpj]
    if df.empty:
        return None
    # Re-apresentações: vale a maior versão da data de referência mais recente
    dt_refer = df["DT_REFER"].max()
    df = df[df["DT_REFER"] == dt_refer]
    return df[df["VERSAO"] == df["VERSAO"].max()]


def _con_or_ind(kind: str, year: int, doc_base: str, cnpj: str) -> pd.DataFrame | None:
    """Consolidado quando existe; individual como fallback (nem toda cia consolida)."""
    for suffix in ("con", "ind"):
        df = _statement(kind, year, f"{doc_base}_{suffix}", cnpj)
        if df is not None and not df.empty:
            return df
    return None


# ---------------------------------------------------------------------------
# Mapa ticker → CNPJ (FCA, formulário cadastral)
# ---------------------------------------------------------------------------

_ticker_map: dict[str, str] | None = None
_ticker_map_lock = threading.Lock()


def _build_ticker_map() -> dict[str, str]:
    from datetime import date
    for year in (date.today().year, date.today().year - 1):
        zip_path = _ensure_zip("fca", year)
        if zip_path is None:
            continue
        member = f"fca_cia_aberta_valor_mobiliario_{year}.csv"
        df = _load_member(zip_path, member,
                          ["CNPJ_Companhia", "Codigo_Negociacao", "Data_Referencia"])
        if df is None:
            continue
        df = df.dropna(subset=["Codigo_Negociacao"])
        df = df.sort_values("Data_Referencia")  # o último registro de cada ticker prevalece
        # Raiz de 4 caracteres pode conter dígito (B3SA3, M4RT3?) — só o 1º é
        # obrigatoriamente letra; sufixo numérico de 1-2 dígitos
        mapping = {
            str(t).strip().upper(): str(c).strip()
            for t, c in zip(df["Codigo_Negociacao"], df["CNPJ_Companhia"])
            if re.fullmatch(r"[A-Z][A-Z0-9]{3}\d{1,2}", str(t).strip().upper())
        }
        if mapping:
            return mapping
    return {}


def ticker_to_cnpj(ticker: str) -> str | None:
    global _ticker_map
    with _ticker_map_lock:
        if _ticker_map is None:
            _ticker_map = _build_ticker_map()
        return _ticker_map.get(ticker.upper().removesuffix(".SA"))


# ---------------------------------------------------------------------------
# Extração de contas
# ---------------------------------------------------------------------------

def _value(df: pd.DataFrame | None, code: str | None = None,
           desc: str | None = None, depth: int | None = None) -> float | None:
    """Valor de uma conta por código exato ou regex na descrição, já na escala certa."""
    if df is None or df.empty:
        return None
    rows = df
    if code is not None:
        rows = rows[rows["CD_CONTA"] == code]
    if desc is not None:
        rows = rows[rows["DS_CONTA"].str.contains(desc, case=False, na=False, regex=True)]
    if depth is not None:
        rows = rows[rows["CD_CONTA"].str.count(r"\.") == depth - 1]
    if rows.empty:
        return None
    return _scaled(rows.iloc[0])


def _sum_matching(df: pd.DataFrame | None, prefix: str, desc: str, depth: int) -> float | None:
    """Soma contas de mesmo nível sob um prefixo (ex.: empréstimos CP+LP)."""
    if df is None or df.empty:
        return None
    rows = df[df["CD_CONTA"].str.startswith(prefix)
              & df["DS_CONTA"].str.contains(desc, case=False, na=False, regex=True)
              & (df["CD_CONTA"].str.count(r"\.") == depth - 1)]
    if rows.empty:
        return None
    return sum(_scaled(r) for _, r in rows.iterrows())


def _scaled(row) -> float:
    value = float(str(row["VL_CONTA"]).replace(",", "."))
    return value * 1000 if str(row.get("ESCALA_MOEDA", "")).upper() == "MIL" else value


def _split_periods(dre: pd.DataFrame | None) -> tuple[pd.DataFrame | None, pd.DataFrame | None]:
    """Separa YTD corrente (ÚLTIMO) e YTD do mesmo período do ano anterior (PENÚLTIMO)."""
    if dre is None or dre.empty:
        return None, None
    ytd = {}
    for ordem in ("ÚLTIMO", "PENÚLTIMO"):
        rows = dre[dre["ORDEM_EXERC"].str.upper() == ordem]
        if rows.empty:
            ytd[ordem] = None
            continue
        # ITR traz o trimestre isolado e o acumulado; o acumulado começa em 01/01
        acc = rows[rows["DT_INI_EXERC"].str.endswith("-01-01", na=False)]
        ytd[ordem] = acc if not acc.empty else rows
    return ytd["ÚLTIMO"], ytd["PENÚLTIMO"]


# ---------------------------------------------------------------------------
# Fundamentos LTM
# ---------------------------------------------------------------------------

_NET_INCOME_DESC = r"lucro.*per[íi]odo|preju[íi]zo.*per[íi]odo"
_DIVIDENDS_DESC = r"dividendo|juros sobre (?:o )?capital"


def _dre_metrics(ytd: pd.DataFrame | None) -> dict[str, float | None]:
    # Lucro atribuído aos sócios da controladora quando existe (3.11.01) — é o
    # que faz par com o market cap; consolidado com minoritários como fallback
    net_income = _value(ytd, desc=r"atribu[íi]do a s[óo]cios da (?:empresa )?controladora")
    if net_income is None:
        net_income = _value(ytd, desc=_NET_INCOME_DESC, depth=2)
    return {
        "revenue": _value(ytd, code="3.01"),
        "ebit": _value(ytd, code="3.05"),
        "net_income": net_income,
    }


def _dividends_paid(dfc: pd.DataFrame | None) -> float | None:
    total = _sum_matching(dfc, "6.03", _DIVIDENDS_DESC, depth=3)
    if total is None:
        total = _sum_matching(dfc, "6.03", _DIVIDENDS_DESC, depth=4)
    return abs(total) if total is not None else None


def _ltm(curr: float | None, annual: float | None, prev: float | None) -> float | None:
    """LTM = YTD corrente + exercício anual anterior − YTD anterior do mesmo período."""
    if curr is None:
        return annual
    if annual is None or prev is None:
        return None
    return curr + annual - prev


def fetch_cvm_fundamentals(ticker: str) -> dict | None:
    """
    Fundamentos contábeis oficiais da companhia. Retorna None quando o ticker
    não está no cadastro da CVM ou os dados ainda não estão no cache local.
    Valores absolutos em BRL; razões em decimal (0.25 = 25%).
    """
    from datetime import date

    cnpj = ticker_to_cnpj(ticker)
    if cnpj is None:
        return None

    year = date.today().year
    itr_dre = _con_or_ind("itr", year, "DRE", cnpj)
    if itr_dre is None:
        itr_dre = _con_or_ind("itr", year - 1, "DRE", cnpj)

    # DFP do último exercício fechado — base do cálculo LTM
    dfp_year = None
    dfp_dre = None
    for y in (year - 1, year - 2):
        dfp_dre = _con_or_ind("dfp", y, "DRE", cnpj)
        if dfp_dre is not None:
            dfp_year = y
            break
    if dfp_dre is None:
        return None

    annual = _dre_metrics(dfp_dre[dfp_dre["ORDEM_EXERC"].str.upper() == "ÚLTIMO"])
    annual_prev = _dre_metrics(dfp_dre[dfp_dre["ORDEM_EXERC"].str.upper() == "PENÚLTIMO"])

    # O ITR só entra se for mais novo que o exercício do DFP
    itr_is_newer = (itr_dre is not None
                    and itr_dre["DT_FIM_EXERC"].max() > f"{dfp_year}-12-31")
    if itr_is_newer:
        ytd_curr, ytd_prev = _split_periods(itr_dre)
        curr, prev = _dre_metrics(ytd_curr), _dre_metrics(ytd_prev)
        revenue = _ltm(curr["revenue"], annual["revenue"], prev["revenue"])
        ebit = _ltm(curr["ebit"], annual["ebit"], prev["ebit"])
        net_income = _ltm(curr["net_income"], annual["net_income"], prev["net_income"])
        # YoY limpo: acumulado do ano vs mesmo acumulado do ano anterior
        revenue_growth = _yoy(curr["revenue"], prev["revenue"])
        earnings_growth = _yoy(curr["net_income"], prev["net_income"])
    else:
        revenue, ebit, net_income = annual["revenue"], annual["ebit"], annual["net_income"]
        revenue_growth = _yoy(annual["revenue"], annual_prev["revenue"])
        earnings_growth = _yoy(annual["net_income"], annual_prev["net_income"])

    # Balanço mais recente disponível (ITR novo > DFP)
    bal_kind, bal_year = ("itr", year) if itr_is_newer else ("dfp", dfp_year)
    bpa = _con_or_ind(bal_kind, bal_year, "BPA", cnpj)
    bpp = _con_or_ind(bal_kind, bal_year, "BPP", cnpj)
    bpp_last = bpp[bpp["ORDEM_EXERC"].str.upper() == "ÚLTIMO"] if bpp is not None else None
    bpa_last = bpa[bpa["ORDEM_EXERC"].str.upper() == "ÚLTIMO"] if bpa is not None else None

    total_assets = _value(bpa_last, code="1")
    equity = _value(bpp_last, desc=r"patrim[ôo]nio l[íi]quido", depth=2)
    debt_st = _sum_matching(bpp_last, "2.01.", r"empr[ée]stimos e financiamentos", depth=3)
    debt_lt = _sum_matching(bpp_last, "2.02.", r"empr[ée]stimos e financiamentos", depth=3)
    total_debt = (debt_st or 0) + (debt_lt or 0) if (debt_st is not None or debt_lt is not None) else None
    cash = _value(bpa_last, desc=r"caixa e equivalentes", depth=3)

    # Dividendos+JCP pagos (DFC) na mesma janela LTM da DRE
    dividends_paid = None
    for dfc_doc in ("DFC_MI", "DFC_MD"):
        dfp_dfc = _con_or_ind("dfp", dfp_year, dfc_doc, cnpj)
        if dfp_dfc is None:
            continue
        div_annual = _dividends_paid(dfp_dfc[dfp_dfc["ORDEM_EXERC"].str.upper() == "ÚLTIMO"])
        if itr_is_newer:
            itr_dfc = _con_or_ind("itr", year, dfc_doc, cnpj)
            ytd_c, ytd_p = _split_periods(itr_dfc)
            dividends_paid = _ltm(_dividends_paid(ytd_c), div_annual, _dividends_paid(ytd_p))
        else:
            dividends_paid = div_annual
        if dividends_paid is not None:
            break

    statement_date = (itr_dre["DT_FIM_EXERC"].max() if itr_is_newer
                      else dfp_dre["DT_FIM_EXERC"].max())

    return {
        "cnpj": cnpj,
        "statementDate": statement_date,
        "revenueLtm": revenue,
        "ebitLtm": ebit,
        "netIncomeLtm": net_income,
        "equity": equity,
        "totalDebt": total_debt,
        "totalCash": cash,
        "dividendsPaidLtm": dividends_paid,
        "revenueGrowth": revenue_growth,
        "earningsGrowth": earnings_growth,
        "roe": _ratio(net_income, equity),
        "roa": _ratio(net_income, total_assets),
        "netMargin": _ratio(net_income, revenue),
        "operatingMargin": _ratio(ebit, revenue),
        "debtToEquity": _ratio(total_debt, equity),
    }


def _yoy(curr: float | None, prev: float | None) -> float | None:
    """Crescimento YoY em decimal (0.12 = 12%)."""
    if curr is None or prev is None or prev == 0:
        return None
    return round((curr - prev) / abs(prev), 4)


def _ratio(num: float | None, den: float | None) -> float | None:
    if num is None or den is None or den == 0:
        return None
    return round(num / den, 4)


def warm_cache() -> None:
    """Pré-baixa os datasets e materializa o mapa de tickers (startup do sidecar)."""
    try:
        ticker_to_cnpj("PETR4")
        from datetime import date
        year = date.today().year
        for kind, y in (("itr", year), ("dfp", year - 1)):
            _ensure_zip(kind, y)
    except Exception:
        pass


if __name__ == "__main__":
    import json
    import sys

    if len(sys.argv) < 2:
        print("Uso: python cvm_data.py <TICKER>", file=sys.stderr)
        sys.exit(1)
    result = fetch_cvm_fundamentals(sys.argv[1].upper().removesuffix(".SA"))
    print(json.dumps(result, ensure_ascii=False, indent=2))
