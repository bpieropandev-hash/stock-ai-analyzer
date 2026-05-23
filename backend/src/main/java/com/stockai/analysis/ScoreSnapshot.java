package com.stockai.analysis;

import java.time.LocalDate;

public record ScoreSnapshot(
        LocalDate date,
        double scoreGeral,
        double fundamentos,
        double valuation,
        double regimeMomentum,
        double sentimentoInstitucional,
        double retornoAcionista,
        double gestaoRisco
) {}
