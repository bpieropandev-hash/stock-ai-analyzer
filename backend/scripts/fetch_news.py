#!/usr/bin/env python3
"""
Busca notícias via RSS do Google News para um ticker B3.
Usa apenas stdlib (urllib + xml.etree.ElementTree) — zero dependências externas.
Retorna JSON no stdout com as últimas 5 notícias.
"""

import json
import sys
import urllib.error
import urllib.parse
import urllib.request
import xml.etree.ElementTree as ET

NEWS_FEED_URL = (
    "https://news.google.com/rss/search"
    "?q={query}&hl=pt-BR&gl=BR&ceid=BR:pt-419"
)


def _build_url(ticker: str) -> str:
    query = urllib.parse.quote(f"{ticker} acao B3")
    return NEWS_FEED_URL.format(query=query)


def fetch_news(ticker: str) -> list[dict]:
    url = _build_url(ticker)
    try:
        req = urllib.request.Request(
            url,
            headers={"User-Agent": "Mozilla/5.0 (compatible; StockNewsBot/1.0)"},
        )
        with urllib.request.urlopen(req, timeout=10) as resp:
            xml_bytes = resp.read()
    except urllib.error.URLError as exc:
        print(f"Aviso: falha de rede ao buscar notícias para {ticker}: {exc}", file=sys.stderr)
        return []

    try:
        root = ET.fromstring(xml_bytes)
    except ET.ParseError as exc:
        print(f"Aviso: RSS malformado para {ticker}: {exc}", file=sys.stderr)
        return []

    channel = root.find("channel")
    if channel is None:
        return []

    news = []
    for item in channel.findall("item")[:5]:
        news.append({
            "title": item.findtext("title", ""),
            "description": item.findtext("description", ""),
            "publishedAt": item.findtext("pubDate", ""),
        })
    return news


if __name__ == "__main__":
    if len(sys.argv) < 2:
        print("Uso: fetch_news.py <TICKER>", file=sys.stderr)
        sys.exit(1)

    ticker_arg = sys.argv[1].upper()
    try:
        result = fetch_news(ticker_arg)
        print(json.dumps(result, ensure_ascii=False))
    except Exception as exc:
        print(f"Erro inesperado: {exc}", file=sys.stderr)
        sys.exit(1)
