package com.stockai.analysis;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.ollama.OllamaEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.pgvector.DefaultMetadataStorageConfig;
import dev.langchain4j.store.embedding.pgvector.MetadataStorageMode;
import dev.langchain4j.store.embedding.pgvector.PgVectorEmbeddingStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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

    @Value("${ollama.chat.model:llama3}")
    private String chatModelName;

    @Value("${spring.datasource.username}")
    private String datasourceUser;

    @Value("${spring.datasource.password}")
    private String datasourcePassword;

    @Bean
    public EmbeddingModel embeddingModel() {
        return OllamaEmbeddingModel.builder()
                .baseUrl(ollamaBaseUrl)
                .modelName(embeddingModelName)
                .build();
    }

    @Bean
    public ChatModel chatLanguageModel() {
        return OllamaChatModel.builder()
                .baseUrl(ollamaBaseUrl)
                .modelName(chatModelName)
                .responseFormat(ResponseFormat.JSON)
                .temperature(0.2)
                .numCtx(8192)
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
        log.info("Datasource user = {}", datasourceUser);
        log.info("Datasource password = [{}]", datasourcePassword);
        return PgVectorEmbeddingStore.builder()
                .host("localhost")
                .port(5432)
                .database("stockai")
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
