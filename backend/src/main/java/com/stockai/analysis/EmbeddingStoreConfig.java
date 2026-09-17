package com.stockai.analysis;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.ollama.OllamaEmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.pgvector.DefaultMetadataStorageConfig;
import dev.langchain4j.store.embedding.pgvector.MetadataStorageMode;
import dev.langchain4j.store.embedding.pgvector.PgVectorEmbeddingStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.List;

@Configuration
public class EmbeddingStoreConfig {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingStoreConfig.class);

    @Value("${embedding.store.table:stock_embeddings}")
    private String tableName;

    @Value("${ollama.base-url:http://localhost:11434}")
    private String ollamaBaseUrl;

    @Value("${ollama.embedding.model:nomic-embed-text}")
    private String embeddingModelName;

    // Dimensão fixa do nomic-embed-text; evita chamada HTTP ao Ollama no startup
    @Value("${ollama.embedding.dimension:768}")
    private int embeddingDimension;

    @Value("${gemini.api-key:}")
    private String geminiApiKey;

    @Value("${groq.api-key:}")
    private String groqApiKey;

    @Value("${pgvector.host:localhost}")
    private String pgHost;

    @Value("${pgvector.port:5432}")
    private int pgPort;

    @Value("${pgvector.database:stockai}")
    private String pgDatabase;

    @Value("${spring.datasource.username}")
    private String datasourceUser;

    @Value("${spring.datasource.password}")
    private String datasourcePassword;

    @Bean
    public EmbeddingModel embeddingModel() {
        return OllamaEmbeddingModel.builder()
                .baseUrl(ollamaBaseUrl)
                .modelName(embeddingModelName)
                .timeout(Duration.ofMinutes(3))
                .build();
    }

    // temperature 0.0 — scoring deve ser determinístico; variância de amostragem
    // contamina o histórico e dispara alertas por ruído, não por fato novo
    @Bean("geminiChatModel")
    public ChatModel geminiChatModel() {
        return OpenAiChatModel.builder()
                .baseUrl("https://generativelanguage.googleapis.com/v1beta/openai/")
                .apiKey(geminiApiKey)
                .modelName("gemini-2.5-flash")
                .responseFormat("json_object")
                .temperature(0.0)
                .maxTokens(8192)
                .build();
    }

    // "qwen/qwen3-32b" foi descomissionado pela Groq (confirmado em 2026-08-10 via
    // GET /v1/models real — respondia 404 model_not_found). Isso deixava o fallback
    // Gemini->Groq morto: qualquer falha do Gemini virava indisponibilidade total em
    // vez de degradar pro Groq. Ver docs/ai/anti-patterns.md.
    //
    // maxTokens 8192->4096 (2026-08-10): mesmo com o modelo certo, uma chamada
    // isolada real (concurrency=1) ainda tomava 413 da Groq — tier "on_demand" desta
    // conta reserva o max_tokens inteiro contra o limite de 8000 TPM antes de gerar,
    // então prompt (~1.5k tokens) + maxTokens(8192) sempre estourava. As 10 respostas
    // reais capturadas em analysis_audit nunca passaram de ~700 tokens — 4096 mantém
    // folga de ~6x sem aproximar do teto de TPM. Ver docs/ai/decisions.md.
    //
    // reasoningEffort("none") (2026-09-17): "qwen/qwen3.6-27b" é um modelo de
    // raciocínio — sem isso, ele gasta parte do budget de maxTokens num bloco
    // <think> interno antes do JSON, e picos de resposta longa estouravam os
    // 4096 tokens (400 json_validate_failed, failed_generation vazio). Validado
    // via tools/promptfoo (PETR4+VALE3, reasoning_effort=none): 0 erros vs. 10/10
    // antes, completion_tokens 682/723. Ver docs/ai/decisions.md.
    @Bean("groqChatModel")
    public ChatModel groqChatModel() {
        return OpenAiChatModel.builder()
                .baseUrl("https://api.groq.com/openai/v1")
                .apiKey(groqApiKey)
                .modelName("qwen/qwen3.6-27b")
                .responseFormat("json_object")
                .temperature(0.0)
                .maxTokens(4096)
                .reasoningEffort("none")
                .build();
    }

    @Bean
    public EmbeddingStore<TextSegment> embeddingStore() {
        try {
            return buildEmbeddingStore();
        } catch (Exception e) {
            Throwable cause = e;
            while (cause.getCause() != null) cause = cause.getCause();
            log.error("Falha ao inicializar PgVectorEmbeddingStore — causa raiz: [{}] {}",
                    cause.getClass().getName(), cause.getMessage(), e);
            throw e;
        }
    }

    private EmbeddingStore<TextSegment> buildEmbeddingStore() {
        log.info("PgVector — host={} port={} db={} user={}", pgHost, pgPort, pgDatabase, datasourceUser);
        return PgVectorEmbeddingStore.builder()
                .host(pgHost)
                .port(pgPort)
                .database(pgDatabase)
                .user(datasourceUser)
                .password(datasourcePassword)
                .table(tableName)
                .dimension(embeddingDimension)
                .createTable(true)
                .metadataStorageConfig(DefaultMetadataStorageConfig.builder()
                        .storageMode(MetadataStorageMode.COLUMN_PER_KEY)
                        .columnDefinitions(List.of("ticker TEXT", "date TEXT", "type TEXT"))
                        .build())
                .build();
    }
}
