package com.stockai.analysis;

import com.stockai.scheduler.PythonDataGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Referências de múltiplos por setor da B3 para comparação relativa no prompt.
 *
 * Sem benchmark explícito, o LLM inventa a média setorial de memória — e os dois
 * modelos (Gemini/Groq) inventam valores diferentes. A fonte primária são as
 * <b>medianas reais</b> dos pares do setor, calculadas dos demonstrativos CVM +
 * market cap (cacheadas no Redis por 24h). As faixas históricas estáticas ficam
 * como fallback quando o cálculo dinâmico não está disponível.
 *
 * Tickers de pares que mudaram de código ou saíram da bolsa são ignorados no
 * cálculo — a mediana usa quem respondeu (mínimo de 3 por métrica).
 */
@Component
public class SectorBenchmarks {

    private static final Logger log = LoggerFactory.getLogger(SectorBenchmarks.class);

    private static final String CACHE_PREFIX = "sector-benchmarks:";
    private static final Duration CACHE_TTL = Duration.ofHours(24);
    private static final int MIN_PEERS = 3;

    /** Pares líquidos por setor — a taxonomia segue o SectorClassifier atual. */
    private static final Map<SectorType, List<String>> PEERS = Map.ofEntries(
            Map.entry(SectorType.ENERGIA, List.of(
                    "PETR4", "PRIO3", "CSAN3", "UGPA3", "VBBR3",
                    "ELET3", "CMIG4", "EQTL3", "CPLE6", "TAEE11")),
            Map.entry(SectorType.FINANCEIRO, List.of(
                    "ITUB4", "BBDC4", "BBAS3", "SANB11", "B3SA3",
                    "BPAC11", "ITSA4", "BBSE3", "PSSA3", "CXSE3")),
            Map.entry(SectorType.VAREJO, List.of(
                    "MGLU3", "LREN3", "ASAI3", "RADL3", "AZZA3",
                    "VIVA3", "SBFG3", "PCAR3", "NTCO3")),
            Map.entry(SectorType.MINERACAO, List.of(
                    "VALE3", "CSNA3", "GGBR4", "USIM5", "CMIN3", "CBAV3", "BRAP4")),
            Map.entry(SectorType.BEBIDAS, List.of(
                    "ABEV3", "MDIA3", "CAML3", "BEEF3", "SMTO3")),
            Map.entry(SectorType.INDUSTRIA, List.of(
                    "WEGE3", "EMBR3", "TUPY3", "KEPL3", "FRAS3",
                    "RAPT4", "MYPK3", "INTB3", "TOTS3", "POMO4")),
            Map.entry(SectorType.LOGISTICA, List.of(
                    "RENT3", "MOVI3", "VAMO3", "RAIL3", "ECOR3", "JSLG3")),
            Map.entry(SectorType.PAPEL_CELULOSE, List.of(
                    "SUZB3", "KLBN11", "RANI3", "DXCO3")),
            Map.entry(SectorType.IMOBILIARIO, List.of(
                    "CYRE3", "EZTC3", "MRVE3", "DIRR3", "TEND3",
                    "JHSF3", "MULT3", "IGTI11", "ALOS3", "LOGG3")),
            Map.entry(SectorType.SAUDE, List.of(
                    "RDOR3", "HAPV3", "FLRY3", "ODPV3", "QUAL3", "HYPE3", "PNVL3"))
    );

    record StaticBenchmark(String plRange, String pvpaRange, String roeRange, String dyRange) {}

    private static final Map<SectorType, StaticBenchmark> STATIC_FALLBACK = Map.ofEntries(
            Map.entry(SectorType.ENERGIA,        new StaticBenchmark("4-8",   "0.8-1.5", "15-30%", "8-14%")),
            Map.entry(SectorType.FINANCEIRO,     new StaticBenchmark("6-10",  "1.0-2.0", "15-22%", "5-9%")),
            Map.entry(SectorType.VAREJO,         new StaticBenchmark("10-20", "1.0-3.0", "5-15%",  "0-3%")),
            Map.entry(SectorType.MINERACAO,      new StaticBenchmark("4-8",   "1.0-2.0", "15-30%", "6-12%")),
            Map.entry(SectorType.BEBIDAS,        new StaticBenchmark("12-18", "2.0-3.5", "12-18%", "4-7%")),
            Map.entry(SectorType.INDUSTRIA,      new StaticBenchmark("15-30", "3.0-8.0", "15-30%", "1-3%")),
            Map.entry(SectorType.LOGISTICA,      new StaticBenchmark("10-20", "1.0-2.5", "8-15%",  "0-3%")),
            Map.entry(SectorType.PAPEL_CELULOSE, new StaticBenchmark("6-12",  "1.0-2.0", "10-25%", "2-5%")),
            Map.entry(SectorType.IMOBILIARIO,    new StaticBenchmark("6-12",  "0.7-1.3", "8-15%",  "6-10%")),
            Map.entry(SectorType.SAUDE,          new StaticBenchmark("12-25", "1.5-3.5", "10-18%", "0-3%")),
            Map.entry(SectorType.OUTROS,         new StaticBenchmark("8-15",  "1.0-2.5", "10-18%", "2-6%"))
    );

    private final PythonDataGateway pythonGateway;
    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    public SectorBenchmarks(PythonDataGateway pythonGateway,
                            RedisTemplate<String, String> redisTemplate,
                            ObjectMapper objectMapper) {
        this.pythonGateway = pythonGateway;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    /** dynamic=true quando veio de medianas reais de pares (cache ou cálculo fresco); false = faixa estática. */
    public record BenchmarkInfo(String text, boolean dynamic) {}

    public String describe(SectorType sector) {
        return describeWithMeta(sector).text();
    }

    public BenchmarkInfo describeWithMeta(SectorType sector) {
        List<String> peers = PEERS.get(sector);
        if (peers == null) {
            return new BenchmarkInfo(staticDescribe(sector), false);
        }

        // Só se cacheia depois de um cálculo dinâmico bem-sucedido (ver cachePut abaixo) —
        // cache hit implica que já teve pares suficientes, sem precisar reconferir peerCount aqui.
        String cached = cacheGet(sector);
        if (cached != null) {
            return new BenchmarkInfo(cached, true);
        }

        try {
            String json = pythonGateway.sectorBenchmarks(String.join(",", peers));
            String text = formatDynamic(objectMapper.readTree(json));
            if (text != null) {
                cachePut(sector, text);
                return new BenchmarkInfo(text, true);
            }
        } catch (Exception e) {
            log.warn("Benchmark dinâmico indisponível para {} — usando faixas estáticas: {}",
                    sector, e.getMessage());
        }
        return new BenchmarkInfo(staticDescribe(sector), false);
    }

    /** Retorna null quando há pares de menos para a mediana significar algo. */
    String formatDynamic(JsonNode root) {
        if (root.path("peerCount").asInt(0) < MIN_PEERS) {
            return null;
        }
        JsonNode m = root.path("medians");
        StringBuilder sb = new StringBuilder("Medianas reais dos pares do setor na B3 (")
                .append(root.path("peerCount").asInt())
                .append(" empresas, demonstrativos CVM, calculadas em ")
                .append(root.path("computedAt").asText("N/D"))
                .append(") — ");

        appendMetric(sb, "P/L", m.path("priceToEarnings"), false);
        appendMetric(sb, "P/VPA", m.path("priceToBook"), false);
        appendMetric(sb, "ROE", m.path("roe"), true);
        appendMetric(sb, "Dividend Yield", m.path("dividendYield"), true);
        appendMetric(sb, "Margem líquida", m.path("netMargin"), true);
        appendMetric(sb, "Dívida/PL", m.path("debtToEquity"), false);

        sb.append("Compare os múltiplos da empresa com estas medianas ao calibrar ")
          .append("valuation e retornoAcionista.");
        return sb.toString();
    }

    private void appendMetric(StringBuilder sb, String label, JsonNode value, boolean percent) {
        if (value.isNull() || value.isMissingNode()) {
            return;
        }
        // Locale fixa — em pt-BR o %.1f viraria "8,9" e mudaria o prompt conforme a máquina
        if (percent) {
            sb.append(label).append(": ").append(String.format(Locale.ROOT, "%.1f%%", value.asDouble() * 100));
        } else {
            sb.append(label).append(": ").append(String.format(Locale.ROOT, "%.1f", value.asDouble()));
        }
        sb.append(" | ");
    }

    private String staticDescribe(SectorType sector) {
        StaticBenchmark b = STATIC_FALLBACK.getOrDefault(sector, STATIC_FALLBACK.get(SectorType.OUTROS));
        return ("Faixas típicas do setor na B3 (referência histórica) — P/L: %s | P/VPA: %s | ROE: %s | " +
                "Dividend Yield: %s. Compare os múltiplos da empresa com estas faixas ao calibrar " +
                "valuation e retornoAcionista.")
                .formatted(b.plRange(), b.pvpaRange(), b.roeRange(), b.dyRange());
    }

    // Redis fora do ar não pode derrubar a análise — benchmark é contexto, não dado vital
    private String cacheGet(SectorType sector) {
        try {
            return redisTemplate.opsForValue().get(CACHE_PREFIX + sector);
        } catch (Exception e) {
            return null;
        }
    }

    private void cachePut(SectorType sector, String text) {
        try {
            redisTemplate.opsForValue().set(CACHE_PREFIX + sector, text, CACHE_TTL);
        } catch (Exception e) {
            log.warn("Falha ao cachear benchmark de {}: {}", sector, e.getMessage());
        }
    }
}
