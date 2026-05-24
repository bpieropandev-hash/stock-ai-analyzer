package com.stockai.analysis;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.ollama.OllamaEmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.pgvector.DefaultMetadataStorageConfig;
import dev.langchain4j.store.embedding.pgvector.MetadataStorageMode;
import dev.langchain4j.store.embedding.pgvector.PgVectorEmbeddingStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.time.Duration;
import java.util.List;

@Configuration
public class EmbeddingStoreConfig {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingStoreConfig.class);

    // ThreadLocal que o wrapper preenche; StockAnalysisService lê após cada chamada
    static final ThreadLocal<String> ACTIVE_MODEL = ThreadLocal.withInitial(() -> "desconhecido");

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

    @Bean("geminiChatModel")
    public ChatModel geminiChatModel() {
        return OpenAiChatModel.builder()
                .baseUrl("https://generativelanguage.googleapis.com/v1beta/openai/")
                .apiKey(geminiApiKey)
                .modelName("gemini-3.5-flash")
                .responseFormat("json_object")
                .temperature(0.2)
                .maxTokens(4096)
                .build();
    }

    @Bean("groqChatModel")
    public ChatModel groqChatModel() {
        return OpenAiChatModel.builder()
                .baseUrl("https://api.groq.com/openai/v1")
                .apiKey(groqApiKey)
                .modelName("qwen-qwen3-32b")
                .responseFormat("json_object")
                .temperature(0.2)
                .maxTokens(4096)
                .build();
    }

    @Bean
    @Primary
    public ChatModel chatModel(
            @Qualifier("geminiChatModel") ChatModel gemini,
            @Qualifier("groqChatModel") ChatModel groq) {
        return new ChatModel() {
            @Override
            public ChatResponse chat(ChatRequest request) {
                try {
                    ChatResponse response = gemini.chat(request);
                    ACTIVE_MODEL.set("Gemini");
                    return response;
                } catch (Exception e) {
                    log.warn("Gemini indisponível ({}), ativando fallback Groq", e.getMessage());
                    ChatResponse response = groq.chat(request);
                    ACTIVE_MODEL.set("Groq (fallback)");
                    return response;
                }
            }
        };
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
