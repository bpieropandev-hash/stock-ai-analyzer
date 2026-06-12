package com.stockai.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

/**
 * Ponto único de acesso aos dados Python.
 *
 * Cada spawn de processo paga 2–5s de import do yfinance/pandas. O sidecar
 * FastAPI ({@code scripts/sidecar_app.py}) mantém esses módulos carregados e
 * responde via HTTP local; este gateway o prefere e cai de volta para o
 * {@link PythonScriptRunner} (spawn por chamada) quando ele está fora do ar.
 *
 * Após uma falha de conexão, o sidecar entra em cooldown de 30s — evita pagar
 * timeout de conexão em toda chamada enquanto ele estiver parado.
 */
@Component
public class PythonDataGateway {

    private static final Logger log = LoggerFactory.getLogger(PythonDataGateway.class);

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration SIDECAR_COOLDOWN = Duration.ofSeconds(30);

    private final PythonScriptRunner scriptRunner;
    private final HttpClient http;

    /** Timestamp (ms) até o qual o sidecar é considerado indisponível. */
    private volatile long sidecarUnavailableUntil = 0;

    @Value("${python.sidecar.enabled:true}")
    private boolean sidecarEnabled;

    @Value("${python.sidecar.base-url:http://127.0.0.1:8001}")
    private String sidecarBaseUrl;

    @Value("${python.script.path:scripts/fetch_stock.py}")
    private String stockScriptPath;

    @Value("${python.script.fundamentals-path:scripts/fetch_fundamentals.py}")
    private String fundamentalsScriptPath;

    @Value("${python.script.macro-path:scripts/fetch_macro.py}")
    private String macroScriptPath;

    @Value("${python.script.news-path:scripts/fetch_news.py}")
    private String newsScriptPath;

    @Value("${python.script.sentiment-path:scripts/analyze_sentiment.py}")
    private String sentimentScriptPath;

    @Value("${python.script.technical-indicators-path:scripts/fetch_technical_indicators.py}")
    private String technicalScriptPath;

    @Value("${python.script.price-history-path:scripts/fetch_price_history.py}")
    private String priceHistoryScriptPath;

    @Value("${python.script.historical-fundamentals-path:scripts/fetch_historical_fundamentals.py}")
    private String historicalFundamentalsScriptPath;

    @Value("${python.script.sector-benchmarks-path:scripts/fetch_sector_benchmarks.py}")
    private String sectorBenchmarksScriptPath;

    public PythonDataGateway(PythonScriptRunner scriptRunner) {
        this.scriptRunner = scriptRunner;
        this.http = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();
    }

    // -------------------------------------------------------------------------
    // API pública — retorna o JSON bruto; lança exceção em falha total
    // -------------------------------------------------------------------------

    public String quotes() throws Exception {
        return fetch("/quotes", Duration.ofSeconds(55), stockScriptPath);
    }

    public String fundamentals(String ticker) throws Exception {
        return fetch("/fundamentals/" + ticker, Duration.ofSeconds(60), fundamentalsScriptPath, ticker);
    }

    public String macro() throws Exception {
        return fetch("/macro", Duration.ofSeconds(45), macroScriptPath);
    }

    public String news(String ticker) throws Exception {
        return fetch("/news/" + ticker, Duration.ofSeconds(30), newsScriptPath, ticker);
    }

    /** @param titlesJson array JSON de títulos, enviado como corpo (sidecar) ou stdin (script) */
    public String sentiment(String titlesJson) throws Exception {
        if (sidecarAvailable()) {
            try {
                return post("/sentiment", titlesJson, Duration.ofSeconds(30));
            } catch (Exception e) {
                markSidecarDown(e);
            }
        }
        PythonScriptRunner.ScriptResult result =
                scriptRunner.run(sentimentScriptPath, Duration.ofSeconds(30), titlesJson, Map.of());
        return requireOutput(result, sentimentScriptPath);
    }

    public String technicalIndicators(String ticker) throws Exception {
        return fetch("/technical/" + ticker, Duration.ofSeconds(60), technicalScriptPath, ticker);
    }

    public String priceHistory(String ticker, String period) throws Exception {
        return fetch("/price-history/" + ticker + "?period=" + period,
                Duration.ofSeconds(60), priceHistoryScriptPath, ticker, period);
    }

    public String historicalFundamentals(String ticker) throws Exception {
        return fetch("/historical-fundamentals/" + ticker,
                Duration.ofSeconds(90), historicalFundamentalsScriptPath, ticker);
    }

    /** @param tickersCsv pares do setor separados por vírgula (ex.: "PETR4,PRIO3") */
    public String sectorBenchmarks(String tickersCsv) throws Exception {
        return fetch("/sector-benchmarks?tickers=" + tickersCsv,
                Duration.ofSeconds(90), sectorBenchmarksScriptPath, tickersCsv);
    }

    // -------------------------------------------------------------------------
    // Sidecar HTTP com fallback para spawn
    // -------------------------------------------------------------------------

    private String fetch(String sidecarPath, Duration timeout, String scriptPath, String... args) throws Exception {
        if (sidecarAvailable()) {
            try {
                return get(sidecarPath, timeout);
            } catch (Exception e) {
                markSidecarDown(e);
            }
        }
        PythonScriptRunner.ScriptResult result = scriptRunner.run(scriptPath, timeout, args);
        return requireOutput(result, scriptPath);
    }

    private String get(String path, Duration timeout) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(sidecarBaseUrl + path))
                .timeout(timeout)
                .GET()
                .build();
        return execute(request, path);
    }

    private String post(String path, String body, Duration timeout) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(sidecarBaseUrl + path))
                .timeout(timeout)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        return execute(request, path);
    }

    private String execute(HttpRequest request, String path) throws Exception {
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() != 200) {
            throw new IllegalStateException(
                    "Sidecar respondeu %d para %s".formatted(response.statusCode(), path));
        }
        return response.body();
    }

    private boolean sidecarAvailable() {
        return sidecarEnabled && System.currentTimeMillis() >= sidecarUnavailableUntil;
    }

    private void markSidecarDown(Exception e) {
        // Erro de aplicação (HTTP != 200) não derruba o sidecar — só falha de transporte
        if (e instanceof java.io.IOException) {
            sidecarUnavailableUntil = System.currentTimeMillis() + SIDECAR_COOLDOWN.toMillis();
            log.warn("Sidecar Python indisponível ({}) — usando spawn por {}s",
                    e.getMessage(), SIDECAR_COOLDOWN.toSeconds());
        } else {
            log.warn("Sidecar Python falhou ({}) — usando spawn nesta chamada", e.getMessage());
        }
    }

    private String requireOutput(PythonScriptRunner.ScriptResult result, String scriptPath) {
        if (result.failed()) {
            throw new IllegalStateException(
                    "Script %s falhou (código %d): %s".formatted(scriptPath, result.exitCode(), result.stderr()));
        }
        return result.stdout();
    }
}
