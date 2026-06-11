package com.stockai.analysis;

import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Referências de múltiplos por setor da B3 para comparação relativa no prompt.
 *
 * Sem benchmark explícito, o LLM inventa a média setorial de memória — e os dois
 * modelos (Gemini/Groq) inventam valores diferentes. Estes são valores típicos
 * históricos da B3, intencionalmente em faixas largas; servem de âncora, não de
 * verdade absoluta.
 */
@Component
public class SectorBenchmarks {

    public record Benchmark(String plRange, String pvpaRange, String roeRange, String dyRange) {}

    private static final Map<SectorType, Benchmark> BENCHMARKS = Map.ofEntries(
            Map.entry(SectorType.ENERGIA,        new Benchmark("4-8",   "0.8-1.5", "15-30%", "8-14%")),
            Map.entry(SectorType.FINANCEIRO,     new Benchmark("6-10",  "1.0-2.0", "15-22%", "5-9%")),
            Map.entry(SectorType.VAREJO,         new Benchmark("10-20", "1.0-3.0", "5-15%",  "0-3%")),
            Map.entry(SectorType.MINERACAO,      new Benchmark("4-8",   "1.0-2.0", "15-30%", "6-12%")),
            Map.entry(SectorType.BEBIDAS,        new Benchmark("12-18", "2.0-3.5", "12-18%", "4-7%")),
            Map.entry(SectorType.INDUSTRIA,      new Benchmark("15-30", "3.0-8.0", "15-30%", "1-3%")),
            Map.entry(SectorType.LOGISTICA,      new Benchmark("10-20", "1.0-2.5", "8-15%",  "0-3%")),
            Map.entry(SectorType.PAPEL_CELULOSE, new Benchmark("6-12",  "1.0-2.0", "10-25%", "2-5%")),
            Map.entry(SectorType.IMOBILIARIO,    new Benchmark("6-12",  "0.7-1.3", "8-15%",  "6-10%")),
            Map.entry(SectorType.SAUDE,          new Benchmark("12-25", "1.5-3.5", "10-18%", "0-3%")),
            Map.entry(SectorType.OUTROS,         new Benchmark("8-15",  "1.0-2.5", "10-18%", "2-6%"))
    );

    public String describe(SectorType sector) {
        Benchmark b = BENCHMARKS.getOrDefault(sector, BENCHMARKS.get(SectorType.OUTROS));
        return ("Faixas típicas do setor na B3 — P/L: %s | P/VPA: %s | ROE: %s | Dividend Yield: %s. " +
                "Compare os múltiplos da empresa com estas faixas ao calibrar valuation e retornoAcionista.")
                .formatted(b.plRange(), b.pvpaRange(), b.roeRange(), b.dyRange());
    }
}
