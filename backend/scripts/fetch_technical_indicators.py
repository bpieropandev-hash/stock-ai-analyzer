#!/usr/bin/env python3
"""Calcula indicadores técnicos para uma ação B3 usando yfinance e pandas."""
import json
import math
import sys

import pandas as pd
import yfinance as yf


def _safe(value) -> float | None:
    """Descarta NaN/Inf não-serializáveis em JSON."""
    try:
        f = float(value)
        return None if (math.isnan(f) or math.isinf(f)) else round(f, 4)
    except (TypeError, ValueError):
        return None


# ---------------------------------------------------------------------------
# Cálculo dos indicadores
# ---------------------------------------------------------------------------

def _rsi(close: pd.Series, period: int = 14) -> float | None:
    delta = close.diff()
    gain = delta.clip(lower=0)
    loss = -delta.clip(upper=0)
    # EWM com com=period-1 equivale ao método de Wilder (suavização 1/period)
    avg_gain = gain.ewm(com=period - 1, min_periods=period).mean()
    avg_loss = loss.ewm(com=period - 1, min_periods=period).mean()
    rs = avg_gain / avg_loss
    return _safe((100 - (100 / (1 + rs))).iloc[-1])


def _macd(close: pd.Series, fast=12, slow=26, signal=9) -> dict:
    ema_fast = close.ewm(span=fast, adjust=False).mean()
    ema_slow = close.ewm(span=slow, adjust=False).mean()
    macd_line = ema_fast - ema_slow
    signal_line = macd_line.ewm(span=signal, adjust=False).mean()
    histogram = macd_line - signal_line
    return {
        "macdLine": _safe(macd_line.iloc[-1]),
        "macdSignal": _safe(signal_line.iloc[-1]),
        "macdHistogram": _safe(histogram.iloc[-1]),
    }


def _bollinger(close: pd.Series, period: int = 20, std_dev: float = 2.0) -> dict:
    sma = close.rolling(window=period).mean()
    std = close.rolling(window=period).std()
    return {
        "bollingerUpper": _safe((sma + std_dev * std).iloc[-1]),
        "bollingerMiddle": _safe(sma.iloc[-1]),
        "bollingerLower": _safe((sma - std_dev * std).iloc[-1]),
    }


def _signal(rsi, macd_hist, price, sma20, sma50, b_upper, b_lower) -> str:
    """
    Pontuação composta: cada indicador contribui de -2 a +2.
    Positivo = bullish, negativo = bearish.
    """
    score = 0

    if rsi is not None:
        if rsi < 30:
            score += 2   # sobrevenda acentuada
        elif rsi < 45:
            score += 1
        elif rsi > 70:
            score -= 2   # sobrecompra acentuada
        elif rsi > 55:
            score -= 1

    if macd_hist is not None:
        score += 1 if macd_hist > 0 else -1

    if price is not None and sma20 is not None:
        score += 1 if price > sma20 else -1

    if price is not None and sma50 is not None:
        score += 1 if price > sma50 else -1

    if price is not None and b_upper is not None and b_lower is not None:
        band_range = b_upper - b_lower
        if band_range > 0:
            position = (price - b_lower) / band_range
            if position < 0.2:
                score += 1   # próximo da banda inferior → potencial reversão de alta
            elif position > 0.8:
                score -= 1   # próximo da banda superior → potencial reversão de baixa

    if score >= 4:
        return "STRONG_BUY"
    if score >= 2:
        return "BUY"
    if score <= -4:
        return "STRONG_SELL"
    if score <= -2:
        return "SELL"
    return "NEUTRAL"


# ---------------------------------------------------------------------------
# Função principal
# ---------------------------------------------------------------------------

def fetch_technical_indicators(ticker: str) -> dict:
    symbol = ticker + ".SA"
    hist = yf.Ticker(symbol).history(period="6mo")

    if hist.empty:
        raise ValueError(f"Sem dados históricos para {symbol}")

    close = hist["Close"]
    volume = hist["Volume"]

    current_price = _safe(close.iloc[-1])
    sma20 = _safe(close.rolling(20).mean().iloc[-1])
    sma50 = _safe(close.rolling(50).mean().iloc[-1])
    rsi = _rsi(close)
    macd = _macd(close)
    bollinger = _bollinger(close)

    avg_vol_20 = volume.rolling(20).mean().iloc[-1]
    volume_ratio = _safe(volume.iloc[-1] / avg_vol_20) if avg_vol_20 and avg_vol_20 > 0 else None

    signal = _signal(
        rsi,
        macd["macdHistogram"],
        current_price,
        sma20,
        sma50,
        bollinger["bollingerUpper"],
        bollinger["bollingerLower"],
    )

    return {
        "ticker": ticker,
        "currentPrice": current_price,
        "rsi": rsi,
        **macd,
        "sma20": sma20,
        "sma50": sma50,
        **bollinger,
        "volumeRatio": volume_ratio,
        "technicalSignal": signal,
    }


if __name__ == "__main__":
    if len(sys.argv) < 2:
        print("Uso: python fetch_technical_indicators.py <TICKER>", file=sys.stderr)
        sys.exit(1)

    raw_ticker = sys.argv[1].upper().removesuffix(".SA")
    try:
        print(json.dumps(fetch_technical_indicators(raw_ticker)))
    except Exception as exc:
        print(f"Erro ao calcular indicadores técnicos de {raw_ticker}: {exc}", file=sys.stderr)
        sys.exit(1)
