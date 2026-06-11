package com.stockai.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Executor central de scripts Python.
 *
 * Resolve dois problemas do uso direto de ProcessBuilder:
 * 1. Deadlock de buffer — stdout e stderr são lidos em threads separadas; ler um
 *    stream por completo antes do outro trava quando o buffer do SO (~64KB) enche.
 * 2. Threads penduradas — waitFor sem timeout prende a thread do servidor para
 *    sempre se o script travar (ex.: yfinance sem resposta).
 */
@Component
public class PythonScriptRunner {

    private static final Logger log = LoggerFactory.getLogger(PythonScriptRunner.class);
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(60);

    public record ScriptResult(int exitCode, String stdout, String stderr) {
        public boolean failed() {
            return exitCode != 0 || stdout.isBlank();
        }
    }

    public ScriptResult run(String scriptPath, String... args) throws Exception {
        return run(scriptPath, DEFAULT_TIMEOUT, null, null, args);
    }

    public ScriptResult run(String scriptPath, Duration timeout, String... args) throws Exception {
        return run(scriptPath, timeout, null, null, args);
    }

    /**
     * @param stdin conteúdo enviado via stdin (ou null)
     * @param env   variáveis de ambiente extras (ou null)
     */
    public ScriptResult run(String scriptPath, Duration timeout, String stdin,
                            Map<String, String> env, String... args) throws Exception {
        List<String> command = new ArrayList<>();
        command.add("python");
        command.add(scriptPath);
        command.addAll(List.of(args));

        ProcessBuilder pb = new ProcessBuilder(command);
        if (env != null) pb.environment().putAll(env);

        Process process = pb.start();

        if (stdin != null) {
            process.getOutputStream().write(stdin.getBytes(StandardCharsets.UTF_8));
        }
        process.getOutputStream().close();

        // Leitura concorrente — virtual threads são baratas e evitam o deadlock de buffer
        CompletableFuture<String> stdoutFuture = readAsync(process.getInputStream());
        CompletableFuture<String> stderrFuture = readAsync(process.getErrorStream());

        boolean finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
        if (!finished) {
            process.destroyForcibly();
            throw new IllegalStateException(
                    "Script %s excedeu o timeout de %ds e foi encerrado".formatted(scriptPath, timeout.toSeconds()));
        }

        String stdout = stdoutFuture.get(5, TimeUnit.SECONDS);
        String stderr = stderrFuture.get(5, TimeUnit.SECONDS);

        if (!stderr.isBlank()) {
            log.warn("{}: {}", scriptPath, stderr.strip());
        }

        return new ScriptResult(process.exitValue(), stdout.strip(), stderr.strip());
    }

    private CompletableFuture<String> readAsync(java.io.InputStream stream) {
        CompletableFuture<String> future = new CompletableFuture<>();
        Thread.startVirtualThread(() -> {
            try {
                future.complete(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });
        return future;
    }
}
