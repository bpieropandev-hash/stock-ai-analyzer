package com.stockai.stock;

/**
 * Ponto único de normalização de ticker. Antes desta classe, uppercase e o
 * sufixo .SA do yfinance eram tratados solto em pelo menos 4 arquivos
 * diferentes — portfolio_items/stock_alerts guardavam ticker sem sufixo
 * (ex.: "PETR4"), score_history guardava com sufixo (ex.: "PETR4.SA"), e
 * nada detectava a divergência porque não havia FK unindo as tabelas.
 */
public final class TickerNormalizer {

    private static final String YAHOO_SUFFIX = ".SA";

    private TickerNormalizer() {}

    /** Forma canônica persistida em {@link Stock}: ticker B3 puro, maiúsculo, sem sufixo do Yahoo. */
    public static String canonical(String rawTicker) {
        String upper = rawTicker.trim().toUpperCase();
        return upper.endsWith(YAHOO_SUFFIX)
                ? upper.substring(0, upper.length() - YAHOO_SUFFIX.length())
                : upper;
    }

    /** Formato exigido pelo yfinance (sidecar Python / spawn de processo). */
    public static String toYahooFormat(String rawTicker) {
        return canonical(rawTicker) + YAHOO_SUFFIX;
    }
}
