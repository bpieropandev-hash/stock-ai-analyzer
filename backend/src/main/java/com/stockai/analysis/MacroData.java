package com.stockai.analysis;

import java.math.BigDecimal;
import java.util.List;

public record MacroData(
        BigDecimal selicPct,
        BigDecimal ipca12mPct,
        List<IpcaMensal> ipcaMensal,
        BigDecimal usdBrl,
        BigDecimal brentPrice,
        BigDecimal brentChangePct,
        BigDecimal wtiPrice,
        BigDecimal wtiChangePct,
        // Expectativas Focus (medianas) — análise usa expectativa, não só o dado corrente
        BigDecimal focusSelicCurrentYear,
        BigDecimal focusSelicNextYear,
        BigDecimal focusIpcaCurrentYear,
        BigDecimal focusIpcaNextYear
) {}
