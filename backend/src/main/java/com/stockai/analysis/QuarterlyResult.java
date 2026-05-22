package com.stockai.analysis;

import java.math.BigDecimal;

public record QuarterlyResult(String period, BigDecimal revenue, BigDecimal earnings) {}
